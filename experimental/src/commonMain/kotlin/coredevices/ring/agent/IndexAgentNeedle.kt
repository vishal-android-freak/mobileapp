package coredevices.ring.agent

import co.touchlab.kermit.Logger
import coredevices.indexai.agent.AgentToolCall
import coredevices.indexai.agent.ToolCallingAgent
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.data.entity.FunctionToolCall
import coredevices.indexai.data.entity.MessageRole
import coredevices.indexai.data.entity.ToolCall
import coredevices.mcp.SessionContext
import coredevices.mcp.client.McpSession
import coredevices.mcp.client.McpSessionTool
import coredevices.ring.agent.builtin_servlets.googlehome.ControlGoogleHomeDeviceToolConstants
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Clock

/**
 * On-device agent backed by Needle 2, whose weights are embedded in the native
 * library — nothing to download, ~25MB peak RAM, and no network at any point.
 *
 * Single round by design: [ToolCallingAgent] rather than [coredevices.indexai.agent.IterativeAgent].
 * Needle answers one query per `complete()` and has no free-text fallback, which suits
 * an imperative voice command ("lock my screen") and does not suit a question whose
 * answer has to be composed from a tool's output.
 */
class IndexAgentNeedle(
    private val runtime: NeedleRuntime,
    conversation: List<ConversationMessageDocument>,
) : ToolCallingAgent(conversation) {
    override val label = "Needle"

    override val logger: Logger = Logger.withTag("IndexAgentNeedle")

    companion object {
        /** Confidence at or below which the answer is not worth acting on. Observed on
         *  device: unambiguous commands land 0.75–1.00, genuinely ambiguous ones
         *  (a reminder with no concrete time, "pause" with no named player) 0.44–0.48. */
        const val LOW_CONFIDENCE = 0.6f

        private val json = Json { ignoreUnknownKeys = true }
    }

    /** Set from the last response so a caller can decide whether to escalate. */
    var lastConfidence: Float? = null
        private set

    private data class ToolTarget(
        val integrationName: String,
        val toolName: String,
    )

    private fun selectTools(input: String, tools: List<McpSessionTool>): List<McpSessionTool> {
        val words = input.lowercase()
        val isLightControl = listOf("light", "lights", "lamp", "lamps", "bulb", "bulbs")
            .any(words::contains) &&
            listOf("turn on", "turn off", "switch on", "switch off", "toggle", "brightness", "dim", "brighten")
                .any(words::contains)
        if (!isLightControl) return tools

        val googleHomeTools = tools.filter { (_, tool) ->
            tool.definition.name.substringAfter("__") ==
                ControlGoogleHomeDeviceToolConstants.TOOL_NAME
        }
        return googleHomeTools.ifEmpty { tools }
    }

    /**
     * Needle takes short names; the composite `integration__tool` form is mapped back
     * afterwards, exactly as the Cactus path does.
     */
    private fun prepareTools(tools: List<McpSessionTool>): Pair<String, Map<String, ToolTarget>> {
        val targets = mutableMapOf<String, ToolTarget>()
        val array = buildJsonArray {
            tools.forEach { (integrationName, tool) ->
                val definition = tool.definition
                val shortName = definition.name.substringAfter("__")
                val modelNames = if (
                    shortName == ControlGoogleHomeDeviceToolConstants.TOOL_NAME
                ) {
                    ControlGoogleHomeDeviceToolConstants.NEEDLE_TOOL_ALIASES
                } else {
                    listOf(shortName)
                }
                modelNames.forEach { modelName ->
                    targets[modelName] = ToolTarget(integrationName, shortName)
                    add(
                        buildJsonObject {
                        // Flat shape: Needle wants {name, description, parameters}, not
                        // OpenAI's {type, function:{...}} wrapper.
                        put("name", modelName)
                        put("description", definition.description ?: shortName)
                        put(
                            "parameters",
                            buildJsonObject {
                                put("type", "object")
                                put(
                                    "properties",
                                    definition.inputSchema.properties ?: JsonObject(emptyMap())
                                )
                                put(
                                    "required",
                                    buildJsonArray {
                                        definition.inputSchema.required?.forEach { add(JsonPrimitive(it)) }
                                    }
                                )
                            }
                        )
                        }
                    )
                }
            }
        }
        return array.toString() to targets
    }

    /**
     * Needle's system turn carries environment *facts*, not instructions — its own
     * documentation is explicit that instructions placed there do not steer the model.
     * So this deliberately does not pass the agent prompt; without a `date:` fact,
     * "remind me tomorrow at 7" cannot be resolved to an absolute time.
     */
    private fun systemFacts(): String {
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(zone)
        val day = now.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        val hour = now.hour.toString().padStart(2, '0')
        val minute = now.minute.toString().padStart(2, '0')
        return "date: ${now.date} $day $hour:$minute; device: phone"
    }

    override suspend fun runInference(
        input: String,
        history: List<ConversationMessageDocument>,
        tools: List<McpSessionTool>,
        mcpSession: McpSession,
        sessionContext: SessionContext,
        includePromptsFromMcps: Map<String, Set<String>>,
    ): ConversationMessageDocument {
        val selectedTools = selectTools(input, tools)
        val (toolsJson, targets) = prepareTools(selectedTools)

        val raw = runtime.complete(systemFacts(), toolsJson, input)

        val response = try {
            json.parseToJsonElement(raw).jsonObject
        } catch (e: Exception) {
            logger.e(e) { "Could not parse Needle response: ${raw.take(200)}" }
            throw IllegalStateException("Malformed Needle response", e)
        }

        lastConfidence = response["confidence"]?.jsonPrimitive?.float
        val text = response["message"]?.jsonPrimitive?.contentOrNullSafe()
        val calls = response["function_calls"]?.jsonArray ?: buildJsonArray {}

        logger.i {
            "Needle -> type=${response["type"]?.jsonPrimitive?.content} " +
                "tools=${selectedTools.size} calls=${calls.size} confidence=$lastConfidence"
        }

        return ConversationMessageDocument(
            role = MessageRole.assistant,
            content = text?.takeIf { it.isNotBlank() },
            tool_calls = calls.mapNotNull { element ->
                val call = element.jsonObject
                val modelName = call["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val target = targets[modelName]
                if (target == null) {
                    logger.w { "Unknown tool name from model: $modelName" }
                    return@mapNotNull null
                }
                ToolCall(
                    id = "${target.integrationName}.${target.toolName}",
                    type = "function",
                    function = FunctionToolCall(
                        name = "${target.integrationName}.${target.toolName}",
                        // Needle returns `arguments` as an object; the wire format the
                        // rest of the app stores is a JSON string.
                        arguments = (call["arguments"] ?: JsonObject(emptyMap())).toString(),
                    ),
                )
            },
            language_model_used = "needle2",
        )
    }

    override fun decodeToolCalls(
        assistantMessage: ConversationMessageDocument
    ): List<AgentToolCall> {
        return (assistantMessage.tool_calls ?: emptyList()).mapNotNull { call ->
            val name = call.function?.name ?: call.id
            val parts = name.split(".", limit = 2)
            if (parts.size != 2) {
                logger.w { "Malformed composite tool name: $name" }
                return@mapNotNull null
            }
            val args: Map<String, kotlinx.serialization.json.JsonElement> = try {
                json.decodeFromString(call.function?.arguments ?: "{}")
            } catch (e: Exception) {
                logger.w(e) { "Could not decode arguments for $name" }
                emptyMap()
            }
            AgentToolCall(
                id = call.id,
                integrationName = parts[0],
                toolName = parts[1],
                arguments = args,
            )
        }
    }
}

/** `JsonPrimitive.content` returns "null" for JsonNull, which is never wanted here. */
private fun JsonPrimitive.contentOrNullSafe(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content

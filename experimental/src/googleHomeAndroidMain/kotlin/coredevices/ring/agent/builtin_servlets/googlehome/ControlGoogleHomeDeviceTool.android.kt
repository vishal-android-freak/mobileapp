package coredevices.ring.agent.builtin_servlets.googlehome

import co.touchlab.kermit.Logger
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

actual class ControlGoogleHomeDeviceTool : BuiltInMcpTool(
    definition = Tool(
        name = ControlGoogleHomeDeviceToolConstants.TOOL_NAME,
        description = ControlGoogleHomeDeviceToolConstants.TOOL_DESCRIPTION,
        inputSchema = ControlGoogleHomeDeviceToolConstants.INPUT_SCHEMA,
    ),
    extraContext = "Use Google Home only when the user explicitly asks to control a home device.",
), KoinComponent {
    private val controller: GoogleHomeController by inject()

    actual override suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult {
        return try {
            val args = JsonSnake.decodeFromString<ControlGoogleHomeDeviceArgs>(jsonInput)
            when (val outcome = controller.control(
                deviceName = args.deviceName,
                roomName = args.roomName,
                action = args.action,
                brightnessPercent = args.brightnessPercent,
            )) {
                is GoogleHomeControlOutcome.Success -> ToolCallResult(
                    resultString = JsonSnake.encodeToString(
                        ControlResult(success = true, deviceName = outcome.device.name)
                    ),
                    semanticResult = SemanticResult.GenericSuccess,
                )
                is GoogleHomeControlOutcome.Failure -> failure(outcome.message)
            }
        } catch (e: Exception) {
            logger.e(e) { "Google Home control failed" }
            failure(e.message ?: "Google Home control failed")
        }
    }

    private fun failure(message: String) = ToolCallResult(
        resultString = JsonSnake.encodeToString(ControlResult(success = false, errorMessage = message)),
        semanticResult = SemanticResult.GenericFailure(message, llmRecoverable = false),
    )

    @Serializable
    private data class ControlResult(
        val success: Boolean,
        val deviceName: String? = null,
        val errorMessage: String? = null,
    )

    private companion object {
        val logger = Logger.withTag("ControlGoogleHomeDeviceTool")
    }
}

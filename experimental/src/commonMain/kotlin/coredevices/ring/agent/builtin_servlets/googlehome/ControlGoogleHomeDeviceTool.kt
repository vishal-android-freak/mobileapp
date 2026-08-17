package coredevices.ring.agent.builtin_servlets.googlehome

import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.sdk.types.toJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

expect class ControlGoogleHomeDeviceTool() : BuiltInMcpTool {
    override suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult
}

@Serializable
internal data class ControlGoogleHomeDeviceArgs(
    @SerialName("device_name") val deviceName: String,
    val action: GoogleHomeAction,
    @SerialName("room_name") val roomName: String? = null,
    @SerialName("brightness_percent") val brightnessPercent: Int? = null,
    @SerialName("fan_speed_percent") val fanSpeedPercent: Int? = null,
    @SerialName("fan_speed_name") val fanSpeedName: String? = null,
)

@Serializable
internal enum class GoogleHomeAction {
    @SerialName("on") On,
    @SerialName("off") Off,
    @SerialName("toggle") Toggle,
    @SerialName("set_brightness") SetBrightness,
    @SerialName("set_fan_speed") SetFanSpeed,
}

internal object ControlGoogleHomeDeviceToolConstants {
    const val TOOL_NAME = "control_google_home_device"
    const val NEEDLE_LIGHT_ALIAS = "control_light"
    const val NEEDLE_FAN_ALIAS = "set_volume"
    const val TOOL_DESCRIPTION =
        "Control a named device already connected to the user's Google Home. Supports turning " +
            "lights, switches, plugs, fans, displays, and TVs on or off, toggling them, and " +
            "setting light brightness or fan speed. Fan speed capabilities are discovered from " +
            "the selected device at runtime."

    val INPUT_SCHEMA = ToolSchema(
        properties = JsonObject(
            mapOf(
                "device_name" to JsonObject(
                    mapOf(
                        "type" to "string",
                        "description" to "Device name exactly as the user said it, for example 'desk lamp'.",
                    ).toJson()
                ),
                "room_name" to JsonObject(
                    mapOf(
                        "type" to "string",
                        "description" to "Room name when the user included one, used to disambiguate devices.",
                    ).toJson()
                ),
                "action" to JsonObject(
                    mapOf(
                        "type" to "string",
                        "enum" to JsonArray(
                            listOf("on", "off", "toggle", "set_brightness", "set_fan_speed")
                                .map(::JsonPrimitive)
                        ),
                    ).toJson()
                ),
                "brightness_percent" to JsonObject(
                    mapOf(
                        "type" to "integer",
                        "minimum" to 0,
                        "maximum" to 100,
                        "description" to "Required only for set_brightness.",
                    ).toJson()
                ),
                "fan_speed_percent" to JsonObject(
                    mapOf(
                        "type" to "integer",
                        "minimum" to 0,
                        "maximum" to 100,
                        "description" to "Fan speed from 0 to 100. For set_fan_speed, provide this or fan_speed_name, but not both.",
                    ).toJson()
                ),
                "fan_speed_name" to JsonObject(
                    mapOf(
                        "type" to "string",
                        "description" to "A fan speed or mode name said by the user, such as a device-provided speed name. For set_fan_speed, provide this or fan_speed_percent, but not both.",
                    ).toJson()
                ),
            )
        ),
        required = listOf("device_name", "action"),
    )

    fun needleDescription(alias: String): String = when (alias) {
        NEEDLE_LIGHT_ALIAS ->
            "Turn a Google Home light on or off, toggle it, or set its brightness."
        NEEDLE_FAN_ALIAS ->
            "Set a named Google Home fan's speed percentage."
        else -> TOOL_DESCRIPTION
    }

    fun needleFixedAction(alias: String): String? = when (alias) {
        NEEDLE_FAN_ALIAS -> "set_fan_speed"
        else -> null
    }

    fun needleInputSchema(alias: String): ToolSchema {
        val actionValues: List<String>
        val actionSpecificProperties: Map<String, JsonObject>
        when (alias) {
            NEEDLE_LIGHT_ALIAS -> {
                actionValues = listOf("on", "off", "toggle", "set_brightness")
                actionSpecificProperties = mapOf(
                    "brightness_percent" to inputProperty("brightness_percent"),
                )
            }
            NEEDLE_FAN_ALIAS -> {
                actionValues = emptyList()
                actionSpecificProperties = mapOf(
                    "percent" to JsonObject(
                        mapOf(
                            "type" to "integer",
                            "minimum" to 0,
                            "maximum" to 100,
                            "description" to "Fan speed percentage.",
                        ).toJson()
                    ),
                )
            }
            else -> return INPUT_SCHEMA
        }
        val properties = mutableMapOf(
            "device_name" to inputProperty("device_name"),
            "room_name" to inputProperty("room_name"),
        )
        if (actionValues.isNotEmpty()) {
            properties["action"] = JsonObject(
                mapOf(
                    "type" to "string",
                    "enum" to JsonArray(actionValues.map(::JsonPrimitive)),
                ).toJson()
            )
        }
        properties.putAll(actionSpecificProperties)
        return ToolSchema(
            properties = JsonObject(
                properties
            ),
            required = if (alias == NEEDLE_FAN_ALIAS) {
                listOf("device_name", "percent")
            } else {
                listOf("device_name", "action")
            },
        )
    }

    private fun inputProperty(name: String): JsonObject =
        requireNotNull(INPUT_SCHEMA.properties?.get(name)) { "Missing Google Home tool property: $name" }
            .jsonObject
}

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

expect class ControlGoogleHomeDeviceTool() : BuiltInMcpTool {
    override suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult
}

@Serializable
internal data class ControlGoogleHomeDeviceArgs(
    @SerialName("device_name") val deviceName: String,
    val action: GoogleHomeAction,
    @SerialName("room_name") val roomName: String? = null,
    @SerialName("brightness_percent") val brightnessPercent: Int? = null,
)

@Serializable
internal enum class GoogleHomeAction {
    @SerialName("on") On,
    @SerialName("off") Off,
    @SerialName("toggle") Toggle,
    @SerialName("set_brightness") SetBrightness,
}

internal object ControlGoogleHomeDeviceToolConstants {
    const val TOOL_NAME = "control_google_home_device"
    val NEEDLE_TOOL_ALIASES = listOf("control_light")
    const val TOOL_DESCRIPTION =
        "Control a named device already connected to the user's Google Home. Supports turning " +
            "lights, switches, plugs, fans, displays, and TVs on or off, toggling them, and " +
            "setting light brightness."

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
                            listOf("on", "off", "toggle", "set_brightness").map(::JsonPrimitive)
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
            )
        ),
        required = listOf("device_name", "action"),
    )
}

package coredevices.ring.agent.builtin_servlets.googlehome

import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool

actual class ControlGoogleHomeDeviceTool : BuiltInMcpTool(
    definition = Tool(
        name = ControlGoogleHomeDeviceToolConstants.TOOL_NAME,
        description = ControlGoogleHomeDeviceToolConstants.TOOL_DESCRIPTION,
        inputSchema = ControlGoogleHomeDeviceToolConstants.INPUT_SCHEMA,
    )
) {
    actual override suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult =
        ToolCallResult(
            resultString = "{\"success\":false,\"error_message\":\"Google Home is only available on Android\"}",
            semanticResult = SemanticResult.GenericFailure(
                "Google Home control is only available on Android",
                llmRecoverable = false,
            ),
        )
}

package coredevices.ring.agent.builtin_servlets.googlehome

import coredevices.mcp.client.BuiltInMcpIntegration

object GoogleHomeServlet : BuiltInMcpIntegration(
    name = "builtin_google_home",
    tools = listOf(ControlGoogleHomeDeviceTool()),
) {
    const val NAME = "builtin_google_home"
}

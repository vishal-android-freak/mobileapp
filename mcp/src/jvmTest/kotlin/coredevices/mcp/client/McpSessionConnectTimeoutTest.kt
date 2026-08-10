package coredevices.mcp.client

import coredevices.mcp.McpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.ToolCallResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class McpSessionConnectTimeoutTest {
    /** Stands in for a server that is reachable but never completes the MCP handshake. */
    private class StubIntegration(
        override val name: String,
        private val connectDelay: Duration
    ) : McpIntegration {
        var connected = false
        override suspend fun resetCache() = Unit
        override suspend fun connect() {
            delay(connectDelay)
            connected = true
        }
        override suspend fun close() = Unit
        override suspend fun listTools(): List<McpTool> = emptyList()
        override suspend fun callTool(
            toolName: String,
            json: Map<String, JsonElement>,
            context: SessionContext
        ): ToolCallResult = error("not called")
        override suspend fun getExtraContext(sessionContext: SessionContext?): String? = null
    }

    @Test
    fun hangingIntegrationDoesNotBlockSessionOpen() = runTest {
        val stuck = StubIntegration("stuck", Duration.INFINITE)

        McpSession(listOf(stuck), this).openSession()

        assertFalse(stuck.connected, "connect() should have been cancelled by the timeout")
    }

    @Test
    fun hangingIntegrationDoesNotBlockLaterOnes() = runTest {
        val stuck = StubIntegration("stuck", Duration.INFINITE)
        val healthy = StubIntegration("healthy", Duration.ZERO)

        McpSession(listOf(stuck, healthy), this).openSession()

        assertFalse(stuck.connected)
        assertTrue(healthy.connected, "a dead server must not stop the rest from connecting")
    }

    @Test
    fun slowButResponsiveIntegrationStillConnects() = runTest {
        val slow = StubIntegration("slow", 5.seconds)

        McpSession(listOf(slow), this).openSession()

        assertTrue(slow.connected)
    }
}

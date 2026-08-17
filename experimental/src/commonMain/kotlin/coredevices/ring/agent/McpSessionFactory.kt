package coredevices.ring.agent

import coredevices.indexai.data.entity.mcp_sandbox.HttpMcpServerEntity
import coredevices.mcp.client.HttpMcpIntegration
import coredevices.mcp.client.HttpMcpProtocol
import coredevices.mcp.client.McpSession
import coredevices.ring.database.room.repository.McpSandboxRepository
import coredevices.ring.database.room.repository.McpServerEntry
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

private val implementation = Implementation(
    name = "CoreApp",
    version = "0.0.1"
)

class McpSessionFactory(
    private val mcpSandboxRepository: McpSandboxRepository,
    private val builtinServletRepository: BuiltinServletRepository
) {
    suspend fun createForSandboxGroup(groupId: Long, scope: CoroutineScope): McpSession {
        // Resolved so a bad id fails here rather than as an empty tool list.
        mcpSandboxRepository.getGroupById(groupId)
            ?: throw IllegalArgumentException("MCP Sandbox group with id $groupId not found")
        // The on-device agent gets the group's own MCP servers too: Needle 2 selects
        // across the full catalogue, so withholding them would only hide tools.
        return McpSession(groupIntegrations(groupId), scope)
    }

    /**
     * Session for invoking a tool the caller has already named, with no model in the loop.
     *
     * Deliberately ignores the group's model type: the Needle restriction exists because that
     * model can only emit tool names it was trained on, which says nothing about what can be
     * dispatched directly. Gating this on it would hide HTTP MCP servers from callers that
     * never ask a model to choose.
     *
     * [onlyIntegration] narrows the session to a single integration. Opening a session connects
     * every integration in it, so a caller that already knows which one it needs should say so
     * rather than pay a network round trip per unrelated HTTP server.
     */
    suspend fun createForDirectToolCall(
        groupId: Long,
        scope: CoroutineScope,
        onlyIntegration: String? = null,
    ): McpSession {
        val integrations = groupIntegrations(groupId)
            .filter { onlyIntegration == null || it.name == onlyIntegration }
        return McpSession(integrations, scope)
    }

    private suspend fun groupIntegrations(groupId: Long) =
        mcpSandboxRepository.getMcpServerEntriesForGroup(groupId).first().mapNotNull {
            when (it) {
                is McpServerEntry.BuiltinMcpEntry -> builtinServletRepository.resolveName(it.builtinMcpName)
                is McpServerEntry.HttpServerEntry -> it.server.toMcpIntegration()
            }
        }
}

private fun HttpMcpServerEntity.toMcpIntegration(): HttpMcpIntegration {
    return HttpMcpIntegration(
        name = this.name,
        implementation = implementation,
        url = this.url,
        protocol = if (this.streamable) HttpMcpProtocol.Streaming else HttpMcpProtocol.Sse,
        authHeader = this.authHeader
    )
}

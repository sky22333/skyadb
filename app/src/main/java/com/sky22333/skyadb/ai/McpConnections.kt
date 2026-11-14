package com.sky22333.skyadb.ai

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.mcp.DefaultMcpToolDescriptorParser
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.header
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.PaginatedRequestParams
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

internal class McpConnections {
    private data class Connection(val client: Client, val http: HttpClient)
    private data class RemoteTool(val client: Client, val originalName: String, val serverName: String, val descriptor: ToolDescriptor)
    private val connections = mutableListOf<Connection>()
    private val tools = mutableMapOf<String, RemoteTool>()
    val descriptors: List<ToolDescriptor> get() = tools.values.map { it.descriptor }
    val toolNames: List<String> get() = tools.values.map { it.originalName }

    suspend fun connect(server: McpServerConfig) {
        val http = HttpClient(OkHttp) {
            install(SSE)
            if (server.token.isNotBlank()) defaultRequest { header("Authorization", "Bearer ${server.token}") }
        }
        val client = Client(Implementation(name = "SkyADB", version = "1.0"))
        try {
            client.connect(StreamableHttpClientTransport(http, server.url))
            val discovered = mutableMapOf<String, RemoteTool>()
            var cursor: String? = null
            val seenCursors = mutableSetOf<String>()
            do {
                val page = client.listTools(ListToolsRequest(cursor?.let { PaginatedRequestParams(cursor = it) }))
                page.tools.forEach { tool ->
                    val parsed = DefaultMcpToolDescriptorParser.parse(tool)
                    val hash = java.security.MessageDigest.getInstance("SHA-256").digest(tool.name.toByteArray()).take(4).joinToString("") { "%02x".format(it) }
                    val alias = "mcp_${server.id.take(8)}_${hash}_${tool.name.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)}"
                    val descriptor = parsed.copy(name = alias, description = "${server.name}: ${parsed.description}")
                    discovered[alias] = RemoteTool(client, tool.name, server.name, descriptor)
                }
                cursor = page.nextCursor
                check(cursor == null || seenCursors.add(cursor)) { "MCP pagination repeated a cursor" }
            } while (cursor != null)
            connections += Connection(client, http)
            tools.putAll(discovered)
        } catch (e: Exception) {
            withContext(NonCancellable) { runCatching { client.close() }; http.close() }
            throw e
        }
    }

    fun contains(name: String): Boolean = name in tools
    fun label(name: String): String = tools.getValue(name).let { "${it.serverName} · ${it.originalName}" }
    suspend fun call(name: String, arguments: String): String {
        val tool = tools.getValue(name)
        val result = tool.client.callTool(tool.originalName, Json.parseToJsonElement(arguments).jsonObject)
        val output = Json.encodeToString(CallToolResult.serializer(), result)
        check(result.isError != true) { output.take(20000) }
        return output
    }
    suspend fun close() {
        connections.asReversed().forEach { runCatching { it.client.close() }; it.http.close() }
        connections.clear()
        tools.clear()
    }
}

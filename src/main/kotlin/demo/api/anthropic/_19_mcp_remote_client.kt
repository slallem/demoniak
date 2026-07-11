package demo.api.anthropic

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.*
import com.fasterxml.jackson.databind.ObjectMapper
import demo.api.anthropic.common.anthropicClient
import demo.api.anthropic.common.defaultModel
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.sse.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.mcpStreamableHttpTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import com.anthropic.models.messages.Tool as AnthropicTool
import io.modelcontextprotocol.kotlin.sdk.types.Tool as McpTool

/**
 * **Remote MCP client** — the networked counterpart to [_18_mcp_client].
 *
 * _18 spawns a local process and talks **stdio**. Here we talk to a **public remote**
 * MCP server over HTTP: [DeepWiki](https://mcp.deepwiki.com) (by Cognition/Devin), a
 * free, no-auth service that answers questions about public GitHub repositories.
 *
 * The ONLY real change from _18 is the **transport**:
 *   - stdio → `StdioClientTransport(process streams)`  (local, launches a subprocess)
 *   - remote → **Streamable HTTP** via a Ktor `HttpClient` + `mcpStreamableHttpTransport(url)`
 *     (no subprocess — just point at the URL). Streamable HTTP uses SSE for the
 *     server→client stream, so the HttpClient must `install(SSE)`.
 *
 * Everything else — discovering tools, bridging them to Anthropic `Tool`s, and the
 * tool-use loop — is identical to _18. DeepWiki exposes 3 read-only tools:
 * `read_wiki_structure`, `read_wiki_contents`, `ask_question` (each takes a repo name).
 *
 * Needs the Anthropic key + outbound HTTPS to mcp.deepwiki.com. No server-side auth.
 */

private const val DEEPWIKI_URL = "https://mcp.deepwiki.com/mcp"

private const val PROMPT =
    "Using the DeepWiki tools, answer this about the GitHub repository " +
        "'modelcontextprotocol/kotlin-sdk': which client transports does it support? " +
        "Answer in one or two sentences and name the repository."

private val JACKSON = ObjectMapper()

/** kotlinx JSON (from the MCP schema) → Anthropic JsonValue. */
private fun toJsonValue(element: JsonElement): JsonValue =
    JsonValue.fromJsonNode(JACKSON.readTree(element.toString()))

/** Convert an MCP tool definition into an Anthropic tool definition. */
private fun toAnthropicTool(mcp: McpTool): AnthropicTool {
    val properties = AnthropicTool.InputSchema.Properties.builder()
    mcp.inputSchema.properties?.forEach { (name, schema) ->
        properties.putAdditionalProperty(name, toJsonValue(schema))
    }
    return AnthropicTool.builder()
        .name(mcp.name)
        .description(mcp.description ?: "")
        .inputSchema(
            AnthropicTool.InputSchema.builder()
                .properties(properties.build())
                .required(mcp.inputSchema.required ?: emptyList())
                .build()
        )
        .build()
}

/** Anthropic tool_use input → the argument map MCP's callTool expects. */
@Suppress("UNCHECKED_CAST")
private fun argsOf(toolUse: ToolUseBlock): Map<String, Any?> =
    (toolUse._input().convert(Map::class.java) as? Map<String, Any?>) ?: emptyMap()

/** Flatten an MCP tool result to text. */
private fun textOf(result: CallToolResult): String =
    result.content.filterIsInstance<TextContent>().joinToString("\n") { it.text }

fun main() = runBlocking {
    // Remote transport: a Ktor HttpClient (with SSE) pointed at the DeepWiki URL.
    // No subprocess — the difference from _18 is entirely in these two lines.
    val http = HttpClient(CIO) { install(SSE) }
    val transport = http.mcpStreamableHttpTransport(DEEPWIKI_URL)

    val mcp = Client(Implementation(name = "demoniak-remote-client", version = "1.0.0"))
    val anthropic = anthropicClient()

    try {
        mcp.connect(transport)
        val mcpTools = mcp.listTools().tools
        println("Connected to $DEEPWIKI_URL")
        println("Discovered ${mcpTools.size} MCP tools: ${mcpTools.joinToString { it.name }}\n")
        val tools = mcpTools.map { toAnthropicTool(it) }

        val messages = mutableListOf(
            MessageParam.builder().role(MessageParam.Role.USER).content(PROMPT).build()
        )

        while (true) {
            val builder = MessageCreateParams.builder()
                .model(defaultModel)
                .maxTokens(1024L)
                .messages(messages)
            tools.forEach { builder.addTool(it) }

            val response = anthropic.messages().create(builder.build())
            messages.add(response.toParam())

            val toolUses = response.content().mapNotNull { it.toolUse().orElse(null) }
            if (toolUses.isEmpty()) {
                response.content()
                    .mapNotNull { it.text().orElse(null) }
                    .forEach { println("\nFINAL: ${it.text()}") }
                break
            }

            // Dispatch each requested tool to the remote MCP server, collect the results.
            val results = mutableListOf<ContentBlockParam>()
            for (toolUse in toolUses) {
                val output = textOf(mcp.callTool(name = toolUse.name(), arguments = argsOf(toolUse)))
                println("[mcp] ${toolUse.name()}(${toolUse._input()}) -> ${output.take(120)}${if (output.length > 120) "…" else ""}")
                results.add(
                    ContentBlockParam.ofToolResult(
                        ToolResultBlockParam.builder()
                            .toolUseId(toolUse.id())
                            .content(output)
                            .build()
                    )
                )
            }
            messages.add(
                MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(results).build()
            )
        }
    } finally {
        mcp.close()
        anthropic.close()
        http.close()
    }
}

package demo.api.anthropic

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.ToolResultBlockParam
import com.anthropic.models.messages.ToolUseBlock
import com.anthropic.models.messages.Tool as AnthropicTool
import com.fasterxml.jackson.databind.ObjectMapper
import demo.api.anthropic.common.anthropicClient
import demo.api.anthropic.common.defaultModel
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool as McpTool
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonElement
import java.io.File

/**
 * **MCP client** — let Claude call the tools of our stdio server (`mcp/python/server.py`).
 *
 * The server is a tiny dependency-free Python script (see `mcp/python/README.md`); this
 * Kotlin example is the *client* half. Two bridges are wired together:
 *
 *   1. **MCP side**: spawn `python3 mcp/python/server.py` as a subprocess, connect over stdio,
 *      and discover its tools (`listTools`). When Claude asks for a tool, we `callTool`
 *      on the server and get the result back.
 *   2. **Anthropic side**: convert each MCP tool into an Anthropic `Tool`, then run the
 *      normal tool-use loop (like _11) — except each `tool_use` is dispatched to the MCP
 *      server instead of a local Kotlin function.
 *
 * Flow per tool call:
 *   Claude → tool_use → [this client] → MCP callTool → python server → result → tool_result → Claude
 *
 * This is the self-hosted equivalent of the API's `mcp_toolset` connector: *we* run the
 * MCP connection and relay results, keeping full control. Needs only the Anthropic key;
 * the server is launched locally (requires `python3` on PATH).
 */

private const val PROMPT =
    "Roll four 20-sided dice and tell me the total. Then turn the phrase " +
        "'The Adventures of: Sherlock Holmes' into a URL slug. Use the available tools."

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
    // Launch the Python MCP server (project root is the working dir under Gradle).
    val process = ProcessBuilder("python3", "mcp/python/server.py")
        .redirectError(ProcessBuilder.Redirect.INHERIT) // server logs → our stderr, never stdout
        .start()

    val transport = StdioClientTransport(
        input = process.inputStream.asSource().buffered(),   // server's stdout
        output = process.outputStream.asSink().buffered(),   // server's stdin
    )
    val mcp = Client(Implementation(name = "demoniak-anthropic-client", version = "1.0.0"))
    val anthropic = anthropicClient()

    try {
        mcp.connect(transport)
        val mcpTools = mcp.listTools().tools
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

            // Dispatch each requested tool to the MCP server, collect the results.
            // (Explicit loop: callTool is suspend, so it runs in the coroutine body,
            //  not inside a non-suspend map { } lambda.)
            val results = mutableListOf<ContentBlockParam>()
            for (toolUse in toolUses) {
                val output = textOf(mcp.callTool(name = toolUse.name(), arguments = argsOf(toolUse)))
                println("[mcp] ${toolUse.name()}(${toolUse._input()}) -> $output")
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
        process.destroy()
    }
}

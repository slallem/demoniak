package demo.api.anthropic

import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import demo.api.anthropic.common.anthropicClient
import demo.api.anthropic.common.defaultModel
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Role as McpRole
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.util.stream.Collectors

/**
 * **MCP prompts** — use a reusable prompt *template* hosted by an MCP server.
 *
 * This completes the trio of MCP primitives on the client side:
 *   - [_18_mcp_client]    TOOLS     — model-controlled *actions* (tool-use loop).
 *   - [_20_mcp_resources] RESOURCES — app-controlled *data* (read, inject into context).
 *   - this one            PROMPTS   — user-controlled *templates* (the server owns the
 *                                     wording; you fill the arguments and run it).
 *
 * The point of prompts: the prompt text lives on the SERVER, not in your code. The
 * client picks a prompt, supplies its arguments, and the server returns ready-to-send
 * messages (arguments already substituted). You then hand those messages to Claude —
 * you never wrote the wording yourself.
 *
 * Flow:
 *   1. LIST — `prompts/list` → the templates the server offers (with their arguments).
 *   2. GET  — `prompts/get` with arguments → the filled-in messages.
 *   3. RUN  — convert those messages to Anthropic messages and send them to Claude.
 *
 * Same local stdio server as _18 (`mcp/python/server.py`); needs only the Anthropic key.
 */

private const val PROMPT_NAME = "haiku"
private val PROMPT_ARGS = mapOf("subject" to "the London fog over Baker Street")

/** Join all text blocks of a Claude response into one string. */
private fun textOf(response: Message): String =
    response.content().stream()
        .flatMap { it.text().stream() }
        .map { it.text() }
        .collect(Collectors.joining())

fun main() = runBlocking {
    val process = ProcessBuilder("python3", "mcp/python/server.py")
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()

    val transport = StdioClientTransport(
        input = process.inputStream.asSource().buffered(),
        output = process.outputStream.asSink().buffered(),
    )
    val mcp = Client(Implementation(name = "demoniak-prompt-client", version = "1.0.0"))
    val anthropic = anthropicClient()

    try {
        mcp.connect(transport)

        // 1. LIST the prompt templates the server offers.
        val prompts = mcp.listPrompts().prompts
        println("Prompts available (${prompts.size}):")
        prompts.forEach { p ->
            val args = p.arguments.orEmpty().joinToString { it.name + if (it.required == true) "*" else "" }
            println("  - ${p.name}($args): ${p.description ?: ""}")
        }

        // 2. GET one, supplying its arguments. The SERVER substitutes them and returns
        //    ready-to-send messages — we didn't write the wording.
        val filled = mcp.getPrompt(
            GetPromptRequest(GetPromptRequestParams(name = PROMPT_NAME, arguments = PROMPT_ARGS))
        )
        println("\nFetched prompt '$PROMPT_NAME' with $PROMPT_ARGS → ${filled.messages.size} message(s):")
        filled.messages.forEach { m ->
            println("  [${m.role}] ${(m.content as? TextContent)?.text ?: "(non-text content)"}")
        }

        // 3. RUN — replay the server's messages to Claude as the actual conversation.
        val builder = MessageCreateParams.builder().model(defaultModel).maxTokens(256L)
        for (m in filled.messages) {
            val text = (m.content as? TextContent)?.text ?: continue
            when (m.role) {
                McpRole.User -> builder.addUserMessage(text)
                McpRole.Assistant -> builder.addAssistantMessage(text)
            }
        }
        val response = anthropic.messages().create(builder.build())

        println("\nClaude's response to the prompt:")
        println(textOf(response).trim())
    } finally {
        mcp.close()
        anthropic.close()
        process.destroy()
    }
}

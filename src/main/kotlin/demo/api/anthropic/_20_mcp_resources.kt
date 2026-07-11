package demo.api.anthropic

import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import demo.api.anthropic.common.anthropicClient
import demo.api.anthropic.common.defaultModel
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.util.stream.Collectors

/**
 * **MCP resources** — read documents from an MCP server and have Claude work on one.
 *
 * Where [_18_mcp_client] used **tools** (model-controlled *actions*, driven by a tool-use
 * loop), this uses **resources** (app-controlled *data*). The key difference:
 *
 *   - Tools: the MODEL decides what to call; you run a loop feeding tool_results back.
 *   - Resources: YOUR CODE decides what to read; you inject the content into the prompt.
 *     There is no loop — a resource is just context you choose to provide.
 *
 * The flow here mirrors a real "pick a document and summarise it" feature:
 *   1. LIST    — `resources/list`  → the Sherlock stories the server exposes.
 *   2. SELECT  — the app picks one (here: by a keyword in the URI). The model is NOT
 *                involved in the choice — that's what "app-controlled" means.
 *   3. READ    — `resources/read` → the full text of the chosen document.
 *   4. RESUME  — put that text in the prompt and ask Claude to summarise it.
 *
 * Same local stdio server as _18 (`mcp/python/server.py`); needs only the Anthropic key.
 */

// Which document to select in step 2 — the story whose URI contains this slug
// (falls back to the first resource if not found).
private const val PICK = "speckled-band"

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
    val mcp = Client(Implementation(name = "demoniak-resource-client", version = "1.0.0"))
    val anthropic = anthropicClient()

    try {
        mcp.connect(transport)

        // 1. LIST the available documents.
        val resources = mcp.listResources().resources
        println("Documents available (${resources.size}):")
        resources.forEachIndexed { i, r -> println("  %2d. %s  <%s>".format(i + 1, r.name, r.uri)) }

        // 2. SELECT one — the app chooses (not the model).
        val chosen = resources.firstOrNull { it.uri.contains(PICK) } ?: resources.first()
        println("\nSelected: ${chosen.name}  (${chosen.uri})")

        // 3. READ its full content via resources/read.
        val read = mcp.readResource(ReadResourceRequest(ReadResourceRequestParams(uri = chosen.uri)))
        val document = read.contents.filterIsInstance<TextResourceContents>().joinToString("\n") { it.text }
        println("Read ${document.length} characters.\n")

        // 4. RESUME — feed the document to Claude and ask for a summary.
        val response = anthropic.messages().create(
            MessageCreateParams.builder()
                .model(defaultModel)
                .maxTokens(512L)
                .system(
                    "You are given the full text of a Sherlock Holmes story. " +
                        "Summarise the plot in exactly 3 sentences, without spoiling the final twist."
                )
                .addUserMessage(document)
                .build()
        )

        println("Summary of \"${chosen.name}\":")
        println(textOf(response).trim())
        println("\n(input tokens: ${response.usage().inputTokens()} — the whole document went into context)")
    } finally {
        mcp.close()
        anthropic.close()
        process.destroy()
    }
}
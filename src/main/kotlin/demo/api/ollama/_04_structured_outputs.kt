package demo.api.ollama

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import demo.api.ollama.common.Models
import demo.api.ollama.common.OLLAMA_URL
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Structured outputs against Ollama's **native** `/api/chat`, in pure HTTP — same "no SDK" style
 * as `_02_chat_http`/`_03_tool_calling`.
 *
 * Where Anthropic's `_09_structured_outputs` and OpenAI's `_06_structured_outputs` hand a typed
 * class to the SDK and get a typed object straight back, Ollama's native `format` field takes a
 * plain JSON Schema — no SDK, no reflection over a data class — and constrains decoding
 * grammar-level to match it (same idea, different plumbing). The reply's `message.content` is
 * still just a **JSON string** that happens to be guaranteed valid against the schema: nothing
 * deserializes it into a [Book] for you, so that last step is ours — read defensively off the
 * parsed tree, same idea as `_07_json_mode` (OpenAI), rather than a one-line `readValue` into the
 * data class, which would need the Kotlin Jackson module explicitly registered on this plain
 * `ObjectMapper` to construct [Book] from its primary constructor.
 *
 * Same `Book` shape, and the same prompt, as `_06_structured_outputs` (OpenAI) and
 * `_09_structured_outputs` (Anthropic) — a direct three-way comparison on identical input.
 *
 * Two knobs matter here, both nudged by Ollama's own docs:
 *  - `think: false` (top-level) — reasoning left on can wrap the JSON in prose around it, which
 *    breaks a naive `readValue` even though the schema itself was respected.
 *  - `options.temperature = 0` — recommended for reliable schema-constrained decoding. Note this
 *    lives inside a nested `options` object, unlike `think`, which sits at the top level.
 *
 * This example deliberately overrides [demo.api.ollama.common.Models.DEFAULT] to
 * [Models.GEMMA4_12B] instead of using it as-is. Tested with the repo's usual default,
 * `qwen3.5:2b`: the schema itself was respected structurally, but the model's actual content for
 * the `genres` array came back corrupted — `["\",\"Science Fiction\",\"Space Opera\", \"Philosophy\"]} {\" ]}`
 * — extra tokens leaking past a technically-valid array-of-one-string. `format` constrains the
 * *grammar*, not the *content*; a small model can still produce garbage that happens to parse.
 * `gemma4:12b` gets this right every time in testing, at the cost of ~30-50s per call instead of
 * a few seconds.
 */

private val mapper = ObjectMapper()

/** The shape we want back — same fields as OpenAI's/Anthropic's `Book`. */
private data class Book(
    val title: String,
    val author: String,
    val year: Int,
    val genres: List<String>,
)

/** JSON Schema for [Book] — hand-written here; the other two providers derive this reflectively. */
private val bookSchema = mapper.createObjectNode().apply {
    put("type", "object")
    putObject("properties").apply {
        putObject("title").put("type", "string")
        putObject("author").put("type", "string")
        putObject("year").put("type", "integer")
        putObject("genres").apply {
            put("type", "array")
            putObject("items").put("type", "string")
        }
    }
    putArray("required").apply {
        add("title"); add("author"); add("year"); add("genres")
    }
}

fun main() {
    val body = mapper.createObjectNode().apply {
        put("model", Models.GEMMA4_12B) // see the header note: qwen3.5:2b garbles the genres array here
        set<JsonNode>(
            "messages",
            mapper.valueToTree(
                listOf(
                    mapOf(
                        "role" to "user",
                        "content" to "Give me details about the novel \"Dune\" by Frank Herbert."
                    )
                )
            )
        )
        set<JsonNode>("format", bookSchema)
        put("stream", false)
        put("think", false) // reasoning left on can wrap the JSON in prose, breaking a naive parse
        putObject("options").put("temperature", 0) // recommended for reliable schema-constrained decoding
    }

    val request = HttpRequest.newBuilder(URI.create("$OLLAMA_URL/api/chat"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
        .build()

    val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) {
        "Ollama returned ${response.statusCode()}: ${response.body()}"
    }

    val content = mapper.readTree(response.body()).path("message").path("content").asText()
    println("Raw content (a JSON string, not auto-parsed unlike _06/_09):")
    println(content)

    // Guaranteed to match the schema structurally, but still just a tree to read ourselves.
    val tree = mapper.readTree(content)
    val book = Book(
        title = tree.path("title").asText(),
        author = tree.path("author").asText(),
        year = tree.path("year").asInt(),
        genres = tree.path("genres").map { it.asText() },
    )

    println()
    println("Read into a Book by hand:")
    println("  title  = ${book.title}")
    println("  author = ${book.author}")
    println("  year   = ${book.year}")
    println("  genres = ${book.genres.joinToString(", ")}")
}

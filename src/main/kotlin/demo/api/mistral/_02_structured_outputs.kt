package demo.api.mistral

import demo.api.mistral.common.defaultModel
import demo.api.mistral.common.mistralClient
import kotlin.jvm.optionals.getOrNull
import com.openai.models.chat.completions.ChatCompletionCreateParams

/**
 * **Structured outputs**: hand the API a schema and get a guaranteed-conforming, already-parsed
 * reply back — no "```json" fence, no `JSON.parse` on our side.
 *
 * Identical code to `openai/_06_structured_outputs.kt`: `.responseFormat(Book::class.java)` swaps
 * the builder into a parallel generic type (`StructuredChatCompletionCreateParams.Builder<Book>`),
 * and `message().content()` comes back as `Optional<Book>` instead of a string. The schema is
 * derived reflectively from the data class, same mechanism OpenAI's SDK uses.
 *
 * Mistral upgraded from a plain `json_object` mode (ask nicely for JSON, no guarantee) to this
 * same `response_format: json_schema` + `strict: true` shape OpenAI popularized — so the identical
 * SDK call actually exercises a real, distinct Mistral feature, not just a compatibility shim.
 *
 * The one change from the OpenAI version: `.maxTokens(...)` instead of `.maxCompletionTokens(...)`
 * — see `_01_starter`'s note on that field.
 */

/** The shape we want back. The SDK turns this into a JSON schema for the API. */
data class Book(
    val title: String,
    val author: String,
    val year: Int,
    val genres: List<String>,
)

fun main() {
    val client = mistralClient()
    try {
        val params = ChatCompletionCreateParams.builder()
            .model(defaultModel)
            .maxTokens(512L) // Mistral rejects `max_completion_tokens`; only `max_tokens` works
            .addUserMessage("Give me details about the novel \"Dune\" by Frank Herbert.")
            .responseFormat(Book::class.java) // schema derived from Book; reply guaranteed to match
            .build()

        val message = client.chat().completions().create(params).choices().first().message()

        // .content() yields a typed Book here — no string parsing on our side.
        val book = message.content().getOrNull()
            ?: error("refused: ${message.refusal().getOrNull()}")

        println("Parsed straight into a typed Book:")
        println("  title  = ${book.title}")
        println("  author = ${book.author}")
        println("  year   = ${book.year}")
        println("  genres = ${book.genres.joinToString(", ")}")
    } finally {
        client.close()
    }
}

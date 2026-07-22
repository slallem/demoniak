package demo.api.openai

import com.openai.models.chat.completions.ChatCompletionCreateParams
import demo.api.openai.common.defaultModel
import demo.api.openai.common.openaiClient
import kotlin.jvm.optionals.getOrNull

/**
 * **Structured outputs**: hand the API a schema and get a guaranteed-conforming, already-parsed
 * reply back — no "```json" fence, no stop sequence, no `JSON.parse` on our side.
 *
 * Anthropic's `_09_structured_outputs` does this with one params/response type throughout:
 * `.outputConfig(Book::class.java)` on `MessageCreateParams`, and `.text()` on the reply comes
 * back typed as `Book`. OpenAI instead **swaps in a parallel generic type** the moment you call
 * `.responseFormat(Book::class.java)`:
 *
 *   `ChatCompletionCreateParams.Builder` → (after `.responseFormat`) →
 *   `StructuredChatCompletionCreateParams.Builder<Book>` → `.build()` →
 *   `StructuredChatCompletionCreateParams<Book>` → `client...create(...)` →
 *   `StructuredChatCompletion<Book>`
 *
 * The builder still reads like one fluent chain (the structured builder re-exposes `model`,
 * `addUserMessage`, etc.), so nothing looks different at the call site — but the *type* carries
 * `Book` from that point on, and `create(...)` is overloaded to route to the structured response
 * when it sees that type. `message().content()` is then an `Optional<Book>`, not a string.
 *
 * The schema itself is derived reflectively from the data class — same mechanism as Anthropic's
 * `.outputConfig`. Unlike `_04_function_calling`, where `strict: true` had to be set by hand
 * (with its "every property must be in `required`" gotcha), here it comes for free: the generated
 * schema already satisfies it.
 *
 * One shape unique to structured outputs: `message().refusal()` is the other side of
 * `.content()` — if the model declines instead of answering, content is empty and refusal is set.
 */

/** The shape we want back. The SDK turns this into a JSON schema for the API. */
data class Book(
    val title: String,
    val author: String,
    val year: Int,
    val genres: List<String>,
)

fun main() {
    val client = openaiClient()
    try {
        val params = ChatCompletionCreateParams.builder()
            .model(defaultModel)
            .maxCompletionTokens(512L)
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

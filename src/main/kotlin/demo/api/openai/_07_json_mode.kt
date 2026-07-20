package demo.api.openai

import com.fasterxml.jackson.databind.ObjectMapper
import com.openai.models.ResponseFormatJsonObject
import com.openai.models.chat.completions.ChatCompletionCreateParams
import demo.api.openai.common.defaultModel
import demo.api.openai.common.openaiClient
import kotlin.jvm.optionals.getOrNull

/**
 * **JSON mode**: the older, weaker sibling of `_06_structured_outputs`'s schema-guaranteed
 * output — `response_format: {type: "json_object"}` instead of `{type: "json_schema", ...}`.
 *
 * It guarantees only that the reply **parses as JSON** — not which keys it has, their types, or
 * whether they are present at all. There is no `Class<T>` to derive a schema from, so
 * `.responseFormat(ResponseFormatJsonObject)` does **not** switch the builder to a generic
 * `StructuredChatCompletionCreateParams<T>` the way `.responseFormat(Book::class.java)` did in
 * `_06` — it stays the plain untyped builder, and `message().content()` comes back as an ordinary
 * `String`. The desired shape lives only in the prompt, so getting it means asking nicely *and*
 * parsing defensively, since nothing enforces the model actually complied.
 *
 * Same shakier ground as Anthropic's prefill trick in `_08_structured_output` — both are the
 * "older way" that `_06`'s native structured outputs supersedes — but the mechanic is unrelated:
 * OpenAI's JSON mode is a real API mode, not a prompt trick, and it comes with its own hard
 * requirement: **the word "json" must appear somewhere in the messages**, or the API rejects the
 * request with a 400. The prompt below satisfies that by asking for JSON explicitly.
 */

fun main() {
    val client = openaiClient()
    try {
        val params = ChatCompletionCreateParams.builder()
            .model(defaultModel)
            .maxCompletionTokens(512L)
            .addUserMessage(
                """
                Give me details about the novel "Dune" by Frank Herbert as JSON with
                keys: title (string), author (string), year (number), genres (array of strings).
                """.trimIndent()
            )
            .responseFormat(ResponseFormatJsonObject.builder().build()) // valid JSON guaranteed, shape is not
            .build()

        val message = client.chat().completions().create(params).choices().first().message()
        val json = message.content().getOrNull()?.trim() ?: error("no content")

        println("── raw reply (valid JSON, but no schema behind it) ──")
        println(json)

        // Nothing guarantees these keys exist or have the right type — read them defensively,
        // unlike _06 where the typed Book was handed back ready-made.
        val tree = ObjectMapper().readTree(json)
        println("── reading fields defensively ──")
        println("  title  = ${tree.path("title").asText("?")}")
        println("  author = ${tree.path("author").asText("?")}")
        println("  year   = ${tree.path("year").asText("?")}")
        println("  genres = ${tree.path("genres").joinToString(", ") { it.asText() }}")
    } finally {
        client.close()
    }
}

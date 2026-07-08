package org.example.demo.anthropic

import com.anthropic.models.messages.MessageCreateParams
import org.example.demo.anthropic.common.anthropicClient
import org.example.demo.anthropic.common.defaultModel

/**
 * Demonstrating **native structured outputs** — the up-to-date way to get JSON.
 *
 * Where [_08_structured_output] *coaxes* JSON out of a text model (prefill a
 * "```json" fence, stop at "```", then hope it parses), this hands the API a
 * schema and lets it **guarantee** a conforming result. No prefill, no stop
 * sequence, no fence stripping, no `JSON.parse` on our side.
 *
 * The Java SDK derives the JSON schema straight from a class: `.outputConfig(
 * Book::class.java)`. The API constrains decoding to that schema, and the SDK
 * deserializes the reply back into a typed [Book] — `.text()` returns a `Book`,
 * not a `String`. (The SDK bundles the Jackson Kotlin module, so a plain
 * `data class` works with no annotations.)
 *
 * Supported on current models — including the repo default `claude-haiku-4-5`,
 * so unlike [_08_structured_output] this needs no model change. A first request
 * with a new schema pays a one-time compilation cost, then it is cached ~24h.
 */

/** The shape we want back. The SDK turns this into a JSON schema for the API. */
data class Book(
    val title: String,
    val author: String,
    val year: Int,
    val genres: List<String>,
)

fun main() {
    val client = anthropicClient()
    try {
        val params = MessageCreateParams.builder()
            .model(defaultModel)
            .maxTokens(512L)
            .outputConfig(Book::class.java) // schema derived from Book; reply guaranteed to match
            .addUserMessage("Give me details about the novel \"Dune\" by Frank Herbert.")
            .build()

        // .text() yields a typed Book here — no string parsing on our side.
        val book: Book = client.messages().create(params).content().stream()
            .flatMap { block -> block.text().stream() }
            .map { it.text() }
            .findFirst()
            .orElseThrow()

        println("Parsed straight into a typed Book:")
        println("  title  = ${book.title}")
        println("  author = ${book.author}")
        println("  year   = ${book.year}")
        println("  genres = ${book.genres.joinToString(", ")}")
    } finally {
        client.close()
    }
}

package demo.api.mistral

import com.fasterxml.jackson.databind.ObjectMapper
import demo.api.mistral.common.MISTRAL_BASE_URL
import demo.api.mistral.common.mistralApiKey
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Locale

/**
 * Moderation: Mistral's dedicated `/v1/moderations` endpoint — the counterpart to OpenAI's
 * `_10_moderation`, but a genuinely different API shape rather than the chat-completions-compatible
 * surface the rest of this provider reuses (see the note in `common.kt`). Like `_04_ocr`/`_05_ocr_pdf`,
 * this one is plain HTTP + Jackson: there is no OpenAI-shaped equivalent to piggyback the SDK on.
 *
 * The taxonomy differs from OpenAI's too — 9 categories (`sexual`, `hate_and_discrimination`,
 * `violence_and_threats`, `dangerous_and_criminal_content`, `selfharm`, `health`, `financial`,
 * `law`, `pii`), each with its own boolean + float score — and there is no top-level `flagged`
 * field in the response; it is derived here as "any category true", same idea as OpenAI's
 * `_10_moderation` but computed client-side instead of handed back ready-made.
 *
 * A separate `/v1/chat/moderations` endpoint exists for moderating a whole conversation (roles +
 * turns) rather than a single string — out of scope for this starter.
 */

private val mapper = ObjectMapper()

fun main() {
    val samples = listOf(
        "What's the weather like in Paris today?",
        "I want to hurt someone who wronged me."
    )

    val http = HttpClient.newHttpClient()

    samples.forEach { text ->
        val body = mapper.createObjectNode().apply {
            put("model", "mistral-moderation-latest")
            put("input", text)
        }

        val request = HttpRequest.newBuilder(URI.create("$MISTRAL_BASE_URL/moderations"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${mistralApiKey()}")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) {
            "Mistral moderation returned ${response.statusCode()}: ${response.body()}"
        }

        val result = mapper.readTree(response.body()).path("results").first()
        val categories = result.path("categories")
        val scores = result.path("category_scores")

        val flagged = categories.fields().asSequence().any { (_, value) -> value.asBoolean() }

        println("── \"$text\" ──")
        println("  flagged: $flagged")

        categories.fields().forEach { (name, value) ->
            if (value.asBoolean()) {
                val score = scores.path(name).asDouble()
                println("    $name: score=${String.format(Locale.ROOT, "%.4f", score)}")
            }
        }
    }
}

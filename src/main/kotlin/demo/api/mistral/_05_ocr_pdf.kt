package demo.api.mistral

import com.fasterxml.jackson.databind.ObjectMapper
import com.openai.models.chat.completions.ChatCompletionCreateParams
import demo.api.mistral.common.MISTRAL_BASE_URL
import demo.api.mistral.common.defaultModel
import demo.api.mistral.common.mistralApiKey
import demo.api.mistral.common.mistralClient
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import kotlin.jvm.optionals.getOrNull

/**
 * **OCR on a PDF, chained into chat** — same `/v1/ocr` call as a plain OCR pass, but this time
 * its markdown output feeds a second request that asks the exact question Anthropic's
 * `_23_ocr_pdf` answers in one shot: "List the available potentiometer types and their prices as
 * a Markdown table."
 *
 * `/v1/ocr` has no prompt of its own — it always transcribes the whole page, never a subset.
 * Anthropic's single vision call does transcription *and* targeted extraction together, because
 * the model reads the PDF and follows a system prompt in the same pass. Mistral's OCR is a
 * two-call pipeline instead of one: `/v1/ocr` for the raw transcript (images go into the model,
 * markdown comes out), then a normal `mistralClient()` chat completion — with that transcript as
 * context — for the targeted extraction. Same end result as Anthropic's example, reached by
 * composing two purpose-built calls rather than one multimodal one.
 */

private const val ASSET = "assets/pdf/electrovalue1973.pdf"

private const val EXTRACT_SYSTEM = """
You extract structured data from an OCR transcript of a product catalogue. Be careful about omega
signs (ohm symbols). Find every POTENTIOMETER offered and its price. Output ONLY a Markdown table
with exactly these columns: | Type | Available values | Price |. Keep the type designation and the
price verbatim as printed (including units, ranges, or currency). Do not add, infer, translate, or
comment. Ignore every product that is not a potentiometer. If no price is shown for a type, put "-".
"""

private val mapper = ObjectMapper()

/** Calls Mistral's `/v1/ocr` on [file] and returns the concatenated markdown of every page. */
private fun ocrTranscript(file: File): String {
    val base64 = Base64.getEncoder().encodeToString(file.readBytes())
    val dataUri = "data:application/pdf;base64,$base64"

    val body = mapper.createObjectNode().apply {
        put("model", "mistral-ocr-latest")
        putObject("document").apply {
            put("type", "document_url")
            put("document_url", dataUri)
        }
    }

    val request = HttpRequest.newBuilder(URI.create("$MISTRAL_BASE_URL/ocr"))
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer ${mistralApiKey()}")
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
        .build()

    val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) {
        "Mistral OCR returned ${response.statusCode()}: ${response.body()}"
    }

    val pages = mapper.readTree(response.body()).path("pages")
    return pages.joinToString("\n\n") { it.path("markdown").asText() }
}

fun main() {
    val file = File(ASSET)
    require(file.exists()) { "Asset not found: ${file.absolutePath} — run from the project root." }

    val transcript = ocrTranscript(file)
    println("── OCR transcript (${file.name}, ${transcript.length} chars) ──")
    println(transcript.trim())

    val client = mistralClient()
    try {
        val params = ChatCompletionCreateParams.builder()
            .model(defaultModel)
            .maxTokens(1024L) // Mistral rejects `max_completion_tokens`; only `max_tokens` works
            .addSystemMessage(EXTRACT_SYSTEM.trim())
            .addUserMessage(
                "$transcript\n\n---\nList the available potentiometer types and their prices as a Markdown table."
            )
            .build()

        val message = client.chat().completions().create(params).choices().first().message()

        println("\n── Potentiometers ──")
        println(message.content().getOrNull()?.trim())
    } finally {
        client.close()
    }
}

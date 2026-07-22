package demo.api.mistral

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import demo.api.mistral.common.MISTRAL_BASE_URL
import demo.api.mistral.common.mistralApiKey
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64

/**
 * **OCR** — Mistral's dedicated `/v1/ocr` endpoint, not a vision-model prompt.
 *
 * Where Anthropic's `_22_ocr`/`_23_ocr_pdf` and OpenAI's `_08_vision` get transcription by
 * *asking* a chat model to read an image, Mistral ships a separate document-understanding
 * endpoint (`mistral-ocr-latest`) purpose-built for this: it returns **structured markdown per
 * page** (headings, tables, reading order) rather than a paragraph of prose, and it is priced and
 * rate-limited separately from chat completions.
 *
 * This is also the one Mistral example in this repo *not* going through the OpenAI SDK: `/v1/ocr`
 * has no chat-completions equivalent, so there is no shape for the SDK to reuse — see the note in
 * `common.kt`. It's plain HTTP + Jackson instead, the same trick `ollama/_02_chat_http.kt` uses for
 * native-only endpoints.
 *
 * Request shape: `document.type: "image_url"` with the value being either a real `https://` URL
 * or (as here, for a local file) a `data:` URI — unlike chat completions' vision content part,
 * this field is a **plain string**, not `{ "url": ... }`.
 *
 * Same dense 1925 Meccano page Anthropic's `_22_ocr` transcribes by prompting — good for
 * comparing the two approaches on identical input: a paragraph of prose there, structured
 * per-page markdown here.
 */

private const val ASSET = "assets/images/meccano1925.png"

private val mapper = ObjectMapper()

private fun mimeTypeFor(name: String): String =
    when (name.substringAfterLast('.').lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        else -> error("Unsupported image type for '$name' (this example handles png/jpeg).")
    }

fun main() {
    val file = File(ASSET)
    require(file.exists()) { "Asset not found: ${file.absolutePath} — run from the project root." }

    val base64 = Base64.getEncoder().encodeToString(file.readBytes())
    val dataUri = "data:${mimeTypeFor(file.name)};base64,$base64"

    val body = mapper.createObjectNode().apply {
        put("model", "mistral-ocr-latest")
        putObject("document").apply {
            put("type", "image_url")
            put("image_url", dataUri)
        }
    }

    val request = HttpRequest.newBuilder(URI.create("$MISTRAL_BASE_URL/ocr"))
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer ${mistralApiKey()}")
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
        .build()

    val http = HttpClient.newHttpClient()
    val response = http.send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) {
        "Mistral OCR returned ${response.statusCode()}: ${response.body()}"
    }

    val json = mapper.readTree(response.body())
    val pages = json.path("pages")
    val pagesProcessed = json.path("usage_info").path("pages_processed").asInt(pages.size())

    println("Model: ${json.path("model").asText()}  pages processed: $pagesProcessed\n")
    pages.forEach { page: JsonNode ->
        val dims = page.path("dimensions")
        println("── Page ${page.path("index").asInt()} (${dims.path("width").asInt()}x${dims.path("height").asInt()} px) ──")
        println(page.path("markdown").asText().trim())
        println()
    }
}

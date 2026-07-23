package demo.api.ollama

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import demo.api.ollama.common.OLLAMA_URL
import demo.api.ollama.common.defaultModel
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import javax.imageio.ImageIO

/**
 * Vision against Ollama's **native** `/api/chat`, in pure HTTP — same "no SDK" style as
 * `_02_chat_http`/`_03_tool_calling`/`_04_structured_outputs`/`_05_embeddings`.
 *
 * Same asset and question as Anthropic's `_17_vision_asset`, OpenAI's `_08_vision`, and Mistral's
 * `_07_vision` — for a direct four-way comparison on identical input, entirely offline for this
 * one.
 *
 * The real shape difference from all three of those: they attach the image as an `image_url`
 * **content part** carrying a `data:` URI (a convention OpenAI pioneered that Anthropic and
 * Mistral converged on). Ollama's native API instead puts images on their own field — an
 * `images` array of **raw base64 strings, no `data:` URI prefix at all** — sitting alongside a
 * plain `content` string rather than inside it:
 * ```json
 * { "role": "user", "content": "...", "images": ["<base64, no prefix>"] }
 * ```
 *
 * Vision support is per-model, same caveat as tool calling (`_03`): check `capabilities` in
 * `ollama show <model>` / `/api/tags` first. Both models this repo already uses list it —
 * [demo.api.ollama.common.Models.DEFAULT] (`qwen3.5:2b`) and
 * [demo.api.ollama.common.Models.GEMMA4_12B] both report `vision` — so no separate vision-only
 * model (llava, qwen2.5vl, …) needs pulling here, unlike a fresh Ollama install where the default
 * text model often doesn't see images at all.
 *
 * **Tested against the real asset**: the answer quality is good — a coherent, correctly detailed
 * description plus valid JSON matching the requested shape — but it is by far the slowest call
 * `qwen3.5:2b` makes anywhere in this repo, several minutes for this one ~1.4MB image versus the
 * ~1-2s this same model needs for a plain text turn. Vision encoding a full-resolution image is
 * simply much more expensive than tokenizing a sentence, even with `think` disabled; budget for
 * that latency rather than assuming vision inherits the small model's usual speed.
 */

private const val ASSET = "assets/images/desktop.png"

private val QUESTION = """
    1) Describe this image in detail. Read any clearly visible text. Respond as a descriptive bullet list of objects and facts.
    2) Check every object to see if you can see barcodes on them (barcodes are not always black on white).
    3) Return object list as a json array (fields: object name, barcode present, main colors)
    """.trimIndent()

private val mapper = ObjectMapper()

fun main() {
    val file = File(ASSET)
    require(file.exists()) { "Asset not found: ${file.absolutePath} — run from the project root." }

    val bytes = file.readBytes()
    val base64 = Base64.getEncoder().encodeToString(bytes) // raw base64, no data: URI prefix

    val body = mapper.createObjectNode().apply {
        put("model", defaultModel)
        set<JsonNode>(
            "messages",
            mapper.valueToTree(
                listOf(
                    mapOf(
                        "role" to "user",
                        "content" to QUESTION,
                        "images" to listOf(base64),
                    )
                )
            )
        )
        put("stream", false)
        put("think", false) // this is a description task, not a reasoning one — keep it quick
    }

    val request = HttpRequest.newBuilder(URI.create("$OLLAMA_URL/api/chat"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
        .build()

    val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) {
        "Ollama returned ${response.statusCode()}: ${response.body()}"
    }

    val answer = mapper.readTree(response.body()).path("message").path("content").asText().trim()

    val dims = ImageIO.read(file)
    if (dims != null) {
        println("Asset: ${file.name}  ${dims.width}x${dims.height} px  (${bytes.size} bytes, base64 ${base64.length} chars)")
    }
    println("Model: $defaultModel\n")
    println("Ollama sees:")
    println(answer)
}

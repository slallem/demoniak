package demo.api.anthropic

import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.TextBlockParam
import demo.api.anthropic.common.anthropicClient
import demo.api.anthropic.common.defaultModel
import java.io.File
import java.util.Base64
import java.util.stream.Collectors
import javax.imageio.ImageIO

/**
 * **OCR** — transcribe the text of an image with the vision model.
 *
 * Where [_17_vision_asset] *describes* an image, this *reads* it: it asks the model to
 * transcribe every visible word verbatim. The image is a 1925 Meccano document
 * (`assets/images/meccano1925.png`) — dense vintage print, a realistic OCR target.
 *
 * Same image-input plumbing as _17 (base64 `image` block, image before the instruction).
 * The OCR-specific parts are the **system prompt** (transcribe verbatim, preserve layout,
 * mark illegible spots) and a **generous `max_tokens`** — a full page is a lot of text.
 *
 * ⚠️ Resolution matters for OCR. Cost is ~(W×H)/750 tokens, and images above the model's
 * cap are **auto-downscaled before the model sees them** — which can blur small text.
 * This image is 1200×1600 ≈ 1.92 MP, above the ~1.15 MP cap of older models (incl. the
 * repo default `claude-haiku-4-5`), so it IS downscaled. For sharper OCR of dense pages,
 * use a high-res model (Opus 4.7/4.8, Sonnet 5 — up to ~3.75 MP).
 */

private const val ASSET = "assets/images/meccano1925.png"

private const val OCR_SYSTEM = """
You are an OCR engine. Transcribe ALL text visible in the image, verbatim.
Preserve the reading order and line breaks; keep headings and lists as separate lines.
Do not translate, summarise, correct, or comment. If a word is unreadable, write [illegible].
Output only the transcribed text.
"""

private fun mediaTypeFor(name: String): Base64ImageSource.MediaType =
    when (name.substringAfterLast('.').lowercase()) {
        "png" -> Base64ImageSource.MediaType.IMAGE_PNG
        "jpg", "jpeg" -> Base64ImageSource.MediaType.IMAGE_JPEG
        else -> error("Unsupported image type for '$name' (this example handles png/jpeg).")
    }

fun main() {
    System.setProperty("java.awt.headless", "true")

    val client = anthropicClient()
    try {
        val file = File(ASSET)
        require(file.exists()) { "Asset not found: ${file.absolutePath} — run from the project root." }

        val bytes = file.readBytes()
        val base64 = Base64.getEncoder().encodeToString(bytes) // no line breaks

        val response = client.messages().create(
            MessageCreateParams.builder()
                .model(defaultModel)
                .maxTokens(4096L)                       // a full page of text needs room
                .system(OCR_SYSTEM.trim())
                .addUserMessageOfBlockParams(
                    listOf(
                        ContentBlockParam.ofImage(
                            ImageBlockParam.builder()
                                .source(
                                    Base64ImageSource.builder()
                                        .mediaType(mediaTypeFor(file.name))
                                        .data(base64)
                                        .build()
                                )
                                .build()
                        ),
                        ContentBlockParam.ofText(
                            TextBlockParam.builder().text("Transcribe the text in this image.").build()
                        ),
                    )
                )
                .build()
        )

        val transcription = response.content().stream()
            .flatMap { it.text().stream() }
            .map { it.text() }
            .collect(Collectors.joining())
        val usage = response.usage()

        val dims = ImageIO.read(file)
        if (dims != null) {
            val mp = dims.width.toLong() * dims.height / 1_000_000.0
            val estimate = dims.width.toLong() * dims.height / 750
            println("Image: ${file.name}  ${dims.width}x${dims.height} px  (%.2f MP, ${bytes.size} bytes)".format(mp))
            println("Image tokens estimate (W×H/750): ~$estimate  [auto-downscaled if above the model's cap]")
        }
        println("Usage: input=${usage.inputTokens()}, output=${usage.outputTokens()}\n")
        println("── Transcription ──")
        println(transcription.trim())
    } finally {
        client.close()
    }
}

package demo.api.anthropic

import com.anthropic.models.messages.*
import demo.api.anthropic.common.anthropicClient
import demo.api.anthropic.common.defaultModel
import java.io.File
import java.util.*
import java.util.stream.Collectors
import javax.imageio.ImageIO

/**
 * **Image analysis of a real asset** — the file-based counterpart to [_16_vision]
 * (which draws its image in code).
 *
 * Loads `assets/images/desktop.png`, sends it as a base64 `image` block, and asks
 * Claude to analyse it. Same mechanics as [_16_vision]:
 *   - image block FIRST, then the text question;
 *   - `media_type` inferred from the file extension;
 *   - base64 with no line breaks (`Base64.getEncoder()`).
 *
 * Pixel→token reminder: cost ≈ **(W × H) / 750**, driven by *pixels, not file bytes* —
 * this ~1.4 MB PNG is only ~1300 image tokens. Images larger than the model cap
 * (~1.15 MP on older models incl. `claude-haiku-4-5`, ~3.75 MP on 4.7+) are auto-resized
 * before tokenizing; this one (1213×822 ≈ 1.0 MP) is under the cap, so it isn't shrunk.
 */

private const val ASSET = "assets/images/desktop.png"

// Notes:
//   Claude API by itself is not capable of decoding barcodes/QRcodes easily
//   Consider using companion tools to add robust decoding functions (see OpenCV, zbar, zxing among others)

private const val QUESTION = """
    1) Describe this image in detail. Read any clearly visible text. Respond as a descriptive bullet list of objects and facts.
    2) Check every object to see if you can see barcodes on them (barcodes are not always black on white). 
    3) Return object list as a json array (fields: object name, barcode present, main colors)
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
                .model(defaultModel) // Force newer model here to get more accurate results
                .maxTokens(1024L)
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
                            TextBlockParam.builder().text(QUESTION).build()
                        ),
                    )
                )
                .build()
        )

        val answer = response.content().stream()
            .flatMap { it.text().stream() }
            .map { it.text() }
            .collect(Collectors.joining())
        val usage = response.usage()

        // Dimensions → rough token estimate, to compare with the real usage count.
        val dims = ImageIO.read(file)
        if (dims != null) {
            val estimate = (dims.width.toLong() * dims.height) / 750
            println("Asset: ${file.name}  ${dims.width}x${dims.height} px  (${bytes.size} bytes, base64 ${base64.length} chars)")
            println("Image tokens estimate (W×H/750): ~$estimate")
        }
        println("Actual usage: input=${usage.inputTokens()} (image + prompt), output=${usage.outputTokens()}\n")
        println("Claude sees:")
        println(answer)
    } finally {
        client.close()
    }
}
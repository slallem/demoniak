package demo.api.mistral

import com.openai.models.chat.completions.ChatCompletionContentPart
import com.openai.models.chat.completions.ChatCompletionContentPartImage
import com.openai.models.chat.completions.ChatCompletionContentPartText
import com.openai.models.chat.completions.ChatCompletionCreateParams
import demo.api.mistral.common.defaultModel
import demo.api.mistral.common.mistralClient
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.jvm.optionals.getOrNull

/**
 * **Vision** — same asset and question as OpenAI's `_08_vision` and Anthropic's `_17_vision_asset`,
 * for a side-by-side comparison on identical input.
 *
 * Unlike `_04_ocr`/`_05_ocr_pdf`/`_06_fim`, this one goes back through the plain `mistralClient()`
 * chat completions call — because vision here isn't a separate endpoint, it's just another
 * content part on a normal message. The code below is close to line-for-line identical to
 * OpenAI's `_08_vision`: same `image_url` content part carrying a `data:` URI, same shape. That
 * convergence *is* the interesting bit — this content-part shape (pioneered by OpenAI) has become
 * a lingua franca vision providers converge on, so the same request works against a different
 * vendor by swapping the client and the model string, nothing else.
 *
 * Two real differences, neither hit by this example:
 *  - No `detail` (`auto`/`low`/`high`) knob on `image_url` — OpenAI trades resolution for a fixed
 *    token budget with it; Mistral's vision encoder (Pixtral, folded into the general-purpose
 *    models since Small 3.1) handles variable resolution up to 1024×1024 natively, so there's
 *    nothing to hint.
 *  - No dedicated vision model to switch to, either: [Models.DEFAULT] (`mistral-small-latest`)
 *    already sees images. Anthropic's `_17_vision_asset` has to force a newer model for sharper
 *    results, and pre-4-series OpenAI chat models had no vision at all — Mistral folded vision
 *    into its everyday chat models rather than shipping it as a separate Pixtral-branded product.
 */

private const val ASSET = "assets/images/desktop.png"

private const val QUESTION = """
    1) Describe this image in detail. Read any clearly visible text. Respond as a descriptive bullet list of objects and facts.
    2) Check every object to see if you can see barcodes on them (barcodes are not always black on white).
    3) Return object list as a json array (fields: object name, barcode present, main colors)
    """

private fun mimeTypeFor(name: String): String =
    when (name.substringAfterLast('.').lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        else -> error("Unsupported image type for '$name' (this example handles png/jpeg).")
    }

fun main() {
    val client = mistralClient()
    try {
        val file = File(ASSET)
        require(file.exists()) { "Asset not found: ${file.absolutePath} — run from the project root." }

        val bytes = file.readBytes()
        val base64 = Base64.getEncoder().encodeToString(bytes) // no line breaks
        val dataUrl = "data:${mimeTypeFor(file.name)};base64,$base64"

        val params = ChatCompletionCreateParams.builder()
            .model(defaultModel)
            .maxTokens(1024L) // Mistral rejects `max_completion_tokens`; only `max_tokens` works
            .addUserMessageOfArrayOfContentParts(
                listOf(
                    // Image part first, then the question about it (same ordering as _17/_08).
                    ChatCompletionContentPart.ofImageUrl(
                        ChatCompletionContentPartImage.builder()
                            .imageUrl(
                                ChatCompletionContentPartImage.ImageUrl.builder()
                                    .url(dataUrl)
                                    .build()
                            )
                            .build()
                    ),
                    ChatCompletionContentPart.ofText(
                        ChatCompletionContentPartText.builder().text(QUESTION).build()
                    ),
                )
            )
            .build()

        val completion = client.chat().completions().create(params)
        val answer = completion.choices().first().message().content().getOrNull()?.trim().orEmpty()
        val usage = completion.usage().getOrNull()

        val dims = ImageIO.read(file)
        if (dims != null) {
            println("Asset: ${file.name}  ${dims.width}x${dims.height} px  (${bytes.size} bytes, base64 ${base64.length} chars)")
        }
        println("Actual usage: prompt=${usage?.promptTokens()} (image + text), completion=${usage?.completionTokens()}\n")
        println("Mistral sees:")
        println(answer)
    } finally {
        client.close()
    }
}

package demo.api.openai

import com.openai.models.chat.completions.ChatCompletionContentPart
import com.openai.models.chat.completions.ChatCompletionContentPartImage
import com.openai.models.chat.completions.ChatCompletionContentPartText
import com.openai.models.chat.completions.ChatCompletionCreateParams
import demo.api.openai.common.defaultModel
import demo.api.openai.common.openaiClient
import java.io.File
import java.util.*
import javax.imageio.ImageIO
import kotlin.jvm.optionals.getOrNull

/**
 * **Image analysis** (vision) — reusing the exact same asset and question as Anthropic's
 * `_17_vision_asset`, so the two replies can be compared side by side on identical input.
 *
 * The content-part shape differs from Anthropic's:
 *   - Anthropic splits an image into two fields on an `image` block — `media_type` and `data`.
 *   - OpenAI has one field, `image_url.url`, which is either a real remote URL **or** a `data:`
 *     URI that carries the mime type and the base64 payload together in one string
 *     (`data:image/png;base64,<...>`). No separate content-block "type" for base64 vs URL — both
 *     go through `image_url`.
 *
 * `ChatCompletionContentPartImage.ImageUrl` also exposes a `detail` knob (`auto`/`low`/`high`,
 * default `auto`) with no Anthropic equivalent: it trades resolution for tokens, `low` fixing the
 * image to a flat 85-token budget regardless of size, `high` processing it at full detail in
 * (up to several) 512×512 tiles.
 *
 * Token accounting is also less transparent here: OpenAI doesn't publish a pixel formula the way
 * Anthropic's (W×H)/750 rule does, and `usage.promptTokens()` folds the image in with the text —
 * there is no separate image-token count to compare an estimate against, so we just print the
 * actual usage.
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
    val client = openaiClient()
    try {
        val file = File(ASSET)
        require(file.exists()) { "Asset not found: ${file.absolutePath} — run from the project root." }

        val bytes = file.readBytes()
        val base64 = Base64.getEncoder().encodeToString(bytes) // no line breaks
        val dataUrl = "data:${mimeTypeFor(file.name)};base64,$base64"

        val params = ChatCompletionCreateParams.builder()
            .model(defaultModel)
            .maxCompletionTokens(1024L)
            .addUserMessageOfArrayOfContentParts(
                listOf(
                    // Image part first, then the question about it (same ordering as Anthropic's _17).
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
        println("GPT sees:")
        println(answer)
    } finally {
        client.close()
    }
}

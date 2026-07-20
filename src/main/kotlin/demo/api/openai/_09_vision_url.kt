package demo.api.openai

import com.openai.models.chat.completions.ChatCompletionContentPart
import com.openai.models.chat.completions.ChatCompletionContentPartImage
import com.openai.models.chat.completions.ChatCompletionContentPartText
import com.openai.models.chat.completions.ChatCompletionCreateParams
import demo.api.openai.common.defaultModel
import demo.api.openai.common.openaiClient
import kotlin.jvm.optionals.getOrNull

/**
 * **Image analysis from a remote URL** — no download, no base64.
 *
 * `_08_vision` reads a local asset, encodes it, and embeds the bytes in a `data:` URI.
 * Here `image_url.url` is just a plain `https://` link: OpenAI fetches the image itself,
 * server-side. (This repo's Anthropic examples, `_16`/`_17`, only demonstrate the base64 path —
 * there is no remote-URL variant of them here to compare against.)
 *
 * Everything else about the content-part shape matches `_08`: `image_url` first, then the
 * question, in the same `addUserMessageOfArrayOfContentParts` call — the only change is what
 * goes into `.url(...)`.
 *
 * The image: Manet's *Le Déjeuner sur l'herbe* (1863), fetched live from Wikimedia Commons. The
 * question asks the model to count and describe the people in the scene — a task with a real,
 * checkable answer (three figures in the foreground, a fourth bathing in the background), so a
 * wrong or vague count is a visible failure rather than a matter of taste.
 */

private const val IMAGE_URL =
    "https://upload.wikimedia.org/wikipedia/commons/f/fc/%C3%89douard_Manet_-_Le_D%C3%A9jeuner_sur_l%27herbe.jpg"

private const val QUESTION = """
    How many people are visible in this painting? List each one with their position in the
    scene (foreground/background), what they are doing, and what they are wearing (or not).
    """

fun main() {
    val client = openaiClient()
    try {
        val params = ChatCompletionCreateParams.builder()
            .model(defaultModel)
            .maxCompletionTokens(1024L)
            .addUserMessageOfArrayOfContentParts(
                listOf(
                    ChatCompletionContentPart.ofImageUrl(
                        ChatCompletionContentPartImage.builder()
                            .imageUrl(
                                ChatCompletionContentPartImage.ImageUrl.builder()
                                    .url(IMAGE_URL) // remote URL — no download or encoding on our side
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

        println("Image: $IMAGE_URL")
        println("Actual usage: prompt=${usage?.promptTokens()} (image + text), completion=${usage?.completionTokens()}\n")
        println("GPT sees:")
        println(answer)
    } finally {
        client.close()
    }
}

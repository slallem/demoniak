package demo.api.openai

import com.openai.models.moderations.ModerationCreateParams
import com.openai.models.moderations.ModerationModel
import demo.api.openai.common.openaiClient
import java.util.Locale

/**
 * Moderation: a free, standalone classification endpoint — `POST /moderations` — that flags
 * potentially harmful text (and, with the omni-moderation model family, images too) across a
 * fixed set of categories: harassment, hate, self-harm, sexual, violence, and their
 * sub-categories.
 *
 * It is unrelated to chat completions: no tokens are billed, and it uses its own small
 * [ModerationModel] family rather than the `Models.DEFAULT` chat model used by `_01`-`_09`.
 * Typical use is as a cheap guardrail — screening user input before it reaches a chat model, or
 * screening model output before it reaches a user — not as a generation call in its own right.
 */

fun main() {
    val client = openaiClient()
    try {
        val samples = listOf(
            "What's the weather like in Paris today?",
            "I want to hurt someone who wronged me."
        )

        samples.forEach { text ->
            val params = ModerationCreateParams.builder()
                .input(text)
                .model(ModerationModel.OMNI_MODERATION_LATEST)
                .build()

            val result = client.moderations().create(params).results().first()
            val categories = result.categories()
            val scores = result.categoryScores()

            println("── \"$text\" ──")
            println("  flagged: ${result.flagged()}")

            listOf(
                "harassment" to (categories.harassment() to scores.harassment()),
                "hate" to (categories.hate() to scores.hate()),
                "self-harm" to (categories.selfHarm() to scores.selfHarm()),
                "sexual" to (categories.sexual() to scores.sexual()),
                "violence" to (categories.violence() to scores.violence()),
            ).filter { (_, flaggedAndScore) -> flaggedAndScore.first }
                .forEach { (name, flaggedAndScore) ->
                    println("    $name: score=${String.format(Locale.ROOT, "%.4f", flaggedAndScore.second)}")
                }
        }
    } finally {
        client.close()
    }
}

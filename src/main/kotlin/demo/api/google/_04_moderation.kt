package demo.api.google

import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.HarmBlockThreshold
import com.google.genai.types.HarmCategory
import com.google.genai.types.SafetySetting
import demo.api.google.common.defaultModel
import demo.api.google.common.googleClient

/**
 * Moderation, Gemini-style — genuinely different from OpenAI's `_10_moderation` and Mistral's
 * `_09_moderation`, not a relabeled version of the same call.
 *
 * Gemini has **no free-standing classification endpoint**: there is no `models.moderations` or
 * `/v1/moderations` to call on its own. The SDK models a filter layer that rides along on every
 * `generateContent` call instead, on both sides of the exchange:
 *  - [com.google.genai.types.GenerateContentResponse.promptFeedback] would classify the *input*
 *    — `blockReason()` is set if the prompt itself is rejected before generation ever runs.
 *  - Each [com.google.genai.types.Candidate.safetyRatings] would classify the *output* — one
 *    rating per harm category, each with a `blocked` flag.
 *
 * "Would", because — tested live below, `safetySettings` explicitly tightened to
 * `BLOCK_LOW_AND_ABOVE` on all four categories — **neither ever showed up** for either prompt,
 * including the "I want to hurt someone..." one that both OpenAI and Mistral flag with a
 * violence score above 0.85. Gemini instead handled it entirely through the model's own trained
 * behavior: a normal, in-character empathetic reply, `finishReason: STOP`, no filter metadata at
 * all. This is the real, useful finding, not a bug in this example: unlike OpenAI/Mistral's
 * always-on numeric classifier, Gemini's structured safety metadata is opportunistic for text —
 * it engages for hard-blocked prompts (rare, and not something to go hunting for as a "wins" demo
 * — most policy-violating asks just get a natural-language refusal, as seen here) but stays
 * silent the rest of the time. In practice, the refusal text *is* Gemini's moderation signal for
 * text content, not a parallel structured score.
 *
 * Other differences worth knowing if you configure `safetySettings` yourself:
 *  - Taxonomy is smaller than OpenAI/Mistral — 4 categories on the public Gemini API:
 *    `HARASSMENT`, `HATE_SPEECH`, `SEXUALLY_EXPLICIT`, `DANGEROUS_CONTENT` (`CIVIC_INTEGRITY`
 *    still exists as an enum value but is deprecated; several other
 *    [com.google.genai.types.HarmCategory] values are Vertex-only, not usable here).
 *  - Probability, when a rating *is* returned, comes back as a coarse enum —
 *    `NEGLIGIBLE`/`LOW`/`MEDIUM`/`HIGH` — not a numeric score:
 *    [com.google.genai.types.SafetyRating.probabilityScore] exists in the type but is explicitly
 *    "not supported in Gemini API" (Vertex AI only, along with `severity`/`severityScore`).
 */

fun main() {
    val client = googleClient()
    client.use { client ->
        val samples = listOf(
            "What's the weather like in Paris today?",
            "I want to hurt someone who wronged me."
        )

        val config = GenerateContentConfig.builder()
            .safetySettings(
                listOf(
                    HarmCategory.Known.HARM_CATEGORY_HARASSMENT,
                    HarmCategory.Known.HARM_CATEGORY_HATE_SPEECH,
                    HarmCategory.Known.HARM_CATEGORY_SEXUALLY_EXPLICIT,
                    HarmCategory.Known.HARM_CATEGORY_DANGEROUS_CONTENT,
                ).map { category ->
                    SafetySetting.builder()
                        .category(category)
                        .threshold(HarmBlockThreshold.Known.BLOCK_LOW_AND_ABOVE)
                        .build()
                }
            )
            .build()

        samples.forEach { text ->
            val response = client.models.generateContent(defaultModel, text, config)

            println("── \"$text\" ──")
            println("  reply: ${response.text() ?: "(no content — see ratings below)"}")

            // Prompt-level verdict: only present if the prompt itself was rejected pre-generation.
            val feedback = response.promptFeedback().orElse(null)
            val blockReason = feedback?.blockReason()?.orElse(null)
            if (blockReason != null) {
                println("  blockReason: $blockReason")
            }

            // Output-level verdict: in principle present on every candidate; in practice, absent
            // whenever the model handles a risky prompt itself via a natural-language refusal.
            val candidate = response.candidates().orElse(emptyList()).firstOrNull()
            val finishReason = candidate?.finishReason()?.orElse(null)
            if (finishReason != null) {
                println("  finishReason: $finishReason")
            }

            val ratings = candidate?.safetyRatings()?.orElse(emptyList()).orEmpty()
            if (ratings.isEmpty()) {
                println("  (no safety ratings returned — see the note in this file's header)")
            }
            ratings.forEach { rating ->
                val category = rating.category().orElse(null)
                val probability = rating.probability().orElse(null)
                val blocked = rating.blocked().orElse(false)
                println("    $category: probability=$probability blocked=$blocked")
            }
        }
    }
}

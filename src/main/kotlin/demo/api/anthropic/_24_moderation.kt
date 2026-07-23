package demo.api.anthropic

import com.anthropic.models.messages.MessageCreateParams
import com.fasterxml.jackson.databind.ObjectMapper
import demo.api.anthropic.common.anthropicClient
import demo.api.anthropic.common.defaultModel

/**
 * Moderation, Claude-style — the most different of the four provider examples in this repo.
 *
 * Anthropic ships **no moderation endpoint and no safety-metadata mechanism at all** — not a
 * dedicated classifier like OpenAI's `_10_moderation`/Mistral's `_09_moderation`, not even the
 * opportunistic safety ratings of Gemini's `_04_moderation`. Anthropic's own documented approach
 * (platform.claude.com, "Content moderation" guide) is to **use Claude itself as the
 * classifier**: an ordinary prompt asking for a JSON verdict, sent through the same Messages API
 * `_01_starter` uses for a greeting — just with a moderation-shaped prompt instead. This example
 * reproduces that recommendation as-is, including its 12-category taxonomy and
 * `claude-haiku-4-5` as the classifier model (Anthropic's own pick for cost — and, conveniently,
 * already this repo's `Models.DEFAULT`).
 *
 * Nothing enforces the JSON shape here: unlike `_09`'s native structured outputs, this is a plain
 * text completion asked nicely to reply in JSON — the same shakier ground as OpenAI's
 * `_07_json_mode`. Anthropic's guide accepts that tradeoff explicitly: there is no cheaper,
 * structurally-guaranteed alternative on this API today. Pairing each category with a short
 * definition (below) is one of the guide's own accuracy tips, not incidental detail.
 */

private val CATEGORIES = mapOf(
    "Child Exploitation" to "Content that depicts child nudity or that enables, encourages, excuses, or depicts the sexual abuse of children.",
    "Conspiracy Theories" to "Content that promotes or endorses unfounded, false, or misleading theories about events, situations, or individuals, which could incite harm or undermine public trust in institutions or facts.",
    "Hate" to "Content that is hateful toward people on the basis of a protected attribute, or that perpetuates negative stereotypes.",
    "Indiscriminate Weapons" to "Content that enables, encourages, or excuses the creation of weapons of mass destruction.",
    "Intellectual Property" to "Content that violates the intellectual property rights of third parties.",
    "Non-Violent Crimes" to "Content that enables, encourages, or excuses the commission of non-violent crimes.",
    "Privacy" to "Content that contains sensitive, personal information about private individuals.",
    "Self-Harm" to "Content that enables, encourages, or excuses acts of intentional self-harm.",
    "Sex Crimes" to "Content that enables, encourages, or excuses the commission of sex crimes.",
    "Sexual Content" to "Content that contains erotica.",
    "Specialized Advice" to "Content that contains financial, medical, or legal advice.",
    "Violent Crimes" to "Content that enables, encourages, or excuses the commission of violent crimes.",
)

private val mapper = ObjectMapper()

private fun moderationPrompt(message: String): String {
    val categoryList = CATEGORIES.entries.joinToString("\n") { (name, description) -> "$name: $description" }
    return """
        Determine whether the following message warrants moderation, based on the unsafe
        categories outlined below.

        Message:
        <message>$message</message>

        Unsafe Categories:
        <categories>
        $categoryList
        </categories>

        Respond with ONLY a JSON object, using the format below:
        {
          "violation": <Boolean field denoting whether the message should be moderated>,
          "categories": [Comma-separated list of violated categories],
          "explanation": [Optional. Only include if there is a violation.]
        }
        Do not include markdown formatting or code fences in your response.
    """.trimIndent()
}

/** Strips an occasional ```json fence Haiku adds despite being told not to — defensive, not required. */
private fun stripCodeFence(raw: String): String =
    raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

fun main() {
    val client = anthropicClient()
    try {
        val samples = listOf(
            "What's the weather like in Paris today?",
            "I want to hurt someone who wronged me."
        )

        samples.forEach { text ->
            val params = MessageCreateParams.builder()
                .model(defaultModel)
                .maxTokens(512L)
                .addUserMessage(moderationPrompt(text))
                .build()

            val response = client.messages().create(params)
            val raw = response.content().stream()
                .flatMap { block -> block.text().stream() }
                .map { it.text() }
                .findFirst()
                .orElse("")

            val json = mapper.readTree(stripCodeFence(raw))
            val violation = json.path("violation").asBoolean(false)

            println("── \"$text\" ──")
            println("  violation: $violation")
            if (violation) {
                val categories = json.path("categories").joinToString(", ") { it.asText() }
                println("    categories: $categories")
                println("    explanation: ${json.path("explanation").asText("")}")
            }
        }
    } finally {
        client.close()
    }
}

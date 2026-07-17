package demo.api.google

import demo.api.google.common.defaultModel
import demo.api.google.common.googleClient

/**
 * Two-turn (or more) chat with the Gemini API.
 *
 * Like the Claude Messages API, `generateContent` is stateless: a conversation is just the
 * full list of contents resent on every call. The difference is that the GenAI SDK ships the
 * "memorizing" part out of the box — `client.chats.create(model)` returns a [com.google.genai.Chat]
 * session that appends the user message, resends the whole history, then appends the model reply.
 * So there is no hand-rolled `Conversation` class here, unlike
 * [demo.api.anthropic.common.Conversation] on the Anthropic side.
 *
 * `chat.getHistory(true)` exposes that remembered list — *curated* history (only the turns the
 * model accepted); passing `false` returns the *comprehensive* one, including invalid turns.
 *
 * This example also points LLM weakness about randomness, because it relies on
 * probabilities and so generally returns the same responses when asked for random.
 * */

fun main() {
    val client = googleClient()
    client.use { client ->
        val chat = client.chats.create(defaultModel) // uses Models.DEFAULT

        // Each sendMessage() remembers the previous turns, so questions can build on each other.
        val color = chat.sendMessage(
            "Pick a random color among the rainbow colors. " +
                    "Answer with just the color name, nothing else."
        )
        println("Color: ${color.text()?.trim()}")

        val thing = chat.sendMessage(
            "Now name a random flower, fruit, or vegetable that is this color. " +
                    "Answer with just its name."
        )
        println("Thing: ${thing.text()?.trim()}")

        // The session kept all four contents: 2 questions + 2 replies.
        println("Remembered contents: ${chat.getHistory(true).size}")
    }
}
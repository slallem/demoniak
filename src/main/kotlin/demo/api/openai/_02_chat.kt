package demo.api.openai

import demo.api.openai.common.Conversation
import demo.api.openai.common.openaiClient

/**
 * Two-turn (or more) chat with the OpenAI Chat Completions API.
 *
 * Chat Completions is **stateless**, exactly like the Claude Messages API: the server keeps
 * nothing between calls, so a conversation is just the growing list of messages resent whole
 * on every request. Unlike the Gemini SDK — which ships a memorizing `Chat` session (see
 * [demo.api.google._02_chatKt]) — `openai-java` has no such helper, so the memory is
 * hand-rolled in [Conversation], mirroring [demo.api.anthropic.common.Conversation]. Read that
 * class: the resend *is* the whole mechanism.
 *
 * This example also points at an LLM weakness about randomness: because it works on
 * probabilities, it generally returns the same answer when asked for something "random".
 */

fun main() {
    val client = openaiClient()
    try {
        val chat = Conversation(client)

        // Each ask() resends the previous turns, so questions can build on each other.
        val color = chat.ask(
            "Pick a random color among the rainbow colors. " +
                "Answer with just the color name, nothing else."
        )
        println("Color: $color")

        // This only works because turn 1 is resent — the server remembered nothing.
        val thing = chat.ask(
            "Now name a random flower, fruit, or vegetable that is this color. " +
                "Answer with just its name."
        )
        println("Thing: $thing")

        // 2 questions + 2 replies.
        println("Remembered messages: ${chat.size}")
    } finally {
        client.close()
    }
}

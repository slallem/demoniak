package demo.api.ovh

import demo.api.ovh.common.Conversation
import demo.api.ovh.common.ovhClient

/**
 * Two-turn (or more) chat with OVHcloud AI Endpoints, with memory between calls.
 *
 * The chat API is **stateless**, exactly like Claude's or OpenAI's: the server keeps nothing
 * between calls, so a conversation is just the growing list of messages resent whole on every
 * request. [Conversation] "memorizes" that list — see it, the resend *is* the whole mechanism.
 *
 * This example also points at an LLM weakness about randomness: because it works on
 * probabilities, it generally returns the same answer when asked for something "random".
 */

fun main() {
    val client = ovhClient()
    try {
        val chat = Conversation(client) // uses Models.DEFAULT

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

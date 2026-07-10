package demo.api.anthropic

import demo.api.anthropic.common.Conversation
import demo.api.anthropic.common.anthropicClient

/**
 * Two-turn (or more) chat with the Claude API.
 *
 * The Messages API is stateless, so a conversation is just the full list of
 * messages resent on every call. [demo.api.anthropic.common.Conversation] "memorizes" that list: each
 * [demo.api.anthropic.common.Conversation.ask] appends the user question, sends the whole history, then
 * appends the assistant reply — so chaining more questions is one call each.
 *
 * This example also points LLM weakness about randomness, because it relies on
 * probabilities and so generally returns the same responses when asked for random.
 * */

fun main() {
    val client = anthropicClient()
    try {
        val chat =
            Conversation(client) // uses Models.DEFAULT

        // Each ask() remembers the previous turns, so questions can build on each other.
        val color = chat.ask(
            "Pick a random color among the rainbow colors. " +
                "Answer with just the color name, nothing else."
        )
        println("Color: $color")

        val thing = chat.ask(
            "Now name a random flower, fruit, or vegetable that is this color. " +
                "Answer with just its name."
        )
        println("Thing: $thing")

    } finally {
        client.close()
    }
}
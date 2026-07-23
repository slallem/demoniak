package demo.api.aws

import demo.api.aws.common.Conversation
import demo.api.aws.common.bedrockClient

/**
 * Two-turn (or more) chat with Claude through Amazon Bedrock's Converse API.
 *
 * [demo.api.aws.common.Conversation] "memorizes" the history the same way
 * [demo.api.anthropic.common.Conversation] does on the direct Anthropic side: each `ask()`
 * appends the user turn, resends the whole history, then appends the assistant reply.
 *
 * This example also points at an LLM weakness around randomness: it relies on probabilities and
 * so generally returns the same responses when asked for something "random".
 */

fun main() {
    val client = bedrockClient()
    client.use { client ->
        val chat = Conversation(client) // uses Models.DEFAULT

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
    }
}

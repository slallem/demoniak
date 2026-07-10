package demo.api.anthropic

import demo.api.anthropic.common.Conversation
import demo.api.anthropic.common.anthropicClient

/**
 * The system prompt sets a persona and answering rules once, up front. It is not
 * a conversation turn: it is resent (unchanged) on every call and shapes every
 * reply. Here it turns Claude into a terse botanist — so the three questions all
 * come back as short, expert one-liners without repeating the instruction each time.
 */

private const val BOTANIST = """
You are a botanist. Answer every question with a single, very concise sentence,
in the precise register a botany expert would use. No preamble, no pleasantries.
"""

fun main() {
    val client = anthropicClient()
    try {
        val chat = Conversation(
            client,
            system = BOTANIST.trim()
        )

        for (plant in listOf("apple", "banana", "cherry")) {
            val answer = chat.ask("Explain to me what is a $plant.")
            println("$plant → $answer")
        }
    } finally {
        client.close()
    }
}
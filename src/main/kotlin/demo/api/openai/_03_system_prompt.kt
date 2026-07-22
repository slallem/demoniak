package demo.api.openai

import com.openai.models.chat.completions.ChatCompletionCreateParams
import demo.api.openai.common.defaultModel
import demo.api.openai.common.openaiClient
import kotlin.jvm.optionals.getOrNull

/**
 * The system prompt sets a persona and answering rules once, up front. It is not a conversation
 * turn: it is resent (unchanged) on every call and shapes every reply. Here it turns the model
 * into a terse botanist — so the three questions all come back as short, expert one-liners
 * without repeating the instruction each time.
 *
 * Where Claude takes a top-level `system` parameter and Gemini a `systemInstruction` on its
 * config, OpenAI makes it **the first message of the list**: `addSystemMessage(...)` before any
 * user message. Newer OpenAI models also accept `addDeveloperMessage(...)`, the renamed
 * successor of the system role — same behaviour, and `system` is still accepted everywhere.
 *
 * Note the three questions here are *independent* calls (no memory, unlike [_02_chatKt]) — the
 * only thing they share is the system message. That is exactly the point: the persona comes from
 * the resent instruction, not from the history. To combine both, pass `system` to
 * [demo.api.openai.common.Conversation], which prepends it once and resends it every turn.
 */

private const val BOTANIST = """
You are a botanist. Answer every question with a single, very concise sentence,
in the precise register a botany expert would use. No preamble, no pleasantries.
"""

fun main() {
    val client = openaiClient()
    try {
        for (plant in listOf("apple", "banana", "cherry")) {
            val params = ChatCompletionCreateParams.builder()
                .model(defaultModel)
                .maxCompletionTokens(1024L)
                .addSystemMessage(BOTANIST.trim()) // must come before the user message
                .addUserMessage("Explain to me what a $plant is.")
                .build()

            val answer = client.chat().completions().create(params)
                .choices().first().message().content().getOrNull()?.trim()

            println("$plant → $answer")
        }
    } finally {
        client.close()
    }
}

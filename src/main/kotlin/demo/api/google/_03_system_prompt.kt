package demo.api.google

import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import demo.api.google.common.defaultModel
import demo.api.google.common.googleClient

/**
 * The system instruction sets a persona and answering rules once, up front. It is not
 * a conversation turn: it is resent (unchanged) on every call and shapes every reply.
 * Here it turns Gemini into a terse botanist — so the three questions all come back as
 * short, expert one-liners without repeating the instruction each time.
 *
 * Gemini calls it `systemInstruction` (on [GenerateContentConfig]) where Claude calls it
 * `system`, but the notion is the same. Two SDK details:
 *  - there is no `String` overload — it takes a [Content], hence [Content.fromParts].
 *  - the config is handed to `chats.create(model, config)` once, and the session applies
 *    it to every turn.
 */

private const val BOTANIST = """
You are a botanist. Answer every question with a single, very concise sentence,
in the precise register a botany expert would use. No preamble, no pleasantries.
"""

fun main() {
    val client = googleClient()
    client.use { client ->
        val config = GenerateContentConfig.builder()
            .systemInstruction(Content.fromParts(Part.fromText(BOTANIST.trim())))
            .build()

        val chat = client.chats.create(defaultModel, config)

        for (plant in listOf("apple", "banana", "cherry")) {
            val answer = chat.sendMessage("Explain to me what a $plant is.")
            println("$plant → ${answer.text()?.trim()}")
        }
    }
}

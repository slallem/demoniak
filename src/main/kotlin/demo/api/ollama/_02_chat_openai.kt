package demo.api.ollama

import com.openai.client.OpenAIClient
import com.openai.models.ChatModel
import com.openai.models.ReasoningEffort
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import demo.api.ollama.common.defaultModel
import demo.api.ollama.common.ollamaClient
import kotlin.jvm.optionals.getOrNull

/**
 * Multi-turn chat against local Ollama through the **OpenAI SDK** — the same conversation as the
 * pure-HTTP [_02_chat_httpKt], via `openai-java` pointed at `localhost/v1`.
 *
 * Read the two together. The memory mechanics are *identical* — the API is stateless, so a
 * conversation is the growing message list resent whole every turn — but here the SDK carries the
 * typed message objects and the HTTP/JSON, where the HTTP variant spells all of that out. Note the
 * mechanics don't change: [OpenAiConversation] still appends each turn to [history] and resends
 * everything, exactly as the hand-rolled version does. A client library saves typing, not resends.
 *
 * The one Ollama-specific touch is `reasoningEffort(NONE)`: it maps to Ollama's `think: false`, so
 * the 2B model doesn't run away reasoning. That is as far as this route reaches — `keep_alive` and
 * native streaming aren't in the OpenAI schema, so for those you drop to `_02_chat_http`.
 *
 * Prerequisites: `ollama serve` running, and `ollama pull qwen3.5:2b` done once.
 */

/** A remembered chat over the OpenAI-compatible endpoint: keeps typed message params and grows them per turn. */
private class OpenAiConversation(
    private val client: OpenAIClient,
    private val model: String = defaultModel,
) {
    private val history = mutableListOf<ChatCompletionMessageParam>()

    /** Sends [question] with the full remembered history and returns the reply text. */
    fun ask(question: String): String {
        history += ChatCompletionMessageParam.ofUser(
            ChatCompletionUserMessageParam.builder().content(question).build()
        )

        val params = ChatCompletionCreateParams.builder()
            .model(ChatModel.of(model))
            .maxCompletionTokens(1024L)
            .reasoningEffort(ReasoningEffort.NONE) // Ollama reads this as `think: false`
            .messages(history)
            .build()

        val answer = client.chat().completions().create(params)
            .choices().first().message().content().getOrNull()?.trim() ?: ""

        history += ChatCompletionMessageParam.ofAssistant(
            ChatCompletionAssistantMessageParam.builder().content(answer).build()
        )
        return answer
    }
}

fun main() {
    val client = ollamaClient()
    try {
        val chat = OpenAiConversation(client)

        // Turn 1 tells the model some facts. Nothing is stored server-side.
        val ack = chat.ask(
            "Remember two facts about me: my name is Ada, and my favorite fruit is mango. " +
                "Reply with just OK."
        )
        println("Turn 1: $ack")

        // Turn 2 asks them back. This works *only* because ask() resends turn 1 in the history —
        // the endpoint kept nothing. Drop the resend and the model would have no idea who "I" am.
        val recall = chat.ask("Using only what I told you, what are my name and my favorite fruit?")
        println("Turn 2: $recall")
    } finally {
        client.close()
    }
}

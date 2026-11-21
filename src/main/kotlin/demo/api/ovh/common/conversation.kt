package demo.api.ovh.common

import com.openai.client.OpenAIClient
import com.openai.models.ChatModel
import com.openai.models.ReasoningEffort
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import com.openai.models.completions.CompletionUsage
import kotlin.jvm.optionals.getOrNull

/**
 * A remembered chat: keeps the running message history and grows it per turn.
 *
 * AI Endpoints' chat API is **stateless** — the server keeps nothing between calls — so "memory"
 * is simply this list, resent whole on every request. Same class as
 * [demo.api.openai.common.Conversation] (the API is OpenAI-compatible), with two adjustments for
 * Olala being a *reasoning* model: a larger default [maxTokens] (thinking tokens count against
 * it) and a `low` reasoning effort so quick Q&A stays quick.
 *
 * Only the answer text is stored back in the history, not the thinking.
 */
class Conversation(
    private val client: OpenAIClient,
    private val model: ChatModel = defaultModel,
    private val maxTokens: Long = 2048L,
    private val effort: ReasoningEffort = ReasoningEffort.LOW,
    system: String? = null,
) {
    private val history = mutableListOf<ChatCompletionMessageParam>()

    init {
        if (system != null) {
            history += ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam.builder().content(system).build()
            )
        }
    }

    /** Number of messages remembered so far (system prompt + questions + replies). */
    val size: Int get() = history.size

    /** Token usage of the most recent [ask]. */
    var lastUsage: CompletionUsage? = null
        private set

    /** Sends [question] with the full remembered history and returns the reply text. */
    fun ask(question: String): String {
        history += ChatCompletionMessageParam.ofUser(
            ChatCompletionUserMessageParam.builder().content(question).build()
        )

        val params = ChatCompletionCreateParams.builder()
            .model(model)
            .maxCompletionTokens(maxTokens)
            .reasoningEffort(effort)
            .messages(history)
            .build()

        val completion = client.chat().completions().create(params)
        lastUsage = completion.usage().getOrNull()

        val answer = completion.choices().first().message().content().getOrNull()?.trim() ?: ""
        history += ChatCompletionMessageParam.ofAssistant(
            ChatCompletionAssistantMessageParam.builder().content(answer).build()
        )
        return answer
    }
}

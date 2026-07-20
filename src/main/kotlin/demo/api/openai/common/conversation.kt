package demo.api.openai.common

import com.openai.client.OpenAIClient
import com.openai.models.ChatModel
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
 * Chat Completions is **stateless** — the server keeps nothing between calls — so "memory" is
 * simply this list, resent whole on every request. Same mechanic as
 * [demo.api.anthropic.common.Conversation]; the difference is shape, not behaviour: OpenAI
 * messages are a sealed union, so each turn is wrapped in `ChatCompletionMessageParam.ofUser(…)`
 * / `.ofAssistant(…)`, and the system prompt is the **first message of the list** rather than a
 * separate top-level parameter.
 *
 * When [system] is provided it is prepended once, at construction, and therefore resent with
 * every turn — that is what keeps a persona stable across a conversation.
 */
class Conversation(
    private val client: OpenAIClient,
    private val model: ChatModel = defaultModel,
    private val maxTokens: Long = 1024L,
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

package org.example.demo.anthropic.common

import com.anthropic.client.AnthropicClient
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.Model
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.Usage
import java.util.stream.Collectors

/** Joins all text blocks of a response into a single string. */
private fun textOf(response: Message): String =
    response.content().stream()
        .flatMap { block -> block.text().stream() }
        .map { it.text() }
        .collect(Collectors.joining())

/**
 * A remembered chat: keeps the running message history and grows it per turn.
 *
 * When [system] is provided, it is sent as a **cached** system prompt: a
 * `cache_control` breakpoint marks the end of the stable prefix (tools + system),
 * so it is written to cache once and re-read on every later turn instead of being
 * re-processed at full price.
 *
 * Caveat — caching is a prefix match with a per-model minimum size. For
 * `claude-opus-4-8` the cached prefix must be **≥ 4096 tokens** or nothing is
 * cached (silently: [Usage.cacheCreationInputTokens] stays 0). The [system] text
 * therefore has to be genuinely large to see any effect.
 */
class Conversation(
    private val client: AnthropicClient,
    private val model: Model,
    private val maxTokens: Long = 1024L,
    private val system: String? = null,
) {
    private val history = mutableListOf<MessageParam>()

    /** Usage of the most recent [ask] — includes cache read/creation token counts. */
    var lastUsage: Usage? = null
        private set

    /** Sends [question] with the full remembered history and returns the reply text. */
    fun ask(question: String): String {
        history.add(
            MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(question)
                .build()
        )

        val builder = MessageCreateParams.builder()
            .model(model)
            .maxTokens(maxTokens)
            .messages(history)

        if (system != null) {
            builder.systemOfTextBlockParams(
                listOf(
                    TextBlockParam.builder()
                        .text(system)
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()
                )
            )
        }

        val response = client.messages().create(builder.build())
        lastUsage = response.usage()

        val answer = textOf(response)
        history.add(
            MessageParam.builder()
                .role(MessageParam.Role.ASSISTANT)
                .content(answer)
                .build()
        )
        return answer
    }
}
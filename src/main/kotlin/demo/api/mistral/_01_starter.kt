package demo.api.mistral

import com.openai.models.chat.completions.ChatCompletionCreateParams
import demo.api.mistral.common.defaultModel
import demo.api.mistral.common.mistralClient

/**
 * Starter for connecting to the Mistral API from Kotlin.
 *
 * Mistral has no dedicated Java SDK here: its `/v1/chat/completions` mirrors OpenAI's shape
 * closely enough that the `openai-java` SDK talks to it with nothing but a different `baseUrl`
 * (see `mistralClient()`) — the same trick `ollama/_01_starter.kt` uses for a local server.
 *
 * One real gap in that compatibility: `.maxTokens(...)` is used below instead of the newer
 * `.maxCompletionTokens(...)` seen in the OpenAI examples — Mistral returns a 422 on
 * `max_completion_tokens` and only accepts the deprecated `max_tokens` field.
 */

fun main() {
    val client = mistralClient()
    try {
        val params = ChatCompletionCreateParams.builder()
            .model(defaultModel)
            .maxTokens(1024L) // Mistral rejects `max_completion_tokens`; only `max_tokens` works
            .addUserMessage("Hello, Mistral! Reply with a short one-line greeting.")
            .build()

        val completion = client.chat().completions().create(params)

        completion.choices().stream()
            .flatMap { choice -> choice.message().content().stream() }
            .forEach { text -> println(text) }
    } finally {
        client.close()
    }
}

package demo.api.ovh

import com.openai.models.ReasoningEffort
import com.openai.models.chat.completions.ChatCompletionCreateParams
import demo.api.ovh.common.defaultModel
import demo.api.ovh.common.ovhClient

/**
 * Starter for connecting to OVHcloud AI Endpoints from Kotlin, with OVH's own **Olala** model.
 *
 * Same recipe as the other OpenAI-compatible providers here (`mistral`, `deepseek`, `ollama`): the
 * `openai-java` SDK with a different `baseUrl` (see `ovhClient()`).
 *
 * Olala is a *reasoning* model: it "thinks" before answering, and those thinking tokens count
 * against the output budget. A tight `maxCompletionTokens` can be spent entirely on reasoning and
 * leave the visible answer **empty** (`finish_reason = length`) — so this starter sets a generous
 * budget and a `low` reasoning effort (the API accepts `low`, `medium`, `high`).
 */

fun main() {
    val client = ovhClient()
    try {
        val params = ChatCompletionCreateParams.builder()
            .model(defaultModel)
            .maxCompletionTokens(2048L)
            .reasoningEffort(ReasoningEffort.LOW)
            .addUserMessage("Hello, Olala! Reply with a short one-line greeting.")
            .build()

        val completion = client.chat().completions().create(params)

        completion.choices().stream()
            .flatMap { choice -> choice.message().content().stream() }
            .forEach { text -> println(text) }
    } finally {
        client.close()
    }
}

package demo.api.deepseek

import com.openai.models.chat.completions.ChatCompletionCreateParams
import demo.api.deepseek.common.deepseekClient
import demo.api.deepseek.common.defaultModel

/**
 * Starter for connecting to the DeepSeek API from Kotlin.
 *
 * DeepSeek has no dedicated Java SDK here: its API is explicitly OpenAI-compatible, so the
 * `openai-java` SDK talks to it with nothing but a different `baseUrl` (see `deepseekClient()`) —
 * the same trick `mistral/_01_starter.kt` and `ollama/_01_starter.kt` use. Unlike Mistral, DeepSeek's
 * docs claim full compatibility with no field-level gap, so this uses the modern
 * `.maxCompletionTokens(...)` rather than Mistral's legacy `.maxTokens(...)` fallback — confirmed
 * against a live key: works as documented, no gap to work around here.
 *
 * One real thing to know if you hit it: a fresh/unfunded account gets a `402 Insufficient
 * Balance` from `client.chat().completions().create(...)` — a clean structured API error (not a
 * connection or auth failure), meaning the request shape and auth were both already correct by
 * the time billing was checked. Top up at platform.deepseek.com and retry.
 */

fun main() {
    val client = deepseekClient()
    try {
        val params = ChatCompletionCreateParams.builder()
            .model(defaultModel)
            .maxCompletionTokens(1024L)
            .addUserMessage("Hello, DeepSeek! Reply with a short one-line greeting.")
            .build()

        val completion = client.chat().completions().create(params)

        completion.choices().stream()
            .flatMap { choice -> choice.message().content().stream() }
            .forEach { text -> println(text) }
    } finally {
        client.close()
    }
}

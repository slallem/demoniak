package demo.api.ollama

import com.openai.models.ChatModel
import com.openai.models.ReasoningEffort
import com.openai.models.chat.completions.ChatCompletionCreateParams
import demo.api.ollama.common.defaultModel
import demo.api.ollama.common.ollamaClient

/**
 * Ollama can be called two ways: its **OpenAI-compatible** API at `/v1` (reuse any OpenAI SDK,
 * just change the URL), or its **native HTTP** API under `/api` (raw JSON, exposes Ollama-only
 * features). This starter uses the OpenAI-compatible route; `_02_chat_http` shows the native one.
 *
 * Talking to a **local** model the cheap way: Ollama re-implements the OpenAI API at `/v1`, so the
 * `openai-java` SDK already in this build works against it unchanged.
 *
 * Compare with `demo/api/openai/_01_starter.kt`: the code below is nearly identical. The only
 * differences live in `ollamaClient()` — a `baseUrl` on localhost and a throwaway API key. That
 * is the whole point: "running locally" is a URL change, not a rewrite. No key, no quota, no
 * network.
 *
 * The one addition is `reasoningEffort(NONE)`: Qwen3.5 reasons by default, and on a 2B model that
 * runs away — a one-word answer never returns. Ollama maps OpenAI's standard `reasoning_effort`
 * onto its thinking switch, so `NONE` is the OpenAI-compatible way to say `think: false`. That is
 * the limit of this route, though: only what the OpenAI schema already models is reachable.
 * Pulling a model, `keep_alive`, native streaming — those need Ollama's own protocol (`_02_chat_http`).
 *
 * Prerequisites: `ollama serve` running, and `ollama pull qwen3.5:2b` done once.
 */

fun main() {
    val client = ollamaClient()
    try {
        val params = ChatCompletionCreateParams.builder()
            .model(ChatModel.of(defaultModel))
            .maxCompletionTokens(1024L)
            .reasoningEffort(ReasoningEffort.NONE) // maps to Ollama's `think: false`; without it a 2B reasoner never returns
            .addUserMessage("Hello! Reply with a short one-line greeting.")
            .build()

        val completion = client.chat().completions().create(params)

        completion.choices().stream()
            .flatMap { choice -> choice.message().content().stream() }
            .forEach { text -> println(text) }
    } finally {
        client.close()
    }
}
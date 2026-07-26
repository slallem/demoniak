package demo.api.deepseek

import com.openai.core.JsonValue
import com.openai.models.ReasoningEffort
import com.openai.models.chat.completions.ChatCompletionCreateParams
import demo.api.deepseek.common.deepseekClient
import demo.api.deepseek.common.defaultModel

/**
 * DeepSeek V4's **thinking mode** — the dual-mode toggle that replaced the old, separate
 * `deepseek-reasoner` model (see `common/models.kt`): one model id, [defaultModel]
 * (`deepseek-v4-flash`), can run with reasoning on or off depending on a request flag.
 *
 * None of this is in the typed `openai-java` SDK — it's DeepSeek-specific, layered on top of the
 * OpenAI-compatible surface, so it has to go through the SDK's raw-JSON escape hatches:
 *   - **Request**: a `thinking: {"type": "enabled" | "disabled"}` object has no typed builder
 *     method, so it's set via `putAdditionalBodyProperty`. Default is `"enabled"`. `reasoning_effort`
 *     *is* a typed field on `ChatCompletionCreateParams` (shared with OpenAI's own reasoning
 *     models), but DeepSeek only recognizes `"high"` (default) or `"max"` for it — not OpenAI's
 *     `minimal`/`low`/`medium`/`xhigh` scale — so [ReasoningEffort.of] is used to send the exact
 *     string DeepSeek expects rather than picking from the OpenAI-flavored constants.
 *   - **Response**: the chain-of-thought comes back as `reasoning_content`, a sibling of `content`
 *     on the assistant message. `ChatCompletionMessage` has no typed getter for it either, so it's
 *     read off `_additionalProperties()`.
 *
 * Also DeepSeek-specific: thinking mode silently **ignores** `temperature`/`top_p`/
 * `presence_penalty`/`frequency_penalty` (no error — they just have no effect). And in multi-turn
 * tool-calling conversations, `reasoning_content` must be threaded back on every subsequent turn or
 * the API 400s — out of scope for this single-turn example, but worth knowing before building
 * anything agentic on top of this.
 *
 * The demo asks the classic bat-and-ball riddle (bat costs $1 more than the ball, both cost $1.10
 * total — the well-known trap answer is "$0.10", the correct one is "$0.05") once with thinking
 * off and once with thinking on. **Tested live**: `deepseek-v4-flash` answers correctly ($0.05)
 * either way — it's evidently strong enough not to need visible reasoning for a riddle this well
 * known — so the real, visible contrast here isn't right-vs-wrong, it's that only the thinking-on
 * call has a `reasoning_content` at all: thinking-off returns just the final answer, thinking-on
 * shows the full "let the ball cost x…" derivation before it.
 */

// Note: Hard to find a tricky enough question in order to fool the thinking=OFF case.
// I can notice that thinking=ON response often takes more time than thinking=OFF on most tricky questions
private const val RIDDLE =
    "A bat and a ball cost \$1.10 in total. The bat costs \$1.00 more than the ball. " +
        "How much does the ball cost? Answer with just the price."

/** Builds a request, toggling DeepSeek's thinking mode via the raw-JSON escape hatch. */
private fun request(thinkingEnabled: Boolean) =
    ChatCompletionCreateParams.builder()
        .model(defaultModel)
        .maxCompletionTokens(1024L)
        .addUserMessage(RIDDLE)
        .putAdditionalBodyProperty(
            "thinking",
            JsonValue.from(mapOf("type" to if (thinkingEnabled) "enabled" else "disabled"))
        )
        .apply { if (thinkingEnabled) reasoningEffort(ReasoningEffort.of("high")) }
        .build()

fun main() {
    val client = deepseekClient()
    try {
        listOf(false, true).forEach { thinkingEnabled ->
            val label = if (thinkingEnabled) "THINKING ON " else "THINKING OFF"
            val message = client.chat().completions().create(request(thinkingEnabled))
                .choices().first().message()

            val reasoning = message._additionalProperties()["reasoning_content"]
                ?.convert(String::class.java)
            val answer = message.content().orElse("(no content)")

            println("[$label]")
            if (reasoning != null) println("reasoning_content: $reasoning")
            println("answer: $answer\n")
        }
    } finally {
        client.close()
    }
}

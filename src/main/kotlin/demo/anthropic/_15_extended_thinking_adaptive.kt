package org.example.demo.anthropic

import com.anthropic.client.AnthropicClient
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.Model
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.ThinkingConfigAdaptive
import org.example.demo.anthropic.common.Models
import org.example.demo.anthropic.common.anthropicClient

/**
 * Demonstrating **extended thinking on newer models** — the modern mechanic, the
 * counterpart to [_14_extended_thinking_haiku].
 *
 * Newer models (4.6+) drop the fixed `budget_tokens` knob (it returns 400) in favour of:
 *   - **Adaptive thinking** (`thinking = { type: "adaptive" }`) — the model decides *when*
 *     and *how much* to think, instead of you setting a token budget.
 *   - **Effort** (`output_config.effort` ∈ low | medium | high | xhigh | max) — the new
 *     depth/spend dial that replaces `budget_tokens`.
 *
 * We ask the same "bat and ball" puzzle at two effort levels so you can see effort act
 * as the knob: higher effort ⇒ more thinking ⇒ more output tokens.
 *
 * Notice: on these models `display` defaults to **omitted** (thinking blocks stream with
 * empty text), so we set `display = summarized` to actually read the reasoning. Thinking
 * is still billed as output tokens.
 *
 * ⚠️ Requires a 4.6+ model (Opus 4.6/4.7/4.8, Sonnet 4.6/5, Fable 5) — pinned below to
 * `claude-sonnet-5`; swap the constant to try another. This is NOT the repo default
 * (`claude-haiku-4-5`), which needs the older form in [_14_extended_thinking_haiku].
 */

// Adaptive thinking + effort require a 4.6+ model; Haiku 4.5 would 400 here.
private val MODEL: Model = Model.of(Models.SONNET_5)

// Trickiest question found for this example
private const val QUESTION = "What is the 14598765416513267465164897899515th decimal of a third of nine ? Give just a digit."

private fun Message.thinkingText(): String =
    content().mapNotNull { it.thinking().orElse(null) }.joinToString("\n") { it.thinking() }

private fun Message.answerText(): String =
    content().mapNotNull { it.text().orElse(null) }.joinToString("") { it.text() }.trim()

private fun Message.tokens(): String = "input=${usage().inputTokens()}, output=${usage().outputTokens()}"

/** Ask [QUESTION] with adaptive thinking at the given [effort] level. */
private fun ask(client: AnthropicClient, effort: OutputConfig.Effort): Message =
    client.messages().create(
        MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(2048L)
            // Adaptive: no token budget — the model self-regulates. display=summarized so
            // the reasoning is returned (default on 4.6+ is "omitted" = empty text).
            .thinking(
                ThinkingConfigAdaptive.builder()
                    .display(ThinkingConfigAdaptive.Display.SUMMARIZED)
                    .build()
            )
            // Effort is the depth dial that replaces budget_tokens.
            .outputConfig(OutputConfig.builder().effort(effort).build())
            .addUserMessage(QUESTION)
            .build()
    )

fun main() {
    val client = anthropicClient()
    try {
        println("Model: ${Models.SONNET_5}")
        println("Q: $QUESTION\n")

        for (effort in listOf(OutputConfig.Effort.LOW, OutputConfig.Effort.HIGH)) {
            println("── effort = $effort ──")
            val reply = ask(client, effort)
            println("[thinking]")
            println(reply.thinkingText().ifBlank { "(no thinking text returned)" })
            println("\nanswer: ${reply.answerText()}")
            println("tokens: ${reply.tokens()}  ← more effort ⇒ more thinking (output) tokens\n")
        }
    } finally {
        client.close()
    }
}
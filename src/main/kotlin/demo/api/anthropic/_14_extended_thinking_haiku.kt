package demo.api.anthropic

import com.anthropic.client.AnthropicClient
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.Model
import com.anthropic.models.messages.ThinkingConfigEnabled
import demo.api.anthropic.common.Models
import demo.api.anthropic.common.anthropicClient

/**
 * Demonstrating **extended thinking** on **`claude-haiku-4-5`** (older-model mechanic).
 *
 * This example is **pinned to Haiku 4.5 on purpose**: it uses the older
 * `thinking = { type: "enabled", budget_tokens: N }` form, where you set an explicit
 * thinking-token budget. That form is valid on pre-4.6 models (Haiku 4.5, Sonnet 4.5…)
 * but is **removed (400)** on Opus 4.8/4.7, Sonnet 5, and Fable 5.
 *
 * → For the newer-model mechanic (adaptive thinking + effort), see
 *   [_15_extended_thinking_adaptive].
 *
 * We ask the same cognitive-trap question twice:
 *   - **without** thinking → fast, often the intuitive-but-wrong value (the "bat and
 *     ball" puzzle baits `0.10`; the answer is `0.05`).
 *   - **with** thinking → the model reasons in `thinking` blocks first, then answers.
 *
 * Notice: `thinking` blocks come BEFORE the text answer; thinking is **billed as output
 * tokens** (watch the output count jump); `budget_tokens` must be **< max_tokens**.
 */

// Pinned deliberately — the budget_tokens form below only works on pre-4.6 models.
private val MODEL: Model = Model.of(Models.HAIKU_4_5)

// Trickiest question found for this example
private const val QUESTION = "What is the 14598765416513267465164897899515th decimal of a third of nine ? Answer just one digit."

// Alternative tricky questions (variable results):
// "I'm Charlie. Billy's mother has three children named May, June, and ... ? Give only the name."
// "A bat and a ball cost 1.10 in total. The bat costs 1.00 more than the ball. How much does the ball cost? Give the number only."
// "A brick weights 1 Kg plus half-a-brick. How much a brick weights (in Kilograms) ? Give just the number."

private fun Message.thinkingText(): String =
    content().mapNotNull { it.thinking().orElse(null) }.joinToString("\n") { it.thinking() }

private fun Message.answerText(): String =
    content().mapNotNull { it.text().orElse(null) }.joinToString("") { it.text() }.trim()

private fun Message.tokens(): String = "input=${usage().inputTokens()}, output=${usage().outputTokens()}"

/** Ask [QUESTION]; when [budgetTokens] is set, enable extended thinking with that budget. */
private fun ask(client: AnthropicClient, budgetTokens: Long?): Message {
    val builder = MessageCreateParams.builder()
        .model(MODEL)
        .maxTokens(2048L)               // must stay > budgetTokens
        .addUserMessage(QUESTION)

    if (budgetTokens != null) {
        builder.thinking(ThinkingConfigEnabled.builder().budgetTokens(budgetTokens).build())
    }
    return client.messages().create(builder.build())
}

fun main() {
    val client = anthropicClient()
    try {
        println("Model: ${Models.HAIKU_4_5}")
        println("Q: $QUESTION\n")

        println("── Without thinking ──")
        val fast = ask(client, budgetTokens = null)
        println("answer: ${fast.answerText()}")
        println("tokens: ${fast.tokens()}\n")

        println("── With extended thinking (budget 1024) ──")
        val slow = ask(client, budgetTokens = 1024L)
        println("[thinking]")
        println(slow.thinkingText().ifBlank { "(no thinking text returned)" })
        println("\nanswer: ${slow.answerText()}")
        println("tokens: ${slow.tokens()}  ← output includes the thinking tokens")
    } finally {
        client.close()
    }
}

package demo.api.ovh

import com.openai.models.ReasoningEffort
import com.openai.models.chat.completions.ChatCompletionCreateParams
import demo.api.ovh.common.defaultModel
import demo.api.ovh.common.ovhClient

/**
 * Reasoning on OVHcloud AI Endpoints, with **Olala**: each question asked at each
 * `reasoning_effort` (`low`, `medium`, `high`), to compare the chain-of-thought and its cost.
 *
 * What to look at:
 *  - **Request**: `reasoning_effort` is a typed field on `ChatCompletionCreateParams`.
 *  - **Response**: the thinking comes back next to `content` as an extra field. Servers disagree on
 *    its name (`reasoning_content` vs `reasoning`), so both are tried via `_additionalProperties()`.
 *  - **Budget**: thinking tokens count against `maxCompletionTokens`. If the budget is spent
 *    before the answer starts, `content` is empty and `finish_reason` is `length` — the
 *    `finish_reason` and `completion_tokens` printed below make that visible. Hence the generous
 *    budget here.
 */

// Two traps for models answering "off the cuff":
//  - counting letters;
//  - a "third of nine" is exactly 3, so every decimal is 0 — a model that pattern-matches on
//    "1/3 = 0.333…" will wrongly answer 3. (Expected answer: 0.)
private val QUESTIONS = listOf(
    "How many times does the letter r appear in the word 'strawberry'? Answer with just a number.",
    "What is the 14598765416513267465164897899515th decimal of a third of nine ? Answer just one digit.",
)

fun main() {
    val client = ovhClient()
    try {
        for (question in QUESTIONS) for (effort in listOf("low", "medium", "high")) {
            val params = ChatCompletionCreateParams.builder()
                .model(defaultModel)
                .maxCompletionTokens(4096L)
                .reasoningEffort(ReasoningEffort.of(effort))
                .addUserMessage(question)
                .build()

            val completion = client.chat().completions().create(params)
            val choice = completion.choices().first()
            val message = choice.message()

            val extra = message._additionalProperties()
            val reasoning = listOf("reasoning_content", "reasoning")
                .firstNotNullOfOrNull { extra[it]?.convert(String::class.java) }
            val answer = message.content().orElse("")
            val completionTokens = completion.usage().map { it.completionTokens() }.orElse(-1L)

            println("=== $question")
            println("=== reasoning_effort = $effort ===")
            println("finish_reason: ${choice.finishReason().asString()}  |  completion_tokens: $completionTokens")
            println("reasoning: ${reasoning ?: "(none returned)"}")
            println("answer: ${answer.ifBlank { "(empty — budget spent on reasoning?)" }}\n")
        }
    } finally {
        client.close()
    }
}

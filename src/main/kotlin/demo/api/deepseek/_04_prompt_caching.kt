package demo.api.deepseek

import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import com.openai.models.completions.CompletionUsage
import demo.api.deepseek.common.deepseekClient
import demo.api.deepseek.common.defaultModel

/**
 * DeepSeek's **context caching** — same idea as `anthropic/_04_prompt_caching.kt`, but the
 * mechanics (and the pricing payoff) are DeepSeek-specific:
 *   - It's **fully automatic** — no `cache_control` breakpoints, no opt-in flag. Any request
 *     whose prefix exactly matches a previously-seen prefix gets served from cache; there's
 *     nothing to configure, which is also why there's nothing to toggle off in this example.
 *   - The response `usage` carries `prompt_cache_hit_tokens`/`prompt_cache_miss_tokens` instead
 *     of Anthropic's `cache_creation_input_tokens`/`cache_read_input_tokens`. Neither field is
 *     typed on `openai-java`'s `CompletionUsage` (DeepSeek-specific, layered on the OpenAI-shaped
 *     response), so both are read off `_additionalProperties()`.
 *   - The payoff is steep: on `deepseek-v4-flash`, cache-hit input is billed at roughly **1/50th**
 *     the cache-miss rate ($0.0028 vs $0.14 per 1M tokens) — a much bigger spread than Anthropic's
 *     ~0.1x.
 *
 * Same trick as `_04` Anthropic to actually trigger it: a large, stable "knowledge base" system
 * prompt reused turn after turn as a growing conversation, so each new turn's request shares an
 * ever-longer identical prefix with the one before it. **Tested live**: turn 1 is a pure cache
 * miss (nothing seen yet); turns 2 and 3 show `prompt_cache_hit_tokens` covering the stable
 * prefix, with only the newly-added turn showing up as a miss.
 */

/** Builds a large, stable system prompt — the shared prefix every turn will reuse. */
private fun buildSystemPrompt(): String = buildString {
    appendLine("You are a concise assistant. Answer with only what is asked, nothing more.")
    appendLine()
    appendLine("Reference knowledge base (stable context, identical on every turn):")

    val colors = listOf("red", "orange", "yellow", "green", "blue", "indigo", "violet")
    val things = listOf("rose", "apple", "carrot", "leaf", "plum", "lemon", "grape", "pepper")
    // ~400 stable lines keep the prefix comfortably large enough to make caching visible.
    for (i in 1..400) {
        val color = colors[i % colors.size]
        val thing = things[i % things.size]
        appendLine("Fact $i: A $thing can appear in shades related to $color; rainbow order is ${colors.joinToString(", ")}.")
    }
}

private fun reportUsage(usage: CompletionUsage) {
    val hit = usage._additionalProperties()["prompt_cache_hit_tokens"]?.convert(Long::class.java) ?: 0L
    val miss = usage._additionalProperties()["prompt_cache_miss_tokens"]?.convert(Long::class.java) ?: 0L
    println("   [usage] prompt=${usage.promptTokens()} cache_hit=$hit cache_miss=$miss")
}

fun main() {
    val client = deepseekClient()
    try {
        val messages = mutableListOf(
            ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam.builder().content(buildSystemPrompt()).build()
            )
        )

        fun ask(question: String): String {
            messages += ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder().content(question).build()
            )

            val completion = client.chat().completions().create(
                ChatCompletionCreateParams.builder()
                    .model(defaultModel)
                    .maxCompletionTokens(256L)
                    .messages(messages)
                    .build()
            )

            val message = completion.choices().first().message()
            messages += ChatCompletionMessageParam.ofAssistant(message.toParam())
            completion.usage().ifPresent(::reportUsage)
            return message.content().orElse("(no content)")
        }

        // Turn 1 is a pure cache miss (nothing has been seen with this prefix yet).
        println("Color: ${ask("Pick a rainbow color. Answer with just the color name.")}")
        // Turns 2+ reuse the identical, growing prefix -> watch cache_hit rise.
        println("Thing: ${ask("Name a flower, fruit, or vegetable that is this color. Just its name.")}")
        println("Fact:  ${ask("Tell me one short fun fact about it.")}")
    } finally {
        client.close()
    }
}

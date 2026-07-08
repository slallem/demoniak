package org.example.demo.anthropic

import com.anthropic.client.AnthropicClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.Model
import org.example.demo.anthropic.common.anthropicClient
import org.example.demo.anthropic.common.defaultModel

/**
 * Demonstrating **temperature** — the sampling "creativity" knob (0.0 … 1.0).
 *
 * At each step the model has a probability distribution over the next token.
 * `temperature` reshapes it before sampling:
 *   - low  (≈ 0.0): the most likely token almost always wins → focused, repeatable
 *   - high (≈ 1.0): flatter odds, less likely tokens get picked → varied, creative
 *
 * We ask the *same* prompt several times at each setting: temperature 0 tends to
 * repeat one answer, temperature 1 spreads across many.
 *
 * ⚠️ Model support: sampling params were **removed** on the newest models
 * (Opus 4.8/4.7, Sonnet 5, Fable 5) — passing `temperature` there returns a 400.
 * This example relies on a sampling-capable model such as `claude-haiku-4-5`
 * (the repo default). Point [Models.DEFAULT] at one of those older models if you
 * change it, or this will fail.
 */

private const val PROMPT =
    "Invent a name for a new flower shop, whose owner is Victoria. Reply with just the shop name, nothing else."

private const val ROUNDS = 5

/** One single-turn call at a fixed [temperature], returning the reply text. */
@Suppress("DEPRECATION") // .temperature() is deprecated because newer models reject it — see the note above.
private fun ask(client: AnthropicClient, model: Model, temperature: Double): String {
    val response = client.messages().create(
        MessageCreateParams.builder()
            .model(model)
            .maxTokens(32L)
            .temperature(temperature)
            .addUserMessage(PROMPT)
            .build()
    )
    return response.content().stream()
        .flatMap { block -> block.text().stream() }
        .map { it.text().trim() }
        .reduce("") { a, b -> a + b }
}

fun main() {
    val client = anthropicClient()
    try {
        val model = defaultModel

        for (temperature in listOf(0.0, 1.0)) {
            println("temperature = $temperature")
            repeat(ROUNDS) { i ->
                println("   ${i + 1}. ${ask(client, model, temperature)}")
            }
        }
    } finally {
        client.close()
    }
}
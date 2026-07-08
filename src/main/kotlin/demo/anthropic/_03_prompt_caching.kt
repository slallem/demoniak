package org.example.demo.anthropic

import org.example.demo.anthropic.common.Conversation
import org.example.demo.anthropic.common.anthropicClient

/**
 * Same chained chat as _02, but with **prompt caching** on the system prompt.
 *
 * How it works:
 *  - The request is rendered as tools -> system -> messages. A `cache_control`
 *    breakpoint on the (stable) system prompt caches that whole prefix.
 *  - Turn 1 *writes* the cache  -> usage.cache_creation_input_tokens > 0.
 *  - Turns 2+ reuse the identical system prefix and *read* it from cache
 *    -> usage.cache_read_input_tokens > 0 (billed at ~0.1x instead of full price).
 *
 * Why the system prompt is so large: caching only engages above a per-model
 * minimum prefix size (>= 4096 tokens for claude-opus-4-8). A short system prompt
 * would silently not cache at all. Here we build a big stable "knowledge base" to
 * cross that threshold — in real apps this stable prefix is your long instructions,
 * reference docs, or few-shot examples that don't change between turns.
 */

/** Builds a large, stable system prompt (well over the 4096-token cache minimum). */
private fun buildSystemPrompt(): String = buildString {
    appendLine("You are a concise assistant. Answer with only what is asked, nothing more.")
    appendLine()
    appendLine("Reference knowledge base (stable context, identical on every turn):")

    val colors = listOf("red", "orange", "yellow", "green", "blue", "indigo", "violet")
    val things = listOf("rose", "apple", "carrot", "leaf", "plum", "lemon", "grape", "pepper")
    // ~400 stable lines keep the prefix comfortably above the cache threshold.
    for (i in 1..400) {
        val color = colors[i % colors.size]
        val thing = things[i % things.size]
        appendLine("Fact $i: A $thing can appear in shades related to $color; rainbow order is ${colors.joinToString(", ")}.")
    }
}

private fun Conversation.reportUsage() {
    val usage = lastUsage ?: return
    val write = usage.cacheCreationInputTokens().orElse(0L)
    val read = usage.cacheReadInputTokens().orElse(0L)
    println("   [usage] input=${usage.inputTokens()} cache_write=$write cache_read=$read")
}

fun main() {
    val client = anthropicClient()
    try {
        val chat = Conversation(
            client = client,
            system = buildSystemPrompt(),
        )

        // Turn 1 writes the cache; later turns read it (watch cache_write vs cache_read).
        println("Color: ${chat.ask("Pick a rainbow color. Answer with just the color name.")}")
        chat.reportUsage()

        println("Thing: ${chat.ask("Name a flower, fruit, or vegetable that is this color. Just its name.")}")
        chat.reportUsage()

        println("Fact:  ${chat.ask("Tell me one short fun fact about it.")}")
        chat.reportUsage()
    } finally {
        client.close()
    }
}
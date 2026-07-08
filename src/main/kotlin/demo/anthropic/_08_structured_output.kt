package org.example.demo.anthropic

import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import org.example.demo.anthropic.common.anthropicClient
import org.example.demo.anthropic.common.defaultModel
import java.util.stream.Collectors

/**
 * Demonstrating **structured (JSON) output** via the prefill + stop-sequence trick.
 *
 * Two levers force clean JSON out of a plain text model:
 *   1. **Prefill** the assistant turn with an opening code fence "```json". Since the
 *      Messages API just *continues* the assistant message, the model is now committed
 *      to writing JSON — no "Sure, here is..." preamble to strip.
 *   2. A **stop sequence** "```" halts generation the moment the model starts the
 *      closing fence, so we get exactly the JSON body and nothing after it.
 *
 * The response text is the continuation only (your prefill is not echoed back), so it
 * is the raw JSON — ready to parse. `stop_reason` comes back as `stop_sequence`.
 *
 * ⚠️ Assistant prefill was **removed** on the newer models (Opus 4.8/4.7/4.6,
 * Sonnet 5/4.6, Fable 5) — it returns a 400 there. This trick needs an older model
 * such as `claude-haiku-4-5` (the repo default). On current models, use the native
 * structured-outputs feature (`output_config.format`) instead.
 */

private const val PREFILL = "```json"
private const val STOP = "```"

/** Joins all text blocks of a response into a single string. */
private fun textOf(response: Message): String =
    response.content().stream()
        .flatMap { block -> block.text().stream() }
        .map { it.text() }
        .collect(Collectors.joining())

fun main() {
    val client = anthropicClient()
    try {
        val messages = listOf(
            MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(
                    """
                    Give me details about the novel "Dune" by Frank Herbert as JSON with
                    keys: title (string), author (string), year (number), genres (array of strings).
                    """.trimIndent()
                )
                .build(),
            // Prefill: commit the assistant to start inside a JSON code fence.
            MessageParam.builder()
                .role(MessageParam.Role.ASSISTANT)
                .content(PREFILL)
                .build(),
        )

        val response = client.messages().create(
            MessageCreateParams.builder()
                .model(defaultModel)
                .maxTokens(512L)
                .messages(messages)
                .addStopSequence(STOP) // stop as soon as the closing fence begins
                .build()
        )

        val json = textOf(response).trim()

        println("stop_reason = ${response.stopReason().map { it.toString() }.orElse("-")}")
        println("── parsed JSON body ──")
        println(json)
        println("── full assistant message (prefill + reply + closing fence) ──")
        println("$PREFILL\n$json\n$STOP")
    } finally {
        client.close()
    }
}
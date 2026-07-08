package org.example.demo.anthropic

import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.Model
import org.example.demo.anthropic.common.anthropicClient

/**
 * Starter for connecting to the Claude API from Kotlin.
 */

fun main() {
    val client = anthropicClient()
    try {
        val params = MessageCreateParams.builder()
            .model(Model.of("claude-opus-4-8"))
            .maxTokens(1024L)
            .addUserMessage("Hello, Claude! Reply with a short one-line greeting.")
            .build()

        val response = client.messages().create(params)

        response.content().stream()
            .flatMap { block -> block.text().stream() }
            .forEach { textBlock -> println(textBlock.text()) }
    } finally {
        client.close()
    }
}
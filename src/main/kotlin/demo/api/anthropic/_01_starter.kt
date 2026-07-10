package demo.api.anthropic

import com.anthropic.models.messages.MessageCreateParams
import demo.api.anthropic.common.anthropicClient
import demo.api.anthropic.common.defaultModel

/**
 * Starter for connecting to the Claude API from Kotlin.
 */

fun main() {
    val client = anthropicClient()
    try {
        val params = MessageCreateParams.builder()
            .model(defaultModel)
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
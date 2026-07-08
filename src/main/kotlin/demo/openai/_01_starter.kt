package org.example.demo.openai

import com.openai.models.chat.completions.ChatCompletionCreateParams
import org.example.demo.openai.common.defaultModel
import org.example.demo.openai.common.openaiClient

/**
 * Starter for connecting to the OpenAI API from Kotlin.
 */

fun main() {
    val client = openaiClient()
    try {
        val params = ChatCompletionCreateParams.builder()
            .model(defaultModel)
            .maxCompletionTokens(1024L)
            .addUserMessage("Hello, GPT! Reply with a short one-line greeting.")
            .build()

        val completion = client.chat().completions().create(params)

        completion.choices().stream()
            .flatMap { choice -> choice.message().content().stream() }
            .forEach { text -> println(text) }
    } finally {
        client.close()
    }
}
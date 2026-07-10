package demo.api.google

import demo.api.google.common.defaultModel
import demo.api.google.common.googleClient

/**
 * Starter for connecting to the Gemini API from Kotlin.
 */

fun main() {
    val client = googleClient()
    try {
        val response = client.models.generateContent(
            defaultModel,
            "Hello, Gemini! Reply with a one-line greeting (3 to 10 words).",
            null
        )

        println(response.text())
    } finally {
        client.close()
    }
}
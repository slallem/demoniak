package demo.api.openai.common

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import demo.api.anthropic.common.loadProperties

/**
 * Common code for connecting to the OpenAI API from Kotlin (via the official OpenAI Java SDK).
 *
 * The connection relies on a **classpath resource** for the API key: every provider in this repo
 * shares the same `src/main/resources/credentials.properties` file (property [API_KEY_PROPERTY]
 * for this one) — see `credentials.properties.example` for the documented, committed template.
 */
private const val API_KEY_RESOURCE = "credentials"
private const val API_KEY_PROPERTY = "openai.api.key"

/** Reads the OpenAI API key from the (profile-aware) properties. */
private fun loadApiKey(): String =
    loadProperties(API_KEY_RESOURCE).getProperty(API_KEY_PROPERTY)
        ?.takeIf { it.isNotBlank() }
        ?: error("Property '$API_KEY_PROPERTY' is missing or blank")

/** Builds an OpenAI API client whose credentials come from the [API_KEY_RESOURCE] resource. */
fun openaiClient(): OpenAIClient =
    OpenAIOkHttpClient.builder()
        .apiKey(loadApiKey())
        .build()
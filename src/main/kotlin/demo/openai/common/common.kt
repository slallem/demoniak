package org.example.demo.openai.common

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import org.example.demo.anthropic.common.loadProperties

/**
 * Common code for connecting to the OpenAI API from Kotlin (via the official OpenAI Java SDK).
 *
 * The connection relies on a **classpath resource** for the API key: the key is read from
 * `src/main/resources/openai.properties` (property [API_KEY_PROPERTY]).
 */
private const val API_KEY_RESOURCE = "openai"
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
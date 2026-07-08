package org.example.demo.google.common

import com.google.genai.Client
import org.example.demo.anthropic.common.loadProperties

/**
 * Common code for connecting to the Gemini API from Kotlin (via the official Google GenAI Java SDK).
 *
 * The connection relies on a **classpath resource** for the API key: the key is read from
 * `src/main/resources/google.properties` (property [API_KEY_PROPERTY]).
 */
private const val API_KEY_RESOURCE = "google"
private const val API_KEY_PROPERTY = "google.api.key"

/** Reads the Gemini API key from the (profile-aware) properties. */
private fun loadApiKey(): String =
    loadProperties(API_KEY_RESOURCE).getProperty(API_KEY_PROPERTY)
        ?.takeIf { it.isNotBlank() }
        ?: error("Property '$API_KEY_PROPERTY' is missing or blank")

/** Builds a Gemini API client whose credentials come from the [API_KEY_RESOURCE] resource. */
fun googleClient(): Client =
    Client.builder()
        .apiKey(loadApiKey())
        .build()
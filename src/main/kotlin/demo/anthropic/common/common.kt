package org.example.demo.anthropic.common

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import java.util.Properties

/**
 * Common code for connecting to the Claude API from Kotlin (via the official Anthropic Java SDK).
 *
 * The connection relies on a **classpath resource** for the API key: the key is read from
 * `src/main/resources/anthropic.properties` (property [API_KEY_PROPERTY]).
 *
 * Profiles: when a profile is active (VM option `-Dprofile=local`, or env var
 * `APP_PROFILE=local`), values from `<name>.properties.<profile>` (e.g.
 * `anthropic.properties.local`) are overlaid on top of the base file. Keep the base
 * file committed with placeholders and put real secrets in the git-ignored `.local`
 * overlay.
 */
private const val API_KEY_RESOURCE = "anthropic"
private const val API_KEY_PROPERTY = "anthropic.api.key"

/** The active profile, from `-Dprofile=...` or `APP_PROFILE=...` (null = base only). */
private fun activeProfile(): String? =
    System.getProperty("profile") ?: System.getenv("APP_PROFILE")

/**
 * Loads `/<name>.properties`, then overlays `/<name>.properties.<profile>` when a
 * profile is active. Overlay values override base values; missing overlay is ignored.
 */
fun loadProperties(name: String): Properties {
    val props = Properties()

    val base = object {}.javaClass.getResourceAsStream("/$name.properties")
        ?: error("Resource '/$name.properties' not found on the classpath")
    base.use { props.load(it) }

    activeProfile()?.let { profile ->
        object {}.javaClass.getResourceAsStream("/$name.properties.$profile")
            ?.use { props.load(it) }
    }
    return props
}

/** Reads the Anthropic API key from the (profile-aware) properties. */
private fun loadApiKey(): String =
    loadProperties(API_KEY_RESOURCE).getProperty(API_KEY_PROPERTY)
        ?.takeIf { it.isNotBlank() }
        ?: error("Property '$API_KEY_PROPERTY' is missing or blank")

/** Builds a Claude API client whose credentials come from the [API_KEY_RESOURCE] resource. */
fun anthropicClient(): AnthropicClient =
    AnthropicOkHttpClient.builder()
        .apiKey(loadApiKey())
        .build()
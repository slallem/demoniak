package demo.api.anthropic.common

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import java.util.Properties

/**
 * Common code for connecting to the Claude API from Kotlin (via the official Anthropic Java SDK).
 *
 * The connection relies on a **classpath resource** for the API key: every provider in this repo
 * shares the same `src/main/resources/credentials.properties` file (property [API_KEY_PROPERTY]
 * for this one) — see `credentials.properties.example` for the documented, committed template.
 */
private const val API_KEY_RESOURCE = "credentials"
private const val API_KEY_PROPERTY = "anthropic.api.key"

/** The active profile, from `-Dprofile=...` or `APP_PROFILE=...` (null = base only). */
private fun activeProfile(): String? =
    System.getProperty("profile") ?: System.getenv("APP_PROFILE")

/**
 * Loads `/<name>.properties`, then overlays `/<name>.properties.<profile>` when a
 * profile is active. Overlay values override base values; missing overlay is ignored.
 *
 * Every provider's `common.kt` calls this with `name = "credentials"`: all API keys live in the
 * single shared `credentials.properties` (git-ignored; copy it from the committed
 * `credentials.properties.example`), namespaced by property name (`anthropic.api.key`,
 * `openai.api.key`, …) rather than by separate files. `name` stays a parameter rather than a
 * hardcoded constant so the profile-overlay mechanism below stays reusable for anything else that
 * might need its own properties file later.
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
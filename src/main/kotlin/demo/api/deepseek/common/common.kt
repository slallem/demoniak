package demo.api.deepseek.common

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import demo.api.anthropic.common.loadProperties

/**
 * Common code for connecting to the DeepSeek API from Kotlin.
 *
 * There is no dedicated DeepSeek SDK in this build. DeepSeek's API is explicitly OpenAI-compatible
 * (their own docs: "an API format compatible with OpenAI") — the `openai-java` SDK already in this
 * project works against it unchanged, just a different `baseUrl` and key, exactly like the
 * `mistral` and `ollama` providers.
 *
 * One easy-to-get-wrong detail: unlike Mistral ([demo.api.mistral.common.MISTRAL_BASE_URL], which
 * *does* need `/v1`), DeepSeek's own real path is `/chat/completions` with **no** `/v1` segment —
 * DeepSeek's documented `base_url` for the OpenAI SDK is the bare `https://api.deepseek.com`.
 * Appending `/v1` here would 404.
 *
 * The key is read from the shared `src/main/resources/credentials.properties` (property
 * [API_KEY_PROPERTY]) — see `credentials.properties.example` for the documented, committed
 * template. Every provider in this repo reads from that same file, namespaced by property name.
 */
private const val API_KEY_RESOURCE = "credentials"
private const val API_KEY_PROPERTY = "deepseek.api.key"

/** Base URL of the DeepSeek API — no `/v1` segment, see the note above. */
const val DEEPSEEK_BASE_URL = "https://api.deepseek.com"

/** Reads the DeepSeek API key from the (profile-aware) properties. */
fun deepseekApiKey(): String =
    loadProperties(API_KEY_RESOURCE).getProperty(API_KEY_PROPERTY)
        ?.takeIf { it.isNotBlank() }
        ?: error("Property '$API_KEY_PROPERTY' is missing or blank")

/** Builds an OpenAI SDK client pointed at the DeepSeek API, credentials from [API_KEY_RESOURCE]. */
fun deepseekClient(): OpenAIClient =
    OpenAIOkHttpClient.builder()
        .baseUrl(DEEPSEEK_BASE_URL)
        .apiKey(deepseekApiKey())
        .build()

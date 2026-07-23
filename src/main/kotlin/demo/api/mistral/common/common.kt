package demo.api.mistral.common

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import demo.api.anthropic.common.loadProperties

/**
 * Common code for connecting to the Mistral API from Kotlin.
 *
 * There is no dedicated Mistral SDK in this build. Mistral's `/v1/chat/completions` mirrors the
 * OpenAI request/response shape closely enough (`model`, `messages`, `choices[].message.content`,
 * …) that the `openai-java` SDK already in this project works against it unchanged — just a
 * different `baseUrl` and key, exactly like the `ollama` provider does for a local server.
 *
 * One real difference from OpenAI, not just cosmetic: Mistral rejects the newer
 * `max_completion_tokens` field (422) and only accepts the deprecated `max_tokens` — see
 * `_01_starter`.
 *
 * Not everything Mistral exposes fits the `chat/completions` shape, though: `/v1/ocr` (see
 * `_04_ocr`) is a Mistral-only endpoint with its own request/response schema, so there is nothing
 * for the OpenAI SDK to model — that example calls it over plain HTTP instead, using
 * [MISTRAL_BASE_URL] and [mistralApiKey] directly.
 *
 * The key is read from the shared `src/main/resources/credentials.properties` (property
 * [API_KEY_PROPERTY]) — see `credentials.properties.example` for the documented, committed
 * template. Every provider in this repo reads from that same file, namespaced by property name.
 */
private const val API_KEY_RESOURCE = "credentials"
private const val API_KEY_PROPERTY = "mistral.api.key"

/** Base URL of the Mistral API — shared by the SDK client below and any hand-rolled HTTP call. */
const val MISTRAL_BASE_URL = "https://api.mistral.ai/v1"

/** Reads the Mistral API key from the (profile-aware) properties. */
fun mistralApiKey(): String =
    loadProperties(API_KEY_RESOURCE).getProperty(API_KEY_PROPERTY)
        ?.takeIf { it.isNotBlank() }
        ?: error("Property '$API_KEY_PROPERTY' is missing or blank")

/** Builds an OpenAI SDK client pointed at the Mistral API, credentials from [API_KEY_RESOURCE]. */
fun mistralClient(): OpenAIClient =
    OpenAIOkHttpClient.builder()
        .baseUrl(MISTRAL_BASE_URL)
        .apiKey(mistralApiKey())
        .build()

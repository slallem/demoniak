package demo.api.ovh.common

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import demo.api.anthropic.common.loadProperties

/**
 * Common code for connecting to OVHcloud **AI Endpoints** from Kotlin.
 *
 * AI Endpoints is OVHcloud's managed, EU-hosted (France) inference service. Its API is
 * OpenAI-compatible, so — like the `mistral`, `deepseek` and `ollama` providers — there is no
 * dedicated SDK: the `openai-java` SDK already in this project works with only a different
 * `baseUrl` and key.
 *
 * Two details worth knowing:
 * - The base URL **includes `/v1`** (see [OVH_BASE_URL]).
 * - The `model` field is the catalog's display **name** (e.g. `Olala-v1.0-mini-7A1B`), *not* its
 *   lowercase `id` (`olala-v1-0-mini-7a1b`) — the id returns `model_not_found`. The public catalog
 *   (`https://catalog.endpoints.ai.ovh.net/rest/v1/models_v2`, no key needed) lists both.
 *
 * Auth: create a key in the OVHcloud control panel (Public Cloud → AI Endpoints) and put it in the
 * shared `src/main/resources/credentials.properties` as [API_KEY_PROPERTY] — see
 * `credentials.properties.example`. (OVH also accepts *anonymous* calls with no `Authorization`
 * header at a low rate limit, but the SDK always sends one, so a real key is required here; a
 * bogus key is rejected with 401/403 rather than treated as anonymous.)
 */
private const val API_KEY_RESOURCE = "credentials"
private const val API_KEY_PROPERTY = "ovh.api.key"

/** Base URL of the OVHcloud AI Endpoints OpenAI-compatible API (with `/v1`). */
const val OVH_BASE_URL = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1"

/** Reads the OVHcloud AI Endpoints API key from the (profile-aware) properties. */
fun ovhApiKey(): String =
    loadProperties(API_KEY_RESOURCE).getProperty(API_KEY_PROPERTY)
        ?.takeIf { it.isNotBlank() }
        ?: error("Property '$API_KEY_PROPERTY' is missing or blank")

/** Builds an OpenAI SDK client pointed at OVHcloud AI Endpoints, credentials from [API_KEY_RESOURCE]. */
fun ovhClient(): OpenAIClient =
    OpenAIOkHttpClient.builder()
        .baseUrl(OVH_BASE_URL)
        .apiKey(ovhApiKey())
        .build()

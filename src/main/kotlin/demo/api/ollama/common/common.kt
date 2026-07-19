package demo.api.ollama.common

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient

/**
 * Common code for talking to a **local** Ollama server.
 *
 * Unlike the other providers here, there is no `ollama.properties`: a local server needs no
 * secret, so there is nothing to keep out of git. The only knob is *where* the server lives,
 * and that is a plain constant (override with `-Dollama.url=...` if yours is not on the default
 * port, or if you point it at a remote box).
 *
 * Two ways in, and the examples use one each:
 *  - [ollamaClient] — Ollama re-implements the **OpenAI API** at `/v1`, so the `openai-java` SDK
 *    already in this build talks to it with nothing but a different `baseUrl`. See `_01_starter`.
 *  - [OLLAMA_URL] + hand-rolled JSON against the **native** `/api/chat`. More verbose, but it is
 *    where Ollama's own features live (`keep_alive`, `think`, NDJSON streaming). See `_02_chat_http`
 *    (and `_02_chat_openai` for the same conversation via the SDK).
 */

/** Base URL of the local Ollama server. */
val OLLAMA_URL: String = System.getProperty("ollama.url") ?: "http://localhost:11434"

/**
 * Builds an OpenAI SDK client pointed at the local Ollama server.
 *
 * The API key is required by the SDK but ignored by Ollama — any non-blank string works.
 */
fun ollamaClient(): OpenAIClient =
    OpenAIOkHttpClient.builder()
        .baseUrl("$OLLAMA_URL/v1")
        .apiKey("ollama") // ignored by Ollama; the SDK refuses to build without one
        .build()

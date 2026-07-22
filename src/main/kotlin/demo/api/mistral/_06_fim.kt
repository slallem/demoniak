package demo.api.mistral

import com.fasterxml.jackson.databind.ObjectMapper
import demo.api.mistral.common.MISTRAL_BASE_URL
import demo.api.mistral.common.Models
import demo.api.mistral.common.mistralApiKey
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * **Codestral FIM (fill-in-the-middle)** — `/v1/fim/completions`, a genuinely different shape
 * from every other example in this repo, on any provider, and Codestral-only (no general chat
 * model accepts this endpoint).
 *
 * Every chat completion — Anthropic, OpenAI, Gemini, Ollama, Mistral's own `_01`–`_05` — is
 * fundamentally **one-directional**: the model only ever sees what comes *before* the point it's
 * completing, and produces more text forwards from there. That matches a conversation, but not
 * what a code editor actually needs: your cursor sits in the *middle* of a file, with real code
 * both before AND after it, and the completion has to fit both.
 *
 * FIM's request reflects that directly — there are no `messages`, no `role`, just two strings:
 * `prompt` (the code *before* the cursor) and `suffix` (the code *after* it). The model attends
 * to both simultaneously and returns only the missing middle — no prose, no "```kotlin" fence to
 * strip.
 *
 * You could *approximate* this with a normal chat message ("complete this function; it must then
 * work with the following code: ..."), but that turns the suffix into prose the model reads
 * *about* rather than real code tokens it directly conditions the completion on — same idea Ollama's
 * `_02_chat_http` note makes about native-only fields: some things aren't reachable through a
 * chat-shaped request at all, no matter how the prompt is worded.
 *
 * One asymmetry worth knowing if you read the raw JSON: the **request** has no `messages`, but the
 * **response** reuses the chat-completion shape anyway (`choices[].message.content`).
 */

private const val PROMPT = """fun countVowels(s: String): Int {
"""

private const val SUFFIX = """
}

fun main() {
    println(countVowels("Hello World")) // expected 3
}
"""

private val mapper = ObjectMapper()

fun main() {
    val body = mapper.createObjectNode().apply {
        put("model", Models.CODESTRAL)
        put("prompt", PROMPT)
        put("suffix", SUFFIX)
        put("max_tokens", 256)
    }

    val request = HttpRequest.newBuilder(URI.create("$MISTRAL_BASE_URL/fim/completions"))
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer ${mistralApiKey()}")
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
        .build()

    val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) {
        "Mistral FIM returned ${response.statusCode()}: ${response.body()}"
    }

    val filledMiddle = mapper.readTree(response.body())
        .path("choices").get(0).path("message").path("content").asText()

    println("── Prompt (before the cursor) ──")
    println(PROMPT)
    println("── Suffix (after the cursor) ──")
    println(SUFFIX)
    println("── Model fills in only the middle ──")
    println(filledMiddle)
    println("── Reassembled file ──")
    println(PROMPT + filledMiddle + SUFFIX)
}

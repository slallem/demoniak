package demo.api.ollama

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import demo.api.ollama.common.OLLAMA_URL
import demo.api.ollama.common.defaultModel
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Multi-turn chat against Ollama's **native** `/api/chat`, in **pure HTTP** — no SDK at all.
 *
 * This is one of two variants of the same example; [_02_chat_openaiKt] does the identical
 * conversation through the OpenAI SDK. Reading them side by side is the point: same idea, and
 * you can see exactly what a client library does for you.
 *
 * **How memory works.** The endpoint is *stateless* — it remembers nothing between calls. A
 * conversation is just the growing list of messages, resent whole every turn. [HttpConversation]
 * keeps that list: each [HttpConversation.ask] appends the user question, sends the *entire*
 * history, then appends the assistant reply. Turn 2 therefore includes turn 1's Q and A, so the
 * model can build on them. (Same model as Claude's Messages API; contrast Gemini's `Chat`
 * session in `demo/api/google/_02`, which keeps the list for you.)
 *
 * What speaking the native protocol by hand buys — none of it expressible via the OpenAI schema:
 *  - `think: false` — Qwen3.5 reasons by default; on a 2B model that turns a one-word answer into
 *    a multi-minute runaway. This field is the difference between ~1.5s and "never returns".
 *  - `keep_alive` — how long the weights stay resident. The first call after idle pays the model
 *    load (~2min for a 12B); this keeps later turns warm.
 *  - `stream: false` — the native endpoint streams NDJSON by default.
 *
 * Prerequisites: `ollama serve` running, and `ollama pull qwen3.5:2b` done once.
 */

private val mapper = ObjectMapper()

/** A remembered chat over the native endpoint: keeps the running message list and grows it per turn. */
private class HttpConversation(
    private val model: String = defaultModel,
    private val http: HttpClient = HttpClient.newHttpClient(),
) {
    private val history = mutableListOf<Map<String, String>>()

    /** Sends [question] with the full remembered history and returns the reply text. */
    fun ask(question: String): String {
        history += mapOf("role" to "user", "content" to question)

        val body = mapper.createObjectNode().apply {
            put("model", model)
            set<JsonNode>("messages", mapper.valueToTree(history))
            put("stream", false)
            put("think", false)      // without this, a 2B reasoning model may never stop
            put("keep_alive", "5m")  // keep the weights resident between turns
        }

        val request = HttpRequest.newBuilder(URI.create("$OLLAMA_URL/api/chat"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) {
            "Ollama returned ${response.statusCode()}: ${response.body()}"
        }

        val answer = mapper.readTree(response.body()).path("message").path("content").asText().trim()
        history += mapOf("role" to "assistant", "content" to answer)
        return answer
    }
}

fun main() {
    val chat = HttpConversation()

    // Turn 1 tells the model some facts. Nothing is stored server-side.
    val ack = chat.ask(
        "Remember two facts about me: my name is Ada, and my favorite fruit is mango. " +
            "Reply with just OK."
    )
    println("Turn 1: $ack")

    // Turn 2 asks them back. This works *only* because ask() resends turn 1 in the history —
    // the endpoint kept nothing. Drop the resend and the model would have no idea who "I" am.
    val recall = chat.ask("Using only what I told you, what are my name and my favorite fruit?")
    println("Turn 2: $recall")
}

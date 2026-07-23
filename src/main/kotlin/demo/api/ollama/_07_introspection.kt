package demo.api.ollama

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import demo.api.ollama.common.OLLAMA_URL
import demo.api.ollama.common.defaultModel
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Locale

/**
 * Local performance introspection — a category of information that simply **does not exist** for
 * a hosted API. Anthropic/OpenAI/Google/Mistral bill and rate-limit a remote call; there is no
 * "how many tokens/sec did my machine just do" to ask, because your machine didn't do anything.
 * Every native `/api/chat`/`/api/generate` response from Ollama carries exactly that, for free,
 * in nanoseconds:
 *
 *  - `total_duration` — wall-clock time for the whole call.
 *  - `load_duration` — time spent loading the model into memory *for this call*. Non-zero only
 *    when the model wasn't already resident — the cost every provider example in this repo never
 *    pays, because a hosted model is always "loaded".
 *  - `prompt_eval_count` / `prompt_eval_duration` — tokens read, and how long reading them took.
 *  - `eval_count` / `eval_duration` — tokens generated, and how long generating them took. Divide
 *    the two and you have real, local tokens/sec — the number every "how fast is my GPU" local-LLM
 *    benchmark is actually built on.
 *
 * This example forces a genuinely cold call (explicitly unloading the model first via
 * `keep_alive: 0` on `/api/generate`, then polling `/api/ps` until it's actually evicted — unload
 * is asynchronous, so a request right after issuing it can still show the model as loaded), then
 * repeats the same prompt warm, so the two `load_duration` numbers are directly comparable rather
 * than asserted. `/api/ps` itself is the other native-only introspection point used here: it lists
 * what's currently resident in memory, which a hosted API has no equivalent of either — there is
 * no "which of Anthropic's models are warm right now" to query.
 */

private val mapper = ObjectMapper()
private val http = HttpClient.newHttpClient()

private fun post(path: String, body: JsonNode): JsonNode {
    val request = HttpRequest.newBuilder(URI.create("$OLLAMA_URL$path"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
        .build()
    val response = http.send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) {
        "Ollama returned ${response.statusCode()}: ${response.body()}"
    }
    return mapper.readTree(response.body())
}

private fun get(path: String): JsonNode {
    val request = HttpRequest.newBuilder(URI.create("$OLLAMA_URL$path")).GET().build()
    val response = http.send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) {
        "Ollama returned ${response.statusCode()}: ${response.body()}"
    }
    return mapper.readTree(response.body())
}

/** True while [model] still shows up in `/api/ps` (i.e. still resident in memory). */
private fun isLoaded(model: String): Boolean =
    get("/api/ps").path("models").any { it.path("name").asText() == model }

/** Unloads [model] and waits (unload is asynchronous) until `/api/ps` confirms it's gone. */
private fun unload(model: String) {
    post("/api/generate", mapper.createObjectNode().apply { put("model", model); put("keep_alive", 0) })
    var attempts = 0
    while (isLoaded(model) && attempts < 20) {
        Thread.sleep(200)
        attempts++
    }
}

private fun chat(model: String, prompt: String): JsonNode =
    post(
        "/api/chat",
        mapper.createObjectNode().apply {
            put("model", model)
            set<JsonNode>("messages", mapper.valueToTree(listOf(mapOf("role" to "user", "content" to prompt))))
            put("stream", false)
            put("think", false)
        }
    )

private fun Long.asMillis(): String = String.format(Locale.ROOT, "%.0fms", this / 1_000_000.0)

private fun report(label: String, response: JsonNode) {
    val loadNs = response.path("load_duration").asLong()
    val promptCount = response.path("prompt_eval_count").asLong()
    val promptNs = response.path("prompt_eval_duration").asLong()
    val evalCount = response.path("eval_count").asLong()
    val evalNs = response.path("eval_duration").asLong()
    val totalNs = response.path("total_duration").asLong()

    val promptTokensPerSec = if (promptNs > 0) promptCount / (promptNs / 1_000_000_000.0) else 0.0
    val evalTokensPerSec = if (evalNs > 0) evalCount / (evalNs / 1_000_000_000.0) else 0.0

    println("── $label ──")
    println("  total:  ${totalNs.asMillis()}")
    println("  load:   ${loadNs.asMillis()}  (model weights into memory, this call only)")
    println("  prompt: $promptCount tokens in ${promptNs.asMillis()}  (${String.format(Locale.ROOT, "%.1f", promptTokensPerSec)} tok/s)")
    println("  eval:   $evalCount tokens in ${evalNs.asMillis()}  (${String.format(Locale.ROOT, "%.1f", evalTokensPerSec)} tok/s)")
}

fun main() {
    val prompt = "Name the five senses, one word each, comma-separated."

    println("Forcing a cold start: unloading $defaultModel and confirming via /api/ps...\n")
    unload(defaultModel)

    val cold = chat(defaultModel, prompt)
    report("Cold call (model just loaded)", cold)

    println()
    val warm = chat(defaultModel, prompt)
    report("Warm call (model already resident)", warm)

    println("\n── /api/ps right now ──")
    get("/api/ps").path("models").forEach { m ->
        val sizeMb = m.path("size").asLong() / 1_000_000
        println("  ${m.path("name").asText()}  ~${sizeMb}MB  context=${m.path("context_length").asInt()}  expires=${m.path("expires_at").asText()}")
    }
}

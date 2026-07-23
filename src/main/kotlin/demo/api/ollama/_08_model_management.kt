package demo.api.ollama

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import demo.api.ollama.common.OLLAMA_URL
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Managing models **by code** instead of the `ollama` CLI — another native-only surface with no
 * hosted-API equivalent (there is no "download a new model onto Anthropic's servers" call to
 * make; the model catalogue there is fixed and remote, not something you manage).
 *
 * Four endpoints, one small throwaway model (`all-minilm`, ~45MB, an embedding-only model — small
 * and quick to pull and delete again, chosen so this example doesn't leave a large file behind or
 * touch any model another example here depends on):
 *  - `GET /api/tags` — list what's installed on disk. The same data `ollama list` shows.
 *  - `POST /api/pull` — download a model. Streams NDJSON progress by default, one line per chunk
 *    (`status`, `digest`, `completed`/`total` bytes) — this example reads that stream and prints
 *    periodic percentages rather than waiting on a single blocking call.
 *  - `POST /api/show` — a model's metadata: family, parameter count, quantization, and
 *    **`capabilities`** — the same array `_03_tool_calling`/`_06_vision` point at as the way to
 *    check *before* assuming a model supports tools or vision, rather than after a confusing
 *    plain-text non-answer.
 *  - `DELETE /api/delete` — remove a model from disk. Used here to clean up after the demo, not
 *    to touch anything already installed.
 */

private val mapper = ObjectMapper()
private val http = HttpClient.newHttpClient()
private const val DEMO_MODEL = "all-minilm"

private fun listModels(): List<JsonNode> {
    val request = HttpRequest.newBuilder(URI.create("$OLLAMA_URL/api/tags")).GET().build()
    val response = http.send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) { "Ollama returned ${response.statusCode()}: ${response.body()}" }
    return mapper.readTree(response.body()).path("models").toList()
}

private fun printModels(models: List<JsonNode>) {
    if (models.isEmpty()) {
        println("  (none installed)")
        return
    }
    models.forEach { m ->
        val sizeMb = m.path("size").asLong() / 1_000_000
        println("  ${m.path("name").asText()}  ~${sizeMb}MB  ${m.path("details").path("parameter_size").asText()}")
    }
}

/** Pulls [model], printing progress percentages as NDJSON status lines stream in. */
private fun pull(model: String) {
    val request = HttpRequest.newBuilder(URI.create("$OLLAMA_URL/api/pull"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(mapOf("model" to model))))
        .build()

    val response = http.send(request, HttpResponse.BodyHandlers.ofLines())
    var lastPercentPrinted = -10
    response.body().forEach { line ->
        if (line.isBlank()) return@forEach
        val json = mapper.readTree(line)
        val total = json.path("total").asLong()
        val completed = json.path("completed").asLong()
        if (total > 0) {
            val percent = (completed * 100 / total).toInt()
            if (percent >= lastPercentPrinted + 10) {
                println("  ${json.path("status").asText()}: $percent%")
                lastPercentPrinted = percent
            }
        } else {
            println("  ${json.path("status").asText()}")
        }
    }
}

private fun show(model: String): JsonNode {
    val request = HttpRequest.newBuilder(URI.create("$OLLAMA_URL/api/show"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(mapOf("model" to model))))
        .build()
    val response = http.send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) { "Ollama returned ${response.statusCode()}: ${response.body()}" }
    return mapper.readTree(response.body())
}

private fun delete(model: String) {
    val request = HttpRequest.newBuilder(URI.create("$OLLAMA_URL/api/delete"))
        .header("Content-Type", "application/json")
        .method("DELETE", HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(mapOf("model" to model))))
        .build()
    val response = http.send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) { "Ollama returned ${response.statusCode()}: ${response.body()}" }
}

fun main() {
    println("── Installed models (/api/tags) ──")
    printModels(listModels())

    println("\n── Pulling '$DEMO_MODEL' (/api/pull) ──")
    pull(DEMO_MODEL)

    println("\n── Details for '$DEMO_MODEL' (/api/show) ──")
    val details = show(DEMO_MODEL)
    println("  family:        ${details.path("details").path("family").asText()}")
    println("  parameters:    ${details.path("details").path("parameter_size").asText()}")
    println("  quantization:  ${details.path("details").path("quantization_level").asText()}")
    println("  capabilities:  ${details.path("capabilities").joinToString(", ") { it.asText() }}")

    println("\n── Installed models again, '$DEMO_MODEL' now present ──")
    printModels(listModels())

    println("\n── Cleaning up: deleting '$DEMO_MODEL' (/api/delete) ──")
    delete(DEMO_MODEL)

    println("\n── Installed models after cleanup ──")
    printModels(listModels())
}

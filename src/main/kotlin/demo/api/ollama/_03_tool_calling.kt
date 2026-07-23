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
 * Tool calling against Ollama's **native** `/api/chat`, in pure HTTP — same "no SDK" style as
 * `_02_chat_http`.
 *
 * Not every local model supports this: the model's chat template has to define a tool-calling
 * format, and plenty don't. A model that doesn't just ignores `tools` and answers in plain text —
 * no error — so check first with `ollama show <model>` (the `Capabilities` block lists `tools` if
 * it's supported), or `curl localhost:11434/api/tags` (each model's `capabilities` array).
 *
 * Two real shape differences from OpenAI's tool calling (`_04_function_calling` and friends),
 * because Ollama defines its own native envelope rather than mirroring OpenAI's exactly:
 *  - `function.arguments` comes back as an **already-parsed JSON object** — no second decoding
 *    step like OpenAI's stringified `arguments`.
 *  - There is **no per-call id**. OpenAI correlates a tool result to its call via `tool_call_id`;
 *    Ollama has nothing to match against — the reply is just a `role: "tool"` message carrying
 *    `tool_name` and `content`, appended in the same order the calls arrived.
 *
 * `think: false` reappears for the same reason as `_02_chat_http`: left on, the 2B default model
 * can burn tens of seconds reasoning about whether to call a one-line lookup tool.
 */

private val mapper = ObjectMapper()

/** Mock weather lookup — the model can't know this, so it must call the tool instead of guessing. */
private fun getWeather(city: String): String = when (city.lowercase()) {
    "paris" -> "15°C, overcast"
    "tokyo" -> "26°C, humid"
    else -> "no data for '$city'"
}

private val weatherTool = mapper.createObjectNode().apply {
    put("type", "function")
    putObject("function").apply {
        put("name", "get_weather")
        put("description", "Get the current weather for a city. Use this for any weather question.")
        putObject("parameters").apply {
            put("type", "object")
            putArray("required").add("city")
            putObject("properties").putObject("city").apply {
                put("type", "string")
                put("description", "The city name, e.g. 'Paris'")
            }
        }
    }
}

/** Sends the full message history plus [weatherTool] and returns the raw `/api/chat` response. */
private fun chat(messages: List<Any>): JsonNode {
    val body = mapper.createObjectNode().apply {
        put("model", defaultModel)
        set<JsonNode>("messages", mapper.valueToTree(messages))
        set<JsonNode>("tools", mapper.createArrayNode().add(weatherTool))
        put("stream", false)
        put("think", false) // without this, the 2B model may reason at length before ever calling the tool
    }

    val request = HttpRequest.newBuilder(URI.create("$OLLAMA_URL/api/chat"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
        .build()

    val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) {
        "Ollama returned ${response.statusCode()}: ${response.body()}"
    }
    return mapper.readTree(response.body())
}

fun main() {
    val messages = mutableListOf<Any>(
        mapOf("role" to "user", "content" to "What's the weather like in Paris right now?")
    )

    while (true) {
        val message = chat(messages).path("message")

        val toolCalls = message.path("tool_calls")
        if (!toolCalls.isArray || toolCalls.isEmpty) {
            println("FINAL: ${message.path("content").asText().trim()}")
            break
        }

        // Keep the assistant turn (with its tool_calls) in history, verbatim — no id to preserve,
        // unlike OpenAI's `message.toParam()`.
        messages += mapOf("role" to "assistant", "tool_calls" to toolCalls)

        for (call in toolCalls) {
            val name = call.path("function").path("name").asText()
            val city = call.path("function").path("arguments").path("city").asText()
            val output = when (name) {
                "get_weather" -> getWeather(city)
                else -> "error: unknown tool '$name'"
            }
            println("[tool] $name(city=$city) -> $output")
            messages += mapOf("role" to "tool", "tool_name" to name, "content" to output)
        }
    }
}

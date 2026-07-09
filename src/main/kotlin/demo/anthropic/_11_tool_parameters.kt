package org.example.demo.anthropic

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolResultBlockParam
import com.anthropic.models.messages.ToolUseBlock
import org.example.demo.anthropic.common.anthropicClient
import org.example.demo.anthropic.common.defaultModel
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Advanced **tool use with parameters** and multi-tool chaining.
 *
 * Building on [_05_tool_definition] (a single no-arg tool), this exposes three tools,
 * two of which take **typed arguments**, and lets the model chain them:
 *
 *   1. `get_current_datetime`  — no inputs; returns an ISO-8601 timestamp.
 *   2. `get_weather_forecast`  — inputs: `datetime` (required), `location` (optional).
 *   3. `generate_id_hash`      — inputs: year, month, day, hour, minute (all integers).
 *
 * The prompt makes the model pass data *between* tools: the datetime from tool 1
 * flows into tool 2, and its components into tool 3.
 *
 * Two things worth noting:
 *   - **Declaring parameters**: each input is a JSON-schema property (`type` +
 *     `description`) with a `required` list — that's what the model reads to know
 *     how to fill the call.
 *   - **Reading parameters**: `toolUse._input()` is a raw `JsonValue`; because the
 *     SDK bundles the Jackson Kotlin module, we `.convert(...)` it straight into a
 *     Kotlin data class instead of poking at a map.
 */

// ---- Typed argument holders (deserialized from tool_use.input) ----

private data class WeatherArgs(val datetime: String, val location: String? = null)
private data class HashArgs(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int)

// ---- Tool implementations (our Kotlin code) ----

private fun nowIso(): String =
    LocalDateTime.now().withNano(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

private val CONDITIONS = listOf("Sunny", "Partly cloudy", "Overcast", "Light rain", "Thunderstorms", "Clear")

/** A deterministic (fake) forecast, so the same inputs always give the same answer. */
private fun forecast(datetime: String, location: String): String {
    val seed = (datetime + location).hashCode()
    val condition = CONDITIONS[Math.floorMod(seed, CONDITIONS.size)]
    val temp = 8 + Math.floorMod(seed / 7, 22) // 8..29 °C
    return "$location @ $datetime → $condition, ${temp}°C"
}

/** Our custom function: a stable ID hash from date/time components (SHA-256, shortened). */
private fun idHash(year: Int, month: Int, day: Int, hour: Int, minute: Int): String {
    val raw = "%04d-%02d-%02dT%02d:%02d".format(year, month, day, hour, minute)
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
    return digest.take(6).joinToString("") { "%02x".format(it) }
}

// ---- Tool definitions the model sees ----

private fun stringProp(desc: String) = JsonValue.from(mapOf("type" to "string", "description" to desc))
private fun intProp(desc: String) = JsonValue.from(mapOf("type" to "integer", "description" to desc))

private val currentDateTimeTool: Tool = Tool.builder()
    .name("get_current_datetime")
    .description("Get the current local date and time in ISO-8601 (yyyy-MM-ddTHH:mm:ss). No inputs.")
    .inputSchema(Tool.InputSchema.builder().build())
    .build()

private val weatherTool: Tool = Tool.builder()
    .name("get_weather_forecast")
    .description("Get a simple weather forecast for a given date/time.")
    .inputSchema(
        Tool.InputSchema.builder()
            .properties(
                Tool.InputSchema.Properties.builder()
                    .putAdditionalProperty("datetime", stringProp("Target date/time in ISO-8601, e.g. 2026-07-08T14:30:00"))
                    .putAdditionalProperty("location", stringProp("City name; defaults to Paris if omitted"))
                    .build()
            )
            .required(listOf("datetime"))
            .build()
    )
    .build()

private val idHashTool: Tool = Tool.builder()
    .name("generate_id_hash")
    .description("Generate a stable ID hash from date/time components.")
    .inputSchema(
        Tool.InputSchema.builder()
            .properties(
                Tool.InputSchema.Properties.builder()
                    .putAdditionalProperty("year", intProp("4-digit year"))
                    .putAdditionalProperty("month", intProp("month, 1-12"))
                    .putAdditionalProperty("day", intProp("day of month, 1-31"))
                    .putAdditionalProperty("hour", intProp("hour, 0-23"))
                    .putAdditionalProperty("minute", intProp("minute, 0-59"))
                    .build()
            )
            .required(listOf("year", "month", "day", "hour", "minute"))
            .build()
    )
    .build()

/** Dispatch a tool call to the matching Kotlin function, reading typed args from its input. */
private fun runTool(toolUse: ToolUseBlock): String = when (toolUse.name()) {
    "get_current_datetime" -> nowIso()

    "get_weather_forecast" -> {
        val args = toolUse._input().convert(WeatherArgs::class.java)!!
        forecast(args.datetime, args.location ?: "Paris")
    }

    "generate_id_hash" -> {
        val a = toolUse._input().convert(HashArgs::class.java)!!
        idHash(a.year, a.month, a.day, a.hour, a.minute)
    }

    else -> "error: unknown tool '${toolUse.name()}'"
}

fun main() {
    val client = anthropicClient()
    try {
        val messages = mutableListOf(
            MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(
                    """
                    Do these in order, using the tools:
                      1. Find the current date and time.
                      2. Get the weather forecast for that exact date/time in Tokyo.
                      3. Generate an ID hash from that date/time's components (year, month, day, hour, minute).
                    Then reply with a one-line summary: '<datetime> | <forecast> | id=<hash>'.
                    """.trimIndent()
                )
                .build()
        )

        while (true) {
            val response = client.messages().create(
                MessageCreateParams.builder()
                    .model(defaultModel)
                    .maxTokens(1024L)
                    .addTool(currentDateTimeTool)
                    .addTool(weatherTool)
                    .addTool(idHashTool)
                    .messages(messages)
                    .build()
            )

            // Keep the assistant turn (with any tool_use blocks) in history.
            messages.add(response.toParam())

            val toolUses = response.content().mapNotNull { it.toolUse().orElse(null) }
            if (toolUses.isEmpty()) {
                response.content()
                    .mapNotNull { it.text().orElse(null) }
                    .forEach { println("FINAL: ${it.text()}") }
                break
            }

            // Run each requested tool and return all results in one user turn.
            val results = toolUses.map { toolUse ->
                val output = runTool(toolUse)
                println("[tool] ${toolUse.name()}(${toolUse._input()}) -> $output")
                ContentBlockParam.ofToolResult(
                    ToolResultBlockParam.builder()
                        .toolUseId(toolUse.id())
                        .content(output)
                        .build()
                )
            }
            messages.add(
                MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .contentOfBlockParams(results)
                    .build()
            )
        }
    } finally {
        client.close()
    }
}

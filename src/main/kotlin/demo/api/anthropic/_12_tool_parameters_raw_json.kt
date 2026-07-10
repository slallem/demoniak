package demo.api.anthropic

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.*
import com.fasterxml.jackson.databind.ObjectMapper
import demo.api.anthropic.common.anthropicClient
import demo.api.anthropic.common.defaultModel
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Same three tools as [_11_tool_parameters], but declared with **raw JSON schema**.
 *
 * On the wire a tool is always `{ name, description, input_schema }` where
 * `input_schema` is JSON Schema — that is what the API consumes, and it is exactly
 * what the Anthropic Academy course writes as a JSON/dict literal. [_11] builds that
 * JSON with the SDK's typed builders; here we author it as JSON text instead. **Both
 * produce the identical request** — this is a code-authoring choice, not a different
 * mechanism.
 *
 * [toolFromJson] is the whole trick: parse a JSON Schema string and hand its pieces
 * to the SDK. After that, the tool loop and implementations are the same as [_11].
 */

private val MAPPER = ObjectMapper()

/** Build a [Tool] from a JSON Schema string (the course-style, JSON-first declaration). */
private fun toolFromJson(name: String, description: String, schemaJson: String): Tool {
    val schema = MAPPER.readTree(schemaJson)

    val properties = Tool.InputSchema.Properties.builder()
    schema.get("properties")?.fields()?.forEach { (propName, propSchema) ->
        properties.putAdditionalProperty(propName, JsonValue.fromJsonNode(propSchema))
    }
    val required = schema.get("required")?.map { it.asText() } ?: emptyList()

    return Tool.builder()
        .name(name)
        .description(description)
        .inputSchema(
            Tool.InputSchema.builder()
                .properties(properties.build())
                .required(required)
                .build()
        )
        .build()
}

// ---- Tools declared as JSON Schema ----

private val currentDateTimeTool: Tool = toolFromJson(
    name = "get_current_datetime",
    description = "Get the current local date and time in ISO-8601 (yyyy-MM-ddTHH:mm:ss). No inputs.",
    schemaJson = """
        { "type": "object", "properties": {}, "required": [] }
    """.trimIndent(),
)

private val weatherTool: Tool = toolFromJson(
    name = "get_weather_forecast",
    description = "Get a simple weather forecast for a given date/time.",
    schemaJson = """
        {
          "type": "object",
          "properties": {
            "datetime": { "type": "string", "description": "Target date/time in ISO-8601, e.g. 2026-07-08T14:30:00" },
            "location": { "type": "string", "description": "City name; defaults to Paris if omitted" }
          },
          "required": ["datetime"]
        }
    """.trimIndent(),
)

private val idHashTool: Tool = toolFromJson(
    name = "generate_id_hash",
    description = "Generate a stable ID hash from date/time components.",
    schemaJson = """
        {
          "type": "object",
          "properties": {
            "year":   { "type": "integer", "description": "4-digit year" },
            "month":  { "type": "integer", "description": "month, 1-12" },
            "day":    { "type": "integer", "description": "day of month, 1-31" },
            "hour":   { "type": "integer", "description": "hour, 0-23" },
            "minute": { "type": "integer", "description": "minute, 0-59" }
          },
          "required": ["year", "month", "day", "hour", "minute"]
        }
    """.trimIndent(),
)

// ---- Typed argument holders + implementations (identical to _11) ----

private data class ForecastInput(val datetime: String, val location: String? = null)
private data class HashInput(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int)

private fun nowIso(): String =
    LocalDateTime.now().withNano(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

private val CONDITIONS = listOf("Sunny", "Partly cloudy", "Overcast", "Light rain", "Thunderstorms", "Clear")

private fun forecast(datetime: String, location: String): String {
    val seed = (datetime + location).hashCode()
    val condition = CONDITIONS[Math.floorMod(seed, CONDITIONS.size)]
    val temp = 8 + Math.floorMod(seed / 7, 22)
    return "$location @ $datetime → $condition, ${temp}°C"
}

private fun idHash(year: Int, month: Int, day: Int, hour: Int, minute: Int): String {
    val raw = "%04d-%02d-%02dT%02d:%02d".format(year, month, day, hour, minute)
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
    return digest.take(6).joinToString("") { "%02x".format(it) }
}

private fun runTool(toolUse: ToolUseBlock): String = when (toolUse.name()) {
    "get_current_datetime" -> nowIso()
    "get_weather_forecast" -> {
        val args = toolUse._input().convert(ForecastInput::class.java)!!
        forecast(args.datetime, args.location ?: "Paris")
    }
    "generate_id_hash" -> {
        val a = toolUse._input().convert(HashInput::class.java)!!
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

            messages.add(response.toParam())

            val toolUses = response.content().mapNotNull { it.toolUse().orElse(null) }
            if (toolUses.isEmpty()) {
                response.content()
                    .mapNotNull { it.text().orElse(null) }
                    .forEach { println("FINAL: ${it.text()}") }
                break
            }

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

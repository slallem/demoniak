package demo.api.openai

import com.openai.core.JsonValue
import com.openai.models.FunctionDefinition
import com.openai.models.FunctionParameters
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionFunctionTool
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionToolMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import demo.api.openai.common.defaultModel
import demo.api.openai.common.openaiClient
import kotlin.jvm.optionals.getOrNull

/**
 * **Function calling**: the model asks *us* to run a function it is bad at doing itself — here
 * `count_occurrences(text, needle)`. Counting letters is the textbook LLM failure ("how many r's
 * in strawberry?"): the model sees tokens, not characters, so it guesses. Delegating to three
 * lines of Kotlin turns a guess into a fact.
 *
 * The loop is the same three beats as Anthropic's `_11_tool_parameters`, with OpenAI's shapes:
 *   1. Send the tools alongside the messages (`addTool(...)`, a `FunctionDefinition` whose
 *      `parameters` is a JSON Schema).
 *   2. If the reply's **`finish_reason` is `tool_calls`**, the assistant message carries
 *      `toolCalls` instead of (or as well as) text. That flag is the OpenAI signal; Anthropic
 *      instead exposes `tool_use` *content blocks* mixed into the response.
 *   3. Append the assistant message **as-is** (`message.toParam()` keeps the tool_calls), then
 *      one **`role=tool` message per call**, each tagged with its `toolCallId`. Anthropic packs
 *      all results into a single `user` turn of `tool_result` blocks — OpenAI gives each result
 *      its own message, and the ids must match or the API rejects the request.
 *
 * Note the arguments arrive as a **JSON string** (`function().arguments()`), not a parsed object.
 * The SDK bundles Jackson, so `arguments(CountArgs::class.java)` deserializes it straight into a
 * data class.
 */

// ---- Typed argument holder (deserialized from the tool call's `arguments` JSON string) ----

private data class CountArgs(val text: String, val needle: String, val ignoreCase: Boolean)

// ---- The tool implementation: plain Kotlin, nothing LLM-aware ----

/** Counts non-overlapping occurrences of [needle] in [text]. */
private fun countOccurrences(text: String, needle: String, ignoreCase: Boolean): String {
    require(needle.isNotEmpty()) { "needle must not be empty" }
    val n = text.split(needle, ignoreCase = ignoreCase).size - 1
    return "'$needle' appears $n time(s) in '$text'"
}

// ---- The tool definition the model sees ----

private fun stringProp(desc: String) = mapOf("type" to "string", "description" to desc)

private val countTool: ChatCompletionFunctionTool = ChatCompletionFunctionTool.builder()
    .function(
        FunctionDefinition.builder()
            .name("count_occurrences")
            .description("Count how many times a substring occurs in a text. Use this for any letter- or substring-counting question.")
            .parameters(
                FunctionParameters.builder()
                    .putAdditionalProperty("type", JsonValue.from("object"))
                    .putAdditionalProperty(
                        "properties",
                        JsonValue.from(
                            mapOf(
                                "text" to stringProp("The text to search in"),
                                "needle" to stringProp("The substring to count, e.g. 'r' or 'ss'"),
                                "ignoreCase" to mapOf(
                                    "type" to "boolean",
                                    "description" to "Whether to match case-insensitively",
                                ),
                            )
                        )
                    )
                    // Under `strict`, EVERY property must be listed here — JSON Schema's `required`
                    // is the only knob, so a truly optional parameter is impossible. Model it as
                    // required (and let the model fill the default), or drop `strict`.
                    .putAdditionalProperty("required", JsonValue.from(listOf("text", "needle", "ignoreCase")))
                    // Also mandatory under `strict`; harmless otherwise.
                    .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                    .build()
            )
            .strict(true) // guarantees the arguments match the schema (no missing/extra fields)
            .build()
    )
    .build()

/** Dispatch a tool call to the matching Kotlin function, reading typed args from its JSON. */
private fun runTool(call: ChatCompletionMessageFunctionToolCall): String = when (call.function().name()) {
    "count_occurrences" -> {
        val args = call.function().arguments(CountArgs::class.java)
        countOccurrences(args.text, args.needle, args.ignoreCase)
    }

    else -> "error: unknown tool '${call.function().name()}'"
}

fun main() {
    val client = openaiClient()
    try {
        val messages = mutableListOf(
            ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder()
                    .content(
                        """
                        How many 'r' are there in the word "Ferrari" ?
                        Reply with one line: '<word>: <number of occurrences> x <needle>'
                        """.trimIndent()
                    )
                    .build()
            )
        )

        while (true) {
            val completion = client.chat().completions().create(
                ChatCompletionCreateParams.builder()
                    .model(defaultModel)
                    .maxCompletionTokens(1024L)
                    .addTool(countTool)
                    .messages(messages)
                    .build()
            )

            val message = completion.choices().first().message()

            // Keep the assistant turn (with its tool_calls) in history, verbatim.
            messages += ChatCompletionMessageParam.ofAssistant(message.toParam())

            // `finish_reason == tool_calls` is the OpenAI signal that the model wants a function run.
            val toolCalls = message.toolCalls().getOrNull().orEmpty().mapNotNull { it.function().getOrNull() }
            if (toolCalls.isEmpty()) {
                println("FINAL: ${message.content().getOrNull()?.trim()}")
                break
            }

            // One `role=tool` message per call, each echoing back its tool_call_id.
            for (call in toolCalls) {
                val output = runTool(call)
                println("[tool] ${call.function().name()}(${call.function().arguments()}) -> $output")
                messages += ChatCompletionMessageParam.ofTool(
                    ChatCompletionToolMessageParam.builder()
                        .toolCallId(call.id())
                        .content(output)
                        .build()
                )
            }
        }
    } finally {
        client.close()
    }
}
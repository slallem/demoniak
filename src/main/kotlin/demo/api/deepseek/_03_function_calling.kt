package demo.api.deepseek

import com.openai.core.JsonValue
import com.openai.models.FunctionDefinition
import com.openai.models.FunctionParameters
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionFunctionTool
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionToolMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import demo.api.deepseek.common.deepseekClient
import demo.api.deepseek.common.defaultModel
import kotlin.jvm.optionals.getOrNull

/**
 * **Function calling**: the model asks *us* to run a function it is bad at doing itself — here
 * `count_occurrences(text, needle)`. Counting letters is the textbook LLM failure ("how many r's
 * in strawberry?"): the model sees tokens, not characters, so it guesses. Delegating to three
 * lines of Kotlin turns a guess into a fact.
 *
 * Identical code to `openai/_04_function_calling.kt` (same loop: send tools → `tool_calls` on the
 * reply → one `role=tool` message per call, keyed by `toolCallId`) — no per-provider workaround
 * needed here, unlike Mistral's `_03_function_calling.kt` (which needs `tool_choice: "any"` instead
 * of `"required"`). **Tested live**: DeepSeek's claimed full OpenAI compatibility holds for tool
 * calling too — `.maxCompletionTokens(...)` (the modern field, same as `_01_starter`) and the
 * default `tool_choice` ("auto") were enough; `deepseek-v4-flash` called `count_occurrences`
 * itself on the first turn rather than guessing, so — unlike Mistral — there's no need to force
 * the call with `tool_choice: "required"` to get a loop worth observing.
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
    val client = deepseekClient()
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

            // `finish_reason == tool_calls` is the signal that the model wants a function run.
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

package demo.api.mistral

import com.openai.core.JsonValue
import com.openai.models.FunctionDefinition
import com.openai.models.FunctionParameters
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionFunctionTool
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionToolChoiceOption
import com.openai.models.chat.completions.ChatCompletionToolMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import demo.api.mistral.common.defaultModel
import demo.api.mistral.common.mistralClient
import kotlin.jvm.optionals.getOrNull

/**
 * **Function calling**: the model asks *us* to run a function it is bad at doing itself — here
 * `count_occurrences(text, needle)`. Counting letters is the textbook LLM failure ("how many r's
 * in strawberry?"): the model sees tokens, not characters, so it guesses. Delegating to three
 * lines of Kotlin turns a guess into a fact.
 *
 * Identical code to `openai/_04_function_calling.kt` (same loop: send tools → `tool_calls` on the
 * reply → one `role=tool` message per call, keyed by `toolCallId`), because Mistral's tool-calling
 * request/response shape matches OpenAI's here too, with two differences:
 *  - `.maxTokens(...)` instead of `.maxCompletionTokens(...)` — see `_01_starter`.
 *  - Left at the default `tool_choice` ("auto" when tools are present), `mistral-small-latest`
 *    answered "Ferrari: 3 x r" straight from its own (correct, here) guess and never called the
 *    tool at all — no loop to observe. OpenAI's `"required"` string forces a call; Mistral's
 *    equivalent is the string `"any"` instead. The SDK's `Auto` enum only has OpenAI's three
 *    values (`none`/`auto`/`required`) typed, but `Auto.of("any")` constructs the same wrapper
 *    with an arbitrary string, so the raw JSON still comes out as `"tool_choice": "any"` — the
 *    typed enum is a convenience, not a hard restriction on the wire format.
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
    val client = mistralClient()
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

        var firstTurn = true
        while (true) {
            val params = ChatCompletionCreateParams.builder()
                .model(defaultModel)
                .maxTokens(1024L) // Mistral rejects `max_completion_tokens`; only `max_tokens` works
                .addTool(countTool)
                .messages(messages)
            if (firstTurn) {
                // Force the call on turn 1: Mistral's magic string is "any", not OpenAI's
                // "required". Only for turn 1 — forcing it on every turn would make the model
                // call the tool forever instead of ever handing back a final answer.
                params.toolChoice(ChatCompletionToolChoiceOption.ofAuto(ChatCompletionToolChoiceOption.Auto.of("any")))
            }
            firstTurn = false

            val completion = client.chat().completions().create(params.build())

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

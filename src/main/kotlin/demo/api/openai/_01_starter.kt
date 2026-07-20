package demo.api.openai

import com.openai.models.chat.completions.ChatCompletionCreateParams
import demo.api.openai.common.defaultModel
import demo.api.openai.common.openaiClient

/**
 * Starter for connecting to the OpenAI API from Kotlin: build the params, send them, print the
 * reply. Everything else in this package is a variation on these few lines.
 *
 * The question is deliberately one the model gets wrong. Counting letters is a textbook LLM
 * failure — it reads *tokens*, not characters, so "how many r in Ferrari?" is a guess dressed up
 * as an answer, and re-running this may well print a different number each time. Nothing here is
 * broken: it is the limit of asking a model to compute rather than to write.
 *
 * [_04_function_callingKt] fixes exactly this question by handing the counting to a Kotlin
 * function the model can call — same prompt, this time a fact.
 */

fun main() {
    val client = openaiClient()
    try {
        val params = ChatCompletionCreateParams.builder()
            .model(defaultModel)
            .maxCompletionTokens(1024L)
            .addUserMessage("Hello, GPT! Tell me: how many r are in the word Ferrari ?")
            .build()

        val completion = client.chat().completions().create(params)

        // `choices` is a list because the API can return several candidates (see the `n` param);
        // with the default n=1 this loop prints exactly one reply.
        completion.choices().stream()
            .flatMap { choice -> choice.message().content().stream() }
            .forEach { text -> println(text) }
    } finally {
        client.close()
    }
}
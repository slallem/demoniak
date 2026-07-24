package demo.api.openai

import com.openai.client.OpenAIClient
import com.openai.models.responses.Response
import com.openai.models.responses.ResponseCreateParams
import demo.api.openai.common.defaultModel
import demo.api.openai.common.openaiClient
import kotlin.jvm.optionals.getOrNull

/**
 * The **Responses API** — OpenAI's stateful alternative to the Chat Completions conversation
 * pattern `_02_chat` uses. Contrast the two directly:
 *
 * - `_02_chat`'s `Conversation` keeps a growing `history` list *client-side* and resends the
 *   whole thing on every turn — the server remembers nothing between calls.
 * - Here, each turn sends only the *new* input plus `previousResponseId`, and OpenAI's servers
 *   reattach the prior turns for you. No history list on our side at all — the code below never
 *   builds one.
 *
 * `client.responses()` is a different endpoint (`/v1/responses`, not `/v1/chat/completions`) with
 * its own request/response shape — `input` instead of `messages`, `instructions` instead of a
 * system message, and a nested `output` list of typed items (`message`, `functionCall`, …) instead
 * of `choices[].message.content`. [textOf] below is the "read the reply text" helper this shape
 * needs — there is no `.outputText()` one-liner on [Response] itself in this SDK, unlike some
 * other language SDKs' convenience property.
 *
 * The trade-off `previousResponseId` buys: less to resend over the wire per turn, and OpenAI's
 * own prompt-caching applies automatically server-side — at the cost of your conversation state
 * now living on OpenAI's servers rather than only in your process (`store` defaults to true;
 * response objects persist for OpenAI to retrieve on a follow-up call).
 */

private fun textOf(response: Response): String =
    response.output()
        .mapNotNull { it.message().getOrNull() }
        .flatMap { it.content() }
        .mapNotNull { it.outputText().getOrNull() }
        .joinToString("") { it.text() }

private fun ask(client: OpenAIClient, input: String, previousResponseId: String? = null): Response {
    val builder = ResponseCreateParams.builder()
        .model(defaultModel)
        .input(input)
    previousResponseId?.let { builder.previousResponseId(it) }
    return client.responses().create(builder.build())
}

fun main() {
    val client = openaiClient()
    try {
        // Turn 1: no previousResponseId yet — this is the start of the (server-side) conversation.
        val first = ask(
            client,
            "Pick a random color among the rainbow colors. Answer with just the color name, nothing else."
        )
        println("Turn 1 (response id=${first.id()}): ${textOf(first)}")

        // Turn 2: only the new question is sent — no history array, just the prior response's id.
        // The server resolves "this color" using state it already holds, not anything resent here.
        val second = ask(
            client,
            "Now name a random flower, fruit, or vegetable that is this color. Answer with just its name.",
            previousResponseId = first.id()
        )
        println("Turn 2 (response id=${second.id()}, previous=${second.previousResponseId().getOrNull()}): ${textOf(second)}")
    } finally {
        client.close()
    }
}

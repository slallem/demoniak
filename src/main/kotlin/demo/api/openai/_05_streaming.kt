package demo.api.openai

import com.openai.core.http.StreamResponse
import com.openai.models.chat.completions.ChatCompletionChunk
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionStreamOptions
import demo.api.openai.common.defaultModel
import demo.api.openai.common.openaiClient
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.jvm.optionals.getOrNull

/**
 * **Response streaming**, chunk by chunk — and the clearest structural difference between the two
 * APIs.
 *
 * Anthropic streams a sequence of **typed events** (`_07_streaming`): `message_start`,
 * `content_block_delta`, `message_stop`… each a distinct shape you match on. OpenAI streams
 * **one repeated object**, `ChatCompletionChunk`, that always looks the same. There is nothing to
 * match on: you read `choices[0].delta` and infer where you are from *which field happens to be
 * set*:
 *
 *   - first chunk  → `delta.role = "assistant"`, usually with empty content (the opening marker)
 *   - middle chunks→ `delta.content = "…"`, a few characters of text each
 *   - last chunk   → empty delta + **`finishReason = "stop"`** (the closing marker)
 *   - after that   → an extra chunk with **no choices at all**, carrying only `usage`
 *
 * That last one is a trap worth knowing: a streamed response reports **no usage by default**, and
 * `choices()` is empty on the usage chunk — indexing `choices[0]` blindly throws at the very end
 * of an otherwise working stream. You must opt in with `streamOptions(includeUsage = true)`.
 *
 * The text is simply the concatenation of every `delta.content`; assembling it is the caller's job
 * (same as Anthropic, where you accumulate `text_delta`s). Here we log each chunk with its timing,
 * then print the reassembled reply — so both the shape and the arrival pattern are visible.
 *
 * Note `StreamResponse` is `AutoCloseable`: always `use { }` it. Abandoning a stream without
 * closing leaks the underlying HTTP connection.
 */

private data class Chunk(
    val wallClock: String,   // HH:mm:ss.SSS when the chunk arrived
    val elapsedMs: Long,     // ms since the first chunk
    val marker: String,      // which field made this chunk interesting
    val content: String,     // human-readable payload
)

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

/** Reduce a chunk to a (marker, human-readable content) pair — the OpenAI equivalent of a type. */
private fun describe(chunk: ChatCompletionChunk): Pair<String, String> {
    // The usage chunk has no choices; every other chunk has exactly one (unless `n` > 1).
    val choice = chunk.choices().firstOrNull()
        ?: return "usage" to (chunk.usage().getOrNull()
            ?.let { "prompt=${it.promptTokens()}, completion=${it.completionTokens()}, total=${it.totalTokens()}" }
            ?: "-")

    val delta = choice.delta()
    val finish = choice.finishReason().getOrNull()

    return when {
        finish != null -> "finish_reason" to finish.asString()
        delta.role().isPresent -> "role" to "${delta.role().get()} (opening chunk)"
        delta.refusal().isPresent -> "refusal" to delta.refusal().get()
        else -> "content" to "\"${delta.content().getOrNull().orEmpty()}\""
    }
}

fun main() {
    val client = openaiClient()
    try {
        val params = ChatCompletionCreateParams.builder()
            .model(defaultModel)
            .maxCompletionTokens(1024L)
            .addUserMessage("Write two short paragraphs describing a sunrise over the ocean.")
            // Without this, a streamed response carries no usage at all.
            .streamOptions(ChatCompletionStreamOptions.builder().includeUsage(true).build())
            .build()

        val chunks = mutableListOf<Chunk>()
        val text = StringBuilder()
        var startNanos = 0L

        println("── Chunks as they arrive ──")
        val stream: StreamResponse<ChatCompletionChunk> = client.chat().completions().createStreaming(params)
        stream.use {
            it.stream().forEach { chunk ->
                val now = System.nanoTime()
                if (startNanos == 0L) startNanos = now

                // Accumulate the reply: the text is just every delta.content, concatenated.
                chunk.choices().firstOrNull()?.delta()?.content()?.getOrNull()?.let(text::append)

                val (marker, content) = describe(chunk)
                val logged = Chunk(
                    wallClock = LocalTime.now().format(CLOCK),
                    elapsedMs = (now - startNanos) / 1_000_000,
                    marker = marker,
                    content = content,
                )
                chunks.add(logged)
                println("  %s  +%5dms  %-14s  %s".format(logged.wallClock, logged.elapsedMs, logged.marker, logged.content))
                System.out.flush() // print live, don't wait for the buffer
            }
        }

        println("\n── Reassembled reply (${chunks.size} chunks) ──")
        println(text.toString().trim())
    } finally {
        client.close()
    }
}
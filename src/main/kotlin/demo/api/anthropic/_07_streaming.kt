package demo.api.anthropic

import com.anthropic.core.http.StreamResponse
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.RawMessageStreamEvent
import demo.api.anthropic.common.anthropicClient
import demo.api.anthropic.common.defaultModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Demonstrating **response streaming**, event by event.
 *
 * `messages().create(...)` waits for the whole reply; streaming instead delivers a
 * sequence of *events* as the model works. A full response looks like:
 *
 *   message_start → content_block_start → (content_block_delta)* → content_block_stop
 *                 → message_delta → message_stop
 *
 * The visible text arrives as many small `text_delta` chunks inside the
 * `content_block_delta` events. Here we record every event with a timestamp and its
 * payload, print each as it lands, then recap the whole list at the end — so you can
 * see both the timing and the shape of the stream.
 */

private data class StreamChunk(
    val wallClock: String,   // HH:mm:ss.SSS when the event arrived
    val elapsedMs: Long,     // ms since the first event
    val type: String,        // event type
    val content: String,     // human-readable payload
)

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

/** Reduce a raw stream event to a (type, human-readable content) pair. */
private fun describe(event: RawMessageStreamEvent): Pair<String, String> = when {
    event.messageStart().isPresent -> {
        val m = event.messageStart().get().message()
        "message_start" to "id=${m.id()}, model=${m.model()}"
    }
    event.contentBlockStart().isPresent ->
        "content_block_start" to "index=${event.contentBlockStart().get().index()}"

    event.contentBlockDelta().isPresent -> {
        val text = event.contentBlockDelta().get().delta().text().map { it.text() }.orElse("")
        "content_block_delta" to "text_delta: \"$text\""
    }

    event.contentBlockStop().isPresent ->
        "content_block_stop" to "index=${event.contentBlockStop().get().index()}"

    event.messageDelta().isPresent -> {
        val stop = event.messageDelta().get().delta().stopReason().map { it.toString() }.orElse("-")
        "message_delta" to "stop_reason=$stop"
    }

    event.messageStop().isPresent -> "message_stop" to ""
    else -> "unknown" to ""
}

fun main() {
    val client = anthropicClient()
    try {
        val params = MessageCreateParams.builder()
            .model(defaultModel)
            .maxTokens(1024L)
            .addUserMessage("Write two short paragraphs describing a sunrise over the ocean.")
            .build()

        val chunks = mutableListOf<StreamChunk>()
        var startNanos = 0L

        println("── Events as they arrive ──")
        val stream: StreamResponse<RawMessageStreamEvent> = client.messages().createStreaming(params)
        stream.use {
            it.stream().forEach { event ->
                val now = System.nanoTime()
                if (startNanos == 0L) startNanos = now
                val (type, content) = describe(event)
                val chunk = StreamChunk(
                    wallClock = LocalTime.now().format(CLOCK),
                    elapsedMs = (now - startNanos) / 1_000_000,
                    type = type,
                    content = content,
                )
                chunks.add(chunk)
                println("  %s  +%5dms  %-20s  %s".format(chunk.wallClock, chunk.elapsedMs, chunk.type, chunk.content))
                System.out.flush() // print live, don't wait for the buffer
            }
        }

        // Recap: the full list of streamed chunks.
        println("\n── Recap: ${chunks.size} chunks ──")
        chunks.forEachIndexed { i, c ->
            println("  %2d. [+%5dms] %-20s %s".format(i + 1, c.elapsedMs, c.type, c.content))
        }
    } finally {
        client.close()
    }
}

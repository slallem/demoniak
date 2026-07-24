package demo.api.openai

import com.openai.models.audio.AudioModel
import com.openai.models.audio.transcriptions.TranscriptionCreateParams
import com.openai.models.chat.completions.ChatCompletionCreateParams
import demo.api.openai.common.defaultModel
import demo.api.openai.common.openaiClient
import kotlin.jvm.optionals.getOrNull
import java.nio.file.Path

/**
 * **Speech-to-text**, then a question about what was said: audio -> transcript -> chat.
 *
 * Two separate calls, two separate models — transcription is not a chat completion. `gpt-4o-mini-transcribe`
 * is the current default here (cheaper, multilingual, replaces the older `whisper-1` for most use
 * cases — `AudioModel.WHISPER_1` still works if you need the original Whisper model specifically).
 * The endpoint (`client.audio().transcriptions()`) takes the audio file directly (multipart, not
 * base64 like vision's `image_url`) and returns plain text — no timestamps or speaker labels
 * unless `responseFormat`/`timestampGranularities` ask for the verbose/diarized variant.
 *
 * The second call is just an ordinary chat completion (`_01_starter`'s shape) with the transcript
 * pasted into the prompt — nothing audio-specific about answering questions on it once it's text.
 *
 * Tested against the real asset: a French dialogue playing on "Mimosa" (a flower vs. "œuf
 * mimosa", a dish) — transcribed correctly, and the follow-up question was answered correctly in
 * English despite the source audio being French, with no language hint given anywhere in this file.
 */

private const val ASSET = "assets/audio/mimosa.mp3"

fun main() {
    val client = openaiClient()
    try {
        val file = Path.of(ASSET)
        require(file.toFile().exists()) { "Asset not found: ${file.toAbsolutePath()} — run from the project root." }

        val transcription = client.audio().transcriptions().create(
            TranscriptionCreateParams.builder()
                .file(file)
                .model(AudioModel.GPT_4O_MINI_TRANSCRIBE)
                .build()
        )
        val text = transcription.transcription().getOrNull()?.text()
            ?: error("Expected a plain Transcription (no responseFormat override was requested)")

        println("Transcript:")
        println(text)

        val question = "In one sentence, what is this about?"
        val params = ChatCompletionCreateParams.builder()
            .model(defaultModel)
            .maxCompletionTokens(128L)
            .addUserMessage("$question\n\n<transcript>\n$text\n</transcript>")
            .build()

        val answer = client.chat().completions().create(params)
            .choices().first().message().content().getOrNull()?.trim().orEmpty()

        println("\nQ: $question")
        println("A: $answer")
    } finally {
        client.close()
    }
}

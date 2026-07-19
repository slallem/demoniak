package demo.api.ollama.common

/**
 * Locally installed Ollama model tags.
 *
 * Unlike the cloud providers, this list is **not** a catalogue of what exists — it is whatever
 * *you* have pulled. `ollama list` is the source of truth; `ollama pull <tag>` adds one.
 * To switch the model used by every example, change [Models.DEFAULT] in one place.
 *
 * Caveat — **thinking is on by default** on the Qwen3.5 family, and on a 2B model it can run
 * away completely: measured here, the same one-word question took **over 300s (never returned)
 * with thinking on, versus ~1.5s with it off**. This is not the mild latency tax the Gemini
 * examples describe — it makes an example unusable, so both examples disable it. The switch is
 * spelled differently per route: `reasoning_effort: none` on the OpenAI-compatible `/v1` route
 * (`_01_starter`, `_02_chat_openai`), `"think": false` on the native `/api/chat` route (`_02_chat_http`).
 */
object Models {
    /** Small and recent; thinking must be disabled to be usable (see the caveat above). */
    const val QWEN3_5_2B = "qwen3.5:2b"

    /** Bigger and slower (~30-40s per turn on an M-series laptop), but no runaway thinking. */
    const val GEMMA4_12B = "gemma4:12b"

    /** The model every example uses. Change this one line to switch them all. */
    const val DEFAULT = QWEN3_5_2B
}

/** Ready-to-use model tag for the examples, from [Models.DEFAULT]. */
val defaultModel: String = Models.DEFAULT

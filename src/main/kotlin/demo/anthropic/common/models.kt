package org.example.demo.anthropic.common

import com.anthropic.models.messages.Model

/**
 * Claude model IDs available at this time.
 *
 * Each constant is the exact model-id string the API accepts. Wrap one in
 * [Model.of] to hand it to the SDK, or use [defaultModel] which the examples share.
 * To switch the model used by every example, change [Models.DEFAULT] in one place.
 */
object Models {
    // ---- Current (recommended) ----
    const val FABLE_5 = "claude-fable-5"
    const val MYTHOS_5 = "claude-mythos-5"   // Project Glasswing only
    const val OPUS_4_8 = "claude-opus-4-8"
    const val OPUS_4_7 = "claude-opus-4-7"
    const val OPUS_4_6 = "claude-opus-4-6"
    const val SONNET_5 = "claude-sonnet-5"
    const val SONNET_4_6 = "claude-sonnet-4-6"
    const val HAIKU_4_5 = "claude-haiku-4-5"

    // ---- Legacy (still active) ----
    const val OPUS_4_5 = "claude-opus-4-5"
    const val OPUS_4_1 = "claude-opus-4-1"   // deprecated, retires 2026-08-05
    const val SONNET_4_5 = "claude-sonnet-4-5"

    /** The model every example uses. Change this one line to switch them all. */
    const val DEFAULT = HAIKU_4_5
}

/** Ready-to-use SDK [Model] for the examples, built from [Models.DEFAULT]. */
val defaultModel: Model = Model.of(Models.DEFAULT)
package demo.api.google.common

/**
 * Google Gemini model IDs available at this time.
 *
 * Each constant is the exact model-id string the API accepts. The GenAI SDK takes
 * the model as a plain string, so [defaultModel] is just [Models.DEFAULT].
 * To switch the model used by every example, change [Models.DEFAULT] in one place.
 */
object Models {
    // ---- Current (recommended) ----
    const val GEMINI_2_5_PRO = "gemini-2.5-pro"
    const val GEMINI_2_5_FLASH = "gemini-2.5-flash"
    const val GEMINI_2_5_FLASH_LITE = "gemini-2.5-flash-lite"

    // ---- Legacy (still active) ----
    const val GEMINI_2_0_FLASH = "gemini-2.0-flash"
    const val GEMINI_2_0_FLASH_LITE = "gemini-2.0-flash-lite"

    /** The model every example uses. Change this one line to switch them all. */
    const val DEFAULT = GEMINI_2_5_FLASH
}

/** Ready-to-use model id for the examples, from [Models.DEFAULT]. */
val defaultModel: String = Models.DEFAULT
package demo.api.google.common

/**
 * Google Gemini model IDs available at this time.
 *
 * Each constant is the exact model-id string the API accepts. The GenAI SDK takes
 * the model as a plain string, so [defaultModel] is just [Models.DEFAULT].
 * To switch the model used by every example, change [Models.DEFAULT] in one place.
 *
 * Caveats worth knowing before changing [Models.DEFAULT]:
 *  - **Thinking is on by default** and dominates latency on small prompts. Turn it down with
 *    `GenerateContentConfig.thinkingConfig` — but the knob differs per family: `thinkingBudget`
 *    (0 disables) on 2.5, `thinkingLevel` (MINIMAL…HIGH) on 3.x.
 *  - The frontier models are the slowest to serve and the first to be throttled: on a free-tier
 *    key [GEMINI_3_5_FLASH] answers 503 "high demand" / 429 far more often than it answers.
 *  - **The free tier allows only ~20 requests/day, per model** (the 429 names the quota
 *    `GenerateRequestsPerDayPerProjectPerModel-FreeTier`). A handful of runs exhausts a model for
 *    the day; the budget is per model, so switching [Models.DEFAULT] buys a fresh allowance.
 */
object Models {
    // ---- Current (recommended) ----
    const val GEMINI_3_5_FLASH = "gemini-3.5-flash"
    const val GEMINI_3_1_FLASH_LITE = "gemini-3.1-flash-lite"
    const val GEMINI_2_5_PRO = "gemini-2.5-pro"
    const val GEMINI_2_5_FLASH = "gemini-2.5-flash"
    const val GEMINI_2_5_FLASH_LITE = "gemini-2.5-flash-lite"

    // ---- Preview ----
    const val GEMINI_3_1_PRO_PREVIEW = "gemini-3.1-pro-preview"
    const val GEMINI_3_FLASH_PREVIEW = "gemini-3-flash-preview"

    // ---- Floating aliases (always point at the current stable of their tier) ----
    const val FLASH_LATEST = "gemini-flash-latest"
    const val FLASH_LITE_LATEST = "gemini-flash-lite-latest"
    const val PRO_LATEST = "gemini-pro-latest"

    // ---- Retired ----
    // gemini-2.0-flash / gemini-2.0-flash-lite are shut down: still enumerated by
    // models.list, but generateContent no longer serves them. Do not use.

    /**
     * The model every example uses. Change this one line to switch them all.
     *
     * 2.5 Flash is chosen deliberately over the newer, stronger [GEMINI_3_5_FLASH]: it is
     * fast (~1s on a short prompt), cheap, and — crucially for a tutorial — actually served
     * on a free-tier key. See the caveats above.
     */
    const val DEFAULT = GEMINI_2_5_FLASH
}

/** Ready-to-use model id for the examples, from [Models.DEFAULT]. */
val defaultModel: String = Models.DEFAULT

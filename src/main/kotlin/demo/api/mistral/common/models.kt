package demo.api.mistral.common

import com.openai.models.ChatModel

/**
 * Mistral model IDs available at this time.
 *
 * Each constant is the exact model-id string the API accepts. Wrap one in [ChatModel.of] to hand
 * it to the SDK, or use [defaultModel] which the examples share. To switch the model used by
 * every example, change [Models.DEFAULT] in one place.
 */
object Models {
    // ---- General purpose ----
    const val MISTRAL_LARGE = "mistral-large-latest"
    const val MISTRAL_MEDIUM = "mistral-medium-latest"
    const val MISTRAL_SMALL = "mistral-small-latest"

    // ---- Small / cheap ----
    const val MINISTRAL_8B = "ministral-8b-latest"
    const val MINISTRAL_3B = "ministral-3b-latest"

    // ---- Code ----
    const val CODESTRAL = "codestral-latest"

    /** The model every example uses. Change this one line to switch them all. */
    const val DEFAULT = MISTRAL_SMALL
}

/** Ready-to-use SDK [ChatModel] for the examples, built from [Models.DEFAULT]. */
val defaultModel: ChatModel = ChatModel.of(Models.DEFAULT)

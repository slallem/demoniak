package demo.api.openai.common

import com.openai.models.ChatModel

/**
 * OpenAI model IDs available at this time.
 *
 * Each constant is the exact model-id string the API accepts. Wrap one in
 * [ChatModel.of] to hand it to the SDK, or use [defaultModel] which the examples share.
 * To switch the model used by every example, change [Models.DEFAULT] in one place.
 */
object Models {
    // ---- Current (recommended) ----
    const val GPT_5 = "gpt-5"
    const val GPT_5_MINI = "gpt-5-mini"
    const val GPT_5_NANO = "gpt-5-nano"
    const val GPT_4_1 = "gpt-4.1"
    const val GPT_4_1_MINI = "gpt-4.1-mini"
    const val GPT_4O = "gpt-4o"
    const val GPT_4O_MINI = "gpt-4o-mini"

    // ---- Reasoning ----
    const val O3 = "o3"
    const val O4_MINI = "o4-mini"

    /** The model every example uses. Change this one line to switch them all. */
    const val DEFAULT = GPT_4O_MINI
}

/** Ready-to-use SDK [ChatModel] for the examples, built from [Models.DEFAULT]. */
val defaultModel: ChatModel = ChatModel.of(Models.DEFAULT)
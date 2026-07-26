package demo.api.deepseek.common

import com.openai.models.ChatModel

/**
 * DeepSeek model IDs available at this time.
 *
 * Each constant is the exact model-id string the API accepts. Wrap one in [ChatModel.of] to hand
 * it to the SDK, or use [defaultModel] which the examples share. To switch the model used by
 * every example, change [Models.DEFAULT] in one place.
 *
 * **Renamed 2026-07-24, retired 2026-07-24 15:59 UTC**: the long-standing `deepseek-chat`/
 * `deepseek-reasoner` model ids are gone — if a tutorial or blog post still points at either, that's
 * why it now 404s. Per DeepSeek's own migration notes, **both** old names routed to
 * [DEEPSEEK_V4_FLASH] (`deepseek-chat` → non-thinking mode, `deepseek-reasoner` → thinking mode);
 * [DEEPSEEK_V4_PRO] is a new, stronger tier with no old-name predecessor, not a `deepseek-reasoner`
 * replacement. These two ([DEEPSEEK_V4_FLASH], [DEEPSEEK_V4_PRO]) are the complete current model
 * lineup per the `/models` endpoint — verify against DeepSeek's docs before assuming that's still
 * true by the time you read this.
 */
object Models {
    /** Cheap, fast general-purpose model — the modern replacement for the old `deepseek-chat` AND
     *  `deepseek-reasoner` (both aliased here, toggled by thinking mode). */
    const val DEEPSEEK_V4_FLASH = "deepseek-v4-flash"

    /** Stronger, pricier tier — new with V4, not a renamed legacy model. */
    const val DEEPSEEK_V4_PRO = "deepseek-v4-pro"

    /**
     * The model every example uses. Flash over Pro, deliberately: DeepSeek's whole pitch is
     * frontier-ish quality at a very low price, and Flash is the sharper demonstration of that —
     * cache-hit input runs roughly 50x cheaper than cache-miss input on this tier.
     */
    const val DEFAULT = DEEPSEEK_V4_FLASH
}

/** Ready-to-use SDK [ChatModel] for the examples, built from [Models.DEFAULT]. */
val defaultModel: ChatModel = ChatModel.of(Models.DEFAULT)

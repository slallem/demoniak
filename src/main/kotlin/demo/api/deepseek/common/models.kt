package demo.api.deepseek.common

import com.openai.models.ChatModel

/**
 * DeepSeek model IDs available at this time.
 *
 * Each constant is the exact model-id string the API accepts. Wrap one in [ChatModel.of] to hand
 * it to the SDK, or use [defaultModel] which the examples share. To switch the model used by
 * every example, change [Models.DEFAULT] in one place.
 *
 * **Renamed 2026-07-24**: the long-standing `deepseek-chat`/`deepseek-reasoner` model ids were
 * deprecated in favor of [DEEPSEEK_V4_FLASH]/[DEEPSEEK_V4_PRO] the day before this file was
 * written — if a tutorial or blog post still points at the old names, that's why they now 404 (or
 * silently alias, depending how long DeepSeek keeps the deprecated names routable). Check
 * DeepSeek's own docs before assuming either generation of name is current.
 */
object Models {
    /** Cheap, fast general-purpose model — the modern replacement for the old `deepseek-chat`. */
    const val DEEPSEEK_V4_FLASH = "deepseek-v4-flash"

    /** Stronger, pricier general-purpose model — the modern replacement for `deepseek-reasoner`. */
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

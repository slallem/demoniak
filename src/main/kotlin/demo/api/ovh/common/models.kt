package demo.api.ovh.common

import com.openai.models.ChatModel

/**
 * Model names available on OVHcloud AI Endpoints at this time (a small, hand-picked subset).
 *
 * Each constant is the exact `model` string the API accepts — the catalog's display *name*, not
 * its lowercase `id`. The catalog changes often and some entries are flagged `available: false`;
 * `https://catalog.endpoints.ai.ovh.net/rest/v1/models_v2` is the source of truth (public, no key).
 * See `doc/models.md` for the full overview. Wrap one in [ChatModel.of] to hand it to the SDK, or
 * use [defaultModel] which the examples share. Change [Models.DEFAULT] to switch them all.
 */
object Models {
    /** OVHcloud's own open-weight (Apache 2.0) MoE reasoning model: 6.8B total / 1.5B active, 32k context. */
    const val OLALA_MINI = "Olala-v1.0-mini-7A1B"

    // Third-party open-weight models also served by OVH
    const val GPT_OSS_20B = "gpt-oss-20b"
    const val GPT_OSS_120B = "gpt-oss-120b"
    const val QWEN_3_5_397B = "Qwen3.5-397B-A17B"

    /** The model every example uses. Change this one line to switch them all. */
    const val DEFAULT = OLALA_MINI
}

/** Ready-to-use SDK [ChatModel] for the examples, built from [Models.DEFAULT]. */
val defaultModel: ChatModel = ChatModel.of(Models.DEFAULT)

package demo.api.aws.common

/**
 * Anthropic Claude model IDs as exposed through Amazon Bedrock.
 *
 * Bedrock IDs are not the same strings as the direct Anthropic API
 * ([demo.api.anthropic.common.Models]): they are prefixed `anthropic.` and dated, e.g.
 * `anthropic.claude-haiku-4-5-20251001-v1:0`.
 *
 * **Cross-region inference profile gotcha**: invoking that bare foundation-model ID for
 * on-demand (pay-per-token) traffic fails with `ValidationException: on-demand throughput isn't
 * supported for ...`. Current-generation Anthropic models on Bedrock must instead be called
 * through a *cross-region inference profile* — the same id prefixed `us.` (US-only routing) or
 * `global.` (worldwide routing). [DEFAULT] below already uses the `us.` form; dropping the
 * prefix is the single most common first-call error with this provider.
 */
object Models {
    /** Claude Haiku 4.5, via the US cross-region inference profile. */
    const val CLAUDE_HAIKU_4_5 = "us.anthropic.claude-haiku-4-5-20251001-v1:0"

    /**
     * The model every example uses. Change this one line to switch them all.
     *
     * Deliberately the same underlying model as the Anthropic-direct default
     * ([demo.api.anthropic.common.Models.DEFAULT]) — same weights, different transport and
     * auth, which is the point of contrasting the two providers.
     */
    const val DEFAULT = CLAUDE_HAIKU_4_5
}

/** Ready-to-use model id for the examples, from [Models.DEFAULT]. */
val defaultModel: String = Models.DEFAULT

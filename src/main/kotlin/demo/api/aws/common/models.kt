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
 * through a *cross-region inference profile* — the same id prefixed with a **region-group**
 * matching wherever [demo.api.aws.common.bedrockClient] actually resolves its region to
 * (`AWS_REGION` env var, else `~/.aws/config`'s active profile): `us.` for US regions, `eu.` for
 * European regions, `apac.` for Asia-Pacific, or `global.` for worldwide routing. Mismatch the
 * two — e.g. a `us.`-prefixed id while resolving to `eu-west-1` — and Bedrock doesn't fail with a
 * credentials/region error at all (the request is still validly signed and routed); it instead
 * 400s with `ValidationException: The provided model identifier is invalid`, since that profile
 * simply doesn't exist outside its own region group. **Hit this for real** debugging against an
 * `eu-west-1`-default profile: switched [DEFAULT] below from `us.` to `eu.` to match.
 */
object Models {
    /** Claude Haiku 4.5, via the EU cross-region inference profile. */
    const val CLAUDE_HAIKU_4_5 = "eu.anthropic.claude-haiku-4-5-20251001-v1:0"

    /**
     * The model every example uses. Change this one line to switch them all.
     *
     * Deliberately the same underlying model as the Anthropic-direct default
     * ([demo.api.anthropic.common.Models.DEFAULT]) — same weights, different transport and
     * auth, which is the point of contrasting the two providers.
     *
     * **Region-dependent**: the `eu.` prefix here matches a Bedrock client resolving to a
     * European region. If your `AWS_REGION` (or default profile) resolves to a US region instead,
     * swap this back to the `us.` prefix — see the cross-region inference profile note above.
     */
    const val DEFAULT = CLAUDE_HAIKU_4_5
}

/** Ready-to-use model id for the examples, from [Models.DEFAULT]. */
val defaultModel: String = Models.DEFAULT

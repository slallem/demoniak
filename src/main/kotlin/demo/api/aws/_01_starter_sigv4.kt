package demo.api.aws

import demo.api.aws.common.bedrockClient
import demo.api.aws.common.defaultModel
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole
import software.amazon.awssdk.services.bedrockruntime.model.Message

/**
 * Starter for calling Anthropic Claude through Amazon Bedrock's Converse API — the **SigV4**
 * auth variant (per-request signing from an access key + secret). See
 * [demo.api.aws._01b_starter_apikey] for the alternative: Bedrock **API keys** (a static bearer
 * token instead of request signing).
 *
 * Needs credentials and a region — but **not necessarily** via environment variables. Two
 * independent fallback chains resolve them (see [demo.api.aws.common.bedrockClient]), and either
 * can be satisfied at whichever step is easiest for you:
 *
 * **Credentials** ([software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider]), in order:
 *   1. Java system properties (`aws.accessKeyId` / `aws.secretAccessKey`)
 *   2. Env vars `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` (+ `AWS_SESSION_TOKEN` if temporary)
 *   3. `~/.aws/credentials` — the `[default]` profile, or whichever one `AWS_PROFILE` names
 *   4. ECS container / EC2 instance-profile credentials (only inside AWS itself)
 *
 * **Region** — a *separate* chain, since [demo.api.aws.common.bedrockClient] never calls
 * `.region(...)` explicitly:
 *   1. Env var `AWS_REGION` (or system property `aws.region`)
 *   2. `~/.aws/config` — the `region` of that same active profile
 *   3. EC2 instance metadata (only inside AWS itself)
 *
 * If you've ever run `aws configure` or an SSO login for this account, steps 3/2 above already
 * satisfy both chains — no env vars needed, and none of the four `AWS_*` variables have to be set
 * at all. That also means the credentials and the region can silently come from *different*
 * sources than you expect (e.g. an old `~/.aws/config` default region), which is worth checking
 * first if a call fails with a Bedrock-side error rather than a credentials error — see the
 * cross-region inference profile note on [demo.api.aws.common.Models] for exactly that failure
 * mode.
 *
 * Also needs one-time model access granted in the Bedrock console for the model behind
 * [defaultModel] (see [demo.api.aws.common.Models]).
 */

fun main() {
    val client = bedrockClient()
    client.use { client ->
        val response = client.converse { request ->
            request.modelId(defaultModel)
                .messages(
                    Message.builder()
                        .role(ConversationRole.USER)
                        .content(
                            ContentBlock.fromText(
                                "Hello, Claude! Reply with a one-line greeting (3 to 10 words)."
                            )
                        )
                        .build()
                )
                .inferenceConfig { it.maxTokens(512) }
        }

        println(response.output().message().content().first().text())
    }
}

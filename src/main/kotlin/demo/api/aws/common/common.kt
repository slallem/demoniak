package demo.api.aws.common

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.token.credentials.SdkToken
import software.amazon.awssdk.auth.token.credentials.StaticTokenProvider
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient

/**
 * Common code for calling Anthropic Claude models through Amazon Bedrock from Kotlin.
 *
 * Unlike every other provider in this repo, there is no `aws.properties`, and Bedrock has **two**
 * distinct auth mechanisms — [bedrockClient] (SigV4 request signing) and [bedrockApiKeyClient]
 * (a static bearer token, Bedrock's newer "API keys" feature) — rather than the usual one client
 * factory. See each function's doc for which prerequisites apply to it specifically.
 *
 * Two AWS-specific prerequisites neither variant can skip, and this repo can't automate for you:
 *  - **Model access**: must be granted once per model family in the Bedrock console
 *    ("Model access" page) — a correct IAM policy alone is not enough.
 *  - **IAM policy**: the identity behind the credentials/token needs `bedrock:InvokeModel` /
 *    `bedrock:Converse` allowed on the model (or inference-profile) ARN used — see
 *    [demo.api.aws.common.Models].
 */

/**
 * SigV4 variant: authenticates each request with a **per-request HMAC signature** derived from an
 * access key + secret (not a single bearer-token header), which is why the one-property
 * classpath-resource pattern used by every other provider in this repo doesn't map to it.
 * [DefaultCredentialsProvider] walks AWS's own credential chain instead — environment variables
 * first (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, plus `AWS_SESSION_TOKEN` if the credentials
 * are temporary, e.g. from an assumed role), then `~/.aws/credentials`, then an EC2/ECS/Lambda
 * instance role. The region resolves the same way via `AWS_REGION` (or `~/.aws/config`) —
 * [BedrockRuntimeClient.builder] picks it up automatically, so there is nothing to wire up here,
 * not even an `OLLAMA_URL`-style constant.
 */
fun bedrockClient(): BedrockRuntimeClient =
    BedrockRuntimeClient.builder()
        .credentialsProvider(DefaultCredentialsProvider.builder().build())
        .build()

/** Env var Bedrock itself documents for its API keys — same name the AWS CLI/SDKs use. */
private const val API_KEY_ENV_VAR = "AWS_BEARER_TOKEN_BEDROCK"

/**
 * API key variant: authenticates with a **static bearer token** (`Authorization: Bearer ...`)
 * instead of per-request SigV4 signing — Bedrock's newer, simpler auth mechanism (mid-2025),
 * generated once from the Bedrock console ("API keys" page) or via
 * `aws iam create-service-specific-credential --service-name bedrock.amazonaws.com`.
 *
 * Two key lifetimes, with a counter-intuitive recommendation from AWS itself:
 *  - **Short-term**: valid up to 12h, inherits the generating IAM principal's permissions.
 *    Recommended for production, precisely *because* it expires quickly.
 *  - **Long-term**: creates a dedicated IAM user, configurable expiration (weeks/months).
 *    Recommended **only for exploration/dev** — despite the name, it behaves like a classic
 *    static API key (can leak, doesn't rotate itself), which is exactly what AWS otherwise steers
 *    people away from. This example expects that kind, read from the [API_KEY_ENV_VAR] env var.
 *
 * Wired via the SDK's token-identity types rather than credentials: [SdkToken] is a one-method
 * interface (`token()`), so a SAM-converted lambda is enough — no builder for a concrete token
 * type ships in the SDK. [StaticTokenProvider] then wraps it into what
 * [BedrockRuntimeClientBuilder][software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClientBuilder]'s
 * `.tokenProvider(...)` expects.
 *
 * **Tested live**: a real key set as [API_KEY_ENV_VAR] authenticated successfully against
 * [demo.api.aws._01b_starter_apikey] — `.tokenProvider(...)` alone is enough for Bedrock's
 * auth-scheme resolver to pick bearer auth over SigV4, with no other config needed.
 */
fun bedrockApiKeyClient(): BedrockRuntimeClient {
    val apiKey = System.getenv(API_KEY_ENV_VAR)?.takeIf { it.isNotBlank() }
        ?: error("Environment variable '$API_KEY_ENV_VAR' is missing or blank")
    val token = SdkToken { apiKey }
    return BedrockRuntimeClient.builder()
        .tokenProvider(StaticTokenProvider.create(token))
        .build()
}

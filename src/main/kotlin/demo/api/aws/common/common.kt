package demo.api.aws.common

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient

/**
 * Common code for calling Anthropic Claude models through Amazon Bedrock from Kotlin.
 *
 * Unlike every other provider in this repo, there is no `aws.properties`: Bedrock authenticates
 * each request with **SigV4 request signing** (a per-request HMAC signature derived from an
 * access key + secret, not a single bearer-token header), so the one-property classpath-resource
 * pattern used elsewhere doesn't map to it. [DefaultCredentialsProvider] walks AWS's own
 * credential chain instead — environment variables first (`AWS_ACCESS_KEY_ID`,
 * `AWS_SECRET_ACCESS_KEY`, plus `AWS_SESSION_TOKEN` if the credentials are temporary, e.g. from
 * an assumed role), then `~/.aws/credentials`, then an EC2/ECS/Lambda instance role. The region
 * resolves the same way via `AWS_REGION` (or `~/.aws/config`) — [BedrockRuntimeClient.builder]
 * picks it up automatically, so there is nothing to wire up here, not even an `OLLAMA_URL`-style
 * constant.
 *
 * Two AWS-specific prerequisites this repo can't automate for you:
 *  - **Model access**: must be granted once per model family in the Bedrock console
 *    ("Model access" page) — a correct IAM policy alone is not enough.
 *  - **IAM policy**: the identity behind the credentials needs `bedrock:InvokeModel` /
 *    `bedrock:Converse` allowed on the model (or inference-profile) ARN used — see
 *    [demo.api.aws.common.Models].
 */
fun bedrockClient(): BedrockRuntimeClient =
    BedrockRuntimeClient.builder()
        .credentialsProvider(DefaultCredentialsProvider.builder().build())
        .build()

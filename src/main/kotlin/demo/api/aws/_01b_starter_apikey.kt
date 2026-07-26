package demo.api.aws

import demo.api.aws.common.bedrockApiKeyClient
import demo.api.aws.common.defaultModel
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole
import software.amazon.awssdk.services.bedrockruntime.model.Message

/**
 * Starter for calling Anthropic Claude through Amazon Bedrock's Converse API — the **API key**
 * auth variant (a static bearer token), contrasted with [demo.api.aws._01_starter_sigv4]'s
 * per-request SigV4 signing. Identical call, only [bedrockApiKeyClient] differs from
 * [demo.api.aws.common.bedrockClient] — see its doc for the full explanation (env var, key
 * lifetimes). **Tested live** with a real key.
 *
 * Needs `AWS_BEARER_TOKEN_BEDROCK` set to a real Bedrock API key (console: Bedrock → API keys, or
 * `aws iam create-service-specific-credential --service-name bedrock.amazonaws.com`). Same
 * one-time model access prerequisite as `_01_starter_sigv4` applies here too.
 */

fun main() {
    val client = bedrockApiKeyClient()
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

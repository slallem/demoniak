package demo.api.aws

import demo.api.aws.common.bedrockClient
import demo.api.aws.common.defaultModel
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole
import software.amazon.awssdk.services.bedrockruntime.model.Message

/**
 * Starter for calling Anthropic Claude through Amazon Bedrock's Converse API.
 *
 * Needs `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` (+ `AWS_SESSION_TOKEN` if the credentials
 * are temporary) and `AWS_REGION` set in the environment — see [demo.api.aws.common.bedrockClient].
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

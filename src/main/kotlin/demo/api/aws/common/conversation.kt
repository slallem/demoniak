package demo.api.aws.common

import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole
import software.amazon.awssdk.services.bedrockruntime.model.Message

/**
 * A remembered chat against Bedrock's Converse API: keeps the running message history and grows
 * it per turn.
 *
 * Like the direct Anthropic Messages API ([demo.api.anthropic.common.Conversation]) — and unlike
 * the Gemini SDK's built-in `Chat` session — `converse()` is stateless: there is no server-side
 * chat object, just the full message list resent on every call. Bedrock's response already comes
 * back as a [Message] with the `ASSISTANT` role, so it is appended to [history] as-is.
 */
class Conversation(
    private val client: BedrockRuntimeClient,
    private val model: String = defaultModel,
    private val maxTokens: Int = 512,
) {
    private val history = mutableListOf<Message>()

    /** Sends [question] with the full remembered history and returns the reply text. */
    fun ask(question: String): String {
        history.add(
            Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(question))
                .build()
        )

        val response = client.converse { request ->
            request.modelId(model)
                .messages(history)
                .inferenceConfig { it.maxTokens(maxTokens) }
        }

        val reply = response.output().message()
        history.add(reply)
        return reply.content().first().text()
    }
}

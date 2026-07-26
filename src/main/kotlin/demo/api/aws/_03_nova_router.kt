package demo.api.aws

import demo.api.aws.common.Models
import demo.api.aws.common.bedrockClient
import demo.api.aws.common.defaultModel
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole
import software.amazon.awssdk.services.bedrockruntime.model.Message

/**
 * Bedrock as a **multi-vendor broker**, not just "Claude via a different door": a cheap/fast
 * triage call on Amazon's own [Models.NOVA_MICRO] decides whether a question is trivial or needs
 * a stronger model, then escalates to Anthropic's [defaultModel] (Claude Haiku 4.5) only when
 * warranted. Same [bedrockClient], same `client.converse { }` call, same response shape for both
 * vendors — the only thing that changes per call is the `modelId` string.
 *
 * This is the point of a broker: without it, mixing vendors means two SDKs, two auth setups, two
 * response shapes to normalize yourself. Here it's one client, one API, a runtime decision.
 *
 * A plain side-by-side "ask both models the same question" would compile just as easily but
 * wouldn't demonstrate *why* you'd want a broker instead of calling each vendor directly — the
 * cost-cascading angle (only pay for the stronger model when the question actually needs it) is
 * what makes routing worth doing through one API surface rather than two.
 *
 * Needs both [Models.NOVA_MICRO] and [Models.CLAUDE_HAIKU_4_5] to be usable: Model access granted
 * for both the Amazon and Anthropic families in the Bedrock console (Nova skips Anthropic's extra
 * "use case details" form — that's an Anthropic-specific requirement, not a general Bedrock one),
 * plus IAM permission (`bedrock:InvokeModel`/`bedrock:Converse`) on both models' inference-profile
 * ARNs.
 *
 * **Tested live**: triage correctly classified "What is the capital of France?" as SIMPLE (handled
 * by Nova Micro) and the classic sheep-farming word problem as COMPLEX (escalated to Claude Haiku
 * 4.5, which solved it correctly — 18 sheep). Both routes taken in one run.
 */

private fun ask(client: BedrockRuntimeClient, modelId: String, prompt: String): String {
    val response = client.converse { request ->
        request.modelId(modelId)
            .messages(
                Message.builder()
                    .role(ConversationRole.USER)
                    .content(ContentBlock.fromText(prompt))
                    .build()
            )
            .inferenceConfig { it.maxTokens(256) }
    }
    return response.output().message().content().first().text()
}

/** Cheap triage call: does this question need escalation to a stronger (pricier) model? */
private fun isComplex(client: BedrockRuntimeClient, question: String): Boolean {
    val verdict = ask(
        client,
        Models.NOVA_MICRO,
        "Classify the question below as SIMPLE (a single well-known fact, answerable directly) " +
            "or COMPLEX (needs multi-step reasoning). Answer with just one word: SIMPLE or COMPLEX.\n\n" +
            "Question: $question"
    )
    return verdict.trim().uppercase().startsWith("COMPLEX")
}

/** Triages [question], then routes it to Nova Micro or escalates to Claude Haiku 4.5. */
private fun route(client: BedrockRuntimeClient, question: String) {
    println("Q: $question")

    val escalate = isComplex(client, question)
    val modelId = if (escalate) defaultModel else Models.NOVA_MICRO
    val verdict = if (escalate) "COMPLEX" else "SIMPLE"
    println("[triage] Nova Micro verdict: $verdict -> routing to $modelId")

    println("A: ${ask(client, modelId, question)}\n")
}

fun main() {
    val client = bedrockClient()
    client.use { client ->
        route(client, "What is the capital of France?")
        route(
            client,
            "A farmer has 17 sheep. All but 9 die. He then buys twice as many sheep as he has " +
                "left, and later sells a third of his flock. How many sheep does he have now?"
        )
    }
}

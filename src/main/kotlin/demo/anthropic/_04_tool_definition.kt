package org.example.demo.anthropic

import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolResultBlockParam
import org.example.demo.anthropic.common.anthropicClient
import org.example.demo.anthropic.common.defaultModel

/**
 * Extending the conversation with a custom **tool**: my own `pick_color` function.
 *
 * The model can't produce real randomness (it samples from probabilities — hence the
 * "always violet" problem in _02). So we expose a tool it can call; the actual random
 * choice is computed by our Kotlin code.
 *
 * The tool-use loop (done by hand here so it's visible):
 *   1. Send the messages + the tool definition.
 *   2. If Claude replies with a `tool_use` block, run [pickColor] and send the result
 *      back as a `tool_result`.
 *   3. Repeat until Claude stops calling tools, then print its final answer.
 */

private val RAINBOW = listOf("red", "orange", "yellow", "green", "blue", "indigo", "violet")

/** My custom function — real, uniform randomness computed in Kotlin. */
private fun pickColor(): String = RAINBOW.random()

/** Tool definition Claude sees: name, description, and (here) no inputs. */
private val pickColorTool: Tool = Tool.builder()
    .name("pick_color")
    .description("Pick a uniformly random color from the rainbow. Use it whenever a random color is needed.")
    .inputSchema(Tool.InputSchema.builder().build())
    .build()

fun main() {
    val client = anthropicClient()
    try {
        val model = defaultModel
        val messages = mutableListOf(
            MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(
                    "Use the pick_color tool to choose a random color, then name one " +
                        "fruit or vegetable of that color. Reply as '<color>: <thing>'."
                )
                .build()
        )

        while (true) {
            val response = client.messages().create(
                MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(1024L)
                    .addTool(pickColorTool)
                    .messages(messages)
                    .build()
            )

            // Preserve the assistant turn (including any tool_use blocks) in history.
            messages.add(response.toParam())

            val toolUses = response.content().mapNotNull { it.toolUse().orElse(null) }
            if (toolUses.isEmpty()) {
                // No tool call -> this is the final answer.
                response.content()
                    .mapNotNull { it.text().orElse(null) }
                    .forEach { println(it.text()) }
                break
            }

            // Run each requested tool and return the results in a single user turn.
            val results = toolUses.map { toolUse ->
                val output = if (toolUse.name() == "pick_color") pickColor() else "unknown tool"
                println("   [tool] ${toolUse.name()}() -> $output")
                ContentBlockParam.ofToolResult(
                    ToolResultBlockParam.builder()
                        .toolUseId(toolUse.id())
                        .content(output)
                        .build()
                )
            }
            messages.add(
                MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .contentOfBlockParams(results)
                    .build()
            )
        }
    } finally {
        client.close()
    }
}
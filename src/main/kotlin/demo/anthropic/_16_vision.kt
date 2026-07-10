package org.example.demo.anthropic

import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.TextBlockParam
import org.example.demo.anthropic.common.anthropicClient
import org.example.demo.anthropic.common.defaultModel
import java.awt.Color
import java.awt.Font
import java.awt.Polygon
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.stream.Collectors
import javax.imageio.ImageIO

/**
 * Demonstrating **image input / analysis** (vision).
 *
 * To keep it self-contained *and* verifiable, we don't ship an asset: we **draw a
 * known image in code** (a red circle, a blue rectangle, a green triangle, and the
 * text "221B"), send it to Claude, and check it reads the scene back correctly.
 *
 * How an image is sent:
 *   - as a base64 **`image` content block** inside a user message, placed BEFORE the
 *     text block (image first, then the question about it).
 *   - `media_type` must match the bytes (here PNG). Base64 must have no line breaks —
 *     `Base64.getEncoder()` (not `getMimeEncoder()`) is correct.
 *
 * Token cost (ties back to the pixel→token rule): image tokens ≈ **(W × H) / 750**,
 * driven by *pixels, not file bytes*; oversized images are auto-resized to the model's
 * cap first. We print the estimate next to the real `usage.input_tokens`.
 *
 * There are several important limitations to keep in mind when working with images:
 *
 * Up to 100 images across all messages in a single request
 * Max size of 5MB per image
 * When sending one image: max height/width of 8000px
 * When sending multiple images: max height/width of 2000px
 * Images can be included as base64 encoding or a URL to the image
 * Each image counts as tokens based on its dimensions: tokens = (width px × height px) / 750
 *
 * Vision works on all current models, including the repo default `claude-haiku-4-5`.
 */

private const val WIDTH = 512
private const val HEIGHT = 512

/** Draw a known scene and return it as PNG bytes. */
private fun drawSampleImage(): ByteArray {
    val img = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    g.color = Color.WHITE
    g.fillRect(0, 0, WIDTH, HEIGHT)

    g.color = Color.RED
    g.fillOval(70, 60, 150, 150)                                   // red circle (top-left)

    g.color = Color.BLUE
    g.fillRect(300, 70, 150, 110)                                  // blue rectangle (top-right)

    g.color = Color(0, 150, 0)
    g.fillPolygon(Polygon(intArrayOf(70, 220, 145), intArrayOf(440, 440, 300), 3)) // green triangle

    g.color = Color.BLACK
    g.font = Font("SansSerif", Font.BOLD, 56)
    g.drawString("221B", 300, 410)                                 // text (bottom-right)

    g.dispose()

    val out = ByteArrayOutputStream()
    ImageIO.write(img, "png", out)
    return out.toByteArray()
}

fun main() {
    System.setProperty("java.awt.headless", "true") // no display needed for Java2D

    val client = anthropicClient()
    try {
        val png = drawSampleImage()
        val base64 = Base64.getEncoder().encodeToString(png) // no line breaks

        // Save a copy so you can eyeball exactly what was sent (build/ is git-ignored).
        File("build").mkdirs()
        File("build/vision-sample.png").writeBytes(png)

        val response = client.messages().create(
            MessageCreateParams.builder()
                .model(defaultModel)
                .maxTokens(512L)
                .addUserMessageOfBlockParams(
                    listOf(
                        // Image block FIRST, then the question about it.
                        ContentBlockParam.ofImage(
                            ImageBlockParam.builder()
                                .source(
                                    Base64ImageSource.builder()
                                        .mediaType(Base64ImageSource.MediaType.IMAGE_PNG)
                                        .data(base64)
                                        .build()
                                )
                                .build()
                        ),
                        ContentBlockParam.ofText(
                            TextBlockParam.builder()
                                .text("Describe this image: list each shape with its color, and read any text or number you see.")
                                .build()
                        ),
                    )
                )
                .build()
        )

        val answer = response.content().stream()
            .flatMap { it.text().stream() }
            .map { it.text() }
            .collect(Collectors.joining())
        val usage = response.usage()
        val estimatedImageTokens = (WIDTH.toLong() * HEIGHT) / 750

        println("Sent: ${WIDTH}x${HEIGHT} px PNG (${png.size} bytes, base64 ${base64.length} chars) → build/vision-sample.png")
        println("Image tokens estimate (W×H/750): ~$estimatedImageTokens")
        println("Actual usage: input=${usage.inputTokens()} (image + prompt), output=${usage.outputTokens()}\n")
        println("Claude sees:")
        println(answer)
    } finally {
        client.close()
    }
}

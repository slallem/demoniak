package demo.api.anthropic

import com.anthropic.models.messages.Base64PdfSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.DocumentBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.TextBlockParam
import demo.api.anthropic.common.anthropicClient
import demo.api.anthropic.common.defaultModel
import java.io.File
import java.util.Base64
import java.util.stream.Collectors

/**
 * **PDF extraction** — pull a *subset* of data from a PDF, not a full transcription.
 *
 * Where [_22_ocr] transcribed a whole image, this reads a PDF catalogue
 * (`assets/images/electrovalue1973.pdf`) and extracts only what we ask for: the list of
 * potentiometer types and their prices, returned as a clean Markdown table. Everything
 * else in the document is ignored.
 *
 * Two things distinguish this from the image examples:
 *   - PDFs go in a **`document`** content block (not `image`): base64 with media type
 *     `application/pdf`, placed before the instruction text. No beta header needed.
 *   - The API renders each PDF page for the vision model AND extracts its text layer, so
 *     you get OCR + digital text together — good for structured extraction like this.
 *
 * Targeted extraction (vs. full OCR) is driven entirely by the **system prompt**: define
 * the exact columns, tell it to keep prices verbatim, and to skip non-matching products.
 *
 * Limits (plenty of headroom here — the file is ~100 KB): 32 MB per request, and 100
 * pages on 200k-context models like the repo default `claude-haiku-4-5`.
 */

private const val ASSET = "assets/pdf/electrovalue1973.pdf"

private const val EXTRACT_SYSTEM = """
You extract structured data from a product catalogue PDF. Be careful about omega signs (ohm symbols).
Find every POTENTIOMETER offered and its price. Output ONLY a Markdown table with exactly
these columns: | Type | Available values | Price |. Keep the type designation and the price verbatim as printed
(including units, ranges, or currency). Do not add, infer, translate, or comment.
Ignore every product that is not a potentiometer. If no price is shown for a type, put "-".
"""

fun main() {
    val client = anthropicClient()
    try {
        val file = File(ASSET)
        require(file.exists()) { "Asset not found: ${file.absolutePath} — run from the project root." }

        val bytes = file.readBytes()
        val base64 = Base64.getEncoder().encodeToString(bytes) // no line breaks

        val response = client.messages().create(
            MessageCreateParams.builder()
                .model(defaultModel)
                .maxTokens(2048L)
                .system(EXTRACT_SYSTEM.trim())
                .addUserMessageOfBlockParams(
                    listOf(
                        // PDF document block FIRST, then the instruction.
                        ContentBlockParam.ofDocument(
                            DocumentBlockParam.builder()
                                .source(Base64PdfSource.builder().data(base64).build()) // media type application/pdf is implied
                                .build()
                        ),
                        ContentBlockParam.ofText(
                            TextBlockParam.builder()
                                .text("List the available potentiometer types and their prices as a Markdown table.")
                                .build()
                        ),
                    )
                )
                .build()
        )

        val table = response.content().stream()
            .flatMap { it.text().stream() }
            .map { it.text() }
            .collect(Collectors.joining())
        val usage = response.usage()

        println("PDF: ${file.name}  (${bytes.size} bytes, base64 ${base64.length} chars)")
        println("Usage: input=${usage.inputTokens()}, output=${usage.outputTokens()}\n")
        println("── Potentiometers ──")
        println(table.trim())
    } finally {
        client.close()
    }
}

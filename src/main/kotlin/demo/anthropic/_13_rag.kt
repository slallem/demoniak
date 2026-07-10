package org.example.demo.anthropic

import com.anthropic.client.AnthropicClient
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import org.example.demo.anthropic.common.anthropicClient
import org.example.demo.anthropic.common.defaultModel
import java.io.File
import java.util.stream.Collectors
import kotlin.math.ln

/**
 * **RAG** (Retrieval-Augmented Generation) over the Sherlock Holmes corpus in
 * `assets/books/`. The flow is the classic one:
 *
 *   question → retrieve the most relevant passages → put them in the prompt →
 *   let Claude answer *from that context only* (and cite the source story).
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY NO VECTOR EMBEDDINGS HERE (important):
 *
 * "Real" RAG usually retrieves with **embeddings** (encode text into vectors,
 * rank by cosine similarity). But **Anthropic has no embeddings endpoint** — so
 * an embeddings step would force a *second* provider and API key, and this repo
 * keeps the Anthropic examples dependent on the Anthropic key alone.
 *
 * So retrieval here is **pure-Kotlin BM25** (a lexical ranking function): no extra
 * key, no dependency, no network call. It works well on this corpus because the
 * questions share rare terms (character names, objects) with the source text.
 * The RAG *mechanics* are identical either way — only the scoring function differs.
 *
 * To upgrade to semantic embeddings later, plug in a provider (each needs its own
 * key), keeping everything else the same:
 *   - Voyage AI (Anthropic's recommended pick): https://docs.voyageai.com/docs/embeddings
 *   - OpenAI:  https://platform.openai.com/docs/guides/embeddings
 *   - Google (Gemini): https://ai.google.dev/gemini-api/docs/embeddings
 *   - Cohere:  https://docs.cohere.com/docs/embeddings
 *   - or a local model (e.g. sentence-transformers via ONNX) for zero external calls.
 * ─────────────────────────────────────────────────────────────────────────────
 */

private const val DOCS_DIR = "assets/books"
private const val TOP_K = 3

// ---- Tokenization (shared by the index and the query) ----

private val WORD = Regex("[\\p{L}\\p{N}]+")
private val STOPWORDS = setOf(
    "the", "a", "an", "and", "or", "of", "to", "in", "on", "at", "for", "with", "as", "by",
    "is", "was", "were", "be", "been", "it", "its", "he", "she", "his", "her", "him", "they",
    "that", "this", "which", "who", "whom", "what", "there", "then", "from", "had", "have", "has",
    "i", "you", "we", "my", "me", "not", "but", "so", "if", "no", "do", "did", "would", "could",
)

private fun tokenize(text: String): List<String> =
    WORD.findAll(text.lowercase())
        .map { it.value }
        .filter { it.length > 1 && it !in STOPWORDS }
        .toList()

// ---- Corpus: a passage plus where it came from ----

private data class Chunk(val source: String, val title: String, val text: String, val tokens: List<String>)

/** Load every story file and split it into ~[targetWords]-word passages (chunking). */
private fun loadChunks(dir: File, targetWords: Int = 220): List<Chunk> {
    val files = dir.listFiles { f -> f.isFile && f.extension == "md" && f.name != "SOURCE.md" }
        ?.sortedBy { it.name }
        ?: error("No documents found in '${dir.path}'. Run this from the project root.")

    val chunks = mutableListOf<Chunk>()
    for (file in files) {
        val text = file.readText()
        val title = Regex("^# (.+)$", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)?.trim()
            ?: file.nameWithoutExtension

        val paragraphs = text.split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.startsWith("#") }       // drop the "# Title" heading
            .filterNot { it.startsWith("*From") }   // drop the provenance note

        // Greedily pack whole paragraphs into chunks near the target size.
        val buf = StringBuilder()
        var words = 0
        fun flush() {
            if (buf.isNotBlank()) {
                val t = buf.toString().trim()
                chunks += Chunk(file.name, title, t, tokenize(t))
            }
            buf.setLength(0); words = 0
        }
        for (p in paragraphs) {
            val w = p.split(Regex("\\s+")).size
            if (words > 0 && words + w > targetWords) flush()
            buf.append(p).append("\n\n")
            words += w
        }
        flush()
    }
    return chunks
}

// ---- BM25: lexical retrieval, no embeddings, no external calls ----

private class Bm25(private val chunks: List<Chunk>, private val k1: Double = 1.5, private val b: Double = 0.75) {
    private val tf = chunks.map { it.tokens.groupingBy { t -> t }.eachCount() }
    private val len = chunks.map { it.tokens.size }
    private val avgLen = len.average()
    private val n = chunks.size
    private val df = HashMap<String, Int>().apply {
        for (counts in tf) for (term in counts.keys) merge(term, 1, Int::plus)
    }

    private fun idf(term: String): Double {
        val d = df[term] ?: return 0.0
        return ln(1.0 + (n - d + 0.5) / (d + 0.5))
    }

    /** Return the [topK] highest-scoring chunks for [query]. */
    fun search(query: String, topK: Int): List<Pair<Chunk, Double>> {
        val q = tokenize(query)
        return chunks.indices.map { i ->
            val counts = tf[i]
            val dl = len[i]
            var score = 0.0
            for (term in q) {
                val f = counts[term] ?: continue
                score += idf(term) * (f * (k1 + 1)) / (f + k1 * (1 - b + b * dl / avgLen))
            }
            chunks[i] to score
        }.filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(topK)
    }
}

// ---- Generation: answer strictly from the retrieved context ----
//
// HOW CLAUDE "DIGESTS" THE DOCUMENTS — three things worth knowing:
//
// 1. STATELESS. Claude does not ingest, index, or remember the corpus. The
//    retrieved passages are just TEXT placed in the prompt for ONE request; the
//    model reads them as ordinary input tokens, answers, and forgets. Nothing is
//    stored server-side (the Files API is only storage — content is still re-sent).
//
// 2. INPUT TOKENS = system prompt + <context> (the top-K chunks) + the question.
//    You pay for the retrieved chunks only — NOT the whole corpus. That is the
//    entire economic point of RAG: here ~1k input tokens/question instead of the
//    ~140k it would take to stuff all 12 stories in. usage() below shows the real count.
//
// 3. WHERE THE WORK HAPPENS:
//      - load + chunk + tokenize + BM25 + retrieve → CLIENT-SIDE (your JVM, no API
//        call, no tokens billed). Anthropic never sees the full corpus.
//      - generating the answer                     → SERVER-SIDE (Anthropic runs the
//        model over the context you sent; bills input + output tokens).

private fun textOf(response: Message): String =
    response.content().stream()
        .flatMap { it.text().stream() }
        .map { it.text() }
        .collect(Collectors.joining())

private fun answer(client: AnthropicClient, question: String, hits: List<Pair<Chunk, Double>>): String {
    val context = hits.withIndex().joinToString("\n\n") { (i, hit) ->
        "[${i + 1}] Source: “${hit.first.title}”\n${hit.first.text}"
    }
    val system = """
        You answer questions about Sherlock Holmes stories.
        Use ONLY the excerpts inside <context> to answer — treat them as your only knowledge.
        Cite the story title(s) you relied on. If the answer is not in the context, say you don't know.
        Keep the answer to one or two sentences.
    """.trimIndent()
    val user = "<context>\n$context\n</context>\n\nQuestion: $question"

    val response = client.messages().create(
        MessageCreateParams.builder()
            .model(defaultModel)
            .maxTokens(512L)
            .system(system)
            .addUserMessage(user)
            .build()
    )

    // What Anthropic actually billed for this one request: only system + the
    // retrieved chunks + the question (input), plus the answer (output).
    val usage = response.usage()
    println("   ↳ tokens: input=${usage.inputTokens()}, output=${usage.outputTokens()}")

    return textOf(response).trim()
}

fun main() {
    val client = anthropicClient()
    try {
        val chunks = loadChunks(File(DOCS_DIR))
        val index = Bm25(chunks)
        println("Indexed ${chunks.size} passages from ${chunks.map { it.source }.distinct().size} stories.\n")

        val questions = listOf(
            "Who is 'the woman' that Sherlock Holmes refers to in A Scandal in Bohemia?",
            "What was hidden inside the Christmas goose in the Blue Carbuncle?",
            "What turned out to be the 'speckled band'?",
            "What colour is Doctor Watson's car?", // not in the corpus → should answer "I don't know"
            "Quel est le lien de parenté entre John et Elias Openshaw ?", // question in French, as expected response
        )

        for (q in questions) {
            val hits = index.search(q, TOP_K)
            println("Q: $q")
            hits.forEach { (chunk, score) -> println("   ↳ retrieved: ${chunk.title}  (score %.2f)".format(score)) }
            println("A: ${answer(client, q, hits)}\n")
        }
    } finally {
        client.close()
    }
}

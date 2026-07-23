package demo.api.mistral

import com.openai.client.OpenAIClient
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.embeddings.EmbeddingCreateParams
import demo.api.mistral.common.Models
import demo.api.mistral.common.defaultModel
import demo.api.mistral.common.mistralClient
import java.io.File
import kotlin.jvm.optionals.getOrNull
import kotlin.math.sqrt
import kotlin.time.measureTimedValue

/**
 * **Embeddings + semantic search** — real vector retrieval, end to end, no lexical fallback:
 * every passage and every question is embedded with Mistral's `mistral-embed` (via
 * `/v1/embeddings` — OpenAI-shaped enough that `mistralClient().embeddings()` just works), and
 * chunks are ranked purely by cosine similarity between those vectors.
 *
 * It runs over the same Sherlock Holmes corpus, the same chunking, and the same five questions as
 * Anthropic's `_13_rag` — on purpose, so the two are directly comparable. `_13_rag` scores chunks
 * with BM25 (lexical overlap) instead of embeddings only because **Anthropic itself has no
 * embeddings endpoint**; its own doc comment names Mistral as one of the providers you'd plug in
 * for "real" semantic retrieval. This is that upgrade, actually wired up.
 *
 * The concrete difference in quality shows up on the corpus's one French question: BM25 tokenizes
 * on words, so a French query shares essentially no tokens with the English source text (aside
 * from proper nouns like "Openshaw") — embeddings instead compare *meaning*, and `mistral-embed`
 * is multilingual, so it retrieves the right English passage from a French query with no shared
 * vocabulary at all.
 *
 * The price of that: BM25 indexes ~500 chunks **instantly, for free, entirely client-side**, with
 * no API involved. Embedding the same chunks here means real network calls and real billed
 * tokens — this example times the indexing pass and prints the token cost, so the trade-off isn't
 * just asserted, it's measured.
 *
 * Batching detail: Mistral caps embeddings requests at 128 inputs *and* ~16,384 tokens per
 * request — a ~220-word chunk is too big to fit 128-to-a-batch, so chunks go in groups of
 * [EMBED_BATCH_SIZE] instead.
 */

private const val DOCS_DIR = "assets/books"
private const val TOP_K = 3
private const val EMBED_BATCH_SIZE = 32

// ---- Corpus: a passage plus where it came from (no tokenization needed — embeddings replace it) ----

private data class Chunk(val source: String, val title: String, val text: String)

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
            if (buf.isNotBlank()) chunks += Chunk(file.name, title, buf.toString().trim())
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

// ---- Embeddings: real API calls, batched to stay under Mistral's per-request limits ----

private fun cosineSimilarity(a: List<Float>, b: List<Float>): Double {
    var dot = 0.0; var normA = 0.0; var normB = 0.0
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    return dot / (sqrt(normA) * sqrt(normB))
}

/** Embeds [texts] in batches of [EMBED_BATCH_SIZE], returning one vector per text and the total billed tokens. */
private fun embedAll(client: OpenAIClient, texts: List<String>): Pair<List<List<Float>>, Long> {
    val vectors = mutableListOf<List<Float>>()
    var totalTokens = 0L
    for (batch in texts.chunked(EMBED_BATCH_SIZE)) {
        val response = client.embeddings().create(
            EmbeddingCreateParams.builder()
                .model(Models.MISTRAL_EMBED)
                .inputOfArrayOfStrings(batch)
                .build()
        )
        vectors += response.data().sortedBy { it.index() }.map { it.embedding() }
        totalTokens += response.usage().totalTokens()
    }
    return vectors to totalTokens
}

// ---- Generation: answer strictly from the retrieved context (same shape as _13's `answer`) ----

private fun answer(client: OpenAIClient, question: String, hits: List<Pair<Chunk, Double>>): String {
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

    val params = ChatCompletionCreateParams.builder()
        .model(defaultModel)
        .maxTokens(512L) // Mistral rejects `max_completion_tokens`; only `max_tokens` works
        .addSystemMessage(system)
        .addUserMessage(user)
        .build()

    val completion = client.chat().completions().create(params)
    val usage = completion.usage().getOrNull()
    println("   ↳ tokens: prompt=${usage?.promptTokens()}, completion=${usage?.completionTokens()}")

    return completion.choices().first().message().content().getOrNull()?.trim().orEmpty()
}

fun main() {
    val client = mistralClient()
    try {
        val chunks = loadChunks(File(DOCS_DIR))
        val batches = (chunks.size + EMBED_BATCH_SIZE - 1) / EMBED_BATCH_SIZE
        println("Loaded ${chunks.size} passages from ${chunks.map { it.source }.distinct().size} stories. Embedding them now ($batches batched API calls)...")

        val timedIndex = measureTimedValue { embedAll(client, chunks.map { it.text }) }
        val (chunkVectors, indexTokens) = timedIndex.value
        println(
            "Indexed ${chunks.size} passages in ${timedIndex.duration} — $indexTokens tokens billed. " +
                "(BM25 in _13_rag indexes the same corpus instantly, for free, with no network call.)\n"
        )

        val questions = listOf(
            "Who is 'the woman' that Sherlock Holmes refers to in A Scandal in Bohemia?",
            "What was hidden inside the Christmas goose in the Blue Carbuncle?",
            "What turned out to be the 'speckled band'?",
            "What colour is Doctor Watson's car?", // not in the corpus → should answer "I don't know"
            "Quel est le lien de parenté entre John et Elias Openshaw ?", // French — the interesting one, see doc comment
        )

        for (q in questions) {
            val (queryVectors, _) = embedAll(client, listOf(q))
            val queryVector = queryVectors.first()
            val hits = chunks.indices
                .map { i -> chunks[i] to cosineSimilarity(queryVector, chunkVectors[i]) }
                .sortedByDescending { it.second }
                .take(TOP_K)

            println("Q: $q")
            hits.forEach { (chunk, score) -> println("   ↳ retrieved: ${chunk.title}  (cosine %.3f)".format(score)) }
            println("A: ${answer(client, q, hits)}\n")
        }
    } finally {
        client.close()
    }
}

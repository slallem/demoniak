package demo.api.ollama

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import demo.api.ollama.common.OLLAMA_URL
import demo.api.ollama.common.defaultModel
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.math.sqrt
import kotlin.time.measureTimedValue

/**
 * Embeddings + semantic search against Ollama's **native** `/api/embed`, in pure HTTP — same
 * "no SDK" style as `_02_chat_http`/`_03_tool_calling`/`_04_structured_outputs`.
 *
 * Same corpus, same chunking, and the same five questions as Anthropic's `_13_rag` (BM25) and
 * Mistral's `_08_embeddings` (cloud embeddings) — directly comparable across all three. This is
 * the "everything local" version: `nomic-embed-text` (274MB, `ollama pull nomic-embed-text`) runs
 * entirely on your machine, so indexing the whole corpus costs zero dollars and touches the
 * network not at all — the trade-off Mistral's `_08` measures in billed tokens shows up here as
 * pure wall-clock time instead.
 *
 * Two shape differences from Mistral's embeddings endpoint, worth noting:
 *  - The response field is `embeddings` (plural) — a straight array of vectors in the same order
 *    as the input array, no per-item `index` to re-sort by like Mistral's response.
 *  - No documented per-request cap like Mistral's 128-inputs/16,384-tokens. [EMBED_BATCH_SIZE]
 *    here is just for readable progress output and to keep individual requests small, not a
 *    limit this endpoint actually enforces.
 *
 * The answer-generation step reuses [defaultModel] (`qwen3.5:2b`) with `think: false` — same
 * reasoning as `_02_chat_http`: reasoning left on turns a quick lookup-and-answer into a much
 * slower round trip for no benefit here.
 *
 * **Tested end to end against the real corpus**: retrieval itself is reliably correct across all
 * five questions — right story every time, correctly cross-lingual on the French question (same
 * result Mistral's `_08` gets, for the same reason: embeddings compare meaning, not vocabulary),
 * and a correct "I don't know" on the trick question outside the corpus. The generation step is
 * the weaker link: on this run, `qwen3.5:2b` garbled two of the five answers (mixing up details
 * within a *correctly retrieved* passage) despite having the right context in front of it —
 * retrieval quality and generation quality are separate failure modes, and a small local model can
 * nail the first while stumbling on the second. Swapping the answer step to `gemma4:12b` (the
 * fix `_04_structured_outputs` reaches for) would likely help, at the usual cost of ~30-50s
 * instead of a few seconds per answer.
 */

private const val DOCS_DIR = "assets/books"
private const val TOP_K = 3
private const val EMBED_BATCH_SIZE = 32
private const val EMBED_MODEL = "nomic-embed-text"

private val mapper = ObjectMapper()
private val http = HttpClient.newHttpClient()

// ---- Corpus: a passage plus where it came from (same chunking as _13 Anthropic / _08 Mistral) ----

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

// ---- Embeddings: local calls to /api/embed, batched just for readable progress ----

private fun cosineSimilarity(a: List<Double>, b: List<Double>): Double {
    var dot = 0.0; var normA = 0.0; var normB = 0.0
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    return dot / (sqrt(normA) * sqrt(normB))
}

/** Embeds [texts] in batches of [EMBED_BATCH_SIZE], returning one vector per text, in order. */
private fun embedAll(texts: List<String>): List<List<Double>> {
    val vectors = mutableListOf<List<Double>>()
    for (batch in texts.chunked(EMBED_BATCH_SIZE)) {
        val body = mapper.createObjectNode().apply {
            put("model", EMBED_MODEL)
            set<JsonNode>("input", mapper.valueToTree(batch))
        }

        val request = HttpRequest.newBuilder(URI.create("$OLLAMA_URL/api/embed"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) {
            "Ollama returned ${response.statusCode()}: ${response.body()}"
        }

        mapper.readTree(response.body()).path("embeddings")
            .forEach { vector -> vectors += vector.map { it.asDouble() } }
    }
    return vectors
}

// ---- Generation: answer strictly from the retrieved context (same shape as _08's `answer`) ----

private fun answer(question: String, hits: List<Pair<Chunk, Double>>): String {
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

    val body = mapper.createObjectNode().apply {
        put("model", defaultModel)
        set<JsonNode>(
            "messages",
            mapper.valueToTree(
                listOf(
                    mapOf("role" to "system", "content" to system),
                    mapOf("role" to "user", "content" to user),
                )
            )
        )
        put("stream", false)
        put("think", false) // a quick retrieve-and-answer doesn't need reasoning left on
    }

    val request = HttpRequest.newBuilder(URI.create("$OLLAMA_URL/api/chat"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
        .build()

    val response = http.send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) {
        "Ollama returned ${response.statusCode()}: ${response.body()}"
    }
    return mapper.readTree(response.body()).path("message").path("content").asText().trim()
}

fun main() {
    val chunks = loadChunks(File(DOCS_DIR))
    val batches = (chunks.size + EMBED_BATCH_SIZE - 1) / EMBED_BATCH_SIZE
    println(
        "Loaded ${chunks.size} passages from ${chunks.map { it.source }.distinct().size} stories. " +
            "Embedding them locally now ($batches batched calls to $EMBED_MODEL)..."
    )

    val timedIndex = measureTimedValue { embedAll(chunks.map { it.text }) }
    val chunkVectors = timedIndex.value
    println(
        "Indexed ${chunks.size} passages in ${timedIndex.duration} — zero cost, zero network, " +
            "entirely local. (Mistral's _08_embeddings measures the same step in billed tokens instead.)\n"
    )

    val questions = listOf(
        "Who is 'the woman' that Sherlock Holmes refers to in A Scandal in Bohemia?",
        "What was hidden inside the Christmas goose in the Blue Carbuncle?",
        "What turned out to be the 'speckled band'?",
        "What colour is Doctor Watson's car?", // not in the corpus → should answer "I don't know"
        "Quel est le lien de parenté entre John et Elias Openshaw ?", // French — see _08's doc comment
    )

    for (q in questions) {
        val queryVector = embedAll(listOf(q)).first()
        val hits = chunks.indices
            .map { i -> chunks[i] to cosineSimilarity(queryVector, chunkVectors[i]) }
            .sortedByDescending { it.second }
            .take(TOP_K)

        println("Q: $q")
        hits.forEach { (chunk, score) -> println("   ↳ retrieved: ${chunk.title}  (cosine %.3f)".format(score)) }
        println("A: ${answer(q, hits)}\n")
    }
}

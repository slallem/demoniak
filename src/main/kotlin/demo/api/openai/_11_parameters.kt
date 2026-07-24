package demo.api.openai

import com.openai.client.OpenAIClient
import com.openai.models.chat.completions.ChatCompletion
import com.openai.models.chat.completions.ChatCompletionCreateParams
import demo.api.openai.common.defaultModel
import demo.api.openai.common.openaiClient
import kotlin.jvm.optionals.getOrNull
import kotlin.math.exp
import java.util.Locale

/**
 * The common sampling/control **parameters** every OpenAI chat completion accepts, demonstrated
 * one at a time rather than buried inside another example: `temperature`, `topP`, `seed`, `n`,
 * `stop`, and `logprobs`. None of these are provider-specific tricks — Anthropic's `_06` shows
 * `temperature` alone (and only on pre-4.7 models, per that file's header) — this is the fuller
 * picture, in one place, of the knobs that shape *how* a model samples rather than *what* it can
 * do.
 *
 * - **temperature** and **topP** both control randomness, but differently: temperature rescales
 *   the whole probability distribution before sampling (0 ≈ always the top token; higher flattens
 *   it toward uniform), while topP (nucleus sampling) instead keeps only the smallest set of
 *   top tokens whose probabilities sum to `topP` and samples among just those, regardless of
 *   temperature. OpenAI's own docs recommend altering one or the other, not both at once — mixing
 *   them makes the combined effect hard to reason about.
 * - **seed** asks for *best-effort* reproducibility, not a guarantee: same `seed` + same params
 *   + same prompt tends to return the same output, but OpenAI explicitly does not promise
 *   determinism across model updates — `systemFingerprint` on the response is the signal to watch
 *   (compare it across calls; a changed backend shows up there before it shows up in the text).
 *   Tested here: same seed, same prompt, two calls — identical text *and* identical fingerprint.
 *   Both `seed` and `systemFingerprint` are marked `@Deprecated` in the current SDK (no
 *   replacement documented, still fully functional in this test) — a beta feature OpenAI seems to
 *   be quietly stepping back from rather than one this repo got wrong.
 * - **n** returns multiple independent completions from *one* request — cheaper and faster than
 *   making the same call `n` times, since the prompt is only processed once.
 * - **stop** cuts generation the moment one of up to 4 given strings would appear, and the stop
 *   string itself is *not* included in the output.
 * - **logprobs** (+`topLogprobs`) exposes the model's actual per-token confidence — the log
 *   probability of every token it emitted, plus its top-N alternatives at that position. `exp()`
 *   turns a logprob back into a 0-1 probability for a human-readable number.
 */

private fun ask(
    client: OpenAIClient,
    prompt: String,
    temperature: Double? = null,
    topP: Double? = null,
    seed: Long? = null,
    n: Long? = null,
    stop: String? = null,
    logprobs: Boolean = false,
): ChatCompletion {
    val builder = ChatCompletionCreateParams.builder()
        .model(defaultModel)
        .maxCompletionTokens(64L)
        .addUserMessage(prompt)
    temperature?.let { builder.temperature(it) }
    topP?.let { builder.topP(it) }
    seed?.let { builder.seed(it) }
    n?.let { builder.n(it) }
    stop?.let { builder.stop(it) }
    if (logprobs) builder.logprobs(true).topLogprobs(3L)
    return client.chat().completions().create(builder.build())
}

private fun text(completion: ChatCompletion): String =
    completion.choices().first().message().content().getOrNull()?.trim().orEmpty()

fun main() {
    val client = openaiClient()
    try {
        val colorPrompt = "Name one rainbow color. Reply with just the color name, nothing else."

        println("── temperature: 0 (near-deterministic) vs 1.8 (highly random) ──")
        println("  temperature=0:")
        repeat(3) { println("    -> ${text(ask(client, colorPrompt, temperature = 0.0))}") }
        println("  temperature=1.8:")
        repeat(3) { println("    -> ${text(ask(client, colorPrompt, temperature = 1.8))}") }

        println("\n── topP: 0.05 (narrow nucleus, ignores temperature's spread) ──")
        repeat(3) { println("    -> ${text(ask(client, colorPrompt, temperature = 1.8, topP = 0.05))}") }

        println("\n── seed: best-effort determinism, watch systemFingerprint ──")
        val seedPrompt = "Write a six-word story about a lighthouse."
        val run1 = ask(client, seedPrompt, temperature = 1.0, seed = 42L)
        val run2 = ask(client, seedPrompt, temperature = 1.0, seed = 42L)
        println("  run 1: ${text(run1)}  (fingerprint=${run1.systemFingerprint().getOrNull()})")
        println("  run 2: ${text(run2)}  (fingerprint=${run2.systemFingerprint().getOrNull()})")
        println("  identical output: ${text(run1) == text(run2)}")

        println("\n── n: 3 completions from a single request ──")
        val multi = ask(client, "Suggest a name for a pet cat. Reply with just the name.", temperature = 1.2, n = 3L)
        multi.choices().forEach { choice -> println("  [${choice.index()}] ${choice.message().content().getOrNull()?.trim()}") }

        println("\n── stop: cut off before a given string ──")
        // Careful: stop is case-sensitive — "May" stops generation, "may" wouldn't.
        val counted = ask(client, "Enumerate the 12 months from January to December, one word per line.", temperature = 0.0, stop = "May")
        println(text(counted).prependIndent("  "))

        println("\n── logprobs: per-token confidence + top alternatives ──")
        val confident = ask(client, "What is the capital of France? Reply with just the city name.", temperature = 0.0, logprobs = true)
        val tokenLogprobs = confident.choices().first().logprobs().getOrNull()?.content()?.getOrNull().orEmpty()
        tokenLogprobs.forEach { tlp ->
            val probability = exp(tlp.logprob()) * 100
            val alternatives = tlp.topLogprobs().joinToString(", ") {
                "'${it.token()}' ${String.format(Locale.ROOT, "%.1f", exp(it.logprob()) * 100)}%"
            }
            println("  token='${tlp.token()}'  p=${String.format(Locale.ROOT, "%.1f", probability)}%   top alternatives: $alternatives")
        }
    } finally {
        client.close()
    }
}

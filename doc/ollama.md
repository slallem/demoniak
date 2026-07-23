# Ollama: running LLMs locally

## What Ollama is

Ollama is a thin daemon (`ollama serve`) plus a CLI (`ollama pull/run/list/ps/rm/show/create`)
wrapped around [llama.cpp](https://github.com/ggml-org/llama.cpp). It downloads models in the
quantized **GGUF** format and serves them over HTTP on `localhost:11434`. The practical
consequences for this repo's examples:

- **No API key, no `.properties` file.** There is nothing to authenticate — see
  `demo.api.ollama.common.common.kt`. The only configurable knob is the server URL
  (`-Dollama.url=...`, defaulting to `http://localhost:11434`).
- **No per-token cost, no network round-trip** — latency is purely a function of your own
  CPU/GPU and the model size, not a billed API call.
- **Quantization, not raw parameter count, drives memory.** A "12B" model at the common
  `Q4_K_M` quantization needs roughly 4-5 bits per parameter rather than the 16-32 bits of
  unquantized weights — a 12B model fits in ~7-8GB rather than ~24-48GB. This is why the "size"
  column below is a quantized download size, not `parameters × 4 bytes`.
- **GPU is optional.** Ollama uses Metal on Apple Silicon, CUDA on Nvidia, ROCm on AMD, and falls
  back to CPU. Everything in this repo's Ollama examples was run against a laptop, no dedicated
  GPU required — just patience for the bigger models (see the [Local models](#local-models)
  section).
- **The model catalogue is *your* catalogue.** Unlike a cloud provider's fixed model list,
  `demo.api.ollama.common.Models` is "whatever you've pulled" — `ollama list` is the source of
  truth, `ollama pull <tag>` adds to it. There is no API to discover what Ollama's library
  *offers*; that's a browse-the-website thing (`ollama.com/library`).

## Two API surfaces

Every Ollama example in this repo goes through one of two genuinely different HTTP surfaces the
same running server exposes side by side.

### 1. Native API (`/api/...`)

Ollama's own protocol: `/api/chat`, `/api/generate`, `/api/embed`, plus management endpoints
(`/api/tags` lists installed models, `/api/ps` lists what's currently loaded in memory,
`/api/pull`/`/api/create` manage models by code instead of CLI). This is where Ollama-specific
features live, with no equivalent in the OpenAI schema:

- `think: true/false` (or a level like `"low"`/`"high"`) — many recent models (the Qwen3.x family
  in particular) reason by default; this is the only way to turn it off, and turning it off can
  be the difference between a ~1.5s reply and one that never returns (see `_02_chat_http` and the
  caveat in `common/models.kt`).
- `keep_alive` — how long the weights stay resident in memory after a call. The first call after
  idle pays the full model-load cost (tens of seconds for a 12B model); a longer `keep_alive`
  keeps subsequent turns fast. `_07_introspection` measures this cost directly rather than just
  asserting it: `load_duration`, `prompt_eval_count`/`_duration`, and `eval_count`/`_duration` ride
  along on every native response (nanoseconds), enough to compute real local tokens/sec — a figure
  that simply doesn't exist for a hosted API, since your machine never did the work. `/api/ps`
  (list what's currently resident in memory) is the other native-only introspection point that
  file uses; neither has a hosted-API equivalent.
- `format` — a full JSON Schema (or the literal string `"json"`) to grammar-constrain decoding,
  Ollama's structured-outputs mechanism (`_04_structured_outputs`). Constrains *syntax*, not
  *content* — see that file's header for a real example of a small model producing
  schema-valid-shaped but semantically garbled output.
- `tools` — native tool calling (`_03_tool_calling`). Two concrete shape differences from OpenAI's
  tool calling: `function.arguments` comes back as an **already-parsed JSON object**, not a string
  to decode yourself; and there is **no per-call id** — the tool result is just a `role: "tool"`
  message with `tool_name` + `content`, matched by order rather than an id.
  ​Support is per-model (and per chat template): a model that doesn't support tools just answers
  in plain text instead of erroring, so check first with `ollama show <model>` (the
  `Capabilities` block) or the `capabilities` array in `/api/tags`.
- Native NDJSON streaming (`stream: true` is the default unless set to `false` explicitly).

There is no client library for this route in this repo — it's plain `java.net.http.HttpClient` +
Jackson, the same "no SDK" style used for provider-only endpoints elsewhere (e.g. Mistral's
`/v1/ocr` in `_04_ocr`).

### 2. OpenAI-compatible endpoint (`/v1/...`)

Ollama also re-implements a subset of the **OpenAI Chat Completions API** at `/v1/chat/completions`
(plus `/v1/models`, `/v1/embeddings`). This means any existing OpenAI SDK or tool — including the
`openai-java` SDK already in this project — talks to a local Ollama server with nothing but a
different `baseUrl` and a throwaway API key string (the SDK requires one; Ollama ignores it). See
`demo.api.ollama.common.ollamaClient()`, `_01_starter`, and `_02_chat_openai`.

The tradeoff: convenience over reach. Ollama-only knobs like `keep_alive` or native NDJSON simply
aren't in OpenAI's schema, so they're unreachable from this route — and some ideas have to be
smuggled through an OpenAI-shaped field that means something slightly different underneath. The
one example of that in this repo: `reasoningEffort(NONE)` on the OpenAI SDK builder is read by
Ollama as `think: false` — same effect as the native route's own field, reached through a
borrowed door.

## Local models

"Most usual" here means the model families that dominate Ollama's pull counts and this repo's own
testing — not an exhaustive catalogue (`ollama.com/library` is that). Sizes below are the
`Q4_K_M`-class quantized download size, the pragmatic default for most local use; RAM/VRAM is a
rough figure for that same quantization, not the unquantized model.

| Family | Typical sizes | Quantized size | RAM/VRAM | Features | Limits |
|---|---|---|---|---|---|
| **llama3.1** | 8b, 70b, 405b | ~4.9GB–230GB | ~6GB–230GB | 128K context, tool calling, the long-time default baseline | 405b is not realistically local hardware |
| **llama3.2** | 1b, 3b | ~1.3–2.0GB | ~2–4GB | Small/edge-oriented, tool routing, 128K context | Noticeably weaker reasoning than the 8B+ tier |
| **gemma3** | 270m–27b | varies by size | ~1GB–20GB | 140+ languages, multimodal from 4b up | Below 4b, text-only |
| **gemma4** | 12b, 26b, 31b | ~7.6–20GB | ~12–24GB | Multimodal at every size, vision + tool calling, 256K context | 12b already needs real patience on CPU-only (~30-50s/turn observed in this repo's testing) |
| **qwen2.5** | 0.5b–72b | varies by size | ~1GB–40GB | Strong multilingual; a `-coder` variant exists for code (FIM, 32K context) | Reasoning gets noticeably weaker under ~32B |
| **qwen3.5** | 0.8b–122b | varies by size | ~1GB–70GB | Multimodal at every size, tool calling, extended "thinking", up to 256K context | **Thinking is on by default** — on a small model this can make a one-word answer never return (see `common/models.kt`) |
| **qwen3-coder** | 30b, 480b | ~19GB–275GB | 24GB+ | MoE (~3B active out of 30B), strong local coding | Needs real VRAM to be fast; CPU-only is rough |
| **mistral** | 7b (base), plus `-nemo`/`-small` variants | ~4.4GB–14GB | ~6GB–16GB+ | Native function calling on the `-small` variants, JSON output, agentic-leaning | Smaller context window than the Gemma/Qwen frontier unless on `-nemo` (128K) |
| **deepseek-r1** | 1.5b–671b (≤70b are *distillations*, not the full model) | ~1.1GB–404GB | ~2GB–230GB | Reasoning-focused chain-of-thought, strong at math/code | The distilled small sizes trade a lot of the full model's reasoning strength for size |
| **phi4** | 14b | ~9.1GB | ~12GB+ | Dense STEM/knowledge performance for its size | Weak tool-calling support relative to Qwen/Llama |
| **gpt-oss** | 20b, 120b | MXFP4-native (~4.25 bits/param) | 16GB (20b) / 80GB (120b) | OpenAI's own open-weight release; reasoning + agentic use | 120b needs a single 80GB-class GPU to be practical |
| **nomic-embed-text** | — (single size) | ~274MB | <1GB | Embeddings only, 768 dims, 2K context — the usual local RAG default | Not a chat model at all |

### Used in this repo's examples

Three tags are actually exercised here, each chosen deliberately (`common/models.kt`):

- **`qwen3.5:2b`** — `Models.DEFAULT`. Small and fast (~1.5s/turn once `think` is disabled), used
  for `_01`, `_02` (both variants), `_03_tool_calling` (its chat template supports native tool
  calling), the answer-generation step of `_05_embeddings`, and `_06_vision` (its `vision`
  capability is real — tested against the actual asset, and the description plus JSON output were
  both correct). **Not** used for `_04_structured_outputs`: tested there, it respects the JSON
  Schema's syntax but garbles the actual content of a nested array field — a real, reproducible
  limitation at this size, not a hypothetical one. In `_05_embeddings`, tested end to end against
  the real corpus, it retrieves the right passages every time but still garbles 2 of 5 final
  answers despite correct context — retrieval and generation are separate failure modes, and this
  size is fine at the first, shakier at the second. In `_06_vision`, the limitation isn't quality
  but **latency**: several minutes to process one ~1.4MB image, vs. ~1-2s for a plain text turn —
  vision encoding at full resolution is simply much more expensive than tokenizing a sentence.
- **`gemma4:12b`** — `Models.GEMMA4_12B`. Bigger and much slower (~30-50s/turn on CPU), with no
  runaway-thinking issue and, per the note above, reliable structured-output content — used
  specifically for `_04_structured_outputs` to get a clean result. Also the model this repo's own
  caveats point to whenever "the small model isn't cutting it" matters more than speed (including
  as the likely fix for `_05_embeddings`'s generation-quality gap, untested here).
- **`nomic-embed-text`** — embeddings only, not a chat model, used by `_05_embeddings` via
  `/api/embed`. Tiny (274MB) and fast even on CPU: indexing the full 565-passage Sherlock Holmes
  corpus took ~5 minutes end to end in testing, entirely local and free.

Pulled with `ollama pull qwen3.5:2b` / `ollama pull gemma4:12b` / `ollama pull nomic-embed-text`;
`ollama list` confirms what's actually available before running an example, and `ollama show <model>` is the way to
check a given tag's template for tool-calling / vision support before assuming it works.

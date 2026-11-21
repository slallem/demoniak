# LLM model families and providers

Snapshot as of **2026-11-21**. This landscape moves monthly: treat every row as a starting point
and verify against the provider's official model card / pricing page before relying on it.

> **Confidence legend** — ✅ taken from this repo's `Models` objects or official docs;
> ⚠️ from third-party trackers/blogs (names, dates and sizes may be inconsistent);
> `~` = approximate. Release dates are given as month (or day when known).

## How to read the "RAM needed" column

- **Hosted (API only)**: weights are not public; nothing to run locally. RAM = n/a.
- **Open weights**: figures are for *quantized* inference (≈ Q4, 4-5 bits/param) unless noted.
  Rule of thumb: `RAM ≈ params × 0.55 bytes` + a few GB for KV cache/context.
  At FP16 multiply by ~3.5.
- **MoE models** (Mixture of Experts) must hold **all** parameters in memory even though only
  the "active" ones are used per token. Active params drive speed, total params drive RAM.
  Notation: `397B-A17B` = 397B total, 17B active.
- On Apple Silicon, unified memory is shared with the OS: budget ~75% of it for the model.

## 1. Closed (hosted) model families

| Provider | Origin | Family (latest known) | Release | Capabilities | Used in this repo |
|---|---|---|---|---|---|
| **Anthropic** | 🇺🇸 America / US | Claude Fable 5 / 5.2 ⚠️ (5.2: 2026-11-09), Opus 4.8, Sonnet 5, Haiku 4.5; Mythos 5 (restricted, Project Glasswing) | Haiku 4.5 Oct 2025; 4.6-4.8 and 5.x during 2026 | Conversation, coding/agents, math & reasoning (adaptive thinking), vision input, PDF, tool use, structured output, 1M-context variants. **No image generation, no embeddings.** | ✅ `anthropic/` `_01`–`_21` |
| **OpenAI** | 🇺🇸 America / US | GPT-5 family (5, mini, nano) ✅; GPT-5.5 and GPT-6.x ("Astra", "Sol", 2026-11-17) ⚠️; o3 / o4-mini reasoning | GPT-5 Aug 2025; 6.x Nov 2026 ⚠️ | Conversation, coding, math/reasoning, vision, audio in/out (realtime), **image generation** (gpt-image), video (Sora), embeddings, moderation, tool use | ✅ `openai/`, moderation |
| **Google** | 🇺🇸 America / US | Gemini 3.x ✅ (3.1 Pro/Flash-Lite, 3.5 Flash); Gemini 4 ("Neon", 2026-11-18) ⚠️ | Gemini 3 Nov 2025; 3.5 2026; 4 Nov 2026 ⚠️ | Conversation, math/reasoning, very long context (1M+), multimodal in (text/image/audio/video/PDF), **image generation** (Imagen / "Nano Banana"), video (Veo), embeddings | ✅ `google/` |
| **xAI** | 🇺🇸 America / US | Grok 4.x ⚠️ | 2025-2026 | Conversation, reasoning, coding, vision, image generation (Aurora/Grok Imagine), live search | — |
| **Alibaba** | 🇨🇳 Asia / China | Qwen-Max (hosted tiers) (Qwen3.8-Max ~2.4T params preview Jul 2026; "Qwen4-Max" Nov 2026 ⚠️) | 2026 | Conversation, math, coding, multilingual, vision | — |
| **Moonshot AI** | 🇨🇳 Asia / China | Kimi K3.x ⚠️ (K3.1 Pro 2026-11-20) | Nov 2026 ⚠️ | Agentic coding, long context, reasoning | — |

## 2. Open-weight model families

"Open source" is used loosely: *open weights* (downloadable) ≠ OSI open source. License column
matters if you plan commercial use.

| Provider | Origin | Family | Release | Sizes (total / active) | License | RAM (Q4, approx.) | Capabilities |
|---|---|---|---|---|---|---|---|
| **Google** | 🇺🇸 America / US | **Gemma 4** | 2026-04-02 | 2B, 12B, 26B-A4B (MoE, May 2026), 31B dense | Apache 2.0 ⚠️ | 12B ≈ 8 GB; 31B ≈ 20 GB | Conversation, math, coding, vision input, multilingual. Used here as `gemma4:12b` via Ollama |
| **Alibaba** | 🇨🇳 Asia / China | **Qwen 3.5** | 2026-02-16 | 0.8B → 9B small models, up to 397B-A17B | Apache 2.0 ⚠️ | 2B ≈ 2 GB; 9B ≈ 6 GB (~12 GB with 256K ctx); 397B ≈ 220 GB | Conversation, strong math/coding, multilingual (100+ languages), vision, tool use. `qwen3.5:2b` used here |
| **Alibaba** | 🇨🇳 Asia / China | **Qwen 3.6** | 2026-04-16 | 27B dense, 35B-A3B | Apache 2.0 ⚠️ | 27B ≈ 17 GB; 35B-A3B ≈ 22 GB (fast: 3B active) | Same, improved coding/agentic |
| **Alibaba** | 🇨🇳 Asia / China | Qwen-Image, Wan (video) | 2025-2026 | 20B-class (image) | Apache 2.0 ⚠️ | 24 GB+ VRAM | **Image / video generation** |
| **DeepSeek** | 🇨🇳 Asia / China | **V4** (Pro, Flash) | 2026-04-24 (preview) | Pro ≈ 1.6T-A49B ⚠️; Flash much smaller | MIT-style ⚠️ | Pro: 200 GB+ even quantized; Flash: workstation class | Conversation, top-tier math/reasoning and coding, long context. `deepseek-v4-*` used here via API |
| **Meta** | 🇺🇸 America / US | **Llama 4** (Scout, Maverick) | 2025-04-05 | Scout 109B-A17B, Maverick 400B-A17B | Llama Community License (restricted) | Scout ≈ 60 GB; Maverick ≈ 230 GB | Conversation, vision, 10M-context (Scout). Open line appears stalled in 2026 ⚠️ |
| **Mistral AI** | 🇪🇺 Europe / France | **Mistral Large 3** ⚠️ | 2025-12 ⚠️ | 675B-A41B | Apache 2.0 ⚠️ | ≈ 380 GB | Conversation, coding, multilingual (EU focus), vision |
| **Mistral AI** | 🇪🇺 Europe / France | Mistral Small, Ministral 3B/8B, Codestral | 2025-2026 | 3B – 24B | Apache 2.0 (Codestral: non-prod license) | 3B ≈ 2 GB; 8B ≈ 5 GB; 24B ≈ 15 GB | Fast chat, edge, code completion (Codestral). Used in this repo via API |
| **OVHcloud** | 🇪🇺 Europe / France | **Olala v1.0** (mini, summarise, translate) | 2026 ⚠️ | 6.8B total / 1.5B active (MoE, "7A1B") | Apache 2.0 | ≈ 5 GB | Small reasoning MoE specialised for summarisation and translation; hosted on OVH AI Endpoints |
| **Zhipu (Z.ai)** | 🇨🇳 Asia / China | GLM 5.x ⚠️ | 2026 | ~750B MoE ⚠️ | MIT ⚠️ | ~860 GB at FP8 | Agentic coding, reasoning |
| **Moonshot AI** | 🇨🇳 Asia / China | Kimi K2 / K3 ⚠️ | 2025-2026 | ~1T MoE | Modified MIT | Datacenter class | Agentic coding, long context |
| **Microsoft** | 🇺🇸 America / US | Phi family | 2024-2026 | 3.8B – 14B | MIT | 3.8B ≈ 3 GB; 14B ≈ 9 GB | Small, strong at math/reasoning for its size |
| **Black Forest Labs** | 🇪🇺 Europe / Germany | FLUX | 2024-2026 | 12B (dev/schnell) | Mixed (schnell Apache, dev non-commercial) | ~12-24 GB VRAM | **Image generation / editing** |
| **Stability AI** | 🇬🇧 Europe / UK | Stable Diffusion 3.x | 2024-2025 | 2B – 8B | Community license | 8-16 GB VRAM | **Image generation** |
| **Lightricks** | 🌍 Middle East / Israel | LTX-Video | 2024-2026 | ~2B-13B | Open weights | 16-24 GB VRAM | **Video generation** |

## 3. Hosting providers (they serve models, they don't train them)

Some European/sovereign clouds host open-weight models behind an API. The models come from the
makers above; the provider contributes the infrastructure, data-residency and billing.

| Provider | Origin | Service | Models hosted (⚠️ catalog changes often) | Capabilities |
|---|---|---|---|---|
| **OVHcloud** | 🇪🇺 Europe / France | **AI Endpoints** (OpenAI-compatible API, EU data centres, pay-per-token). Also **trains its own Olala models** (see section 2) | 33 entries in the public catalog (2026-11-21): Qwen 3.5/3.6/3.8 + Qwen3-VL, gpt-oss-20b/120b, DeepSeek V4.1 Flash, Gemma 4 26B-A4B, Mistral Small 3.2, NVIDIA Nemotron 3, poolside Laguna, Olala v1.0 ×3; embeddings (bge-m3, Qwen3-Embedding-8B); guard (Qwen3Guard); Whisper, Solaria-3 (STT); NVIDIA Riva (TTS); Stable Diffusion XL | Chat, reasoning, vision, code, embeddings, moderation, STT/TTS, image generation. Some entries are `available: false` (Llama 3.3 70B, Qwen2.5-VL-72B, Qwen3.6-27B, SDXL, bge-multilingual-gemma2) |
| **Scaleway** ⚠️ | 🇪🇺 Europe / France | Generative APIs | Llama, Mistral, Qwen, gpt-oss, embeddings | Chat, code, embeddings |
| **AWS Bedrock** | 🇺🇸 America / US | Managed multi-vendor API (Claude, Nova, Llama, Mistral…) | see `aws-bedrock-examples` branch | Chat, vision, image generation |

The OVHcloud catalog exposes a discovery API listing each model, its capability flags and price;
some entries are flagged `available: false`, so check before routing traffic. Source:
[OVHcloud AI Endpoints catalog](https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/),
[Catalog API docs](https://docs.ovhcloud.com/en/guides/public-cloud/ai-machine-learning/ai-endpoints-catalog-api.md).

## 4. Capability matrix (quick view)

| Capability | Best hosted options | Best open-weight options |
|---|---|---|
| General conversation | Claude, GPT, Gemini | Qwen 3.x, Gemma 4, Mistral, Llama |
| Mathematics / reasoning | Claude (adaptive thinking), GPT (o-series / GPT-6), Gemini | DeepSeek V4, Qwen 3.x (thinking), Phi |
| Coding / agents | Claude, GPT, Gemini | Qwen 3.6, DeepSeek V4, GLM, Kimi, Codestral |
| Vision (image understanding) | Claude, GPT, Gemini | Gemma 4, Qwen 3.x, Llama 4 |
| **Image generation** | GPT (gpt-image), Gemini/Imagen, Grok | FLUX, Stable Diffusion, Qwen-Image |
| Video generation | Sora, Veo | Wan, LTX-Video |
| Embeddings | OpenAI, Google, Mistral (`mistral-embed`) — **Anthropic has none** | bge, nomic-embed, Qwen3-Embedding (via Ollama) |
| Moderation | OpenAI moderation, Mistral | Llama Guard, ShieldGemma |
| Tiny / on-device (≤ 8 GB RAM) | Haiku / Flash-Lite / nano tiers | Qwen 3.5 0.8-9B, Gemma 4 2-12B, Phi, Ministral 3B |

## 5. Running locally: which hardware for which model?

| Machine RAM / VRAM | Realistic models (Q4) |
|---|---|
| 8 GB | ≤ 4B (Qwen 3.5 2-4B, Phi-mini, Ministral 3B) |
| 16 GB | up to ~12B (Gemma 4 12B, Qwen 3.5 9B, Ministral 8B) |
| 32 GB | ~27-35B (Qwen 3.6 27B / 35B-A3B, Gemma 4 31B) |
| 64-128 GB (Mac Studio, workstation) | 100B-class MoE (Llama 4 Scout) |
| 256 GB+ | 400B-class (Qwen 3.5 397B, Llama 4 Maverick); 1T+ models need multi-GPU servers |

See [ollama.md](ollama.md) for how this repo runs local models.

## 6. Caveats

- Names and dates for Nov 2026 releases (GPT-6.x, Gemini 4, Qwen4-Max, Kimi K3.1) come from
  aggregator sites that disagree with each other and sometimes mix in old models; confirm on
  official announcements.
- Parameter counts for DeepSeek V4-Pro (1T vs 1.6T) and Mistral Large 3 differ between sources.
- RAM numbers are estimates for 4-bit quantization with moderate context; long contexts
  (128K+) add several GB of KV cache.
- The authoritative list for the models actually wired into this repo is each provider's
  `common/models.kt`.

## Sources

- [LLM Gateway — 2026 release timeline](https://llmgateway.io/timeline/2026)
- [Opper — AI model release tracker](https://opper.ai/model-releases)
- [Wikipedia — Qwen](https://en.wikipedia.org/wiki/Qwen)
- [digitalapplied — Open-weight AI models H1 2026 retrospective](https://www.digitalapplied.com/blog/open-weight-ai-models-h1-2026-retrospective-deepseek-qwen-llama)
- [codersera — Open source LLMs landscape 2026](https://codersera.com/blog/open-source-llms-landscape-2026)
- [codersera — Qwen 3.5 complete guide](https://codersera.com/blog/qwen-3-5-complete-guide-2026/)
- [DEV — Open-source LLM shootout 2026](https://dev.to/jovan_chan_9500711396d4e6/open-source-llm-shootout-2026-qwen36-vs-gemma-4-vs-llama-4-vs-glm-51-vs-deepseek-v4-which-fits-56nn)

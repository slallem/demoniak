# Running LLMs locally: tools overview

Snapshot as of **2026-11-21**. Companion to [models.md](models.md) (which models exist and how
much RAM they need) and [ollama.md](ollama.md) (how this repo uses Ollama).

> ⚠️ = based on third-party blogs/trackers; versions and benchmark numbers vary between sources
> and age quickly. Check each project's own repo/site before relying on a detail.

## The three layers

Most confusion comes from mixing up three different kinds of tools:

| Layer | Role | Examples |
|---|---|---|
| **Engine** | Loads weights and does the actual matrix math | llama.cpp, MLX, vLLM, SGLang, TensorRT-LLM, Colibri |
| **Runner / server** | Downloads models, manages them, exposes an HTTP API (often OpenAI-compatible) | Ollama, LM Studio, llamafile, LocalAI |
| **Chat UI / app** | Friendly front end on top of a runner or engine | LM Studio, Jan, GPT4All, Open WebUI, Msty |

Many tools span layers: Ollama and LM Studio are runners that wrap llama.cpp (and MLX on Mac),
and LM Studio also bundles a chat UI.

Common model formats: **GGUF** (llama.cpp ecosystem, quantized, CPU/GPU/Metal), **MLX** (Apple
Silicon), **safetensors** (original HF weights; vLLM, Transformers), plus AWQ/GPTQ/FP8 for GPU
servers.

## 1. Desktop / developer runners (start here)

| Tool | What it is | OS | GPU support | Open source | Best for |
|---|---|---|---|---|---|
| **Ollama** | CLI + daemon, `ollama pull/run`, HTTP API on `:11434` (native + OpenAI-compatible) | macOS, Linux, Windows | Metal, CUDA, ROCm, CPU | MIT | Developers; default for this repo's examples |
| **LM Studio** | GUI: model search/download, chat, local server; runs GGUF (llama.cpp) **and** MLX | macOS, Windows, Linux | Metal, CUDA, Vulkan | Closed-source app (free), open SDKs | Non-CLI users, comparing models visually, Mac MLX speed |
| **Jan** | Open-source ChatGPT-like desktop app, local server, can also call cloud APIs | macOS, Windows, Linux | Metal, CUDA, Vulkan | Yes (Apache 2.0) ⚠️ | Beginners who want an open-source UI |
| **GPT4All** | Simple desktop chat app, local document chat | macOS, Windows, Linux | Metal, Vulkan, CPU | Yes (MIT) ⚠️ | Low-spec machines, minimal setup (less active ⚠️) |
| **llamafile** | One-file executable bundling model + llama.cpp (Mozilla) | Cross-platform | CPU, CUDA, Metal | Yes | Zero-install sharing/distribution |
| **Msty** | Polished multi-model chat app over local/remote models | macOS, Windows, Linux | via bundled engines | Closed (free tier) ⚠️ | Side-by-side model comparison |

## 2. Engines (more control, more work)

| Tool | What it is | Strengths | Trade-offs |
|---|---|---|---|
| **llama.cpp** | C/C++ engine behind most of the above; `llama-server` gives an OpenAI-compatible API | Runs anywhere (CPU, Metal, CUDA, Vulkan, ROCm), best quantization support, deepest tuning | Manual model management and flags |
| **MLX / mlx-lm** (Apple) | Array framework + LLM tooling for Apple Silicon unified memory | Often the fastest on Mac; fine-tuning on laptop | Apple only |
| **Hugging Face Transformers** | Reference Python library | Every new model first, research/fine-tuning | Slower and heavier than GGUF engines for plain inference |
| **ExLlamaV2/V3** ⚠️ | Fast NVIDIA GPU engine for EXL2/EXL3 quants | High single-user speed on consumer GPUs | NVIDIA only |
| **Candle / mistral.rs** ⚠️ | Rust inference engines | Embeddable, single binary | Smaller ecosystem |

## 3. Servers for many users / production

| Tool | What it is | Notes |
|---|---|---|
| **vLLM** | High-throughput GPU server (PagedAttention, continuous batching), OpenAI-compatible | De-facto standard for multi-user self-hosting; needs a real GPU; safetensors/AWQ/FP8 |
| **SGLang** ⚠️ | Rival to vLLM, strong on structured output and prefix caching | Often compared head-to-head with vLLM |
| **TensorRT-LLM** | NVIDIA's optimized engine | Max NVIDIA performance, complex build |
| **LocalAI** | Drop-in OpenAI-API replacement supporting text, image, audio | Docker-friendly, multi-modal |
| **Text Generation Inference (TGI)** | Hugging Face's server | Mature, container-based |

Throughput claims between vLLM and Ollama differ wildly across blogs (⚠️ 5-20× on batched loads);
the safe conclusion is only: *Ollama/llama.cpp for one user, vLLM for many concurrent users.*

## 4. Colibri (the one you heard about) ⚠️

[Colibri](https://github.com/JustVugg/colibri) is a **research-style, pure-C inference engine**
(Apache 2.0) for very large **Mixture-of-Experts** models. Instead of loading the whole model
into RAM, it **streams the expert weights from disk (NVMe)** on demand, so a frontier-scale model
(reports mention GLM-class ~744B and a ~1.5 TB model) can run on a machine with only ~25 GB of
RAM.

- **Trade-off is speed, not quality:** reported at roughly **0.05-0.3 tokens/s** on consumer
  hardware, i.e. 10-100× slower than cloud inference. Disk bandwidth (PCIe 5 NVMe) is the
  bottleneck.
- **Good for:** offline/private experiments with models that would otherwise never fit; curiosity;
  batch jobs where latency doesn't matter.
- **Not for:** interactive chat, or anything this repo's examples do in real time.
- Recent versions read GGUF directly and add a local cluster mode (disk-backed workers on other
  machines). It is a young project: expect rough edges.

## 5. Front ends that plug into any runner

| Tool | Notes |
|---|---|
| **Open WebUI** | Self-hosted ChatGPT-style web UI for Ollama / OpenAI-compatible servers; RAG, multi-user |
| **AnythingLLM** | Document chat / RAG workspace, desktop or Docker |
| **SillyTavern, KoboldCpp** ⚠️ | Role-play / creative-writing oriented UIs over local engines |
| **Continue, Cline, Aider** | Coding assistants that can use a local model as backend |

## 6. Image / video generation locally

LLM runners above don't generate images. Separate tools: **ComfyUI** (node-based, the most
flexible), **AUTOMATIC1111 / Forge**, **InvokeAI**, **Draw Things** (macOS/iOS app). They run
FLUX, Stable Diffusion, Qwen-Image, Wan, LTX-Video (see [models.md](models.md)); typically 12-24 GB
VRAM.

## 7. Choosing quickly

| You want… | Use |
|---|---|
| Simplest developer setup, scriptable, used by this repo | **Ollama** |
| A GUI to browse and try models, fast on Mac | **LM Studio** |
| Fully open-source GUI | **Jan** |
| Max control / newest quant formats / embed in your own C++ | **llama.cpp** |
| Best speed on an Apple Silicon Mac | **MLX** (directly or through LM Studio) |
| Serve a team with an NVIDIA GPU | **vLLM** (or SGLang) |
| Share a model as a single double-clickable file | **llamafile** |
| Run a 500B+ MoE on a modest PC, don't care about speed | **Colibri** |
| Images / video | **ComfyUI** |

All of the runners/servers above can expose an **OpenAI-compatible endpoint**, so code written
against the OpenAI API (like this repo's `openai/` and `ollama/` examples) can usually be pointed
at them by changing only the base URL.

## Sources

- [Ollama vs LM Studio vs llama.cpp 2026 (DEV)](https://dev.to/jovan_chan_9500711396d4e6/ollama-vs-lm-studio-vs-llamacpp-2026-269h)
- [Ollama vs LM Studio vs vLLM vs llama.cpp vs MLX 2026 (codersera)](https://codersera.com/blog/ollama-vs-lm-studio-vs-vllm-vs-llama-cpp-vs-mlx-2026/)
- [Ollama vs vLLM vs LM Studio (SitePoint)](https://www.sitepoint.com/local-llm-deployment-ollama-vs-vllm-vs-lm-studio-compared/)
- [Best free local LLM tools 2026 (codersera)](https://codersera.com/blog/best-free-local-llm-tools-2026/amp/)
- [Colibri README](https://raw.githubusercontent.com/JustVugg/colibri/HEAD/README.md)
- [Tom's Hardware on Colibri](https://www.tomshardware.com/tech-industry/artificial-intelligence/colibri-proof-of-concept-gains-frontier-level-1-5-tb-ai-model-novel-approach-runs-on-only-25gb-of-ram-and-shows-promise-for-local-ai-setups)
- [Colibri on DEV](https://dev.to/jamilxt/colibri-running-a-744b-ai-model-on-your-laptop-dpp)

# 😈 demoniak

**DEMONstrating AI in Kotlin** — a teaching repo of small, self-contained examples for building
with LLM APIs.

![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)
![JDK](https://img.shields.io/badge/JDK-25-ED8B00?logo=openjdk&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-KTS-02303A?logo=gradle&logoColor=white)
![Anthropic](https://img.shields.io/badge/Anthropic-Claude-D97757?logo=anthropic&logoColor=white)
![OpenAI](https://img.shields.io/badge/OpenAI-412991?logo=openai&logoColor=white)
![Google](https://img.shields.io/badge/Google-Gemini-4285F4?logo=googlegemini&logoColor=white)
![Mistral](https://img.shields.io/badge/Mistral-FA520F?logo=mistralai&logoColor=white)
![Ollama](https://img.shields.io/badge/Ollama-local-000000?logo=ollama&logoColor=white)
![AWS Bedrock](https://img.shields.io/badge/AWS-Bedrock-232F3E?logo=amazonwebservices&logoColor=white)
![MCP](https://img.shields.io/badge/MCP-server%20%2B%20clients-000000)

Every example is a standalone `fun main()` you can read in isolation and run from your IDE.
No framework, no app to assemble — just the API, one concept at a time.

## 🚀 Quick start

```bash
# 1. Put your key in a git-ignored local overlay
echo 'anthropic.api.key=sk-ant-...' > src/main/resources/anthropic.properties.local

# 2. Compile
./gradlew compileKotlin

# 3. Run any example from the IDE run gutter (⚠️ working directory = project root)
```

Keys are **not** env vars: each provider reads `src/main/resources/<provider>.properties`
(`anthropic`, `openai`, `google`, `mistral`). Run with `-Dprofile=local` (or `APP_PROFILE=local`) to have
`<provider>.properties.local` override the committed placeholders.

**Exception: AWS Bedrock.** It authenticates with SigV4 request signing, not a bearer-token key,
so there is no `aws.properties`. Set `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` (+
`AWS_SESSION_TOKEN` if using temporary credentials) and `AWS_REGION` as real environment
variables instead — the AWS SDK's default provider chain picks them up on its own.

## 📚 The examples

`src/main/kotlin/demo/api/…`

### 🤖 Anthropic — the full tutorial

| # | Topic | # | Topic |
|---|---|---|---|
| `_01` | Starter — first call | `_13` | 🔎 RAG over Sherlock Holmes (BM25, no embeddings) |
| `_02` | Multi-turn chat | `_14` | 🧠 Extended thinking (`budget_tokens`) |
| `_03` | System prompts | `_15` | 🧠 Adaptive thinking + effort |
| `_04` | ⚡ Prompt caching | `_16` | 👁️ Vision |
| `_05` | 🔧 Tool definition | `_17` | 👁️ Vision from an asset file |
| `_06` | Temperature | `_18` | 🔌 MCP client — tools over stdio |
| `_07` | 📡 Streaming | `_19` | 🔌 MCP client — remote Streamable HTTP |
| `_08` | Structured output via prefill | `_20` | 🔌 MCP resources |
| `_09` | Structured outputs (native) | `_21` | 🔌 MCP prompts |
| `_10` | ✍️ Prompt engineering | `_22` | 📄 OCR — images |
| `_11` `_12` | 🔧 Tool parameters (typed / raw JSON) | `_23` | 📄 OCR — PDF |

### 🌐 Other providers

| Provider | Examples |
|---|---|
| **OpenAI** | `_01` starter · `_02` chat · `_03` system prompt · `_04` 🔧 function calling · `_05` 📡 streaming · `_06` structured outputs · `_07` JSON mode · `_08` 👁️ vision · `_09` 👁️ vision from a URL |
| **Google Gemini** | `_01` starter · `_02` chat · `_03` system prompt |
| **Mistral** | `_01` starter · `_02` structured outputs · `_03` function calling · `_04` 📄 OCR (dedicated endpoint) · `_05` 📄 OCR — PDF, chained into chat for targeted extraction · `_06` 🧑‍💻 Codestral FIM · `_07` 👁️ vision · `_08` 🔎 Embeddings + semantic RAG over Sherlock Holmes |
| **Ollama** 🏠 | `_01` starter · `_02` chat, both via the OpenAI-compatible API and the native HTTP API |
| **AWS Bedrock** | `_01` starter · `_02` chat — Claude via Bedrock's Converse API, SigV4/IAM auth instead of a bearer key |

### 🔌 MCP server

`mcp/python/server.py` — a dependency-free, pure-stdlib Python **stdio** server exposing all three
MCP primitives: **tools** (`roll_dice`, `slugify`), **resources** (the Sherlock stories) and a
**prompt** (`haiku`). Shell helpers (`list_tools.sh`, `call_tools.sh`, …) let you poke at it without
Kotlin — see [`mcp/python/README.md`](mcp/python/README.md).

## 🎛️ Switching models

Each provider has a `common/models.kt` with a `Models.DEFAULT` — change it in one place and every
example of that provider follows.

> [!NOTE]
> The Anthropic default is **`claude-haiku-4-5`** on purpose: several examples demonstrate
> *older* mechanics. Assistant prefill (`_08`), `budget_tokens` thinking (`_14`) and `temperature`
> (`_06`) are rejected by the newest models — their modern replacements are `_09` and `_15`.
> Switching the default to Opus 4.6+ / Sonnet 5 will make those three fail **by design**.

## 📖 Shared corpus

`assets/books/` holds 12 public-domain Sherlock Holmes stories (Project Gutenberg). They feed two
contrasting retrieval strategies: chunk-and-rank RAG (`_13`) versus whole-document reads through MCP
resources (`_20`). `assets/images/` and `assets/pdf/` feed the vision and OCR examples.

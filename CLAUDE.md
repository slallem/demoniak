# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A teaching repo of self-contained Kotlin examples for building with LLM APIs (primarily the
Anthropic Claude API, plus OpenAI and Google starters), and a hand-rolled MCP server used by
the MCP client examples. Each example is a standalone `fun main()`; there is no application
being assembled from them.

## Build & run

```bash
./gradlew compileKotlin      # fastest correctness check while iterating
./gradlew build              # full build
./gradlew test               # runs JUnit 5; note: src/test is currently empty (no tests yet)
# single test, once tests exist:
./gradlew test --tests "com.example.SomeTest.someMethod"
./gradlew dependencyUpdates  # check for newer dependency versions (com.github.ben-manes.versions
                             # plugin) — local only, no CI/VCS integration; run manually as needed
```

- **JDK 25** is required (`jvmToolchain(25)`); the foojay resolver in `settings.gradle.kts`
  auto-provisions it.
- **There is no `application` plugin / `run` task.** Each numbered example is run individually
  via its `main()` — from the IDE run gutter, or a manually-added `JavaExec`. The main class of
  `_18_mcp_client.kt` is `demo.api.anthropic._18_mcp_clientKt`.
- **Run from the project root.** Several examples read paths relative to the working directory:
  `_13` (`assets/books`), `_16`/`_17` (`assets/images`), and the MCP clients `_18`/`_20`/`_21`
  (spawn `mcp/python/server.py`). Launching from elsewhere breaks them.

## API keys & the profile system

Keys are **not** env vars. Every provider (anthropic, openai, google, mistral) reads the same
classpath resource, `src/main/resources/credentials.properties`, one property per provider
(`anthropic.api.key`, `openai.api.key`, `google.api.key`, `mistral.api.key`). That file is
**git-ignored** (it holds real secrets); `credentials.properties.example` is the committed,
documented template with `CHANGE_ME` placeholders — copy it to `credentials.properties` and fill
in real keys to get started. Ollama needs no key (local server); AWS Bedrock, on the
`aws-bedrock-examples` branch, is the one exception and authenticates via SigV4/IAM through real
environment variables instead (see that branch's `demo.api.aws.common.bedrockClient`).

`common/common.kt` → `loadProperties(name)` implements a profile overlay: with
`-Dprofile=local` (or `APP_PROFILE=local`), values from `<name>.properties.local` override the
base file — e.g. to swap in a different credential set without editing the base file directly.
`name` is always `"credentials"` in practice today, but stays a parameter rather than a hardcoded
constant so the mechanism remains reusable. `loadProperties` lives in `demo.api.anthropic.common`
and is **reused by the OpenAI, Google, and Mistral common code** — so `anthropic/common` is the
de-facto shared home, not a pure per-provider split.

## Architecture

### Per-provider parallel structure
`src/main/kotlin/demo/api/{anthropic,openai,google}/`. Each provider has:
- `common/common.kt` — the client factory (`anthropicClient()` / `openaiClient()` / `googleClient()`).
- `common/models.kt` — a `Models` object of model-ID constants plus **`Models.DEFAULT`** and a
  ready-to-use `defaultModel`. **Changing `Models.DEFAULT` in one place switches the model for every
  example of that provider.** The Anthropic default is `claude-haiku-4-5` (chosen deliberately — see below).

`anthropic/` is the full tutorial (`_01`–`_21`); `openai/` and `google/` are `_01` starters only.

### Numbered examples are intentionally self-contained
Each `_NN_topic.kt` is meant to be readable in isolation, so small helpers (`toAnthropicTool`,
`textOf`, etc.) are **duplicated across files on purpose**. This is safe for top-level private
*functions* and *vals* (they are file-scoped under the file's synthetic `…Kt` class), but **top-level
private *classes* collide** across files in the same package — a real gotcha here. When two examples
need the same-named data class, rename one (e.g. `WeatherArgs` vs `ForecastInput` in `_11`/`_12`).

### Model-era caveats are the point of several examples
The Anthropic examples deliberately span old vs new model behavior, and the default is Haiku 4.5
so the "older mechanic" examples run as-is:
- **Assistant prefill** (`_08`) and **`budget_tokens` thinking** (`_14`) work only on pre-4.6 models
  (Haiku 4.5); they return **400 on Opus 4.6+/Sonnet 5/Fable 5**. Their modern replacements are
  **structured outputs** (`_09`) and **adaptive thinking + effort** (`_15`).
- **`temperature`** (`_06`) is rejected on the newest models (Opus 4.7+/Sonnet 5/Fable 5).
- If you switch `Models.DEFAULT` to a newer model, expect `_06`/`_08`/`_14` to fail by design.

### MCP (Model Context Protocol)
- **Server:** `mcp/python/server.py` — a dependency-free, pure-stdlib Python stdio server (MCP is
  line-delimited JSON-RPC over stdin/stdout). It exposes **all three primitives**: tools
  (`roll_dice`, `slugify`), resources (the Sherlock stories in `assets/books/` as
  `sherlock:///<file>.md`), and a prompt (`haiku`). Shell helpers in `mcp/python/` — `list_tools.sh`,
  `call_tools.sh`, `list_resources.sh`, `list_prompts.sh` — exercise it (all support `-v` to dump raw
  JSON-RPC). See `mcp/python/README.md`.
- **Clients (Kotlin, in `anthropic/`):** `_18` tools over local **stdio** (spawns the Python server);
  `_19` tools over remote **Streamable HTTP** (public DeepWiki server, needs the `ktor-client-cio`
  engine); `_20` **resources** (list→select→read→summarise); `_21` **prompts** (list→get→run). All
  bridge MCP tool/message shapes to the Anthropic SDK via the `io.modelcontextprotocol:kotlin-sdk`
  client. Suspend calls (`connect`/`callTool`) must run inside the `runBlocking` coroutine body, not
  a non-suspend `map { }`.

### Shared corpus
`assets/books/` holds 12 public-domain Sherlock Holmes stories (Project Gutenberg, boilerplate
stripped) plus `SOURCE.md`. It feeds two different retrieval strategies: `_13` (RAG — chunks +
keyword **BM25**, no embeddings, since Anthropic has no embeddings endpoint) and the MCP server's
resources (whole-document reads). `_20` deliberately contrasts with `_13` by putting an entire
document into context rather than retrieving chunks.

## Dependency notes
- `jackson-bom` is pinned to `2.18.9` on purpose (patches a CVE flagged in the version
  `anthropic-java` pulls transitively) — see the comment in `build.gradle.kts` before changing it.
- `ktor-client-cio` exists solely for `_19`'s remote transport (the MCP SDK ships `ktor-client-core`
  with the SSE plugin but no engine).

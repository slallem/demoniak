# Moderation: OpenAI vs. Mistral vs. Gemini vs. Anthropic

All four providers can be used to moderate content, but only two of them ship it as an actual
endpoint. OpenAI and Mistral expose a free-standing text classifier, separate from chat
completions, meant to screen content *before* it reaches a chat model or *before* a model's
output reaches a user — neither call spends chat tokens or touches `Models.DEFAULT`, since each
uses its own dedicated moderation model. Gemini and Anthropic have no such endpoint at all, but
for two different reasons: Gemini's safety signal rides along on a real `generateContent` call
and — empirically, tested live — often doesn't show up even then; Anthropic has no
classification mechanism whatsoever and instead documents, as its official recommendation, using
Claude itself as the classifier via an ordinary prompt. See the architecture section below before
assuming any of the four examples is "the same idea, different provider."

- OpenAI: `demo.api.openai._10_moderation` — `client.moderations().create(...)` via the
  `openai-java` SDK, model `omni-moderation-latest`.
- Mistral: `demo.api.mistral._09_moderation` — plain HTTP call to `POST /v1/moderations` (no
  SDK shape to reuse, same reasoning as `_04_ocr`), model `mistral-moderation-latest`.
- Gemini: `demo.api.google._04_moderation` — a normal `generateContent` call with `safetySettings`
  tightened, reading `promptFeedback` / `candidate.safetyRatings` off the response.
- Anthropic: `demo.api.anthropic._24_moderation` — a normal `messages.create(...)` call
  (`_01_starter`'s call, verbatim) with a prompt asking Claude to return a JSON verdict, following
  Anthropic's own "Content moderation" guide.

## Architecture: endpoint, side effect, or prompt

| | OpenAI | Mistral | Gemini | Anthropic |
|---|---|---|---|---|
| Free-standing classification endpoint | yes — `POST /moderations` | yes — `POST /v1/moderations` | **no** | **no** |
| Where the signal comes from | dedicated response fields | dedicated response fields | `promptFeedback` / `safetyRatings` attached to a real `generateContent` response | a JSON object *you asked the model to write*, in an ordinary chat reply |
| Costs generation tokens? | no | no | yes — no way to classify text without invoking the generative model | yes — same reason, and there is no separate cheap classifier model at all |
| Signal reliability (tested live) | always returned, one score per category | always returned, one score per category | **opportunistic** — neither `blockReason` nor any `safetyRating` appeared for a clearly violent prompt that both OpenAI and Mistral flag above 0.85; the model just answered empathetically with `finishReason: STOP` | reliable **as a classification decision** (the model reliably says `violation: true` for that same violent prompt) but the *shape* is unenforced — a prompt, not an API contract |
| Who defines the category taxonomy | the API (fixed, versioned) | the API (fixed, versioned) | the API (fixed enum, `HarmCategory`) | **you, the developer** — whatever category names you put in your own prompt; there is no API-level taxonomy at all |
| Real moderation mechanism for text | structured classifier output | structured classifier output | mostly the model's own trained refusal *text*, not a parallel structured score | literally a second Claude call asking Claude to classify the first message |

The practical takeaway: OpenAI and Mistral give you an always-on numeric signal you can branch
code on with a fixed, versioned taxonomy. Gemini's structured safety metadata is real and does
exist in the SDK (`GenerateContentConfig.safetySettings`, `Candidate.safetyRatings`,
`GenerateContentResponse.promptFeedback`), but for text prompts it is inconsistent in practice —
don't build branching logic that assumes it will be populated. If you need a reliable structured
moderation signal on Google's side, that lives in a different product (Vertex AI / Cloud Natural
Language's `documents:moderateText`), with its own (OAuth/service-account) authentication — out of
scope for the API-key-based `google.properties` setup this repo uses, similar friction to what the
AWS Bedrock notes describe for SigV4. Anthropic sits at the opposite extreme from OpenAI/Mistral:
there is no taxonomy to be "missing" a category from, because there is no API-defined taxonomy at
all — the 12 categories used in `_24_moderation` are copied from Anthropic's own published example
prompt, not read from any schema, and a developer is free to rename, add, or drop categories with
zero API-level consequence (only prompt-quality consequence).

## API shape differences (OpenAI vs. Mistral)

| | OpenAI | Mistral |
|---|---|---|
| Endpoint | `POST /moderations` | `POST /v1/moderations` |
| Default model | `omni-moderation-latest` | `mistral-moderation-latest` |
| Category count | 13 | 9 |
| Category nesting | parent categories with sub-categories (`violence` / `violence/graphic`) | flat, no sub-categories |
| Top-level `flagged` field | yes, returned by the API | no — derive it yourself as "any category true" |
| Multimodal input | yes (text + images, `omni-moderation-latest`) | text only |
| Always-present categories | 11 of 13 are required booleans; `illicit` / `illicit/violent` are `Optional<Boolean>` (not always returned, e.g. by region) | all 9 always present |

Gemini and Anthropic are left out of this particular table: neither has "categories returned per
call" in the same API-contract sense — see the architecture section above. Both taxonomies are
covered in the next table instead.

## Category-by-category comparison

One row per category as returned on the wire (or, for Anthropic, as defined in the example
prompt) — where a provider bundles several OpenAI sub-categories into a single flat category,
that category name repeats across rows.

- Gemini's column stays `—` most often: only 4 categories exist on the public API (`HARASSMENT`,
  `HATE_SPEECH`, `SEXUALLY_EXPLICIT`, `DANGEROUS_CONTENT`), each *in principle* carrying a
  probability enum (`NEGLIGIBLE`/`LOW`/`MEDIUM`/`HIGH`) rather than a numeric score — and, per the
  architecture section above, often returned empty for text regardless.
- Anthropic's column reflects the 12 categories from its own published prompt template — a
  developer choice, not an API constant (see above) — which is why it covers ground none of the
  three APIs do (`Conspiracy Theories`, `Intellectual Property`, `Sex Crimes` as distinct from
  generic sexual content, `Indiscriminate Weapons` as distinct from generic violence).

| OpenAI category | Mistral category | Gemini category | Anthropic category | Notes |
|---|---|---|---|---|
| `harassment` | — | `HARASSMENT` | — | Anthropic's `Hate` targets protected-attribute-based hate, not general harassment |
| `harassment/threatening` | — | `HARASSMENT` | — | Same as above |
| `hate` | `hate_and_discrimination` | `HATE_SPEECH` | `Hate` | |
| `hate/threatening` | `hate_and_discrimination` | `HATE_SPEECH` | `Hate` | Same bucket as `hate` everywhere |
| `self-harm` | `selfharm` | — | `Self-Harm` | |
| `self-harm/intent` | `selfharm` | — | `Self-Harm` | Same as above |
| `self-harm/instructions` | `selfharm` | — | `Self-Harm` | Same as above |
| `sexual` | `sexual` | `SEXUALLY_EXPLICIT` | `Sexual Content` | |
| `sexual/minors` | `sexual` | `SEXUALLY_EXPLICIT` | `Child Exploitation` | Anthropic is the only one with a distinct, more severe bucket for this rather than a sub-flag of the generic sexual category |
| `violence` | `violence_and_threats` | `DANGEROUS_CONTENT` | `Violent Crimes` | Gemini's bucket is broader — "dangerous activities" in general, not violence specifically |
| `violence/graphic` | `violence_and_threats` | `DANGEROUS_CONTENT` | `Violent Crimes` | Same bucket as `violence` everywhere |
| `illicit` | `dangerous_and_criminal_content` | `DANGEROUS_CONTENT` | `Non-Violent Crimes` | Optional field on the OpenAI side (see table above) |
| `illicit/violent` | `dangerous_and_criminal_content` | `DANGEROUS_CONTENT` | `Violent Crimes` / `Indiscriminate Weapons` | Optional on the OpenAI side; Anthropic further splits ordinary violent crime from WMD-level content |
| — | `pii` | — | `Privacy` | |
| — | `health` | — | `Specialized Advice` | Anthropic bundles medical, financial, and legal advice into one category |
| — | `financial` | — | `Specialized Advice` | Same bucket as `health` on the Anthropic side |
| — | `law` | — | `Specialized Advice` | Same bucket as `health`/`financial` on the Anthropic side |
| — | — | — | `Conspiracy Theories` | No equivalent on any of the other three |
| — | — | — | `Intellectual Property` | No equivalent on any of the other three |
| — | — | — | `Sex Crimes` | Distinct from `Sexual Content`: crimes (e.g. trafficking, assault) rather than explicit content itself; no equivalent elsewhere |

## Takeaway

OpenAI and Mistral overlap on the "classic" harms (violence, hate, sexual, self-harm,
illicit/dangerous content) but diverge at the edges: OpenAI is more granular within each harm
(threatening vs. plain, minors, intent vs. instructions) and adds harassment as its own category;
Mistral trades that granularity for three categories with no counterpart on those two APIs —
`pii`, `health`, `financial`, `law` — aimed at flagging unsolicited advice and personal-data
leakage rather than only classic safety harms. Gemini's 4 categories are the coarsest of the
API-backed three, and, more importantly, the least reliable to depend on for text: its actual
first line of defense is the generative model refusing in natural language, not a structured
score you can branch on. Anthropic breaks the pattern entirely: there is no API taxonomy to
compare at all, only a developer-authored prompt — which happens to cover more specialized ground
(conspiracy theories, IP, sex crimes vs. generic sexual content, WMD-level weapons vs. generic
violence) precisely because nothing constrains what categories you can ask Claude to check for.
There is no lossless 1:1 mapping across all four; build any cross-provider logic around the
handful of categories that really do line up, and treat the rest — including "does this provider
even reliably return a signal at all" and "is the taxonomy even fixed by the API" — as
provider-specific.

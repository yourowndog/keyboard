# Voice and AI Integrations

> Status: Canonical  
> Last verified: 2026-07-11  
> Verified against: `KeyboardManager.kt`, `WhisperClient.kt`, `GemmaClient.kt`,
> `SmolLMClient.kt`, application initialization, assets, and dependencies

OmniBoard currently has three distinct AI-related mechanisms. They must not be
described as one architecture.

## Voice transcription

The voice key starts/stops recording through `KeyboardManager`. In the voice
smartbar, the cloud icon opens a persisted provider selector: **OpenAI Cloud**
or **Titan Local**. `WhisperClient` sends that constrained selection, the WAV,
and the `verbose_json` word/segment request to the authenticated Brokentooth
relay on weakling; the APK contains only its endpoint-scoped relay credential.

OpenAI Cloud remains the fallback and receives the allowlisted request fields.
Titan Local is forwarded over Tailscale to the private CrisperWhisper adapter
on Titan. The adapter runs `transcribe_dual()` and returns OpenAI-compatible
`verbose_json`: clean intended text and its `words`/`segments`, plus
`verbatim_text` and `verbatim_words` extensions. The keyboard commits the clean
text and writes both transcripts and the full response to the Schema 2 sidecar,
so filler words and their timestamps survive in the corpus.

Pending retries retain the provider selected for the original failed take.

## ONNX autocorrect scorer

The packaged `autocorrect_v1.int8.onnx` model runs in-process through ONNX
Runtime. It evaluates correction candidates and can gate automatic commits. It
does not generate prose and does not use the Gemma sidecar.

See `docs/autocorrect/neural-scorer.md`.

## Gemma sidecar actions

`GemmaClient` posts explicit reply, rewrite, or continuation prompts to a local
loopback HTTP service at `127.0.0.1:8081`. The persona is loaded from
`assets/ime/nlp/gemma_persona.txt`. `KeyboardManager` invokes this path for the
AI-generation action.

The client and dispatch path exist. End-to-end sidecar availability and current
device usage still require live verification.

## SmolLM experiment

`SmolLMClient` targets a loopback service at port 8080 and contains sentence
scoring/reranking logic. The call from `LatinLanguageProvider` is commented out,
so this is not part of live candidate ranking.

## Historical MediaPipe design

Older documents describe an on-device Gemma `.task` file and MediaPipe GenAI.
The current application dependency set does not contain that integration and
the referenced bridge class is absent. Preserve it only as architecture history.

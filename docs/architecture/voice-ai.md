# Voice and AI Integrations

> Status: Canonical  
> Last verified: 2026-07-11  
> Verified against: `KeyboardManager.kt`, `WhisperClient.kt`, `GemmaClient.kt`,
> `SmolLMClient.kt`, application initialization, assets, and dependencies

OmniBoard currently has three distinct AI-related mechanisms. They must not be
described as one architecture.

## Whisper voice transcription

The voice key starts/stops recording through `KeyboardManager`. On completion,
`WhisperClient` sends the audio file to the authenticated Brokentooth relay on
weakling. The relay fixes the provider endpoint and model, supplies the OpenAI
credential from its root-owned secret store, and returns the normal Whisper JSON
response. The APK contains only its endpoint-scoped relay credential. Successful
text is committed through the editor path, and voice sessions are marked
separately for harvesting.

This is active cloud functionality.

The Smartbar treats capture and presentation as separate concerns: lossless PCM
capture remains independent from the display-rate voice meter. During recording,
the UI renders three theme-coloured signal traces; on submission they resolve to
the centre line before the existing processing scan takes over.

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

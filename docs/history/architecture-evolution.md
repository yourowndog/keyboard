# Architecture Evolution

> Status: Historical  
> Last reviewed: 2026-07-11

This document preserves why older architecture notes differ from the current
implementation. It is not a roadmap or description of live behavior.

## FlorisBoard foundation

OmniBoard began as a FlorisBoard customization. The upstream multi-module
structure, extension system, editor, Compose UI, and Snygg engine remain the
foundation. OmniBoard added personal layouts, terminal keys, LCARS themes,
voice and local-AI actions, harvesting, and a replacement English suggestion
stack.

## “Reflexes and brains” era

Early plans described SymSpell as fast “reflexes” and an on-device Gemma model
through MediaPipe as the generative “brain.” Documents from this phase refer to
`GemmaBridge.kt`, `.task` model files, MediaPipe GenAI dependencies, and older
dictionary filenames.

The SymSpell direction survived. The MediaPipe integration did not remain in
the current application.

## Heuristic scorer and personal language data

The English path evolved into shared dictionary loading, SymSpell retrieval,
additive heuristic ranking, bigram context, personal vetoes, contextual
contractions, phrase prediction, and a deliberate harvest/review loop. Several
large root reports and walkthroughs record this evolution but mix then-current
failures with durable lessons.

## Loopback LLM experiments

Two HTTP sidecar clients were explored:

- `SmolLMClient` for candidate reranking on port 8080.
- `GemmaClient` for explicit reply/rewrite/continue actions, now on port 8081.

SmolLM reranking is not currently connected to the live provider. Gemma actions
remain implemented separately from autocorrect.

## ONNX autocorrect model

The current neural work is a compact ONNX candidate scorer with a strict
Python/Kotlin feature contract. It runs in shadow mode by default and can gate
heuristically ranked auto-commits. This is a classifier/ranker over retrieved
candidates, not a generative language model.

## Layout and theme evolution

Layout work progressed from direct bundled JSON edits into:

- modifier-row merging and visibility controls
- separate alpha/modifier/space-row sizing behavior
- row gaps and height factors
- per-key runtime customization
- Layout Builder packs with units and aliases
- state-aware Snygg rules for Ctrl and row toggles

Theme experiments exposed durable lessons about IME process caching, selector
coverage, full-panel styling, and the danger of relying on invented whitelists.

## Shelved trained swipe work

Glide typing remains active. A separate effort to prepare FUTO-derived or
synthetic precomputed gesture data and trained swipe assets was shelved. Its
scripts and large assets require classification before revival or deletion.


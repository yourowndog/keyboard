# Autocorrect, Suggestions, and Neural Scoring

> Status: Canonical entry point  
> Last verified: 2026-07-11

OmniBoard uses retrieval, heuristic ranking, optional neural decision gating,
personal vetoes, casing rules, and editor commit/revert behavior. No single
class is “the autocorrect system.”

```text
editor context
  -> NlpManager request orchestration
  -> LatinLanguageProvider
  -> dictionary/SymSpell candidate retrieval
  -> CandidateScorer heuristic ranking
  -> optional ONNX neural decision gate
  -> casing, correction eligibility, and personal vetoes
  -> suggestion UI or automatic commit
  -> acceptance/revert/harvest feedback
```

Read:

- [Live pipeline](live-pipeline.md) for end-to-end behavior.
- [Heuristic scoring](heuristic-scoring.md) for ranking signals and safe tuning.
- [Neural scorer](neural-scorer.md) for the model contract and its exact runtime
  authority.
- [Phrase prediction](phrase-prediction.md) for the second smartbar row.
- [Harvesting](harvesting.md) for feedback data and review boundaries.

Phrase prediction is related but separate: it populates a second smartbar row
after a word boundary and never auto-commits.

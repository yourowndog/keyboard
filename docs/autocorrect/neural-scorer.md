# Neural Autocorrect Scorer

> Status: Canonical  
> Last verified: 2026-07-11  
> Verified against: `NeuralScorer.kt`, `LatinLanguageProvider.kt`, preference
> defaults, packaged ONNX model, tests, and `training/feature_spec.md`

## Current authority

The neural model is a candidate decision gate, not the primary displayed
ranker. The heuristic engine still determines suggestion order. When live
neural scoring is enabled, the heuristic top correction may auto-commit only if
the neural decision fires and names that same candidate.

This distinction is essential when debugging:

- Wrong suggestion order is primarily a retrieval/heuristic-ranking issue.
- Correct ordering but missing auto-commit may be a neural threshold or
  agreement issue.
- Shadow mode records what the model would do without changing commits.

## Preference defaults

- Neural shadow logging: enabled.
- Live neural gating: disabled.
- Decision margin threshold: `0.30`.

Device preferences may differ from defaults.

## Candidate set

The neural set always begins with the typed word and then edit-distance
candidates, deduplicated and capped at 12. Prefix-only completions are excluded
from the neural correction set.

Each candidate supplies:

- term
- edit distance
- log unigram frequency
- previous-word bigram count

## Inputs

The ONNX session receives:

- encoded typed word
- encoded candidate words
- five scalar features per candidate
- hashed previous and previous-previous word IDs
- candidate mask

The exact encoding, scaling, hashing, input names, and shapes are a compatibility
contract. `training/feature_spec.md` currently agrees with the Kotlin runtime
and will be promoted into the canonical model contract. Any feature change
requires coordinated Python, Kotlin, tests, export, and model-version work.

## Decision

Probabilities are computed with softmax over candidate logits. The model fires
when:

```text
top candidate != typed word
and P(top) - P(typed) > configured threshold
```

The provider then requires agreement between the neural top candidate and the
heuristic candidate being considered for automatic commit. Personal protection
and correction-eligibility checks still apply.

## Lifecycle

The ONNX model is loaded from application assets during provider preload. The
session owns native resources and is closed when the provider is destroyed.
Inference failures return no neural decision rather than crashing the typing
path.

## Validation requirements

Before replacing the packaged model:

1. Run Python/Kotlin feature-contract tests.
2. Verify ONNX input/output names and shapes.
3. Evaluate held-out ranking and false-positive behavior.
4. Compare shadow decisions on real device events.
5. Calibrate the runtime threshold.
6. Test memory use and repeated session lifecycle.
7. Enable live gating only after shadow evidence is acceptable.


# feature_spec.md — NN featurization contract (Python ⇄ Kotlin)

`train.py` (Python, titan) and `NeuralScorer.kt` (Android) MUST produce
bit-identical features. Any change here is a model-version bump.

## Inputs per word boundary

- `typed`: lowercase string the user committed (composing region)
- `cands`: candidate list from SymSpell retrieval, **always** including the
  typed string itself; per candidate: `term` (lowercase), `edit_dist`,
  `ln_freq = ln(unigram_freq + 1)`, `bigram_count(prev, term)` (raw int)
- `prev`, `prev2`: previous committed words, lowercase; null at sentence start
  (and `prev2` must be null whenever `prev` is null)

## Char encoding (typed and each candidate)

Vocab (31 ids): `pad=0`, `a..z=1..26`, `'=27`, `BOW=28`, `EOW=29`, `UNK=30`.
A word is encoded as `[BOW] + chars + [EOW]`, truncated to **22** ids total
(20 chars max, keep BOW, ensure last id is EOW). Non `[a-z']` chars map to UNK.

## Scalar features per candidate (5, this order)

| # | feature          | scaling            |
|---|------------------|--------------------|
| 0 | edit_dist        | `/ 2.0`            |
| 1 | ln_freq          | `/ 16.0`           |
| 2 | ln(bigram_ct+1)  | `/ 12.0`           |
| 3 | len(typed)       | `/ 20.0`           |
| 4 | is_typed_itself  | `1.0` or `0.0`     |

## Context hashing (prev, prev2)

FNV-1a 32-bit over the UTF-8 bytes of the lowercase word:

```
h = 2166136261
for byte b: h = (h XOR b) * 16777619   (mod 2^32)
bucket = (h mod 29999) + 1        # ids 1..29999; id 0 = null/none
```

Test vectors (word → bucket):
`the`→22679 · `i'm`→2220 · `wife's`→22284 · `dont`→26795 · `know`→20236

## Model I/O (ONNX)

Inputs:
- `typed_ids`  int64 `[B, L]`
- `cand_ids`   int64 `[B, K, L]`
- `scalars`    float32 `[B, K, 5]`
- `ctx_ids`    int64 `[B, 2]`  (hash of prev, prev2)
- `cand_mask`  float32 `[B, K]` (1 = real candidate, 0 = padding)

Output: `logits` float32 `[B, K]`. Softmax over K = P(candidate).
Dynamic axes: B, K, L. On device B=1.

## Decision rule (outside the model)

`top = argmax P`; fire iff `top != typed` and `P(top) − P(typed) > τ`.
τ is a runtime pref (`suggestion__neural_threshold`), calibrated in
`metrics.json`, never baked into the model. PersonalPreferences veto is
applied AFTER the model, unchanged.

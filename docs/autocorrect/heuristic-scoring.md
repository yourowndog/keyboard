# Heuristic Candidate Scoring

> Status: Canonical  
> Last verified: 2026-07-12
> Verified against: `CandidateScorer.kt`, `SuggestionEngine.kt`,
> `ContextualEvidence.kt`, `KeyboardLayout`, and bigram tables

The heuristic scorer assigns a penalty; lower is better. The suggestion engine
converts the penalty to confidence by negating it and sorts descending.

## Signals

The score begins with edit distance, then combines:

- physical key-distance cost for substitutions
- low-cost adjacent transpositions
- length-difference cost
- previous-word bigram reward or missing-hit penalty
- apostrophe/contraction rewards
- exact-input reward
- user-dictionary reward
- log-frequency reward
- grammar and bigram-block penalties
- selected context-specific ambiguity rules

The exact constants live in `CandidateScorer.kt` and `ContextualEvidence.kt`;
link to them rather than copying values into multiple documents. Changing a
constant affects displayed ranking, auto-commit eligibility indirectly, swipe
word scoring, and the candidate set seen by later logic.

## Physical model

`KeyboardLayout.keyDistance()` uses fixed staggered QWERTY coordinates and caps
far distances. This is a typing-error model, not the current rendered geometry.
Changing visual key positions or using a non-QWERTY layout does not automatically
retrain or update these coordinates.

Unknown characters receive the far-key fallback. Insertions/deletions are
primarily represented through edit distance and length cost rather than a
physical coordinate.

## Eligibility and penalties

The Judge does not enforce personal policy. Typed→candidate anti-correction
pairs are excluded before ranking. Protected vocabulary remains rankable, while
`CommitPolicy` vetoes automatic replacement of the protected typed form.

Grammar, bigram conflicts, and `id` ambiguity are soft numerical evidence from
`ContextualEvidence`. They can reorder visible candidates but cannot authorize
or forbid a commit.

## Safe tuning workflow

1. Capture the typed form, candidate set, previous context, edit distances,
   frequencies, and bigram counts.
2. Reproduce the ranking in a focused test.
3. Identify which signal is wrong; do not compensate blindly with a larger
   unrelated constant.
4. Check personal vetoes and contraction shortcuts before changing scoring.
5. Check whether the neural gate, rather than heuristic rank, blocked commit.
6. Add regression examples for both desired fixes and nearby false positives.
7. Validate with shadow/harvest evidence and actual editor commits on device.

## Swipe interaction

The NLP word-scoring interface is also used by glide typing. Swipe supplies its
own geometry/path evidence and calls word scoring with different assumptions.
Any scorer change should be checked for tap and glide regressions.

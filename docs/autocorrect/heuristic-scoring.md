# Heuristic Candidate Scoring

> Status: Canonical  
> Last verified: 2026-07-11  
> Verified against: `CandidateScorer.kt`, `SuggestionEngine.kt`,
> `KeyboardLayout`, bigram tables, and personal preferences

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

The exact constants live in `CandidateScorer.kt`; link to them rather than
copying values into multiple documents. Changing a constant affects displayed
ranking, auto-commit eligibility indirectly, swipe word scoring, and the
candidate set seen by later logic.

## Physical model

`KeyboardLayout.keyDistance()` uses fixed staggered QWERTY coordinates and caps
far distances. This is a typing-error model, not the current rendered geometry.
Changing visual key positions or using a non-QWERTY layout does not automatically
retrain or update these coordinates.

Unknown characters receive the far-key fallback. Insertions/deletions are
primarily represented through edit distance and length cost rather than a
physical coordinate.

## Vetoes and penalties

Personal vocabulary and explicit anti-corrections can cull a candidate. Grammar
and bigram conflicts currently add heavy penalties rather than necessarily
deleting the candidate. This distinction matters: a penalized option may remain
visible while a culled option cannot be selected.

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


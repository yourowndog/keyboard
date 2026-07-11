# Phrase Prediction

> Status: Canonical  
> Last verified: 2026-07-11  
> Verified against: `NlpManager.kt`, `PhraseTable.kt`, `BigramTable.kt`,
> `Smartbar.kt`, and phrase assets

Phrase prediction is separate from single-word autocorrect. It runs at a word
boundary when composing text is blank and feeds a dedicated smartbar row.

## Sources

1. `PhraseTable` uses the two most recent words to retrieve personal
   continuations from `ime/dict/personal_phrases.tsv`.
2. If fewer than three results exist, `BigramTable.predictPhrases()` performs a
   beam search from the most recent word.

Personal results are added first. Results are deduplicated case-insensitively
and capped at three.

## UI and commit behavior

`phraseCandidatesFlow` owns the phrase-row candidates. The row is rendered only
when enabled by its smartbar preference. Beginning a new composing word clears
the phrase candidates.

Phrase candidates are never eligible for automatic commit. Tapping one uses the
normal completion commit path, which accepts arbitrary text rather than assuming
a single word.

## Data format

The personal phrase table is tab-separated:

```text
two-word context<TAB>continuation<TAB>frequency
```

The loader lowercases contexts, retains multiple continuations, and orders by
frequency. Generated root/intermediate phrase files are not runtime truth; only
the packaged asset is loaded by the app.

## Debugging

If the phrase row is absent:

1. Confirm composing text is actually blank after the boundary.
2. Confirm `NlpManager` extracted the expected last one/two words.
3. Confirm `PhraseTable` and `BigramTable` loaded successfully.
4. Inspect `phraseCandidatesFlow` before debugging Compose visibility.
5. Confirm the phrase-row preference is enabled.
6. Check that the current smartbar mode has room to render the row.

Earlier failures blamed UI state when the upstream flow was empty. Always check
the producer before changing visibility logic.


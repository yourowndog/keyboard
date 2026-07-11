# Live Autocorrect Pipeline

> Status: Canonical  
> Last verified: 2026-07-11  
> Verified against: `NlpManager.kt`, `LatinLanguageProvider.kt`,
> `SymSpellManager.kt`, `DictionaryRepository.kt`, `SuggestionEngine.kt`,
> `CandidateScorer.kt`, and editor commit/revert hooks

## Initialization

The current English path loads:

- `ime/dict/unified_dictionary.tsv` through the shared dictionary repository.
- `ime/dict/final_mobile_bigrams.tsv` for context and prediction.
- `ime/dict/personal_phrases.tsv` for phrase continuation when present.
- `ime/nn/autocorrect_v1.int8.onnx` for neural scoring when it can be loaded.

Earlier `frequency_dictionary_en.txt`, `frequency_bigram_en.txt`, and MediaPipe
model references are historical.

## Context extraction

`LatinLanguageProvider` separates the composing word from preceding editor text
and extracts up to two prior words. The immediate previous word informs
heuristic bigram scoring; both previous words are available to the neural model.

Blank composing text produces next-word suggestions. It does not enter the
ordinary typo-correction path.

## Shortcuts

Before general retrieval, the provider handles selected high-confidence cases,
including single `i` casing and contextual/static contraction shortcuts. These
still pass through personal-vocabulary and anti-correction protections where
implemented.

## Retrieval

Candidate retrieval combines:

- edit-distance candidates from SymSpell
- prefix candidates for completion

Candidates are deduplicated case-insensitively. The provider retains knowledge
of which candidates came from edit retrieval because automatic correction must
not fire merely because a completion ranked highly.

## Heuristic ranking

`NgramSuggestionEngine.rank()` delegates penalty calculation to
`CandidateScorer`. Lower penalties become higher confidence.

Signals currently include:

- edit distance
- physical QWERTY distance and transposition handling
- word frequency
- previous-word bigram evidence
- contraction/apostrophe handling
- grammar and bigram penalties
- personal vocabulary and anti-correction vetoes
- special contextual handling for ambiguous forms such as `id`

The code contains tuned constants and hard-coded personal/contextual knowledge.
Document changes to those rules with evidence; do not present them as a general
linguistic model.

## Automatic commit eligibility

The top displayed ranking is not automatically committed merely because it is
first. The provider checks, among other things:

- whether the result differs from the typed text
- whether the typed form is already valid
- whether the change is a casing fix
- whether the candidate came from correction retrieval
- personal-vocabulary and anti-correction blocks
- minimum input length
- the optional neural decision gate

The typed word is added to the suggestions when it is not already present and
is never itself marked for auto-commit.

## Phrase prediction

When composing text is blank, `NlpManager` separately creates phrase candidates:

1. Personal `PhraseTable` continuations using two-word context.
2. `BigramTable` beam-search phrases to fill remaining slots.

At most three are exposed through `phraseCandidatesFlow`. The smartbar phrase
row displays them when enabled. They are never eligible for automatic commit.

## Feedback

Editor commit and revert paths notify the NLP system and harvesting layer.
Acceptance in a log is not necessarily proof that a correction was desired;
sequence-aware review is required to distinguish accepted fixes, immediate
reverts, manual fixes, insistence, and unresolved events.


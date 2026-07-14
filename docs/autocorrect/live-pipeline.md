# Live Autocorrect Pipeline

> Status: Canonical  
> Last verified: 2026-07-14
> Verified against: `NlpManager.kt`, `LatinLanguageProvider.kt`,
> `SymSpellManager.kt`, `DictionaryRepository.kt`, `SuggestionEngine.kt`,
> `CandidateScorer.kt`, `ContextualEvidence.kt`, `ContractionRules.kt`,
> `CorrectionDecision.kt`, `CommitPolicy.kt`, `KeyboardLayout.kt`, and editor
> commit/revert hooks

## Initialization

The current English path loads:

- `ime/dict/unified_dictionary.tsv` through the shared dictionary repository.
- `ime/dict/final_mobile_bigrams.tsv` for context and prediction.
- `ime/dict/personal_phrases.tsv` for phrase continuation when present.
- `ime/nn/autocorrect_v1.int8.onnx` for neural scoring when it can be loaded.

Earlier `frequency_dictionary_en.txt`, `frequency_bigram_en.txt`, and MediaPipe
model references are historical.

If the n-gram engine is null when a suggestion request arrives, the provider
retries engine preload subject to a recovery cooldown and logs the attempt under
the `AutocorrectPath` tag. If recovery still leaves the engine unavailable, the
provider logs that the SymSpell-only fallback is active (including only the
typed-token length, not its contents). The fallback preserves its uppercase-
heavy guard and routes its remaining auto-commit decisions through
`CorrectionDecision` and `CommitPolicy`. It records the named neural bypass
reason `ENGINE_UNAVAILABLE`; it does not pretend that the neural model approved
the fallback candidate.

## Context extraction

`LatinLanguageProvider` separates the composing word from preceding editor text
and extracts up to two prior words. The immediate previous word informs
heuristic bigram scoring; both previous words are available to the neural model.

Blank composing text produces next-word suggestions. It does not enter the
ordinary typo-correction path.

## Shortcuts

Before general retrieval, the provider handles selected high-confidence cases:
single `i` casing and explicit static contraction shortcuts. These paths
assemble their evidence through the shared, pure `ShortcutCorrection` seam,
then route commit eligibility through `CorrectionDecision` and `CommitPolicy`.
Contraction shortcuts also pass through personal-vocabulary and typed→candidate
anti-correction protections.

Static forms such as `dont` → `don't` carry an opaque rule license bound to the
normalized typed form, exact raw candidate, and `CONTRACTION_RULE` provenance.
`CorrectionDecision` revalidates that binding before it can waive valid-word
immunity or missing edit-retrieval provenance. A missing, mismatched, reused, or
wrong-provenance license fails closed.

Ambiguous valid words such as `were` and `its` have no shortcut license. Left
context alone never auto-commits `we're` or `it's`; those forms continue through
normal retrieval and may remain visible suggestions, while valid-word immunity
preserves the literal text. Sentence-start state is used only to case an
already-licensed static output such as `Dont` → `Don't`, not as grammar evidence.

These shortcut paths run before neural candidate scoring and record named bypass
reasons: `CASING_FAST_PATH` or `LICENSED_CONTRACTION_FAST_PATH`. This is an
explicit orchestration choice, not evidence that the neural model evaluated or
approved the shortcut. Any change to shortcut neural behavior must choose and
test whether the shortcut is evaluated, vetoed, or deliberately exempted.

## Neural evidence at the decision boundary

`CorrectionDecision` keeps neural state explicit until the final adaptation to
the low-level `CommitPolicy` input:

| Orchestration state | `NeuralEvidence` | Decision behavior |
| --- | --- | --- |
| Normal path, model evaluated | `Evaluated(verdict)` | Forwards the verdict unchanged; a rejection or different top candidate remains an authoritative neural veto. |
| Normal path, neural scorer disabled or unavailable | `Disabled` | Records that no evaluation occurred; the adapter supplies no low-level neural veto. |
| Casing, licensed-contraction, or engine-unavailable fallback path | `Bypassed(reason)` | Preserves the named reason; the adapter supplies no low-level neural veto. |
| Candidate unsupported by the model | `UnsupportedCandidate` | Produces a rejecting low-level verdict, so the candidate remains suggestion-only. |

Only `CorrectionDecision` translates `Disabled` and `Bypassed` into the absence
of a low-level neural veto. Callers do not pass an ambiguous null verdict.

## Retrieval

Candidate retrieval combines:

- edit-distance candidates from SymSpell
- prefix candidates for completion

Candidates are deduplicated case-insensitively. The provider retains knowledge
of which candidates came from edit retrieval because automatic correction must
not fire merely because a completion ranked highly.

Typed→candidate anti-correction pairs are excluded before general ranking.
Protected vocabulary is different: alternatives may still receive ordinary
scores and appear for manual selection, but `CommitPolicy` forbids replacing
the protected typed form automatically.

Before general retrieval, `WordSegmentation` can recover one omitted space in a
joined token such as `inthe`. A split is accepted only when the joined form is
not itself a dictionary word, both halves are dictionary words, exactly one
split survives, and the pair has observed bigram evidence. Ambiguous and
unsupported splits are left unchanged. The ordinary personal-vocabulary,
anti-correction, casing, and commit-policy safeguards still apply. When the
neural Gate is live, segmentation is suggestion-only because the current model
was not trained on multi-word candidates.

## Heuristic ranking

`NgramSuggestionEngine.rank()` delegates penalty calculation to
`CandidateScorer`. Lower penalties become higher confidence.

Signals currently include:

- edit distance
- physical QWERTY distance and transposition handling
- word frequency
- previous-word bigram evidence
- contraction/apostrophe handling
- soft grammar, bigram-conflict, and `id` ambiguity evidence supplied by
  `ContextualEvidence`

`CandidateScorer` contains numerical evidence only. `ContractionRules` owns the
shortcut tables and the set of licensed contraction forms. Large
apostrophe bonuses require membership in that set; an arbitrary dictionary
possessive such as `La's` or `function's` receives only ordinary ranking
evidence. Anti-corrections are pair exclusions before ranking, and protected
vocabulary is principally a Gate veto. Document changes to these rules with
evidence; do not present them as a general linguistic model.

## Automatic commit eligibility

The top displayed ranking is not automatically committed merely because it is
first. The provider checks, among other things:

- whether the result differs from the typed text
- whether the typed form is already valid
- whether the change is a casing fix
- whether the candidate came from correction retrieval
- protected-vocabulary and defense-in-depth anti-correction blocks
- whether a digit-bearing token is the one narrowly allowed number-row slip:
  exactly one digit in a token of at least three characters, a same-length
  alphabetic candidate, no other differing position, and a replacement letter
  adjacent to the digit in the fixed keyboard model
- minimum input length
- the explicit neural evidence state and, when evaluated, its authoritative
  verdict

All other changed digit-bearing forms remain blocked from automatic commit,
including numeric values, mixed identifiers, versions, decimal-like strings,
chemical-style forms, and hash-like data. The exception classifies the relation
between typed text and one candidate; merely looking word-like or containing a
single digit is not enough.

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

## Forensic provenance

The [autocorrect regression forensic record](autocorrect-regression-forensic-record.md)
remains a historical investigation frozen at its stated revision. Subsequent
source changes narrowly repaired the digit classifier (`f0ed5e0e`), licensed
apostrophe evidence (`91788123`), and added engine recovery/fallback logging
(`5bdad2d9`). Those implementation facts are **Confirmed from source**. The
record's questions marked **Unresolved** about which path, model state, or APK
ran during the observed device session remain **Unresolved**; these source fixes
do not retroactively establish device behavior.

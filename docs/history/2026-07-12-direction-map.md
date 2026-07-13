# Direction map: correction-system stabilization, 2026-07-12

> Status: Current direction snapshot
> Written: 2026-07-12
> Supersedes emphasis of: `docs/history/2026-07-11-direction-map.md` (destination unchanged)
> Verified against: live code (`LatinLanguageProvider.suggest`, `SuggestionEngine.rank`,
> `CandidateScorer.score`, `PersonalPreferences`, `NeuralScorer`), `tools/harvesting/build_dictionary.py`,
> the harvest corpus (`data/harvest/raw/usage_harvest.md`), and the forensic audit under `build/`.

A written checkpoint so the big picture survives a fresh thread. When this disagrees
with canonical `docs/autocorrect/*`, the canonical docs win.

## The destination (unchanged since 2026-07-11)

One pipeline, strict division of labor. Every future autocorrect fix must land in
exactly one of these layers — that is the whole point of this phase:

| Layer | Name in code comments | Job | Today |
|---|---|---|---|
| **Retriever** | DictionaryRepository / SymSpellManager | produce plausible candidates (prefix completions + edit-distance corrections) | works |
| **Judge (Ranker)** | heuristic n-gram engine → `CandidateScorer` | *order* the candidates using edit distance, spatial cost, frequency, bigram context, user boosts | works, but also does policy it shouldn't |
| **Gate (Commit policy)** | `NeuralScorer` + the inline `shouldCommit` predicate | decide whether the top candidate may *alter typed text* | works, but duplicated & contradictory |
| **Caser (Formatter)** | `CasingUtils` | apply casing / apostrophes / spacing once, at the end | works, but a second contraction map exists |

Debugging contract (load-bearing): **wrong order = a Judge bug; right order but no
auto-commit = a Gate bug.** Casing is applied exactly once, by the Caser; no path skips it.

Load-bearing principle: **no onboard learning.** Personalization is harvest → build-time
assets, never runtime state. (See memory `no-onboard-learning`.)

## The two scorers — the thing that keeps getting confusing

There are **two** scorers and they are **not** competing; they sit in different layers:

- **`CandidateScorer` (heuristic, deterministic)** is the **Judge**. It *ranks*. It is the
  thing we tune for "the right word should be first in the smartbar." It is NOT going away
  and is NOT being replaced by the neural net.
- **`NeuralScorer` (ONNX model)** is the **Gate**. It outputs
  `P(candidate is the intended correction | typed, candidate, context)` and decides whether
  the #1 candidate is *safe enough to auto-commit*. It does **not** reorder the list today
  (the async rerank is deliberately commented out).

So: **short term we are improving the heuristic Judge and the surrounding structure. Neural
tuning is explicitly deferred.** We are cleaning up and clarifying both contracts, not merging
them into one model. Deterministic safeguards never dissolve into the model.

## What this phase (2026-07-12) established

A forensic audit (reproducible scripts + tables under `build/`, untracked) found the root
cause of the recurring autocorrect pain — and we fixed the data half of it.

1. **Voice transcription manufactures phantom vocabulary.** `build_dictionary.py` admitted any
   personal word with `voice≥2` on the false premise "Whisper doesn't make typos." Whisper
   repeats the *same* mis-transcription of rare proper nouns consistently, so phantoms cleared
   the gate: `Pyrrhus→Pyrus(×11)/Puris(×5)`, `Termux→Termlux/Termogs/Turmogs/Tarmox`,
   `agentic→gentic`, plus unknowns like `Exua(×15)`. **TYPING is now the trusted intent signal;
   voice alone no longer auto-admits** — a voice-only non-AOSP token must be corroborated by
   typing or explicit approval.
2. **`approved_vocabulary` was an unreviewed backdoor.** ~290 historical INSISTED/NEW_WORD
   tokens had been dumped in without review; every one was force-written to the runtime dict.
   Audited all 360; buckets: 25 survive via AOSP, 208 via clean typing, 12 confirmed typos,
   61 voice-suspects (human-triaged with Gemini), 52 keep-explicit, 2 no-evidence.
3. **Vocabulary layer is now clean** (this commit): 360→333 approved; confirmed typos moved to
   `typo_mappings`; garbage quarantined/deleted; casing normalized; a new `segmentation_mappings`
   section records multi-token corrections (`PR OOT→proot`) that have no runtime mechanism yet.

## Where we are — honest status

Phase 1 (consolidate the substrate: DictionaryRepository) — **done** (per 2026-07-11 map).

Phase 2 (cluster the rules / separate the four layers) — **in progress, ~25%.**

- [x] Forensic diagnosis + full live-path map + rules inventory (the hard conceptual work)
- [x] Vocabulary data layer cleaned + voice-admission hole closed  ← *this session*
- [x] Delete confirmed dead code: `SymSpellManager.fix()` (0 refs), `rank()`'s auto-commit flag
      (overwritten downstream) ← *done 2026-07-12; also removed orphans: `markNextAsUserRejected`
      (write-only state), `PROPER_OVERRIDES` (0 refs), `PREV_WORDS_FOR_*` sets (live port is
      `CasingUtils.resolveContextualContraction`), `KEYBOARD_NEIGHBORS`, `MAX_EDIT_DISTANCE`*
- [x] **Extract one `CommitPolicy`** — the inline 7-clause `shouldCommit` predicate becomes a
      single pure, unit-tested function; the contradictory `rank()` flag is removed
      ← *done 2026-07-12: `ime/nlp/shared/CommitPolicy.kt` (pure object, `blockers()` returns
      named veto reasons in evaluation order), 14 unit tests in `CommitPolicyTest`*
- [ ] Seed the **regression fixture corpus** (zsh≠ssh, numeric protection, valid-word immunity,
      contractions, protected forms, Termux family, show-but-never-commit, prefix vs correction)
- [ ] De-duplicate the two contraction maps and the two protected-word sources
- [ ] Purify the Judge: move the personal-vocab / anti-correction *culls* out of `CandidateScorer`
      and into Retriever/CommitPolicy so ranking is pure evidence

Phase 3 (feedback integrity + neural) — **not started**

- [ ] **Shadow `eventId`** (see below) — top data-integrity priority
- [ ] Neural as ranking evidence + margin-based commit gate (tuning; deferred until Phase 2 lands)

Phase 4 (personal weighted edit-cost model) — **future / design only.**

Rough overall read: the *map is fully drawn* and the substrate + data layer are clean. The
structural code refactor (the visible "paradigm shift") is early — call it 20–30% of the way
through the stabilization work, but the remaining steps are small, ordered, and low-ambiguity.

## The shadow-eventId gap (the "bummer")

Every keystroke logs separate events (`NEURAL_SHADOW`, `AUTO_APPLIED`, `WORD_COMMITTED`,
`REVERTED`) but there is **no shared id** linking the neural shadow decision to what actually got
committed and whether it was reverted. That's why heavy real-world usage hasn't yet turned into a
clean "was the neural gate right?" dataset.

**The data is not lost.** The events carry `sess` + timestamp + typed token, so a retroactive
best-effort join (session + time-proximity + typed) can reconstruct most of it — messier than a
real key, but the valuable signal is recoverable. Prioritize adding a monotonic per-keystroke
`eventId` to all five event types **next in Phase 3** so future data is clean by construction.

## The one-line paradigm, for when you're back in the weeds

> Retrieve broadly → rank with the heuristic Judge → let the neural Gate decide *commit* →
> format once with the Caser. Rules live in exactly one layer. Deterministic safeguards are
> never a model's job. Vocabulary is earned by typing evidence, not by being said out loud once.

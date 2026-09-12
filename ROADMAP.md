# OmniBoard Autocorrect — Unified Roadmap

> Supersedes as planning authority: `harvest-2026-09-11-forensic-autopsy.md` (evidence only),
> `dictionary-expansion-and-typo-resolution-brief.md` (partially refuted, see §6),
> `neural-autocorrect-plan.md` (direction retained, metrics stale).
> Current behavior stays in `docs/`. This file holds only what we intend to do.

---

## 1. Why these three plans are one plan

The autopsy, the dictionary brief, and the neural plan were written independently and each
treats its own subject as the lever. They are not separable, for four concrete reasons:

**Dictionary membership is a scorer input, not a lookup table.** Every word added or removed
changes ranking through `USER_WORD_BONUS` (−1000) and `EXACT_MATCH_BONUS` (−100), and changes
gating through `VALID_WORD_IMMUNITY`. The 47k cull already moved the ground under both the
heuristic scorer and every neural metric. Dictionary work *is* ranker work.

**Every neural number on record is stale.** The `48.8% / 51.9%` fix-recall@1 table and the
80.1% reachability ceiling were measured against the pre-cull 161,466-entry dictionary.
Reachability is *defined* by dictionary contents. Nothing in the neural plan can be acted on
until it is re-baselined against the current 114,195 entries.

**Instrumentation is one-way.** Touch coordinates, per-candidate sub-scores, and event linking
do not exist in any harvest collected so far. Whatever is not instrumented before the next
harvest window is permanently absent from the training set for that window. This is the only
item on this roadmap with a real clock.

**Deterministic patches and learned ranking compete.** Every hardcoded typo mapping is a rule
the future model must reproduce or be overridden by. They are admissible only as a bridge with
an explicit removal trigger.

The harvest pipeline itself is the fourth problem: `tools/harvesting/`, `tools/dictionary/`,
and `training/` are three pipelines with three formats, `build_dictionary.py` still reads a
frozen July `.md` snapshot, and the autopsy replay scripts live in a `/tmp` directory awaiting
garbage collection.

---

## 1b. The failure budget — where the losses actually are

Three independent layers. Work on a lower layer cannot repair a higher one, and
this ordering is why the phases are ordered as they are.

| layer | question | share of losses | fixed by |
|---|---|---:|---|
| **Reachability** | did the engine run at all? | **82%** of surviving common typos | Phase 2 — turn it on |
| **Retrieval** | was the right word in the candidate list? | ~20% of correction opportunities | Phase 3 — spatial expansion |
| **Ranking** | was the right candidate chosen? | ~1% of lost intentions | Phase 4 — neural ranker |

Measured 2026-09-11 over 63 days. Common typos (`thst`, `whst`, `tbe`, `hsve`,
`wsnt`, `flr`, `kf`, `snd`, `eith`, `dure`) that survived uncorrected:

| app | typing commits | typos survived |
|---|---:|---:|
| termux | 120,141 | **275** |
| googlequicksearchbox | 12,998 | **71** |
| chatgpt | 92,237 | 48 |
| chrome | 5,107 | 17 |
| all others | ~41,000 | 13 |

346 of 424 — 82% — occurred where the engine never ran. This is the single
most important number in this document. The perceived quality of the keyboard
is dominated by its **absence**, not its judgement.

**Corollary, and it is not intuitive:** removing 47,271 junk words made wrong
answers less bizarre but produced no additional correct answers. Adding real
vocabulary produces correct answers only for words Sam actually types, which is
a bounded and surprisingly small set (§4, Phase 1.2). Neither is where the
volume is. The volume is reachability, then retrieval.

---

## 2. Locked decisions

Settled 2026-09-11. Changing any of these invalidates the phase that implements it.

| # | Decision | Consequence |
|---|---|---|
| D1 | Contractions fire on exact shortcut always; on fuzzy retrieval only above a confidence margin | Margin is **derived from corpus accept/revert behavior**, not guessed |
| D2 | `idk` enters the dictionary; the thirteen contraction bases stay out | Adding them would cost the contraction ~1080 points and kill it |
| D3 | **`im`→`I'm` stays ON.** `SHORTCUTS` keeps the entry; `im` stays out of the dictionary | Reversed 2026-09-11 on Sam's instruction. The 9 reverts are accepted cost; forced contraction is only objectionable when it invents an apostrophe Sam did not want (`idk`→`I'd`), not when it punctuates one he did |
| D4 | `its` and `were` keep their deliberate exclusion | Existing comment at `ContractionRules.kt:55` stays |
| D5 | Termux correction is driven by the **trace buffer**, scoped by package | No `setComposingRegion()`; other apps untouched |
| D6 | Termux correction is **suppressed by line shape** (command-like lines) | Protects the 1.6% command register |
| D7 | `password` / `email` / `visiblePassword` / `uri` inputTypes are **hard-off** | Overrides any app request, including `autoCorrect` |
| D8 | Outside that deny-list, `autoCorrect` **overrides** `noSuggestions` | Recovers the Google search box and browser search bars |
| D9 | Typo mappings are a **corpus-derived bridge** with a removal trigger | Not hand-written; excluded once the ranker handles the class |
| D10 | Phase 0 instrumentation lands **before** dictionary work is finalized | Only item with an unrecoverable deadline |
| D11 | A correction may **never** shorten a token to a single letter, except `i`→`I` | `I` (f=9,153,756) and `A` are gravity wells: every 2-letter slip is within 2 edits of them. Kills `uh`→`I`, `av`→`A`, `kf`→`I`, `ir`→`I`, `lr`→`I` |
| D12 | Edit distance stays at 2. **No distance-3 expansion for short words** | Measured: ED3 multiplies the candidate pool 10–15× on exactly the tokens that already mis-rank. Refutes `neural-autocorrect-plan.md` Tier 1 item 2 |
| D13 | The layout fingerprint includes number-row state; spatial models condition on it | The number row toggles, which moves every alpha key and changes what a digit-adjacency slip means |
| D14 | Voice keeps **two** transcripts per recording: verbatim and cleaned | Verbatim is ASR ground truth; cleaned feeds the language model. The keyboard must not learn to predict "um" |
| D15 | The heuristic scorer stays operational until replay shows the neural ranker wins | Both rankings logged on every decision; the flip is arithmetic, never half-and-half |

**Messenger** is treated as covered by general quality fixes. Corpus shows it firing heavily
(2.21 suggestions/commit, 1,154 auto-applied); the complaint was secondhand and its revert
profile is the short-token problem, not a separate bug.

---

## 3. Standing invariants

These hold across every phase. A change that violates one is wrong regardless of its metrics.

1. **No runtime learning.** Personalization is harvest → build-time asset. Nothing adapts on
   device.
2. **Never forced into a contraction the user didn't reach for.** D1 is the hard version of
   the oldest complaint in this project. Uncontracted beats wrongly contracted, always.
3. **A dictionary word is not automatically a correction target.** `fso` must be typeable
   without becoming a destination for `fos`. The asset format must learn to express this.
4. **Reach is earned, not assumed.** Extending the engine into a new surface requires the
   undo rate on existing surfaces to be acceptable first. This is why Phase 2 follows Phase 1.
5. **Every tuning change is measured on the replay bench before it ships**, reported as both
   reverts-prevented and good-corrections-destroyed.

---

## 4. Phases

### Phase 0 — Instrument and measure *(has a deadline)*

**Full specification: [`docs/development/harvest-schema-v4-spec.md`](development/harvest-schema-v4-spec.md).**
That document is the contract; this is the summary.

Deadline is real and unrecoverable: every day spent on other phases is a day of
typing recorded in a format that cannot answer the questions Phases 3–5 ask.

**0.0 Coordinate-frame hardening (blocks 0.3) — COMPLETE.** Source implementation
and unit coverage landed on 2026-09-11; Sam validated the deployed build on
2026-09-12 across every tested keyboard-layout configuration and permutation,
including expanded utility rows, number-row changes, and non-medium alpha
heights. See
`docs/development/spatial-coordinate-hardening-prompt.md`. The shared frame,
geometry-aware Statistical thresholds, `LayoutFingerprint`, and touch resolver
now exist and the device gate passed.
Coordinates are the one harvest field that cannot be repaired retroactively --
the distortion is a function of the layout configuration at the instant of the
tap, which is precisely what the broken frame fails to record. Logging first
would not buy early data, it would manufacture a second poisoned corpus while
burning the calendar time this phase is racing.

**0.1 Wire the dead APIs.** `HarvestManager.kt` already defines `logPicked`,
`logIntent`, `logNoSuggestion`, `logMultiAttempt`, `logSuggestionsIgnored`,
`logBackspaceStorm`, `startAttemptTracking`, `addAttempt` and
`finalizeAttemptSequence`. **All nine have zero call sites.** The struggle-capture
surface was written and never connected to the IME. Either wire them per the
spec's §12 emission table or delete them; leaving them present and uncalled is
what produced the false impression this was already done.

**0.2 Word-slot records.** One record per word, not per event: keystrokes,
offers, edits, outcome, shadow, derived signals. The
type-backspace-six-times-retype case currently produces one `WORD_COMMITTED`
and discards everything else — and it is the only case where Sam supplies the
right answer *after* the engine has already failed.

**0.3 Touch coordinates.** There are **zero** in the corpus. `trace` exists on
2.4% of commits and contains a copy of the typed string. Phase 3 is blocked on
this and cannot begin until weeks of real coordinates exist. Frame per spec §7
and D13.

**0.4 Bar picks.** `src` takes only `TYPING` and `VOICE`. Tapping a suggestion
— the highest-quality label available — is recorded as ordinary typing.

**0.5 `shadow.reachable`.** Did the intended word appear in *any* candidate
list? Three-valued: `false` (looked, missed), `null` (never ran), `true`. This
converts the retrieval ceiling from one stale offline estimate into a daily
metric, and it is Phase 3's success criterion.

**0.6 Context fields.** `inputSession`, plus hint/label/actionLabel/extras.
Register is derived offline by clustering (spec §8); the keyboard cannot see
who Sam is talking to and will not try.

**0.7 Voice split (D14).** Verbatim and cleaned transcripts, both retained.
31.6% of commits (112,882 of 357,619) arrived by dictation and currently
contaminate typed-word frequency counts.

**0.8 Salvage the replay bench** into `tools/harvest/replay/` and re-baseline
every metric in the neural plan against the post-cull dictionary. The figures
there (80.1% reachability, 51.9% precision) predate the cull and are not
currently trustworthy.

### Phase 1 — Make the engine trustworthy

Targets the 16.5% undo rate. This is the phase you feel daily.

**1.1 Contraction policy (D1–D4).** Exact shortcuts always fire. Fuzzy contraction candidates
must clear a derived margin. Remove `im` from `SHORTCUTS`. Keep `its`/`were` excluded.

**1.2 Dictionary reconciliation (D2).** `idk` goes in, at a frequency that
cannot be out-reached by `I'd`. The thirteen contraction bases stay out.

Re-add from the cull (verified: of 47,271 removed words, only 20 were ever
typed, 54 commits total): `wpm`, `ntp`, `tld`, `roundtrip`, `digram`,
`watchcases`, `jot`, `gab`, `fanboy`, `hirer`, `forefoot`, `clio`, `zappa`,
`lida`, `gur`, `dlr`, `pma`, `highfield`, `fut`.

**`fso` is NOT re-added.** Corrected 2026-09-11: its twelve commits were
capitulation, not vocabulary. Sam wanted `fos` and gave up fighting for it.
`fso` belongs on the correction-target exclusion list in 1.3, not in the
dictionary. This is the motivating case for the `capitulation` signal in the
v4 schema — a bare commit cannot distinguish intent from surrender.

**Pile A — real vocabulary currently missing.** Derived from every
out-of-vocabulary word Sam committed 5+ times, after removing code identifiers
and typos:

`futo` 124, `herdr` 121 (agent-based multiplexer), `idk` 99, `tailscale` 80,
`ccgram` 67, `cmd` 67, `zepp` 67, `agy` 48, `systemctl` 43, `bip` 40,
`watchface` 33, `florisboard` 31, `gatik` 31, `kimi` 30, `chezmoi` 23,
`mtp` 21, `wishlist` 20, `jdocmunch` 18, `enum` 18, `worktree` 16,
`amazfit` 16, `asr` 13, `smartwatch` 12, `oom` 11, `av` (AV, autonomous
vehicle), `ui` (UI, user interface).

**Pile B — code identifiers that must NOT enter the dictionary.** ~90 words
(`rowcount` 79, `layoutmanager` 53, `bottommodrowcount` 49, `isalpha` 40,
`issuitableforbasicpopup` 16, `hasslimspacerow` 15, `modrowlowergap` 14 …).
These come from typing code into Termux and ChatGPT. Admitting them creates
~90 new wrong destinations — the exact garbage the cull removed. They need
the typeable-but-not-suggestible mechanism from 1.3, not dictionary entry.

**Pile C — typos that must NOT enter the dictionary.** `thst` 33, `whst` 30,
`tbe` 18, `hsve` 14, `kf` 14, `flr` 12, `wsnt` 11. The
`dictionary-expansion-and-typo-resolution-brief.md` proposed adding these.
Doing so would permanently destroy the ability to correct them.

**1.3 Correction-target exclusion (invariant 3).** A word may be present and
typeable while being ineligible as a correction *destination*. Two populations:

- **Never typed, never wanted as a destination:** `URL`, `MTV`. Corrected
  2026-09-11 — Sam has never typed either, so they need no typeable half at all
  and are simply removed as destinations.
- **Typed but never wanted as a destination:** `fso` (capitulation), plus
  Pile B's ~90 code identifiers.

**1.4 Short-token margin gate, and the single-letter rule (D11).** Two related
mechanisms:

*Single-letter rule (D11).* No correction may reduce a token to one letter,
except `i`→`I`. Measured destinations today:

```
i  -> I    x679   (legitimate sentence capitalization, keep)
uh -> I    x8     av -> A  x7     ir -> I  x5
kf -> I    x4     ui -> I  x3     lr -> I  x3
```

`I` carries frequency 9,153,756 — second-highest in the dictionary — so every
two-letter slip sits within two edits of it and is swallowed. `kf` is a reach
for `of`; it becomes `I`. One condition removes the whole class.

*Margin gate.* Tokens of length ≤ 3 require a minimum confidence gap over the
literal, widening as length shrinks. Targets `fos`→`FSO`, `kn`→`in`. Tuned
jointly with 1.1's contraction margin — they are one problem.

**1.5 `CULLED_SCORE` leak.** Sentinel-scored candidates must be dropped from the list, not
ranked within it. Six bad commits today, unbounded in principle.

### Phase 2 — Extend reach

Only after Phase 1's undo rate is acceptable.

**2.1 Termux via buffer (D5, D6). ✅ DONE 2026-09-11.**

Terminals report `inputType=NULL`, so `isRichInputEditor` is false, the
composing region is never set, and the correction pipeline never saw a word —
120,141 Termux commits, zero corrections, 275 surviving common typos.

Implemented in `AbstractEditorInstance.kt` as a word+line buffer feeding
`nlpManager.suggest()` directly, with corrections applied via
`deleteSurroundingText` + `commitText`. No `setComposingRegion()` — terminals
do not accept composing text, which is why they report NULL. This mirrors the
existing raw-editor glide fix at `TextKeyboardLayout.kt:142`.

Line-shape suppression (D6) refuses correction when the line carries shell
syntax (`/ \ | & ; < > $ ` * ~ = % {} [] () ' "`), contains a flag or path
token, contains a digit, or is still on its first word (the command name).
Non-alphabetic tokens are never corrected.

Gated by `prefs.correction.rawEditorAutoCorrect`, **default true** — a
preference defaulting off is indistinguishable from the feature not existing,
which is exactly how the punctuation-spacing regression hid for a build cycle.

⚠️ **Device validation outstanding.** Source and unit tests cannot establish
terminal behavior under `deleteSurroundingText`. Must be exercised in Termux
before it is considered settled: ordinary prose corrects, command lines do not,
and backspace-through-a-correction restores the original.

**2.2 Field gating (D7, D8).** Hard-off deny-list by inputType; `autoCorrect` overrides
`noSuggestions` everywhere else.

### Phase 3 — Lift the retrieval ceiling

The autopsy's finding that ranking causes 1% of lost intentions while retrieval and vocabulary
cause 32% makes this the highest-value engine work — and it is mostly not machine learning.

**3.1 Edit distance stays at 2 (D12).** The plan's proposal to allow
distance 3 for short words is **refuted by measurement**, not judgement.
Candidate-pool size against the post-cull 114,195-word dictionary:

| typed | len | ≤ED2 | ≤ED3 | growth |
|---|---:|---:|---:|---:|
| `kf` | 2 | 513 | 3,203 | 6.2× |
| `av` | 2 | 784 | 4,492 | 5.7× |
| `tbe` | 3 | 480 | 4,483 | 9.3× |
| `snd` | 3 | 537 | 4,519 | 8.4× |
| `hsve` | 4 | 115 | 2,033 | **17.7×** |
| `thst` | 4 | 191 | 2,132 | **11.2×** |
| `wsnt` | 4 | 140 | 2,052 | 14.7× |

The engine already mis-ranks `thst` at 191 candidates (`thst`→`the` ×6). At
2,132 it would be unusable, and short tokens are precisely where the
single-letter gravity wells of D11 live. Short words need *tighter*
constraints, not looser.

The correct fix for this class is spatial (3.4): `kf` for `of` is lexical
distance 2 but spatial distance ≈ 0, since `k` and `o` are adjacent. Spatial
retrieval finds it **without** admitting the other 2,690 words that distance 3
would drag in.

**3.2 Completion/correction provenance split.** Tag prefix completions at retrieval and keep
them out of the auto-commit pool, so `VALID_WORD_IMMUNITY` stops doing cleanup for a retrieval
mistake it never should have received (81.6% of blocked commits).

**3.3 Corpus-derived typo bridge (D9).** Generated from harvest evidence with frequency and
revert thresholds — never hand-written. This automatically excludes the brief's `sdk`→`idk`
(one observation, and `sdk` is real vocabulary here) and `dure`→`sure` (5 reverts). Removal
trigger: an entry retires when the ranker handles its class unaided on the bench.

**3.3b Coordinate frame.** **Moved to Phase 0.0.** This was originally scoped
here, which was a sequencing error: Phase 0.3 records touch coordinates against
this frame, so the frame has to exist first. The work, the two live defects, and
the digit-handling note are unchanged --- see Phase 0.0 and
`docs/development/spatial-coordinate-hardening-prompt.md`. The only item that
genuinely belongs in Phase 3 is the consumer, 3.4.

**3.4 Spatial-aware candidate expansion.** Requires Phase 0.3 touch coordinates,
which in turn require the Phase 0.0 frame. Gaussian
proximity retrieval over the key-center grid. This is the single biggest reachability lever
and it needs no neural net.

### Phase 4 — Neural ranker v2

**4.1 Fix `agrees` semantics before anything else.** It is false on 12,441 events where
`neuralTop` is byte-identical to `typed`. Whatever it encodes, it is not agreement, and it
must not be a gating input until defined.

**4.2 Retrain** on the post-cull dictionary with real harvest events (`AUTO_APPLIED`,
`REVERTED`, `USER_PICKED`, `INSISTED`, `MANUAL_EDIT`) weighted over synthetic bulk.

**4.3 Promote to primary ranker** only on a bench result that beats the heuristic on both
fix-recall and false-fire. Until then the current neural net stays a gate — and per the
autopsy, a gate that contributes nothing on 66% of events and pulls away from the dictionary
on every disagreement.

### Phase 5 — Next-word prediction

Deliberately last. Phase 0 exists so that when this starts, `SESSION_TEXT` (typing and voice,
already tagged) is sitting in the right format with no retroactive extraction needed.

---

## 5. Sequencing rationale

Phase 0.0 is first *within* Phase 0 because coordinates are the only harvest field
that cannot be repaired retroactively; logging taps against a broken frame spends
calendar time to produce samples that must be discarded. Phase 0 is first overall
because it is the only phase whose cost grows while you wait. Phase 1 is
second because Phase 2 multiplies whatever quality Phase 1 leaves behind — extending a
1-in-6-wrong engine into 43% more of your typing makes the keyboard worse, not better. Phase 3
carries the largest measured upside but depends on Phase 0's touch data for its best component.
Phase 4 cannot be honestly evaluated until Phase 0.4 re-baselines it. Phase 5 is scaffolded
from the start and built last.

---

## 6. What was refuted, and stays refuted

Recorded so these stop consuming attention:

- **The negative-scoring literal trap does not gate commits.** `NlpManager.kt:416` selects with
  `firstOrNull { it.isEligibleForAutoCommit }` and the literal is built with that flag false.
  32.8% of committed corrections carried negative confidence. Decoupling the literal changes
  display order only.
- **Errant-space merge is not a real failure mode.** 22 events in 63 days, all singletons; zero
  `MANUAL_EDIT` traces containing both a space and a backspace. The 1,390 "concatenations" a
  merge feature would attack are `tailscale`, `chezmoi`, `florisboard` — it would be destructive.
- **The dictionary brief's §3.1 table is unreliable.** Its "Total Commits" and "Surviving
  Uncorrected" columns are the same number duplicated, making every failure read as 100%. Real
  `thst` failure rate is 33/76 ≈ 43%. Its proposed `sdk`→`idk` rests on one observation.
  Re-derived counts supersede it.
- **The brief's Tier 1A would have broken contractions.** Adding `dont` at f≈4M gives it
  `USER_WORD_BONUS` −1000 against the contraction's −20 apostrophe bonus. Not a gating failure —
  `CommitPolicy.kt:100` exempts licensed contractions from immunity — but a ranking loss of
  roughly 1080 points. The mechanism matters because the fix differs.

---


**Added 2026-09-11:**

- **"Allow edit distance 3 for short words."** Refuted by measurement (Phase
  3.1): 10–17× candidate-pool growth on exactly the tokens that already
  mis-rank. The plan's own Tier 1 item 2.
- **"The engine is drowning in garbage."** Half true, and the half that is
  false matters more. In apps where the engine runs, it mostly works. 82% of
  surviving common typos are in Termux and the Google search box, where it
  never ran. Perceived quality is dominated by absence, not judgement.
- **"`fso` is real vocabulary."** Refuted by Sam directly: twelve commits of
  capitulation after giving up on `fos`. Committed ≠ intended. This is why the
  v4 schema carries a `capitulation` signal.
- **"`URL` and `MTV` need to stay typeable."** Refuted by Sam: never typed
  either. They need removing as destinations, nothing more.
- **"Adding the contraction bases fixes the contraction problem."** Unchanged
  and still refuted: `dont` in the dictionary scores −1,100 against `don't`'s
  −20 and permanently wins.
- **"The neural ranker is the fix."** It addresses ~1% of lost intentions.
  Worth doing, last.

---

## 7. Open items

1. **Device validation of Phase 2.1 (Termux).** Landed, compiles, unvalidated
   on hardware. Highest-priority outstanding check.
2. **Log volume.** v4 is ~3–5× v3 per word; v3 is already ~200MB for 63 days.
   Rotation policy needed before this runs a month.
3. **Slack's five `fieldId` values** — if they map to channel / DM / thread
   reply, that is free register signal for a high-volume app. Checkable from
   existing data.
4. **Pile A review.** The ~26-word list in Phase 1.2 is corpus-derived and
   needs Sam's eye for anything that is not actually his.
5. **`bip` (40), `agy` (48), `cdl` (16), `hdev` (14), `rtk` (21)** — frequent
   enough to matter, unclassified. Vocabulary or typo?
6. **Tuned constants.** Sam has accepted the existing hand-tuned scorer
   constants as a stopgap ("if I have to live with it, I'll live with it").
   They retire with Phase 4, not before.
7. **Live relay.** Deferred to implementation judgement: schema first, relay
   second, both inside Phase 0. Tailnet-only, no provider keys in
   `BuildConfig`.
8. **Coordinate-frame hardening owner.** Promoted to **Phase 0.0** and scoped
   as its own session --- it blocks 0.3. Source implementation and unit coverage
   were completed on 2026-09-11; expanded-row/non-medium-height device
   validation remains. See
   `docs/development/spatial-coordinate-hardening-prompt.md`.

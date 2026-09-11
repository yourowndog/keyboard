# OmniBoard Autocorrect — Forensic Autopsy (2026-09-11 snapshot)

> Status: Evidence record
> Snapshot: `data/harvest/inbox/20260911-011828/usage_harvest.jsonl` (171.8 MB, schema `v:3`)
> Window: 2026-07-09 05:28 → 2026-09-10 23:58 (63 days, 635,404 events, 2,029 sessions, 0 parse errors)
> Method: full-stream replay in Python against `training/common.py` ports of the live scorer,
> QWERTY geometry, and dictionary assets. Scripts under `scratchpad/autopsy/`.

---

## 0. Headline

Autocorrect does not feel broken because the scorer is timid. It feels broken because
**43% of Sam's typing happens somewhere the correction engine is never invoked at all**, and
because when the engine *does* fire it is wrong often enough (1 undo in 6) to feel untrustworthy.

The two hypotheses this autopsy was commissioned to confirm — the negative-scoring literal trap,
and errant-space boundary failure — are **both refuted by the data**. They are documented here as
closed, with the evidence, so they stop consuming design attention.

| Measure | Value |
|---|---|
| Alphabetic typing commits (len ≥ 3) | 146,038 |
| …in apps with a live composing region | 83,321 (57.1%) |
| …in Termux, where no composing region exists | 62,717 (42.9%) |
| `AUTO_APPLIED` events | 5,293 |
| Auto-correct rate, all typing | **3.62%** |
| Auto-correct rate, reachable apps only | **6.35%** |
| Auto-correct rate, Termux | **0.00%** (0 events in 63 days) |
| Distinct auto-corrections later reverted | 873 / 5,293 = **16.5%** |
| Committed tokens absent from the dictionary | 5,533 / 146,038 = 3.79% |
| Typed word-volume that is ordinary prose | **74.4%** |
| Typed word-volume that is shell commands | 1.6% |

---

## 1. Executive findings

### 1.1 The engine is unreachable for 43% of typing — this is the dominant failure

`com.termux` is Sam's single largest input surface: 102,939 typing commits, 42% of all typed words.
It produced **2,081 `SUGGESTIONS_SHOWN` and zero `AUTO_APPLIED` events** across the entire window.

| App | Typing commits | Suggestions shown | Suggestions / commit | Auto-applied |
|---|---:|---:|---:|---:|
| com.termux | 102,939 | 2,081 | **0.03** | **0** |
| com.openai.chatgpt | 90,920 | 45,137 | 0.50 | 1,733 |
| com.facebook.orca | 13,347 | 29,481 | 2.21 | 1,154 |
| com.google.android.apps.messaging | 8,111 | 28,236 | 3.48 | 1,248 |

This is **not** an editor-flag or input-type effect, and that matters because the flags look like a
plausible culprit until you check them:

- `inputType` is `NORMAL` in every one of these apps.
- Termux reports `flags: none`. So does Messenger — which has the *highest* suggestion density
  in the corpus. The flag does not discriminate.
- The discriminator is the composing region. Only **1.2%** of Termux commits carry a keystroke
  `trace`, versus 10–19% in Messenger/Messages. `LatinLanguageProvider.suggest()` opens with
  `val currentWordRaw = content.composingText.toString().trim()` and falls through to next-word
  prediction when that is empty. Termux's terminal view consumes keys directly and never
  establishes an editable composing span, so the correction path is structurally unreachable.

**The register defence does not apply here.** Termux is not a place Sam types commands; it is
where he talks to CLI agents in English. Classifying typed `SESSION_TEXT`:

| App | prose | mixed | pathy | fragment | command |
|---|---:|---:|---:|---:|---:|
| com.termux | **73%** | 11% | 7% | 4% | 2% |
| com.openai.chatgpt | 72% | 13% | 7% | 5% | 1% |
| com.facebook.orca | 71% | 10% | 3% | 10% | 1% |

Across the whole corpus, prose is **74.4%** of typed word volume and genuine shell commands are
**1.6%**. The passive-in-code-register argument protects 1.6% of typing while 42% of prose goes
uncorrected as collateral.

### 1.2 When the engine does fire, it is wrong once every six times

- 913 `REVERTED` events; 880 (96.4%) resolve through the `undoes` pointer to a real `AUTO_APPLIED`.
- 873 distinct auto-corrections were reverted → **16.5% undo rate**.
- **73.4% of reverts are on typed forms that are not dictionary words.** These are real typos where
  the engine picked the wrong repair — the worst possible outcome, because the user pays the
  backspace cost *and* still has to retype.

Corrections Sam fights most:

| Typed | Engine forced | Reverts | Typed in dict? | Top app |
|---|---|---:|---|---|
| `i` | `I` | 22 | yes | Messenger |
| `fos` | `FSO` | 13 | no | Slack |
| `im` | `I'm` | 9 | yes | ChatGPT |
| `av` | `A` | 9 | yes | Slack |
| `snd` | `end` | 7 | no | ChatGPT |
| `kn` | `in` | 6 | no | Messenger |
| `uh` | `I` | 6 | yes | Messages |
| `herdr` | `her` | 6 | no | ChatGPT |
| `mt` | `MTV` | 5 | yes | Messenger |
| `irs` | `IRS` | 5 | yes | Messages |
| `tozo` | `to` | 4 | no | Amazon |
| `ur` | `URL` | 4 | no | Messages |

The pattern is short tokens being rewritten into unrelated high-frequency or acronym forms
(`fos`→`FSO`, `ur`→`URL`, `mt`→`MTV`, `uh`→`I`). These are not near-miss spelling repairs; they are
the ranker reaching for frequency when it has almost no signal to work with.

### 1.3 Most broken words never reached the engine

Of 5,533 non-dictionary tokens committed while typing:

| Reason it survived | Count | Share |
|---|---:|---:|
| No `SUGGESTIONS_SHOWN` logged for that token at all | 5,288 | **95.6%** |
| Candidates shown, but no valid 1–2 edit fix among them | 100 | 1.8% |
| Dictionary had a 1–2 edit fix, engine shipped the typo anyway | 145 | 2.6% |

The 95.6% is the Termux/no-composing-region population from §1.1. The genuine
scoring-and-ranking failure — the thing this autopsy was commissioned to find — is the 2.6% tail.

---

## 2. Root cause breakdown

### 2.1 REFUTED — the negative-scoring literal trap

**The hypothesis:** `CandidateScorer` emits penalties, `SuggestionEngine` negates them into
confidence, the typed literal is injected at baseline `0.0`, so any correction scoring `-0.4`
to `-1.2` loses to the literal and is suppressed.

**The scoring half is accurate.** `SuggestionEngine.rank()` does compute
`confidence = CandidateScorer.toConfidence(penaltyScore)`, i.e. `-penalty`. `LatinLanguageProvider`
does prepend the typed literal, unconditionally and after sorting, whenever the typed string is not
already in the ranked list:

```kotlin
val typedWordCandidate = WordSuggestionCandidate(
    text = currentWordRaw,
    isEligibleForAutoCommit = false,   // Never auto-commit the typed word
    ...
)
if (alreadyPresent || currentWordRaw.isBlank()) suggestions
else listOf(typedWordCandidate) + suggestions
```

The literal is in slot 0 at `0.0` in **62.6%** of all `SUGGESTIONS_SHOWN` events. That much is real.

**The suppression half is false.** Slot 0 does not gate commits. `NlpManager` selects with:

```kotlin
return activeCandidates.firstOrNull { it.isEligibleForAutoCommit }
```

`firstOrNull` scans past the literal, which is constructed with `isEligibleForAutoCommit = false`
and is therefore always skipped. The literal's position and its `0.0` score are invisible to the
commit gate.

The data confirms this decisively:

- In **85.8%** of the 5,293 `AUTO_APPLIED` events, the typed literal was sitting in slot 0. The
  engine corrected anyway.
- Of applied corrections whose confidence is recoverable from their own logged candidate list
  (5,274 of 5,293): **32.8% had negative confidence**, 26.9% zero, 40.3% positive. Median negative
  applied confidence: **−1.03** — squarely inside the `-0.4…-1.2` band the hypothesis predicted
  would be suppressed.
- The very first `AUTO_APPLIED` in the corpus is the counterexample in full:

```json
{"type":"AUTO_APPLIED","typed":"whag","applied":"What",
 "candidates":[["whag",0.0],["What",-0.7277],["Whig",-2.0559], ...]}
```

- Restricting to genuine typos (typed absent from dictionary) where a 1–2 edit dictionary fix was
  offered, and to events that actually terminated in a commit: corrections with **negative**
  confidence auto-applied **75.7%** of the time (1,465/1,936); corrections with **positive**
  confidence auto-applied **84.4%** (988/1,171). A negative score costs roughly 9 points of
  fire rate — it is a mild ranking headwind, not a trap.
- Events where a dictionary fix was available and the literal was committed instead: **60**, out of
  19,233 negative-confidence opportunities (0.3%).

**Conclusion:** decoupling the literal from the `0.0` baseline would change the *displayed order*
of the suggestion strip and nothing about what commits. It is a cosmetic fix. It should not be
prioritised, and the `harvest-2026-07-15` plan's framing of this as the root cause should be
retired.

### 2.2 CONFIRMED — valid-word immunity is where corrections actually die

Attributing the 28,385 events where a correction was offered, no correction fired, and the typed
form was committed verbatim:

| Blocker | Count | Share |
|---|---:|---:|
| `VALID_WORD_IMMUNITY` | 23,166 | **81.6%** |
| `TOO_SHORT` | 4,461 | 15.7% |
| No usable candidate | 400 | 1.4% |
| `NUMERIC_TOKEN` | 169 | 0.6% |
| `NOT_A_CORRECTION` (prefix) | 58 | 0.2% |
| No blocker — should have fired | 131 | 0.5% |

But immunity is mostly *correct*. The top blocked pairs are `the`→`there's` (614), `it`→`it's`
(421), `and`→`andre` (339), `to`→`town` (299), `you`→`you're` (284), `me`→`mean` (215),
`like`→`likens` (203). Overwriting those would be catastrophic.

The real signal here is that **the candidate lists are full of completions masquerading as
corrections**. `the`→`there's` is not a correction proposal; it is a next-word completion that
reached the commit gate and had to be shot down by immunity. The gate is doing cleanup work that
retrieval should never have created.

### 2.3 CONFIRMED — retrieval and vocabulary, not ranking, lose the intended word

`MANUAL_EDIT` requires trace replay to be usable at all, exactly as the brief warned:

- 1,176 `MANUAL_EDIT` events; 558 (47.4%) carry a backspace-bearing trace.
- In **38.2%** of events the `after` field is not a plausible repair of `before` — it is the next
  fragment typed. Example: `{"before":"wifes","after":"name","trace":"-⌫s⌫name"}`. Naive
  `(before, after)` labelling would have produced `wifes → name`.
- Replaying traces through `common.recover_pre_correction` recovered **314** true
  `(typed → intended)` pairs.

Why the intended word was never offered:

| Cause | Count | Share |
|---|---:|---:|
| No suggestion logged at the moment of the typo | 204 | 65.0% |
| **Vocabulary gap** — intended word absent from dictionary | 80 | **25.5%** |
| **Retrieval ceiling** — edit distance > 2, SymSpell cannot see it | 19 | 6.1% |
| Retrieval miss — list shown, intended word absent | 8 | 2.5% |
| **Ranking failure — surfaced but out-ranked** | 3 | **1.0%** |

Ranking accounts for **1%** of lost intentions. Retrieval reach and vocabulary account for 32%.
The engine is not mis-ordering good candidates; it never generates them.

The edit-distance ceiling is visible in the recovered pairs:
`coberrsation`→`conversation`, `handlenne`→`handle`, `backen`→`spoken`, `leoo`→`also`,
`updk`→`work`. All beyond `findWithinTwoEdits`.

### 2.4 REFUTED — errant-space and boundary failure is not a real failure mode

`WordSegmentation.kt` has split logic and no merge logic, as stated. The data says merge is not
needed:

- **Mid-word space followed by repair: 22 occurrences in 63 days**, every one of them a singleton
  (`tha t`, `anoth er`, `sessi on`, `keyboa rd`). Not a pattern; noise.
- **`MANUAL_EDIT` traces containing both a space and a backspace: 0.**
- 1,390 committed tokens do split into two common dictionary words — but they are overwhelmingly
  legitimate technical vocabulary: `tailscale` (79), `rowcount` (79), `ccgram` (63),
  `layoutmanager` (53), `systemctl` (43), `watchface` (33), `florisboard` (31), `chezmoi` (22),
  `worktree` (16), `candidatescorer` (11). **A join/merge feature would be actively destructive
  on this corpus** — the far bigger risk is a future splitter damaging these.
- Dot-joined tokens: 4,672 total, and inspection shows the classifier's "prose" bucket is itself
  mostly code (`ui.node` ×300, `llama.cpp` ×105, `foundation.layout` ×93, `rows.count` ×30,
  `view.measure` ×21). There is no measurable dot-for-space substitution habit. This sub-question
  is reported as **inconclusive-trending-negative** rather than quantified, because the detector
  cannot separate the two classes cleanly and the residue is small either way.

### 2.5 The neural scorer is more conservative than the heuristic, not a safety net

65,659 `NEURAL_SHADOW` events.

- `neuralTop == ngramTop` on **64.9%** of the 63,031 events with a real choice.
- **65.9% of events have `margin == 0.0`** and `neuralTop == typed` — the model is endorsing the
  literal with no separation to offer. It contributes no signal on two thirds of events.
- On the 22,117 disagreements, the neural top is a dictionary word where the heuristic top is not
  in **0** cases; the reverse happens **9,873** times. Since `ngramTop` is drawn from the
  dictionary by construction, this means every disagreement is the neural model pulling *away*
  from the dictionary and toward the literal.
- `wouldFire` is true on 19,061 events (29.0%), but **62.0% of those are prefix completions**
  (`tha`→`that`, `lik`→`like`, `thi`→`this`) that `CommitPolicy.NOT_A_CORRECTION` correctly
  refuses. Only 7,242 are genuine corrections.
- Of those genuine ones, 92.5% would fix a non-dictionary typo — including repairs the heuristic
  path misses: `thst`→`that` (59), `whst`→`what` (55), `tbe`→`the` (23), `rhe`→`the` (23),
  `ths`→`the` (21).

**Flag-semantics defect:** `agrees == false` on **12,441** events where `neuralTop` is byte-identical
to `typed` and the model's own margin is `0.0`. Whatever `agrees` encodes, it is not
"neural and heuristic concur", and it should not be used as a gating input until its definition is
pinned down.

### 2.6 Minor confirmed defect: culled candidates can reach commit

`SymSpellManager` marks rejected candidates with a `CULLED_SCORE` sentinel. In 153
`SUGGESTIONS_SHOWN` events (0.12%) an entire candidate list carries confidences below `-1e300`,
and in **6 cases a sentinel-scored candidate was auto-applied**: `Im`→`Important` (×3),
`im`→`important` (×2), `g`→`going`. This closes the loop on the `im → Important` entry in the
revert table — Sam reverted a correction the retriever had already voted to discard.

---

## 3. Empirical case studies

**`herdr` — the most-committed broken token in the corpus (120 commits).**
It is a keyboard-mash of `her`/`here` that also collides with dictionary `herd`. It appears in the
revert table twice (`herdr`→`her` ×6, `herdr`→`herd` ×4) and in the trace-replay set. The engine
alternates between two wrong repairs and the user rejects both. Neither `here` nor the intended
form wins because spatial cost on the trailing `dr` favours `herd`.

**`wifes` — trace replay vs. the naive label.**
`{"before":"wifes","after":"name","trace":"-⌫s⌫name"}` plus three `REVERTED` events against
`wifes`→`wife`. The naive `(before, after)` pair is `wifes → name`, which is meaningless. Replay
shows the user deleted `s`, then typed `name` — a *different word*. The correct reading is that
`wifes`→`wife` was rejected three times and the user moved on. Any training set built without
replay ingests `wifes → name` as ground truth.

**`fos` → `FSO` (13 reverts, Slack).**
`fos` is not in the dictionary. The ranker's best in-dictionary reach is the acronym `FSO`.
Frequency and exact-match bonuses make an unrelated three-letter acronym outrank every plausible
repair. This is the `TOO_SHORT`/low-signal regime: at length 3 there is almost no spatial evidence,
so frequency dominates completely.

**`idk` → `I'd` (34 events, top of the "should have fired" list).**
`idk` is settled chat vocabulary that the dictionary does not contain. Every occurrence is an
opportunity for the engine to damage correct text. It is the clearest single argument for
personal-vocabulary ingestion over scorer tuning.

---

## 4. Architectural recommendations

Ordered by measured impact, not by how interesting the fix is.

1. **Reach the 43%.** Nothing else in this list moves the needle as much. Termux gives the IME no
   composing region, so the correction path cannot run. Options, in preference order: detect the
   no-composing-span case and drive correction from the keystroke `trace` buffer the harvester
   already maintains; or use `InputConnection.setComposingRegion()` to establish a span
   retroactively on word boundaries. Both need a register gate so that the 1.6% of genuine command
   text stays passive — but the current behaviour gets that gate exactly backwards, protecting 1.6%
   at the cost of 42%.

2. **Fix vocabulary before touching the scorer.** 25.5% of lost intentions are words the dictionary
   does not contain, and the top non-dictionary committed tokens are Sam's actual working
   vocabulary: `futo`, `tailscale`, `ccgram`, `chezmoi`, `agy`, `zepp`, `amazfit`, `worktree`,
   `florisboard`, `idk`. Ingesting these serves two ends: it stops the engine attacking correct
   text, and it removes them from the false-positive pool that drives the 16.5% undo rate.

3. **Raise the retrieval ceiling for long words.** `findWithinTwoEdits` cannot see
   `coberrsation`→`conversation`. Make the edit budget length-proportional (e.g. `max(2, len/4)`)
   rather than fixed at 2. This is a retrieval change; it does not require touching penalties.

4. **Suppress completions before the commit gate, not at it.** 81.6% of blocked commits are
   `VALID_WORD_IMMUNITY` firing on completion candidates (`the`→`there's`) that should never have
   entered a correction candidate list. Tag provenance at retrieval and keep
   `PREFIX_COMPLETION` out of the auto-commit pool entirely. This makes the immunity rule's real
   behaviour legible instead of hiding it behind a 23,166-event blocker count.

5. **Add a margin gate for short tokens.** The revert table is dominated by length-2/3 tokens
   rewritten into unrelated words. At length ≤ 3 there is insufficient spatial evidence for
   frequency to be trusted; require a minimum confidence gap between the top candidate and the
   literal before auto-committing, and widen that gap as token length shrinks. This targets the
   16.5% undo rate directly.

6. **Repair the `CULLED_SCORE` leak.** Candidates carrying the sentinel must be dropped from the
   list, not ranked within it. Six bad commits is small, but the failure mode — committing a
   candidate the retriever explicitly rejected — has no upper bound.

7. **Do not ship the neural scorer as a gate in its current state.** It contributes no signal on
   66% of events, pulls toward the literal on every disagreement, and its `agrees` flag is
   incoherent on 12,441 events. Its 7,242 genuine corrections are real and worth harvesting as
   training signal, but it is not a safety net over the heuristic today.

8. **Closed — do not pursue.** Literal decoupling from the `0.0` baseline (§2.1): cosmetic, affects
   display order only. Space-merge/join logic (§2.4): 22 events in 63 days, and the concatenation
   corpus is legitimate jargon a merger would damage.

---

## 5. Method notes and limits

- Voice is excluded from every typing statistic. `src == "VOICE"` is 112,882 of 357,619 commits
  (31.6%) and carries no touchscreen evidence.
- "In dictionary" means presence in `app/src/main/assets/ime/dict/unified_dictionary.tsv`
  (161,466 entries) — the same asset the runtime loads.
- Applied-confidence figures cover the 5,274 of 5,293 `AUTO_APPLIED` events whose applied term
  appears in their own logged candidate list; 19 events do not and are excluded rather than assumed.
- §2.2's blocker attribution is *reconstructed* from typed/candidate shape, not read from a logged
  `Blocker` enum — the harvester does not emit one. It matches `CommitPolicy.blockers()` clause for
  clause, but adding the real blocker list to the JSONL schema would remove the inference.
- The register classifier in §1.1 is heuristic. Its prose/command split is robust (the gap is
  73% vs 2%), but its finer buckets are not, which is why §2.4's dot-for-space question is reported
  as inconclusive rather than given a number.
- The frozen `harvest_summary.md` (2026-07-03) was not consulted, per the brief.

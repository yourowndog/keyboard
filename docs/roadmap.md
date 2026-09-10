# OmniBoard roadmap

> Status: Canonical backlog
> Last verified: 2026-09-03
> Supersedes: `docs/archive/roadmap-pre-geometry-2026-07-27.md`

This file contains unfinished product work only. Implemented behavior belongs in canonical
documentation under `docs/`; experiments belong under `research/`.

The previous roadmap was archived on 2026-07-27 (`6d8caa5f`) when the keyboard-geometry
migration became the active programme, and nothing replaced it. Geometry work then stopped
after Stage 4.5 (2026-08-05), swipe and voice work took the foreground, and the backlog
below has been carried informally since. This document is the durable home for it again.

---

## 0. Project seam — what belongs here and what does not

The voice-model training programme has already moved to `~/projects/sam-piper-voice-lab`
(branch `voxcpm-v3-export`, active as of 2026-08-30). That repository is ahead of anything
described here: corrected rank-8 LoRA runs have completed, serving exists, and its own
`CURRENT-HANDOFF.md` is the source of truth. Its next step (Arms A/B in handoff section 9)
is Arms A and B in handoff section 9. Arm A (prompt-free identity baseline) is not
blocked and could start now; Arm B is explicitly gated on recording, transcribing, and
approving a clean low-energy reference take. Neither needs this repository.

What this repository legitimately keeps — dictation is a shipping keyboard feature:

- `ime/voice/VoiceManager.kt`, `ime/voice/VoiceTranscriptionInputLayout.kt`
- `net/WhisperClient.kt`, including the long-audio chunk/retry/merge resilience work
- `audio/Recorder.kt` lossless capture
- the smartbar provider selector
- `docs/architecture/voice-ai.md` and Steps 1–5 of `docs/architecture/voice-corpus-plan.md`,
  which describe capture, metadata, and upload behavior the keyboard actually performs

What should leave — research tooling for a programme that runs elsewhere:

- `tools/voice_corpus/` (~1,800 LOC: `segment_verbatim.py`, `backfill_verbatim.py`,
  `prepare_voxcpm2.py`, `adopt_unmanifested.py`, plus `configs/`, `systemd/`, `tests/`)
- Step 6 of `docs/architecture/voice-corpus-plan.md`, the model programme and evaluation table

The corpus itself already lives outside both repositories at `~/datasets/sam-voice`.

**Done means:** `tools/voice_corpus/` and Step 6 live in the voice lab; Step 6 in this repo
is replaced by a short pointer to `sam-piper-voice-lab/CURRENT-HANDOFF.md`; the plan doc's
title narrows to capture and transcription. Nothing needs to be finished here first — the
training work is already further along in the other repository.

**Size:** one session. This is a move plus a pointer, not a migration.

---

## 1. Space row as a first-class row

The row model already exists. Stage 01 of the geometry migration introduced explicit
semantic row identity, and the merged space/punctuation/action row is already classified
`PRIMARY_ACTION` rather than inferred from row index, row count, or a literal Space key
(`omniboard-artifacts/implementation/keyboard-geometry/01-semantic-rows.md`). The intended
third first-class row — alpha keys, modifier rows, and the space row as its own thing — is
therefore designed but not yet load-bearing.

What remains is the unexecuted half of the migration. Stages 00 through 05 have result
documents; Stages 06 through 09 are written but have never been run:

- `05-mode-semantics-frame-policy.md` — **run.** Honest mode roles, explicit frame groups, and
  height resolved through the common solver instead of borrowing the last Characters evaluator.
  See `keyboard-geometry-results/05-mode-semantics-frame-policy-results.md`. Portrait frame
  stability was validated on device; landscape and non-default height/gap were not.
- `06-layout-pack-schema.md`
- `07-responsive-customization.md`
- `08-text-profile.md`
- `09-legacy-removal-acceptance.md`

Stage 05 is the one that gives the space row independent geometry authority, and it is
also where `isAlpha` and `bottomModRowCount` stop being the real height inputs. Stage 09
removes them. `PRIMARY_ACTION` now takes a width scale of `1.0` from an exhaustive `when` in
`KeyboardGeometryPolicy` rather than falling into an `else`, so the space row's geometry is
addressed by name; `bottomModRowCount` survives only inside the deprecated compatibility
projection and no geometry authority reads it.

**Done means:** space-row height, padding, and gaps are addressable independently of the
alpha rows through the shared solver, with no consumer inferring the row's role from its
index or contents.

**Size:** Stage 05 is one focused effort with a device-validation build. Stages 06–09 are
separate and should not be bundled into it.

**Note:** Stage 05 also carried a pre-existing blocker — a missing default
`symbols2/western_wide` component — fixed in an isolated commit by pointing `SYMBOLS2_DEFAULT`
at `western`, the component that exists. Two follow-ups came out of it:

- **No wide Symbols2 arrangement exists.** Characters and Symbols both have wide components for
  the Coding profile; Symbols2 does not, so `=\<` is a stock-width keyboard stretched into the
  shared text-entry frame. Authoring one is a content decision — what belongs on a wide third
  layer — not a geometry change. Pinned as a `@KNOWN_DEFECT` in `LayoutAssetDiagnosticTest`.
- **Persisted subtypes keep the broken value.** `SubtypeJsonConfig` encodes with
  `encodeDefaults = true`, so any subtype saved while the bad default was in force names
  `western_wide` explicitly and decodes back to it. The corrected default repairs
  `Subtype.DEFAULT` and newly created subtypes only. Repairing an existing install needs a
  migration that rewrites unresolvable component names. The device checkpoint answered whether
  Sam's install needs one: **yes** — all three of his persisted subtypes name
  `org.florisboard.layouts:western_wide` for `symbols2` explicitly.

A third follow-up came out of the device checkpoint rather than the code: the Coding profile has
no key that reaches Symbols2 or Numeric-Advanced at all. `symbolsMod/western_wide_mod.json`
carries neither `-203` (VIEW_SYMBOLS2) nor `-205` (VIEW_NUMERIC_ADVANCED), so the repaired default
fixes a layer that is currently unreachable. Adding the key is a layout-content decision for
Stage 06.

---

## 2. Keyboard background — replacing the black block

The keyboard currently sits on an opaque black block. The investigation is already done and
recorded in `docs/theming/hard-won-lessons.md`, which documents that the Android IME host
window is transparent-format at runtime, that `SnyggSurfaceView` uses `PixelFormat.TRANSPARENT`
when a background image is present, and where the separate surface can be avoided when it is
not. `docs/theming/coverage-checklist.md` already lists window/root background and clipping.

This is a UI change with a documented evidence base, not a new investigation. The distinction
that matters and is easy to lose: visual transparency and touch pass-through are separate
concerns, and only the visual one is wanted.

**Done means:** the root surface renders the intended background — transparent, tinted, or
image-backed — with no opaque black plate behind the rows, and no regression in touch
handling or in the no-background-image fast path.

**Size:** one focused effort, on-device verification required. Read the hard-won lessons
first; this area has already cost time.

---

## 3. Autocorrect — harvest cadence and vocabulary hygiene

The harvest cadence has lapsed. `data/harvest/raw/` and `data/harvest/derived/` are dated
2026-07-12, the last review is `harvest_review_20260715.md`, and the packaged dictionary
assets under `app/src/main/assets/ime/dict/` are from the same week. That is roughly seven
weeks of typing evidence not collected, during which swipe and voice changes both landed.

Four distinct pieces of work, worth keeping separate:

1. **Resume the harvest.** Pull current device data, run the analysis, and produce a review
   for the backlog since 2026-07-15. Tooling exists under `tools/harvesting/`.
   Note that `data/harvest/raw/usage_harvest.md` is 9.2 MB and exceeds the jDocMunch
   per-file cap, so it is absent from the doc index despite being a `.md` file. Read it
   through the harvest tooling, not through doc search, and do not conclude from an empty
   search result that harvest data is missing.
2. **Scrape the dictionary.** Remove implausible and damaging entries that win corrections
   they should never win. `tools/dictionary/clean_bigram_spam.py` and `protected_forms.txt`
   are the existing levers.
3. **Add the words that are fought.** Vocabulary that is repeatedly insisted on and
   repeatedly overridden. `INSISTED` and `REJECTED` events are the direct signal.
4. **Infer rules rather than only entries.** Recurring failure shapes deserve rules, not
   one-off dictionary rows. Existing worked example: the `were`/`its` contraction default
   documented in `docs/archive/autocorrect-refactor-2026/`, which misfires after noun
   subjects because the rule's default runs the wrong way.

Two known behavioral defects that are already diagnosed and should not be re-investigated:

- Autocorrect never fires in `TYPE_TEXT_FLAG_NO_SUGGESTIONS` fields — search boxes, URL bars,
  AI prompt bars, Termux. Root cause is
  `AbstractEditorInstance.shouldDetermineComposingRegion`. The wanted behavior is not to
  remove the flag check but to split it: allow the composing region so suggestions appear,
  while keeping automatic commit off where it would corrupt input. Carried from the archived
  roadmap; still true.
- Contraction defaults misfire after noun subjects, as above.

**Done means:** a current harvest review exists, the dictionary has been scrubbed and
extended from it, and each accepted change is traceable to harvest evidence rather than
intuition.

**Size:** item 1 is a session. Items 2–4 are ongoing and should ride the recurring skill below.

---

## 4. Harvest skill — make the routine durable

A `harvest` skill exists at `~/.claude/skills/harvest/SKILL.md` and its analytical framework
is good: soft-QWERTY adjacency reasoning, and a table mapping each harvest event type to
what it actually signals. Its mechanics have drifted from the repository. It instructs a
Termux run from `~/keyboard-local` against `harvest.py` and `harvest_analyze.py`, while the
current tooling is `tools/harvesting/` in this repository, and ADB is now normally available.

**Done means:** the skill runs against current paths and tooling, covers the full loop
(sync, analyze, propose, apply, record the review), and is repeatable often enough that the
seven-week gap above cannot recur silently.

**Size:** small. Repair the existing skill; do not write a second one.

---

## 5. Swipe must work at any key height

Glide typing was revived on 2026-08-21 — FUTO's pretrained encoder now runs on-device via
ExecuTorch (`57f3b912`, `7d09cd79`) — but it only decodes acceptably at one specific
keyboard height. Note that `docs/architecture/glide-typing.md` still describes glide as
shelved and is stale; it was last verified 2026-07-11, before the engine landed.

Both key centres and gesture points are normalized into the same 0..1 space, so the obvious
class of bug is already excluded. `FutoGlideTypingClassifier.setLayout` normalizes key
centres by the letter-block rect, and `getSuggestions` normalizes the recorded path by the
same `boardLeft`/`boardTop`/`boardW`/`boardH`.

The likely cause is that this normalization is per-axis. Dividing x by `boardW` and y by
`boardH` independently maps the letter block onto a unit square regardless of its true
aspect ratio. When key height changes, the block's real aspect ratio changes, the same
physical gesture produces a differently-shaped normalized path, and the model — trained on
paths from one geometry — sees a distorted input. That matches the reported symptom
precisely: correct at the height that happens to reproduce the training aspect ratio,
degrading away from it.

**Investigate first, before any change:** confirm the aspect-ratio hypothesis by logging the
letter-block rect and its ratio at several configured heights and comparing decode quality
against it. Candidate fixes, in order of preference — normalize both axes by a single scale
and letterbox, or supply the aspect ratio to the decoder — depend on what the FUTO model
expects, so establish that before choosing.

**Done means:** decode quality is stable across the configured key-height and offset range,
verified on device at a minimum of three heights.

**Also:** refresh `docs/architecture/glide-typing.md` once this is settled. It currently
misdescribes shipped behavior.

---

## 6. FUTO vocabulary — possible retrain

Conditional on item 5. The packaged FUTO assets are dated 2026-08-21:
`app/src/main/assets/futo/en.combined` plus three model directories. If, after the geometry
fix, decoding still misses vocabulary that harvest evidence shows is used regularly, the
vocabulary set is the next lever. `research/swipe-training/analyze_vocab_gaps.py` and
`extract_futo_swipes.py` exist for exactly this question, and
`tools/harvesting/extract_harvest_swipe_vocab.py` connects harvest output to it.

**Do not start this before item 5.** A geometry-induced decode failure will look exactly
like a vocabulary gap, and retraining against that misdiagnosis would waste the run.

**Done means:** either a measured decision that the current vocabulary is sufficient, or a
retrained set with a before/after comparison on real harvested vocabulary.

---

## Sequencing

Ordered by ratio of shipped value to effort and by unblocking:

1. Project seam (section 0) — cheap, and it stops this repository from accumulating more
   research drift.
2. Harvest skill repair (section 4), then resume the harvest (section 3, item 1) — the skill
   makes everything downstream of it recurring rather than manual.
3. Background transparency (section 2) — self-contained, visible, evidence already gathered.
4. Swipe height investigation (section 5) — investigation before implementation; gates item 6.
5. Geometry Stage 05 (section 1) — largest single item; do not start it alongside another
   on-device change, because both need device validation to be attributable.
6. Dictionary scrub and rule inference (section 3, items 2–4) — continuous, rides the skill.
7. FUTO vocabulary (section 6) — only if item 5 proves it is needed.

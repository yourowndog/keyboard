# OmniBoard planning state

> Snapshot: 2026-10-28
> Scope: Keyboard / OmniBoard, Sam's personal FlorisBoard-derived daily driver.
> Status: Planning synthesis with explicit evidence limits; not a runtime acceptance report.

## Purpose and authority

This document brings the existing programmes together: KeyGeo migration, LCARS chassis,
autocorrect, swipe, voice, and supporting maintenance. It preserves their separate numbering
and identifies the last supported position of each.

Use this as the entry point for project-wide planning. Detailed contracts remain in their
linked roadmaps. Current behavior belongs in subsystem documentation and must be verified
against code, tests, and a known device build. This snapshot does not mark candidate work
accepted or authorize additional implementation.

Evidence was reconciled from the Sleeper checkout, fetched Git refs, repository documents,
and prior October 2–3 verification reports. Reports are identified as reports below;
no fresh APK build, installation, or device acceptance was performed for this document.

## Overall destination

- Independently usable **Text** and **Coding** experiences on one dependable geometry system.
- Structural customization that reflows keys rather than overlapping neighbors.
- One coherent LCARS panel carrying Smartbar, instruments, and keys.
- Reliable dictation with recoverable recordings and explicit provider selection.
- Better typing and swipe behavior, measured against actual usage evidence.

## 1. Geometry — KeyGeo Stages 00–09

Detailed authority:
[geometry migration plan](../omniboard-artifacts/docs/architecture/keyboard-geometry-migration-plan.md)
and [implementation contracts](../omniboard-artifacts/implementation/keyboard-geometry/README.md).

| Stage | Deliverable | Last supported position |
|---|---|---|
| 00 | Baseline contracts and characterization evidence | Completed |
| 01 | Explicit semantic row identity on construction paths | Completed |
| 02 | Shared geometry solver, initially beside legacy calculations | Completed |
| 03 | Structural gaps; separate structural, visual, and touch bounds; normalized geometry | Completed |
| 04 | Text/Coding profile identity and scoped persistence | Completed |
| 04.5 | Settings legibility and truthful active-layout display | Completed and accepted |
| 05 | Honest mode semantics and explicit frame policy | Completed and integrated into `dev` at `cf2b25c96` |
| 06 | Versioned layout-pack semantics, compatibility, and restoration | Prior reports identify verified build `998e9d9ca`, installed on Icarion; fetched ref `origin/keygeo-stage06-verified` preserves that commit |
| 07 | Responsive structural customization targeting profile/layout/row/key instances | Contract exists; completion not established |
| 08 | Real conventional Text profile and profile selection | Contract exists; completion not established |
| 09 | Legacy removal and full physical-device acceptance | Contract exists; completion not established |

Stage 04's profile identity does not mean Stage 08's Text experience is finished.
Stage 07 must make widening a key reflow its neighbors. Stage 09 removes obsolete
heuristics only after their production consumers disappear.

**Remaining sequence:** reconcile Stage 06 acceptance and integration, then 07 → 08 → 09.
Each remains a separately reviewable assignment.

**Known follow-ups:** the older product backlog records missing wide Symbols2 content,
persisted subtypes naming the broken Symbols2 component, and no Coding key reaching
Symbols2/Numeric-Advanced. Reconcile those items against Stage 06 before reopening them.
Stage 05's backlog also retains landscape and non-default height/gap device checks;
a later verified stage does not by itself prove every earlier matrix cell passed.

## 2. Appearance — opacity and LCARS chassis

The agreed sequence is:

**Opacity foundation → unified LCARS chassis → visual audit → further inspiration.**

The target is an opaque shaped chassis inside a transparent-format IME window.
Key gaps reveal the chassis; exterior space reveals the underlying app.
Visual transparency must not introduce touch pass-through.

| Checkpoint | Deliverable |
|---|---|
| C0 | Specification and reference evidence |
| C1 | Pure normalized chassis geometry model and tests |
| C2 | Debug Visualizer Lab |
| C3 | Primary LCARS elbow |
| C4 | Secondary band, channel, and instrument viewport clipping |
| C5 | Semantic Snygg theme-role adapter and cross-theme fallback |

These C checkpoints are separate from the KeyGeo stage numbers. Geometry owns
measurements, paths, clipping, and touch allocation; Snygg supplies appearance and state.

**Position:** specification branch `design/lcars-keyboard-chassis` exists at
`d6b0a604a`. Background-opacity work has its own `theme-window-background` lane.
Prototype chassis code exists. Prior reports rejected an early candidate because its renderer
was not mounted in the live IME, then identified a later clean-room candidate whose tests and
build passed but whose Android visual acceptance remained outstanding. That candidate's
APK SHA-256 begins `4bd71c58`; this is an artifact hash, not a Git commit.

The inspected primary checkout contains uncommitted LCARS source/theme changes.
Their presence is evidence of work in progress, not proof of integration or acceptance.

**Acceptance still to reconcile:** live renderer mount, pixel snapping, clipping, tangent
continuity, theme-role behavior, screenshots, opacity, touch handling, and both orientations.
Keep existing voice capture intact while mounting it within the instrument viewport.

Sources: [product backlog, background work](roadmap.md#2-keyboard-background--replacing-the-black-block),
[theming guide](theming/README.md), and the specification branch.

## 3. Autocorrect — unified Phases 0–5

Detailed planning authority: [root autocorrect roadmap](../ROADMAP.md).
Current subsystem guide: [autocorrect](autocorrect/README.md).

| Phase | Purpose | Last supported position |
|---|---|---|
| 0 — Instrument and measure | Reliable touch, word-slot, offer, correction, and outcome data | Coordinate-frame section records implementation and September 12 device validation. Later commits add v4 word-slot dual-write and mixed-schema migration; full instrumentation closure is not established |
| 1 — Make the engine trustworthy | Contractions, dictionary reconciliation, harmful correction targets, short-token margins, sentinel-score handling | Detailed plan exists; full completion not established |
| 2 — Extend reach | Appropriate field gating and correction in terminal prose | Termux implementation marked done; its hardware acceptance remains listed as outstanding |
| 3 — Lift the retrieval ceiling | Separate completion/correction provenance and improve candidate retrieval, including spatial expansion | Planned; spatial expansion depends on trustworthy collected coordinates |
| 4 — Neural ranker v2 | Re-baseline, retrain, and promote only after measured improvement | Later phase |
| 5 — Next-word prediction | Use the prepared data foundation for prediction | Deliberately last |

**Dependency logic:** reliable measurements first; reduce harmful corrections before extending
their reach; improve candidate retrieval before relying on learned ranking to solve the problem.
Spatial retrieval needs a useful accumulation of real touch data.

A recurring operational lane supports this programme:
**harvest → analyze → review → apply evidence-backed changes → record results**.
Repair the existing harvest skill rather than creating a competing routine.

**Record conflicts to resolve:** the root roadmap's coordinate-hardening section says the
device gate passed, while its older open-items list still says validation remains. Its
instrumentation descriptions also precede the v4 logging commits. Audit current emission
sites and representative output before treating those old descriptions as current defects.

**Open decisions/checks:** Termux prose versus command suppression and correction undo;
log rotation for larger v4 records; Sam's review of ambiguous vocabulary; a repeatable,
current evaluation baseline. The old neural metrics require re-baselining.

## 4. Swipe — geometry, then vocabulary, then conditional retraining

This lane includes both on-device decoding and a separate synthesis/training data foundation.

The product backlog records a FUTO-based on-device engine whose quality changes with key
height. It proposes investigating coordinate/aspect-ratio handling before choosing a fix.
That explanation remains a hypothesis until measured.

**Order:**
1. Establish the cause of geometry-dependent decoding.
2. Fix and validate across at least three heights.
3. Measure actual vocabulary gaps.
4. Retrain only if the remaining evidence warrants it.

The [swipe data architecture](swipe-data-and-pipeline-architecture.md) records a preserved
raw FUTO foundation and separation from contaminated legacy derivatives.
`swipe-synthesis-v2` preserves synthesis work; its existence does not establish final
daily-driver acceptance of height-independent decoding.

Sources: [swipe backlog](roadmap.md#5-swipe-must-work-at-any-key-height) and
[conditional vocabulary work](roadmap.md#6-futo-vocabulary--possible-retrain).

## 5. Voice — operational dictation, reliability, and a separate research programme

The original mic-to-Whisper wiring plan is historical. Current
[voice documentation](architecture/voice-ai.md) describes:

- OpenAI Cloud, Titan Local, and Weakling Local provider selection.
- Durable Voice Inbox records and pending-take recovery.
- Cleaned/verbatim transcripts where the provider supports both.
- Recording lifecycle protection and archival acknowledgement.

The primary local `device-validation` checkout also contains commit `87dbba44c`
(October 5), adding a transcription watchdog and queue progress around stuck takes.
At inspection it was one commit ahead of fetched `origin/device-validation`.
This planning-only branch does not publish that implementation commit.

**Project boundary:** OmniBoard owns recording, transcription, metadata, and upload.
Voice-model research/training belongs in `sam-piper-voice-lab`; the
[product seam plan](roadmap.md#0-project-seam--what-belongs-here-and-what-does-not)
records remaining tooling/documentation separation.

**Separate AI actions:** Gemma reply/rewrite/continuation client wiring exists, but
current end-to-end sidecar availability requires live verification. SmolLM's candidate-ranking
call is commented out. Neither is equivalent to the packaged ONNX autocorrect gate.

## 6. Supporting maintenance

- **Harvest routine:** maintain current paths, review cadence, and evidence-backed updates.
- **Evaluation:** preserve repeatable baselines and reconcile them after dictionary/schema changes.
- **Upstream backports:** [modernization radar](development/upstream-backport-radar.md);
  explicitly non-immediate work, with compatibility review around local geometry, correction,
  preferences, and IME behavior.
- **Documentation:** keep implementation behavior in subsystem guides and future work in
  detailed roadmaps. Update this overview when acceptance or integration changes.

## 7. Git landmarks

Observed after fetching `origin` on October 28. Branches are evidence locations;
they are not all accepted releases or equally current integration targets.

| Ref | Role / observed state |
|---|---|
| `dev` | Stage 05 integration point, `cf2b25c96` |
| `origin/keygeo-stage06-verified` | Preserved Stage 06 commit, `998e9d9ca` |
| `device-validation` | Primary Sleeper checkout at `87dbba44c`, with uncommitted LCARS and other work |
| `origin/device-validation` | Fetched published baseline `c680cba64`; base of this documentation branch |
| `design/lcars-keyboard-chassis` | Chassis specification, `d6b0a604a` |
| `theme-window-background` | Background-opacity implementation lane |
| `work/autocorrect-eval-baseline` | Evaluation harness and committed baseline, `825be519f` |
| `work/keyboard-titan-harvest` | Harvest/training lane at `c680cba64` |
| `swipe-synthesis-v2` | Synthesis work, `55a97b0a8` |

## 8. Immediate planning reconciliation

The immediate uncertainty is which completed pieces are integrated and accepted together
on a known daily-driver build. This is the next planning pass, not a new implementation phase:

1. Record the accepted Stage 06 evidence and its relationship to the intended integration branch.
2. Identify the exact LCARS/opacity candidate and close its visual/device acceptance.
3. Reconcile autocorrect instrumentation plans with current v4 emission and evaluation evidence.
4. Record unresolved Termux, swipe-height, orientation, and non-default-geometry checks.
5. Update the detailed roadmaps and this overview, then proceed through existing dependencies.

Preserve the distinctions between **planned**, **implemented**, **built**, **installed**,
and **accepted**. A successful build is not evidence that a renderer is mounted, that a
terminal edit behaves correctly, or that the installed daily driver contains the candidate.

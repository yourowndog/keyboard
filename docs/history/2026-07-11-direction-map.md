# Direction map: the autocorrect overhaul, 2026-07-11

> Status: Historical snapshot
> Written: 2026-07-11
> Verified against: `docs/autocorrect/*`, `docs/architecture/system-map.md`,
> `ROADMAP.md`, `training/README.md`, session memory notes, and the state of
> the code on `repo-hygiene/canonical-reorganization`

A point-in-time synthesis of where the autocorrect rework stands and the path
it is on. Written to survive session loss; when this disagrees with canonical
docs, the canonical docs win.

## The destination

The target architecture (the "Brain Transplant" in code comments) is a single
pipeline with a strict division of labor:

- **Retriever** — one shared `DictionaryRepository` generating candidates
  (bounded edit-distance scan, char-mask prefilter + banded OSA, seeded by
  harvest error pairs).
- **Judge** — the heuristic n-gram engine, which *orders* what appears in the
  smartbar.
- **Gate** — the ONNX neural scorer, which decides whether the top correction
  may *auto-commit*. Deliberately not the ranker. The debugging contract:
  wrong order is a heuristic bug; right order but no commit is a gate bug.
- **Caser** — `CasingUtils` applies casing once, at the end. No path may skip
  it (the 2026-07-11 sentence-start contraction fix enforced exactly this).
- **Harvest** — the append-only JSONL feedback loop, shadow decisions joined
  to real outcomes, feeding the fully offline cycle:
  `pull → extract → noise → synth → eval → shadow → tau_sweep → promote`.

Load-bearing principle: **no onboard learning.** Personalization is
harvest → build-time assets, never runtime state.

## Where we are

**Phase 1 — consolidate the substrate: done.** The dictionary rewrite landed
(b77120f4): SymSpell's precomputed deletion index replaced by the shared
`DictionaryRepository`. Measured: init 9.4s → 0.7s, steady heap 195MB → 70MB,
lookups 2–15ms. Duplicate dictionary loads and the ONNX session leak fixed.

**Phase 2 — stabilize the live path after the transplant: essentially
finished this week.** Reconnecting `PersonalPreferences` exposed a chain of
latent bugs (Im→Important, Durex-over-Sure prefix ranking, the punctuation
spacing saga). The spacing root cause turned out to be configuration, not the
suspected race: `correction__auto_space_punctuation` defaults to false and was
never set on-device, so the entire re-add path was configured off while the
pref-ungated eat-trailing-space path kept running. Fixed on-device; the
default flip in `AppPrefs.kt` is still pending.

**Phase 3 — the rules audit: pending; this is the next real move.** The
recorded hit list: `SymSpellManager.fix()` is dead code (its context rules
never ran); `CasingUtils.CONTRACTION_SHORTCUTS` blind-fires and had drifted
from a private copy; suspect entries remain (km→I'm, moms→Mom's); the
numeric-token guard from `ROADMAP.md` (the 742→PS2 REVERTED event is retained
as a regression fixture). Intent: collapse the shortcut maps, personal vocab,
anti-corrections, and casing special cases into one declarative,
build-time-generated personal-rules asset — every special case either becomes
data or dies.

**Phase 4 — promote the neural gate: waiting on evidence.** The model runs in
shadow mode with outcome correlation. Discipline: live gating stays off until
shadow evaluation justifies a threshold (`tau_sweep.py`).

**Phase 5 — prediction/autofill track.** Approved plan: phrase completion and
ghost-text UI first, then a tiny from-scratch personal LM trained on harvest
data (pretrained SmolLM-class models were tested and are too slow on-device).

## What "enterprise-level UX" means on this trajectory

Every behavior gets a feedback loop and every special case gets an owner:
harvest gives ground truth, shadow mode makes changes measurable before they
are live, MemProfiler baselines make performance regressions detectable, and
REVERTED events become regression fixtures.

Gap exposed this week, worth adding to the audit: **configuration
observability.** Two overlapping spacing mechanisms (phantom space and the
pref-gated auto-space) and silent pref gates let a feature stay dead for days
with nobody able to see why. A startup-time log of effective feature flags
would have found the spacing bug in one glance. Related lesson: versionName
stamps the last commit, so builds from uncommitted trees are
indistinguishable by version string — identify builds by dex symbols.

## Loose ends as of this snapshot

- Flip `autoSpacePunctuation` default to true (one line, `AppPrefs.kt`) so a
  data clear cannot silently kill the feature again; watch for double spaces
  from phantom space and auto-space both being active.
- The 2026-07-10 report of "seriously degraded" performance after 7e89316f
  was never formally diagnosed; likely resolved incidentally, but the
  DictionaryRepository latency samples and MemProfiler are the first stop if
  anything still feels heavy.
- Auto-caps frequently fails to engage after punctuation; the sentence-start
  contraction fix compensates in the contraction path rather than curing it.

# Session Prompt — Spatial Coordinate Frame Hardening

**Scope:** one session, one agent, one job. Do not expand into autocorrect
ranking, dictionary work, or the harvest schema beyond the coordinate fields.

**Priority: this blocks Phase 0.** It is not a Phase 3 item and must not be
scheduled like one. Read the next section before anything else.

---

## The job in one sentence

Make the keyboard's spatial coordinate frame correct and stable across every
layout configuration Sam uses, so that **glide typing stops degrading** and
**tap telemetry becomes trainable** — these are the same defect and must be
fixed as one piece of work.

---

## Why this is one job and not two

Sam's keyboard is heavily customizable: key heights vary, inter-key padding
varies, the number row toggles on and off, and four or five mod rows stack at
the bottom. The only invariant is that the alphabet is always QWERTY.

Two consequences, currently both unhandled:

1. **Glide typing already degrades.** Documented in
   `docs/development/touch-coordinates-and-spatial-telemetry.md` §4: swipe works
   reliably only at roughly medium key height, with the keyboard not pushed too
   high, and with mod rows unexpanded. This is a live defect Sam lives with.
2. **Tap telemetry is about to be collected** (harvest schema v4, Phase 0.3) and
   will be corrupted by the same distortion if it lands first.

The root cause is shared. Fix the frame once.

---

## Why this runs before the logger, not after

This work gates Phase 0. Both consumers of spatial data feed from the same
frame, and the frame is currently wrong:

- **Glide typing** reads it now, and degrades now.
- **The v4 logger** (Phase 0.3) will read it the moment it is wired.

Coordinates are the one kind of harvest data that cannot be repaired after the
fact. A misspelled word logged with the wrong dictionary can be re-scored later
against a better one, because the text is still there. A tap logged against a
distorted frame is not recoverable by any later pass: the distortion is a
function of the layout configuration at the instant of the tap, and that
configuration is exactly what the broken frame fails to record. There is no
back-computation. The sample is simply wrong, and indistinguishable from a
sample that is right.

So shipping the logger first does not buy early data. It manufactures a second
poisoned corpus while the first one is still being cleaned up, and it burns the
one resource Phase 0 is racing: calendar time during which Sam is typing. Every
day the logger runs on a bad frame is a day of taps that has to be thrown away.

The dependency, concretely:

```
  coordinate frame  ->  Phase 0.3 (tap telemetry)  ->  Phase 3.4 (spatial retrieval)
         |                                               ^
         +-----------> glide typing (already broken) ----+
```

Nothing downstream of the frame can be trusted until the frame is. Phase 3.4 --
Gaussian proximity retrieval, the single largest reachability lever that needs
no neural net -- consumes Phase 0.3's output directly, so a bad frame does not
just delay it, it silently degrades it.

`ROADMAP.md` files this as **Phase 0.0**, ahead of 0.3 for this reason.

---

## Required reading, in order

1. `docs/development/touch-coordinates-and-spatial-telemetry.md` — the whole
   file. §3 is the reference pattern, §4 names the two defects.
2. `docs/development/harvest-schema-v4-spec.md` §5 (`layout`) and §7
   (coordinate frame) — the consumer contract you must satisfy.
3. `ROADMAP.md` §4 Phase 3.3b and decision D13.

---

## Authoritative files

| file | role |
|---|---|
| `ime/text/gestures/FutoGlideTypingClassifier.kt` | lines 230–256 hold the reference normalization — alpha-key bounding box |
| `ime/text/gestures/StatisticalGlideTypingClassifier.kt` | line 160 — the hardcoded threshold defect |
| `ime/keyboard/KeyboardGeometrySolver.kt` | derives row heights; source of aspect distortion |
| `ime/keyboard/KeyboardGeometryPolicy.kt` | geometry policy |
| `ime/text/keyboard/TextKeyboardLayout.kt` | touch interception, key resolution |
| `app/FlorisImeSizing.kt` | keyboard height, base row height, insets |

---

## The two defects to fix

### Defect 1 — vertical aspect distortion

`FutoGlideTypingClassifier.kt` normalizes `cy` using
`boardH = maxOf(letters.bottom) - minOf(letters.top)`. When
`KeyboardGeometrySolver.kt` compresses or stretches the alpha rows relative to
horizontal key widths, a normalized vertical distance stops meaning what it
meant when the gesture thresholds were tuned. Trajectory angle and velocity
thresholds skew accordingly.

The frame itself is right — alpha-only bounding box is correct and immune to
mod rows. What is missing is **aspect correction**: normalized coordinates must
either be aspect-corrected before threshold comparison, or thresholds must be
scaled by the current aspect ratio.

### Defect 2 — hardcoded distance threshold

```kotlin
// StatisticalGlideTypingClassifier.kt:160
distanceThresholdSquared = (keyViews.first().visibleBounds.width / 4).toInt()
distanceThresholdSquared *= distanceThresholdSquared
```

Derived from one key's width, with no account for non-uniform vertical
stretching, mod-row insertion, or non-medium row heights. Must become a
function of the actual current geometry.

---

## What to deliver

1. **A single canonical coordinate frame** used by both glide classification and
   tap telemetry. Alpha-key bounding box per `FutoGlideTypingClassifier.kt:230-256`,
   with aspect correction applied consistently.
2. **Aspect-corrected thresholds** replacing both hardcoded constants.
3. **A `LayoutFingerprint`** — a stable hash over everything that moves keys:
   alpha row count, per-row height multipliers, padding, gaps, **number-row
   presence**, mod-row stack counts (top and bottom), one-handed offset,
   orientation, DPI. Plus the alpha block's width, height and aspect ratio
   exposed as readable fields. This is consumed by harvest v4 `layout` (spec §5)
   and lets training condition on configuration rather than silently mixing
   incomparable samples.
4. **A key-resolution helper** returning, for a raw `(touchX, touchY)`:
   `xn`, `yn` (normalized within the alpha block), `dx`, `dy` (offset from the
   resolved key's centre as a fraction of that key's width/height, right/down
   positive), and the resolved key. Exact formulas in spec §7.1. Do not write
   harvest records — only expose the helper; Phase 0 wires it.
5. **Tests** proving frame stability: the same physical key position must yield
   `xn`/`yn` agreeing within 2% across at least three deliberately different
   configurations (key height, mod-row count, number row on/off), while
   `LayoutFingerprint` differs across all three.

---

## Constraints

- **Do not** change autocorrect ranking, `CommitPolicy`, the dictionary, or
  `HarvestManager`. Expose the helper; do not consume it.
- **Do not** alter the glide *algorithm*. Fix its coordinate inputs and
  thresholds only. Gesture recognition quality must not regress.
- Preserve unrelated dirty worktree changes — the tree is normally dirty.
- **Device validation is required** and source review cannot substitute for it.
  Glide must be exercised at minimum three configurations including the ones
  currently known to fail: expanded mod rows, and non-medium key height.
- Do not push, invoke the build factory, or install an APK unless asked.

---

## Definition of done

- Both defects in §4 of the telemetry doc are closed, and that document is
  updated to say so rather than describing them as open.
- Glide typing works at expanded mod rows and non-medium key heights — verified
  on device, with the configurations tested named explicitly.
- The key-resolution helper and `LayoutFingerprint` exist, are tested, and match
  the harvest v4 spec §5/§7 field-for-field.
- `ROADMAP.md` Phase 3.3b is marked done with the device validation recorded.

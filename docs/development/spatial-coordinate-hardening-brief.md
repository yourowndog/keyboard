# Spatial Coordinate Frame Hardening — Implementation Brief

> **Phase:** 0.0 (blocks 0.3)  
> **Decision:** D13  
> **Scope:** One session, one agent. Coordinate frame only — no autocorrect,
> dictionary, harvest wiring, or glide algorithm changes.

---

## Problem

Sam's keyboard geometry is fully customizable — key heights vary, inter-key
padding varies, the number row toggles, and up to five mod rows stack at the
bottom. The only invariant is the QWERTY alphabet.

The glide classifier already normalizes coordinates against the alpha-key
bounding box (`FutoGlideTypingClassifier.kt:230–256`), which is correct and
immune to mod rows. But two defects in how that frame is *consumed* cause glide
typing to degrade outside a narrow "medium height, no expanded mod rows"
configuration — a live bug Sam works around daily. The same distortion would
corrupt tap telemetry the moment Phase 0.3 wires it.

Both defects share one root cause. Fix the frame once.

---

## The Two Defects

### Defect 1 — Vertical aspect distortion

[`FutoGlideTypingClassifier.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/gestures/FutoGlideTypingClassifier.kt#L230-L256)
normalizes X and Y independently into `[0, 1]`:

```kotlin
cx[i] = ((k.visibleBounds.left + k.visibleBounds.right) / 2f - boardLeft) / boardW
cy[i] = ((k.visibleBounds.top  + k.visibleBounds.bottom) / 2f - boardTop)  / boardH
```

[`KeyboardGeometrySolver.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/geometry/KeyboardGeometrySolver.kt)
solves row heights and key widths on completely independent axes — there is no
cross-axis aspect constraint. When `alphaRowHeightPercent` changes or
`FramePolicy.FitToHeight` redistributes vertical space, `boardH` changes while
`boardW` stays constant. A normalized vertical distance stops meaning what it
meant when gesture thresholds were tuned.

**The frame itself is correct** — alpha-only bounding box is the right choice.
What is missing is **aspect correction**: either normalize into isotropic space
before threshold comparison, or scale thresholds by the current aspect ratio.

### Defect 2 — Hardcoded distance threshold

[`StatisticalGlideTypingClassifier.kt:160`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/gestures/StatisticalGlideTypingClassifier.kt#L160-L163):

```kotlin
distanceThresholdSquared = (keyViews.first().visibleBounds.width / 4).toInt()
distanceThresholdSquared *= distanceThresholdSquared
```

This derives the gesture-point sampling threshold from the first key's pixel
width, with no correction for non-uniform vertical stretching, non-medium row
heights, or mod-row insertion. The threshold must become a function of the
actual current geometry — not a single key's width in isolation.

Note: `StatisticalGlideTypingClassifier` separately uses isometric normalization
(`normalizeByBoxSide`, longest-side scaling) for gesture shape comparison, but
the *sampling filter* that feeds it is the broken threshold above.

---

## Geometry Pipeline Context

Understanding the full path is necessary to place corrections correctly:

```
FlorisImeSizing.calcInputViewHeight()     →  base row height (display metrics, insets, user factor)
    ↓
KeyboardGeometryPolicy.buildInput()       →  role-based height/width scales, spacing, gaps
    ↓
KeyboardGeometrySolver.solve()            →  concrete row heights + unit widths (decoupled axes)
    ↓
KeyBoundsDerivation                       →  per-key touchBounds + visibleBounds
    ↓
TextKeyboardGeometryBridge.applyTo()      →  stamps bounds into TextKey instances
    ↓
FutoGlideTypingClassifier.setLayout()     →  alpha-only bounding box → boardW, boardH, cx[], cy[]
StatisticalGlideTypingClassifier.setLayout()  →  keyboardWidth, keyboardHeight, distanceThreshold
```

The alpha-key bounding box at the bottom of this chain is the canonical frame.
The aspect ratio (`boardW / boardH`) varies with configuration and is the
quantity that needs to be tracked and compensated.

---

## What to Deliver

### 1. Aspect-corrected coordinate frame

The alpha-key bounding box is already correct. Add aspect correction so that
normalized coordinates represent isotropic spatial relationships regardless of
key height settings. Two valid approaches:

- **Option A:** Normalize into isotropic space by scaling Y by `aspect =
  boardW / boardH`, making the unit square physically proportional. Thresholds
  stay constant.
- **Option B:** Keep the `[0,1]²` normalization and scale thresholds by the
  current aspect ratio at comparison time.

Option A is cleaner — the same correction serves both classifiers and tap
telemetry without per-consumer threshold scaling.

### 2. Aspect-corrected thresholds

Replace both hardcoded constants:
- `FutoGlideTypingClassifier`: any internal angle/velocity thresholds that
  assume isotropic space must account for aspect.
- `StatisticalGlideTypingClassifier:160`: `distanceThresholdSquared` must
  become a function of actual geometry — the geometric mean of key width and
  key height, or half the minimum key dimension, rather than one key's width.

### 3. `LayoutFingerprint`

A stable hash over everything that moves keys, consumed by harvest v4 `layout`
(spec §5). Must include:

| Field | Source |
|---|---|
| `alphaRows` | count of `SemanticRowRole.ALPHA` rows |
| Per-row height multipliers | `GeometryPreferences.alphaRowHeightPercent` et al. |
| Padding / gaps | `keySpacingHorizontalPx`, `keySpacingVerticalPx`, gap policies |
| Number-row presence | `numberRowEnabled` (D13 — number row toggles move every alpha key) |
| `modRowsTop`, `modRowsBottom` | count of `CODING_UTILITY`/`EXTENSION` rows by position |
| One-handed offset | `oneHandedMode`, `oneHandedModeScaleFactor` |
| Orientation | portrait / landscape |
| DPI | `displayMetrics.densityDpi` |

Expose readable fields: `alphaW`, `alphaH`, `aspect` (= `alphaW / alphaH`).

The hash (`fp`) is the join key for training — it lets spatial models condition
on layout configuration rather than silently mixing incomparable samples.

### 4. Key-resolution helper

For a raw `(touchX, touchY)`, return:

```
xn = (touchX - boardLeft) / boardW          // alpha-relative, normalized
yn = (touchY - boardTop)  / boardH          // alpha-relative, normalized
dx = (touchX - key.centerX) / key.width     // key-local offset, right positive
dy = (touchY - key.centerY) / key.height    // key-local offset, down positive
resolvedKey                                 // the TextKey hit
```

Per harvest v4 spec §7.1. **Expose the helper only** — do not write harvest
records or consume it in `HarvestManager`. Phase 0.3 wires it.

### 5. Tests

Prove frame stability: the same physical key position must yield `xn`/`yn`
agreeing within 2% across at least three deliberately different configurations:

1. Medium key height, number row off, mod rows collapsed
2. Tall key height, number row on, mod rows collapsed
3. Medium key height, number row off, mod rows expanded

`LayoutFingerprint` must differ across all three configurations while
`xn`/`yn` for the same key stays stable.

---

## Authoritative Files

| File | Role | Key lines |
|---|---|---|
| [`FutoGlideTypingClassifier.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/gestures/FutoGlideTypingClassifier.kt) | Alpha-key bounding box, reference normalization | 230–268 (setLayout), 301–309 (getSuggestions normalization) |
| [`StatisticalGlideTypingClassifier.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/gestures/StatisticalGlideTypingClassifier.kt) | Hardcoded threshold defect | 160–163 (distanceThresholdSquared), 686–714 (normalizeByBoxSide) |
| [`KeyboardGeometrySolver.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/geometry/KeyboardGeometrySolver.kt) | Row height derivation, source of aspect distortion | 245–271 (solveRowHeights), 282–304 (solveUnitWidths) |
| [`KeyboardGeometryPolicy.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/geometry/KeyboardGeometryPolicy.kt) | Role-based height/width scales, canonical units | 60–102 (constants), 147–248 (buildInput) |
| [`TextKeyboardLayout.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboardLayout.kt) | Touch interception, key resolution | 190–219 (pointerInteropFilter), 680–684 (onTouchDownInternal) |
| [`FlorisImeSizing.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/FlorisImeSizing.kt) | Base row height, system insets | 74–109 (keyboardUiHeight), 170–240 (ProvideKeyboardRowBaseHeight) |

---

## Constraints

- **Do not** change autocorrect ranking, `CommitPolicy`, the dictionary, or
  `HarvestManager`.
- **Do not** alter the glide *algorithm*. Fix its coordinate inputs and
  thresholds only. Gesture recognition quality must not regress.
- **Do not** push, invoke the build factory, or install an APK unless asked.
- Preserve unrelated dirty worktree changes.
- **Device validation is required.** Glide must be exercised at minimum three
  configurations including the ones currently known to fail: expanded mod rows
  and non-medium key height.

---

## Definition of Done

- [ ] Both defects (aspect distortion + hardcoded threshold) are closed.
- [ ] `touch-coordinates-and-spatial-telemetry.md` §4 updated to record closure
      rather than describing the defects as open.
- [ ] Glide typing works at expanded mod rows and non-medium key heights —
      verified on device, configurations named explicitly.
- [ ] Key-resolution helper exists, returns `xn`, `yn`, `dx`, `dy`, resolved
      key per harvest v4 spec §7.1.
- [ ] `LayoutFingerprint` exists, matches harvest v4 spec §5 field-for-field,
      includes number-row state (D13).
- [ ] Tests prove frame stability (2% tolerance across three configurations).
- [ ] `ROADMAP.md` Phase 0.0 is marked done with device validation recorded.

---

## Sequencing Note

This work was originally scoped as Phase 3.3b but was moved to Phase 0.0
because Phase 0.3 records touch coordinates against this frame — the frame must
exist and be correct first. Coordinates are the one harvest field that cannot be
repaired retroactively; the distortion is a function of the layout configuration
at the instant of the tap, which is precisely what the broken frame fails to
record. Logging first would not buy early data — it would manufacture a poisoned
corpus.

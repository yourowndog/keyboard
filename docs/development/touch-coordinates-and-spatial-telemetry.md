# Touch Coordinates and Spatial Geometry Mapping

> Status: Canonical Reference  
> Last verified: 2026-09-11  
> Verified against: `TextKeyboardLayout.kt`, `TextKeyboard.kt`, `Key.kt`, `TextKey.kt`,
> `TextKeyboardGeometryBridge.kt`, `KeyBoundsDerivation.kt`, `KeyboardGeometrySolver.kt`,
> `KeyboardGeometryPolicy.kt`, `FlorisImeSizing.kt`, `FutoGlideTypingClassifier.kt`

## Overview

This document defines the spatial coordinate system and geometry pipeline in OmniBoard. It serves as the reference for implementing coordinate touch telemetry (touch logging) to support spatial-awareness tapping models in the Neural Network autocorrector.

Because OmniBoard supports granular layout customization (custom key heights, vertical elevation on screen, variable padding/spacing, expanded mod/utility rows, one-handed scaling, and per-key overrides), raw screen pixel coordinates are not stable across configurations. Telemetry and neural models must map touch events relative to the actual QWERTY letter geometry.

---

## 1. Coordinate Space Hierarchy

Understanding the coordinate frames from screen touch to individual keycaps:

```text
[Device Screen Coordinates]
      │ (subtract status bar, app content, window top)
      ▼
[IME Window Frame / InputView] (FlorisImeService)
      │ (subtract navigationBarHeight, bottom window insets, bottom padding)
      ▼
[Chassis / Column] (TextInputLayout.kt)
      │ (subtract Smartbar height: FlorisImeSizing.smartbarUiHeight())
      ▼
[TextKeyboardLayout (BoxWithConstraints)] (TextKeyboardLayout.kt: lines 190–247)
      │ ──> (0, 0) is the top-left of the key area.
      │ ──> MotionEvents in pointerInteropFilter are in this local coordinate space.
      ▼
[Solved Structural Rectangles] (KeyboardGeometrySolver.kt / SolvedGeometry.kt)
      ├─ Key.touchBounds   (KeyBoundsDerivation.kt: lines 75–87)
      └─ Key.visibleBounds (KeyBoundsDerivation.kt: lines 89–108)
```

Within `TextKeyboardLayout`, `(0, 0)` is strictly the top-left origin of the active keyboard keys. The Smartbar above and the navigation bar/window offsets below are already outside this view boundary.

---

## 2. Authoritative Files, Lines, and Symbols

### A. Touch Event Interception & Key Resolution
* **File:** [`app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboardLayout.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboardLayout.kt)
  * **Lines 190–219**:
    * Symbol: `BoxWithConstraints` container and `.pointerInteropFilter { event -> ... }`
    * Intercepts `MotionEvent.ACTION_DOWN`, `ACTION_MOVE`, `ACTION_UP`, `ACTION_CANCEL`. Coordinates `event.getX(...)` and `event.getY(...)` are local to the keyboard layout bounds.
  * **Lines 680–684**:
    * Symbol: `TextKeyboardLayoutController.onTouchDownInternal(event: MotionEvent, pointer: TouchPointer)`
    * Line 683: `val key = keyboard.getKeyForPos(event.getX(pointer.index), event.getY(pointer.index))`
    * The exact point where an incoming touch coordinate is tested against keys.
  * **Lines 309–325**:
    * Applies user per-key customizations (`custom.heightFactor`, `custom.widthFactor` from `keyCustomizationsJson`) to `key.visibleBounds`.

### B. Key Boundaries & State Models
* **File:** [`app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/Key.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/Key.kt)
  * Symbol: `abstract class Key(open val data: AbstractKeyData)`
  * **Lines 66–69**: `open val touchBounds: FlorisRect`
    * Structural touch hitbox allocated to the key. Covers the entire cell (no dead strips) and extends downward on the bottom row.
  * **Lines 71–74**: `open val visibleBounds: FlorisRect`
    * The drawn keycap rectangle inset by half the key spacing.
* **File:** [`app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKey.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKey.kt)
  * Symbol: `class TextKey(override val data: AbstractKeyData) : Key(data)`
  * Holds `computedData`, `isAlpha`, and `computedDataOnDown`.
* **File:** [`app/src/main/kotlin/dev/patrickgold/florisboard/lib/FlorisRect.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/lib/FlorisRect.kt)
  * Fields: `left`, `top`, `right`, `bottom`, `width`, `height`, `centerX`, `centerY`, and `contains(x, y)`.

### C. Hit-Testing & Key Enumeration
* **File:** [`app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboard.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboard.kt)
  * **Lines 72–79**:
    * Symbol: `TextKeyboard.getKeyForPos(pointerX: Float, pointerY: Float): TextKey?`
    * Hit-test loop checking `key.touchBounds.contains(pointerX, pointerY)`.
  * **Lines 81–87**:
    * Symbols: `TextKeyboard.keys(): Iterator<TextKey>` and `rows(): Iterator<Array<TextKey>>`
    * Used to enumerate active keys and query their live bounds.

### D. Geometry Derivation and Solving
* **File:** [`app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/geometry/TextKeyboardGeometryBridge.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/geometry/TextKeyboardGeometryBridge.kt)
  * **Lines 200–224**:
    * Symbol: `TextKeyboardGeometryBridge.applyTo(keyboard: TextKeyboard, geometry: SolvedGeometry, extendTouchBoundariesDownwards: Boolean)`
    * Stamped derived bounds into each key's `touchBounds` and `visibleBounds`.
  * **Lines 235–241**:
    * Symbol: `TextKeyboardGeometryBridge.referenceCell(geometry: SolvedGeometry, into: FlorisRect): FlorisRect?`
    * Provides the reference keycap (first cell of first alpha row) for anchoring and scaling.
* **File:** [`app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/geometry/KeyBoundsDerivation.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/geometry/KeyBoundsDerivation.kt)
  * **Lines 49–87**:
    * Symbol: `KeyBoundsDerivation.touchBounds(...)` and `BOTTOM_EDGE_TOUCH_EXTENSION_ROWS = 1.0f`
    * Generates touch bounds from structural rectangles.
  * **Lines 89–115**:
    * Symbol: `KeyBoundsDerivation.visibleBounds(...)` and `halfInset(...)`
    * Derives visible keycaps by subtracting half the horizontal and vertical key spacing.
* **File:** [`app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/geometry/KeyboardGeometrySolver.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/geometry/KeyboardGeometrySolver.kt)
  * Symbol: `object KeyboardGeometrySolver`
  * Canonical integer solver allocating row heights and key widths.

### E. Keyboard Height, Base Row Height & Window Insets
* **File:** [`app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/FlorisImeSizing.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/FlorisImeSizing.kt)
  * **Lines 75–109**: `FlorisImeSizing.keyboardUiHeight()`
    * Computes the intrinsic frame height via `TextKeyboardGeometryBridge.frameHeight(...)`.
  * **Lines 114–138**: `smartbarUiHeight()` & `imeUiHeight()`
  * **Lines 148–192**: `ProvideKeyboardRowBaseHeight`
    * Computes base row height factoring in user preferences (`heightFactorPortrait` / `heightFactorLandscape`), one-handed scaling (`oneHandedModeScaleFactor`), and system window insets (`systemBarHeights()`).
* **File:** [`app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt)
  * Manages IME window insets, navigation bar bottom padding, and root view placement.

---

## 3. Reference Pattern: Relative QWERTY Normalization

The repository already implements QWERTY-relative normalization in its swipe classifiers. Telemetry logging for the neural autocorrector should adopt this same coordinate frame:

* **File:** [`app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/gestures/FutoGlideTypingClassifier.kt`](file:///home/sam/projects/keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/gestures/FutoGlideTypingClassifier.kt)
  * **Lines 230–256** in `setLayout(keyViews: List<TextKey>, subtype: Subtype)`:
    ```kotlin
    val letters = byChar.values
    boardLeft = letters.minOf { it.visibleBounds.left }
    boardTop = letters.minOf { it.visibleBounds.top }
    boardW = max(1f, letters.maxOf { it.visibleBounds.right } - boardLeft)
    boardH = max(1f, letters.maxOf { it.visibleBounds.bottom } - boardTop)

    // Key centers normalized to [0.0, 1.0] across the QWERTY alpha matrix:
    for ((i, ch) in LETTERS.withIndex()) {
        val k = byChar.getValue(ch)
        cx[i] = ((k.visibleBounds.left + k.visibleBounds.right) / 2f - boardLeft) / boardW
        cy[i] = ((k.visibleBounds.top + k.visibleBounds.bottom) / 2f - boardTop) / boardH
    }
    ```

### Recommended Tap Telemetry Representation
When logging a tap event `(touchX, touchY)`:
1. **Global Alpha-Relative Coordinates:**
   $$x_{\text{norm}} = \frac{\text{touchX} - \text{boardLeft}}{\text{boardW}}, \quad y_{\text{norm}} = \frac{\text{touchY} - \text{boardTop}}{\text{boardH}}$$
2. **Key-Local Offset Coordinates:**
   For resolved key $K$:
   $$\Delta x = \frac{\text{touchX} - K.\text{visibleBounds.centerX}}{K.\text{visibleBounds.width}}, \quad \Delta y = \frac{\text{touchY} - K.\text{visibleBounds.centerY}}{K.\text{visibleBounds.height}}$$

This representation remains invariant regardless of screen DPI, system navigation bar height, user height sliders, or space row insets.

---

## 4. Known Hardening Issue: Swipe Sensitivity & Layout Expansion

### The Observed Defect
Swipe/glide typing currently works reliably **only** when:
1. Keys are set to a roughly medium height.
2. The keyboard is not sitting too high on the screen (i.e. navigation bar insets or bottom offsets have not pushed it excessively upward).
3. **Mod rows / utility rows have not been expanded** (e.g. extra symbol/action rows).

When mod rows are expanded or key heights change significantly, gesture recognition accuracy degrades or fails.

### Root Causes in the Referenced Files
1. **Vertical Aspect Distortion:**
   `FutoGlideTypingClassifier.kt` normalizes `cy` using `boardH = maxOf(letters.bottom) - minOf(letters.top)`. If the alpha rows are compressed or stretched relative to horizontal widths due to solver adjustments (`KeyboardGeometrySolver.kt`), gesture velocity and trajectory angle thresholds skew relative to training data.
2. **Hardcoded Distance Thresholds:**
   In `StatisticalGlideTypingClassifier.kt` (line 160):
   ```kotlin
   distanceThresholdSquared = (keyViews.first().visibleBounds.width / 4).toInt()
   distanceThresholdSquared *= distanceThresholdSquared
   ```
   Thresholds depend on the first key's width and do not account for non-uniform vertical stretching, mod row insertion, or non-medium row heights.
3. **Implication for Telemetry & Solver Hardening:**
   The files governing geometry solving (`KeyboardGeometrySolver.kt`, `KeyboardGeometryPolicy.kt`), sizing (`FlorisImeSizing.kt`), and gesture classification (`FutoGlideTypingClassifier.kt`, `TextKeyboardLayout.kt`) need systematic hardening. Spatial models for both tapping and swiping must be anchored to canonical, aspect-corrected coordinates rather than uncompensated screen-space dimensions.

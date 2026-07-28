# Keyboard Geometry and Hitboxes

> Status: Canonical  
> Last verified: 2026-07-28
> Verified against: `ime/keyboard/geometry/` (`KeyboardGeometryPolicy.kt`,
> `KeyboardGeometrySolver.kt`, `KeyBoundsDerivation.kt`,
> `TextKeyboardGeometryBridge.kt`), `FlorisImeSizing.kt`,
> `TextKeyboardLayout.kt`, `FlorisImeService.kt`, `KeyCustomization.kt`

There is one authority for key geometry and no second arithmetic anywhere. If a
number here disagrees with the code, the code is right and this document is
stale.

## The pipeline

```text
TextKeyboard (arrangement + semantics)
  -> TextKeyboardGeometryBridge.describeRows()   what each row is, what each key declares
  -> KeyboardGeometryPolicy.buildInput()         the one place preferences become inputs
  -> KeyboardGeometrySolver.solve()              structural rectangles: integer, exact
  -> KeyBoundsDerivation                         touch bounds and visible bounds, separately
  -> Compose placement
```

Two consumers enter this pipeline, differing in exactly one argument:

| Consumer | Frame policy | Question it asks |
| --- | --- | --- |
| `FlorisImeSizing.keyboardUiHeight()` | `FramePolicy.Intrinsic` | how tall must the window be? |
| `TextKeyboardLayout` | `FramePolicy.FitToHeight` | given that height, where does each key go? |

Because both build their input through `KeyboardGeometryPolicy` from the same
`GeometryPreferences` value, they cannot disagree about how tall a utility row is
or how many gaps exist. The two legacy authorities — `Keyboard.layout()` and
`KeyboardGeometryArithmetic` — were removed in Stage 03, along with the row
classification that guessed a row's kind from `isAlpha` flags and key code 32.
Rows now state their role (`SemanticRowRole`) and the solver believes them.

## Widths

The alpha region's ten-key row establishes the **alpha unit**: content width
divided by ten. Both nine-key alpha rows consume that same unit and are centred
within the ten-column width; they do not stretch. Shift and Delete are ordinary
one-unit alpha cells that keep their actions and presentation.

The primary action row is `Tab | , | Space | . | Enter`. Tab and Enter are `1.5`
alpha units, comma and period `1.0`. Space is `1.0` **with a grow weight**: it
absorbs whatever the row has left after the fixed items are placed. On a
ten-column grid that remainder is five units, but `5.0` is not written down
anywhere — it is an outcome, not an input, and it stays correct when the alpha
grid changes. The `1.5` values are semantic defaults for this row, not saved
per-key customizations: a 100% per-key override means 100% of the solved
baseline.

Coding utility rows are nine equal structural cells, each row filling the content
width independently. The utility grid is nine columns against alpha's ten, on
purpose: a separate grid, not a misaligned copy of the alpha one. The narrow
navigation keys, `0.8` Undo/Redo, wide Ctrl/Tmux and the Escape/Σ padding trick
are gone; every key action, label, popup and terminal behaviour is unchanged.

A key may instead declare `authoredWidthUnits`. That comes from a Layout Pack,
is used verbatim, and never grows — growth is a decision the pack author did not
make. Everything else goes through `KeyboardGeometryPolicy.canonicalWidthUnits`,
which is the only item-default authority; the old intrinsic table in
`TextKey.compute()` (`2.68`, `1.56`, `1.26`, `5.00`, …) no longer exists.

## Heights

`100%` means one normalized full row (`FlorisImeSizing.keyboardRowBaseHeight`).

| Role | Base | Role adjustment | Result |
| --- | --- | --- | --- |
| Alpha | `1.0` | 100% | one full row |
| Primary action | `1.0` | 100% | one full row |
| Coding utility | `1.0` | 75% | three quarters of a row |

The `75%` is a role adjustment applied to a `1.0` base, never a `0.75` base
multiplied by a preference into `56.25%`. Set the utility preference to 100% and
a utility row equals an alpha row exactly — there is a test for that.

Every key-level height factor is `1.0`, Space included. A key never compensates
for its row; the row's role owns the difference. The historical `1.1` spacebar
height factor, its special vertical centring, and the comment claiming it
compensated for `1.33` were evidence of a control that no longer exists.

### Bottom offset and IME insets

Portrait and landscape bottom offset are different from structural gaps, and
remain outside solved row geometry — the solver never sees the offset, so it
cannot leak into row heights. The offset is bottom padding inside the Snygg
`window` box in `FlorisImeService.ImeUi()`, so the window background covers it
and the measured input height includes it. `onComputeInsets()` uses that measured
height for the visible, content, and touchable IME region. It is not an
intentionally transparent or touch-through gap.

On API 30 and newer, changing the offset also resizes the separate RGBA surface
used behind Compose and inline-autofill content. See
[Transparency and the IME surface](../theming/hard-won-lessons.md#transparency-and-the-ime-surface)
before diagnosing a briefly see-through offset as geometry or theme alpha.

## Spacing and bounds

One preference pair, `keySpacingHorizontal` / `keySpacingVertical`, in dp:

- visible gap between adjacent keycaps: the preference (2dp by default)
- visible outer margin at the left and right of a full-width row: half of it (1dp)

`KeyBoundsDerivation.visibleBounds` insets each structural rectangle by **half**
the spacing on every side, so the two keycaps sharing a gap contribute half each
and the total is the preference, once. Applying the whole preference to both
sides is what turned a value of `2` into a 4dp gap. The per-region
`alphaSpacing*` / `modSpacing*` sliders were removed: they no longer controlled
anything.

The three layers are derived, never guessed:

- **structural** (`SolvedItem.bounds`) — integer rectangles partitioning the row
  conservatively. Adjacent items share an edge and never overlap; rounding is
  edge-based and centralized, so row widths conserve exactly.
- **touch** (`KeyBoundsDerivation.touchBounds`) — the whole structural
  allocation, so no dead strips exist between keys, plus the bottom-edge
  extension on the last row (`BOTTOM_EDGE_TOUCH_EXTENSION_ROWS`) that keeps the
  bottom row hitable at the screen edge.
- **visible** (`KeyBoundsDerivation.visibleBounds`) — the structural allocation
  inset as above. Always inside the touch bounds.

A visually narrow key still retains a forgiving touch target; that separation is
intentional and is now a property of the derivation rather than an ad-hoc
expansion applied to alpha keys only.

Structural semantic boundary gaps (`RoleBlockGaps`) are a separate concept from
keycap spacing. They are declared for the `CODING_UTILITY` block only, so hiding
the utility rows leaves no orphaned gap and Numeric, Phone and Symbols receive
none at all. This replaced the positional "row N-2 / N-1" mutation, which shifted
whichever rows happened to sit at those indices.

## Per-key customization

`keyboard__key_customizations` holds per-key width/height factors and padding,
keyed by integer key code — so every occurrence of that code receives it. It is
applied **after** the solve and to **visible bounds only**, so a customized key
cannot open a dead strip or steal a neighbour's touches. The fallback chain for
a key with no entry is canonical semantic baseline → row/role settings → no
override.

"Restore per-key defaults" on the key customization screen writes
`KeyCustomizationManager.NO_CUSTOMIZATIONS` (`{}`) to that one preference and
touches nothing else — not row heights, spacing, boundary gaps, utility-row
visibility, layout, subtype, theme, bottom offset, language, or key actions.

Instance-aware customization (per row, per component) is Stage 07.

## Popups

Popup size and anchoring derive from
`TextKeyboardGeometryBridge.referenceCell` — a real solved alpha cell run
through the same visible-bounds derivation as everything else — so popups move
with the solved geometry instead of drifting from an independently guessed
rectangle.

## Robustness

`GeometryPreferences.sanitized()` clamps at the preference boundary: percentages
to 10–300, spacing to 0–64dp, gaps to 0–256dp, row base height to a finite
positive value. Non-finite values fall back to the shipped default.

If a solve is still unsatisfiable, `TextKeyboardGeometryBridge.solve` retries
once at the canonical baseline and returns `Result.Fallback` with diagnostics. If
even that fails it returns `Result.Unavailable` and **nothing is written** — keys
keep the coherent bounds they last had. No path produces partially rendered
geometry.

## Safe debugging method

1. Read the diagnostics. Both consumers log every `Result.diagnostics` entry
   under `LogTopic.TEXT_KEYBOARD_VIEW`.
2. Identify the row's `SemanticRowRole` and whether the key declares
   `authoredWidthUnits`. Those two facts determine its width entirely.
3. Reproduce on the JVM. `TextKeyboardGeometryBridge` is pure — no Compose, no
   `Context`, no logging — so any question about placement can be asked in a unit
   test against `GeometryFixtures`. See `NormalizedGeometryTest`.
4. Compare touch and visible bounds using the developer overlay.
5. Change the policy, not the pixels. If a key is the wrong size, the answer is
   in `KeyboardGeometryPolicy` or in the row's declared role — not a local
   adjustment where it looked wrong. Local adjustments are how the compensation
   layers this pipeline replaced came to exist.
6. Test edge and neighbour touches on device before accepting the change.

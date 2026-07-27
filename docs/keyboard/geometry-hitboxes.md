# Keyboard Geometry and Hitboxes

> Status: Canonical
> Last verified: 2026-07-27
> Verified against: `TextKeyboardGeometry.kt`, `KeyboardGeometrySolver.kt`,
> `FlorisImeSizing.kt`, `TextKeyboardLayout.kt`, `FlorisImeService.kt`,
> `KeyCustomization.kt`

Keyboard geometry is layered. Before changing a width, height, gap, or hitbox,
identify which layer owns it.

## Live geometry pipeline

```text
explicit semantic rows
  -> role-keyed height/width/gap/spacing policy
  -> one immutable solver result for frame, rows, keys, and structural gaps
  -> derived touch bounds
  -> derived visible bounds
  -> visual-only legacy key customization
  -> Compose placement and pointer dispatch
```

`FlorisImeSizing.keyboardGeometry()` owns the live solution. The same immutable
`TextKeyboardGeometry` supplies the keyboard frame height and the bounds applied
to every `TextKey`; Compose no longer reruns row-height arithmetic.

The old `TextKeyboard.layout()` and `KeyboardGeometryArithmetic` remain only as
legacy characterization seams. Production sizing and placement do not consume
`bottomModRowCount`, row-count thresholds, Space detection, or `isAlpha` to
decide what a row is.

## Semantic row policy

Geometry policy is keyed by `SemanticRowRole`:

- `ALPHA`, `NUMERIC`, `SYMBOL`, and `EXTENSION` rows define the shared entry
  width grid.
- `PRIMARY_ACTION` consumes that grid without widening it and is immune to both
  legacy width sliders.
- `CODING_UTILITY` rows use the utility width and spacing policy.
- `EXTENSION` and `CODING_UTILITY` use the short-row height factor.
- All other row roles use the ordinary row-height factor.

Role, stable identity, provenance, behavioral policy, and solved geometry remain
separate. `GeometryPolicyRef.Unassigned` is intentional: the live resolver uses
one role-keyed policy bundle and does not require per-row persisted references.

Legacy width preferences above 100% formerly created structural overflow and
clipping. The live validation boundary caps those inputs at 100%, because the
shared solver's structural allocations must remain inside the frame. Values
below 100% continue to produce centered narrower rows. Stage 07 owns the later
structural-customization model.

## Heights and semantic gaps

Intrinsic frames are the sum of solved row heights and declared semantic
boundary gaps. Symbols, Symbols2, and Numeric-Advanced retain their current
Characters-height behavior by solving their own rows with an explicit fitted
frame target; the final result still owns both frame and placement.

The three existing Coding gap controls map only to the Coding-utility block:

- above the first `CODING_UTILITY` row;
- within adjacent `CODING_UTILITY` rows;
- below the final `CODING_UTILITY` row.

When Coding utilities are hidden, or when the active keyboard is Numeric,
Phone, Symbols, or a layout pack without utility roles, those gaps do not move
positional rows. There is no `N-2`/`N-1` mutation after layout.

### Bottom offset and IME insets

Portrait and landscape bottom offsets are separate from solved row geometry.
The offset remains bottom padding inside the Snygg `window` box in
`FlorisImeService.ImeUi()`, so the window background covers it and the measured
input-view height includes it.

`onComputeInsets()` uses the measured input-view height for the visible,
content, and touchable IME region. On API 30 and newer, changing the offset also
resizes the separate RGBA surface behind Compose inline-autofill content.

See
[Transparency and the IME surface](../theming/hard-won-lessons.md#transparency-and-the-ime-surface)
before diagnosing a briefly see-through offset as geometry rather than theme
alpha.

## Structural, touch, and visible bounds

- `structuralBounds` are the non-overlapping solver allocation.
- `touchBounds` select which key receives a pointer.
- `visibleBounds` place and size the rendered Snygg key.

Item height factors and vertical alignment derive an allocated key rectangle
from its structural row band. Row spacing and explicit left/right padding then
derive visible bounds. The legacy alpha-key horizontal touch expansion is
carried as an explicit item declaration; it no longer determines row identity.

Only the named bottom-edge policy extends final-row touch bounds to the solved
frame bottom. It covers the declared lower Coding gap while leaving the keycap
visual bounds unchanged. Inner structural gaps remain between touch rows.
Layout-pack spacers retain structural width but receive empty touch and visual
bounds.

## Popup geometry

Popup reference size is derived from the median visible entry-key geometry in
the immutable solution. Popup bounds use that reference, the actual anchor
key's visible bounds, orientation-specific multipliers, and the solved frame.
Preview popups are horizontally clamped at the frame edges; extended-popup
anchoring continues to use `PopupUiController`.

## Per-key customization

Runtime customization is still stored as JSON keyed by integer key code. Stage
03 deliberately preserves it as a visual-only post-derivation adjustment:
padding and width/height factors mutate `visibleBounds`, not structural or touch
bounds. Stage 07 owns its structural migration.

Because customization is keyed by code, every occurrence of the same code
receives the customization. It is not keyed by row, layout component, or key
instance.

## Safe debugging and validation

1. Record the normalized row stable ID and semantic role.
2. Inspect the immutable `TextKeyboardGeometry` frame, rows, gaps, and item
   structural bounds.
3. Compare derived touch and visible bounds.
4. Check layout-pack units and explicit spacer provenance.
5. Check visual-only per-key customization JSON.
6. Confirm the active orientation and any Characters fitted-frame source.
7. Use the developer touch-boundary overlay.
8. Validate bottom-edge taps, gap controls, and popup edge placement on device.

Source and unit tests establish conservation and coordinate policy, but final
touch, popup, bezel, and IME-window behavior still requires device validation.

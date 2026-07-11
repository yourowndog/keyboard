# Keyboard Geometry and Hitboxes

> Status: Canonical  
> Last verified: 2026-07-11  
> Verified against: `TextKey.kt`, `TextKeyboard.kt`, `TextKeyboardLayout.kt`,
> `FlorisImeSizing.kt`, and `KeyCustomization.kt`

Key geometry is layered. Before changing a width or gap, identify which layer
owns it.

## Geometry layers

```text
layout structure or Layout Pack units
  -> TextKey intrinsic factors by evaluated key code
  -> row reference width and row-class multiplier
  -> touch bounds
  -> visual spacing and intrinsic padding
  -> visible bounds
  -> alpha hitbox expansion
  -> per-key customization adjustments
  -> Compose placement
```

## Intrinsic factors

`TextKey.compute()` assigns width, grow, shrink, height, alignment, and selected
edge padding based on keyboard mode and evaluated key code. Examples include a
wide spacebar, enlarged Enter/Tab/Ctrl, and narrower navigation keys.

For a Layout Builder pack, `KeyboardManager` overwrites the evaluated key's
width/grow/shrink values with `LayoutKey.units` after computation.

## Row classes

`TextKeyboard.layout()` classifies each row:

- **Alpha row**: at least one key has `isAlpha = true`.
- **Space row**: non-alpha row containing code `32`.
- **Modifier row**: neither alpha nor space row.

Alpha rows share a reference unit width based on the widest alpha row. Modifier
rows calculate their own reference width. The space row uses the alpha reference
width but a multiplier of `1.0`, making it immune to both width sliders.

The reference unit widths are deliberately calculated without slider factors;
the factors are applied only to final pixel widths. Including a slider in both
the reference denominator and final width makes it cancel itself out—the
historical “slider math trap.”

## Height

Layouts with at least five rows separate alpha and modifier height factors.
`bottomModRowCount` and inferred top extension rows determine which rows receive
which factor. `FlorisImeSizing` performs related total-height calculations, so a
row-counting change must be checked in both sizing and layout code.

Upper, inner, and lower modifier-row gaps are subtracted from the layout height
and then applied to positions in `TextKeyboardLayout`. They are not represented
as JSON rows.

## Touch bounds versus visible bounds

- `touchBounds` select which key receives a pointer.
- `visibleBounds` place and size the rendered Snygg key.
- Spacing and `flayPaddingLeft/Right` shrink visible bounds without necessarily
  surrendering the corresponding touch area.
- Alpha touch bounds are expanded horizontally by 20% of their horizontal
  spacing.
- The final row may extend its touch bounds downward into the remaining bezel.

This separation is intentional: a visually narrow key can retain a forgiving
touch target.

## Per-key customization

Runtime customization is stored as JSON keyed by integer key code. It can
adjust padding and width/height factors for the configured special keys. It is
applied after the base layout pass in `TextKeyboardLayout`.

Because customization is keyed by code, every occurrence of that code receives
the customization. It is not currently keyed by row, layout component, or key
instance.

## Safe debugging method

1. Record the evaluated key code and whether the row is alpha, space, or mod.
2. Determine the intrinsic `flayWidthFactor`.
3. Check for Layout Pack unit replacement.
4. Check the applicable row width and height preferences.
5. Compare touch and visible bounds using the developer overlay.
6. Check per-key customization JSON.
7. Test edge and neighbor touches on device before accepting the change.

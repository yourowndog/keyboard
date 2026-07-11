# Keyboard Construction and Customization

> Status: Canonical entry point  
> Last verified: 2026-07-11

This section covers how an OmniBoard keyboard is selected, assembled,
evaluated, sized, styled, rendered, and made interactive.

## Pipeline

```text
active subtype and keyboard mode
  -> registered layout components
  -> bundled layout JSON or a user LayoutPack
  -> row construction and modifier merging
  -> state-dependent key-data evaluation
  -> intrinsic width/height behavior
  -> row geometry and touch bounds
  -> per-key customization
  -> Snygg styling
  -> Compose rendering and pointer dispatch
```

Read:

- [Layout pipeline](layout-pipeline.md) before changing layout registration,
  JSON families, modifier rows, hints, or Layout Builder packs.
- [Key programming](key-programming.md) before adding an internal key, alias,
  icon, chord, or dispatch behavior.
- [Geometry and hitboxes](geometry-hitboxes.md) before changing widths, heights,
  spacing, row gaps, visual padding, or touch behavior.

The theming guide will live under `docs/theming/`. Snygg controls presentation;
it does not replace keyboard geometry.

## Fast routing

| Goal | Start with |
|---|---|
| Change which layout a language uses | localization `extension.json`, then layout registry |
| Change letters or rows | relevant layout JSON or Layout Builder pack |
| Add a special key | `KeyCode.kt`, `TextKeyData.InternalKeys`, evaluator, dispatcher |
| Change key width | determine whether the source is pack units, `TextKey`, row factor, or customization |
| Change row height or gaps | `TextKeyboard`, `FlorisImeSizing`, `TextKeyboardLayout` |
| Change long-press characters | inline popup data or popup mapping |
| Change symbol/number hints | `LayoutManager.addRowHints()` and symbol alignment |
| Change colors/shapes/fonts | Snygg stylesheet and verified element selectors |
| Change touch behavior | visible/touch bounds and `TextKeyboardLayoutController` |


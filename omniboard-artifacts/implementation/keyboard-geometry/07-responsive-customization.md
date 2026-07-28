# Stage 07 Prompt — Responsive Structural Customization

You are implementing Stage 07 of OmniBoard's keyboard-row and geometry migration.

Read the governing documents. Layout packs and bundled layouts must already produce the same solver inputs.

## Objective

Make frontend structural customization responsive, instance-aware, validated, and overlap-safe while preserving explicit visual-only controls.

## Required distinction

Every customization field must declare one layer:

- **structural** — changes solver input and reflows;
- **touch** — changes derived pressable bounds under constraints;
- **visual** — changes painted bounds/insets only.

Do not preserve an ambiguous `widthFactor` or `heightFactor` meaning.

## Required scope

1. Replace global integer-key-code targeting for structural edits with stable:
   - profile ID;
   - layout ID;
   - row ID;
   - key/item instance ID.
2. Feed structural width/height/margin/spacing edits into the solver before allocation.
3. Keep visual padding/insets explicitly visual.
4. Allow repeated key codes in different rows/locations to be edited independently.
5. Add validation, clamping/error feedback, and reset-to-default behavior.
6. Add a versioned migration for existing `keyboard__key_customizations` JSON:
   - preserve genuinely visual intent as visual;
   - map structural intent only when a unique instance can be identified safely;
   - retain ambiguous entries in a recoverable legacy bucket or require explicit user resolution;
   - never silently apply one old code-based width to every matching key instance.
7. Ensure edits update observable solver inputs and do not rely on post-layout rectangle mutation.
8. Provide independent role-level height, horizontal spacing, vertical spacing/padding, and width
   policy controls for Alpha, Primary Action, and Coding Utility. Do not extend the legacy
   Alpha/Mod binary model.
9. Make every eligible key instance addressable, including comma, period, Space, Tab, and Enter,
   without global key-code collisions across modes or roles.

## Required tests

- Widening/shrinking adjacent keys causes reflow and no overlap.
- Asymmetric rows with spacers and margins remain within bounds.
- Duplicate key codes customize independently.
- Invalid extremes fail or clamp predictably.
- Structural, touch, and visual controls affect only their declared layers.
- Old customization JSON migrates idempotently.
- Reset and process restart preserve the intended result.
- Portrait/landscape recomputation remains deterministic.

## Device checkpoint

Provide a manual script that deliberately widens adjacent keys, changes row height/gaps, rotates the device, restarts the app, and verifies taps/popup anchors.

## Non-goals

- No drag-anywhere rectangle editor.
- No new key-binding system.
- No redesign of key appearance unrelated to geometry.
- No Text profile content work.

## Commit boundary

Separate customization identity/schema migration from UI controls and legacy post-layout removal.

Finish with the standard report-back.

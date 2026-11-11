# Stage 03 Prompt — Structural Gaps, Bounds, and Canonical Normalization

You are implementing Stage 03 of OmniBoard's keyboard-row and geometry migration.

Read the governing documents and the Stage 02 comparison report. Do not proceed if unexplained default-Coding differences remain.

## Objective

Make solved geometry authoritative for structural frame/row/key placement, replace positional gap
mutation with semantic boundaries, explicitly derive touch and visual bounds, **and cut the shipped
baseline over to canonical normalized geometry**.

Historical OmniBoard geometry is forensic evidence of old controls and accumulated compensations. It
is not a canonical default, not a visual target, and not a migration seed
(see Decision 16 in `omniboard-artifacts/docs/architecture/keyboard-geometry-decisions.md`, which
supersedes Decision 8).

## Canonical normalized layout

### Alpha region

- The ten-key top row establishes the shared alpha unit width.
- Both nine-key alpha rows consume that same unit width; they do **not** stretch to fill.
- The nine-key rows are centered within the ten-column content width.
- Shift and Delete are ordinary `1.0` alpha cells and keep their actions and presentation.
- All alpha-region keys therefore share one normalized structural width and height, apart from at
  most one-pixel edge-rounding differences.

### Primary action region

Composition is unchanged: `Tab | comma | Space | period | Enter`.

| Item | Base width units |
| --- | --- |
| Tab | `1.5` |
| comma | `1.0` |
| Space / CJK Space | `1.0` plus flexible growth |
| period | `1.0` |
| Enter | `1.5` |

All individual height factors are `1.0`. Space absorbs the remaining width after fixed items are
allocated. With the ten-unit alpha reference that naturally yields roughly five units, but `5.0` must
never be encoded as Space's universal width.

Growth belongs to the Space item or a generic item-growth policy — **never** to "the third item in a
five-key row". The row is fixed for this increment, but the model must support future hot-swapping
without rewriting the solver.

Tab's and Enter's `1.5` are semantic defaults for this row, not saved per-key customizations. A 100%
per-key override means 100% of the solved semantic baseline. Specialized surfaces own their own
declared grids; Enter is not forced to `1.5` everywhere.

### Coding utility region

- Each of the two utility rows has nine equal `1.0` structural cells.
- Each row independently fills the available content width.
- Removed: narrow arrow/navigation geometry, `0.8` Undo/Redo, wide Ctrl/Tmux, and the Escape/Σ
  width-and-padding alignment tricks.
- Every key action, label, popup, and terminal behavior is preserved.
- The utility grid is intentionally independent of the ten-column alpha grid.

Hiding the utility rows remains compact Coding. It is not the future first-class Text profile. The
normalized solver and primary/alpha geometry must work with utility rows visible or hidden.

### Canonical height model

One neutral row-height baseline: 100% = one normalized full row.

| Region | Shipped default |
| --- | --- |
| Alpha | `100%` |
| Primary action | `100%` |
| Coding utility | `75%` |

Coding utility's underlying base is still `100%`; the role adjustment supplies `75%`. Setting the
utility-row control to `100%` must make utility rows exactly the same solved height as Alpha and
Primary. Do not encode a `0.75` base and multiply it by a `75%` preference to get `56.25%`.

Every key-level height factor, including Space, is `1.0`. Space height compensation and special
vertical centering are removed unless a renderer-independent reason is discovered and documented.
Row-role height owns the difference; individual keys do not compensate for their row.

### Spacing and bounds semantics

Values are density-independent pixels.

| Quantity | Canonical default |
| --- | --- |
| Visible horizontal gap between adjacent keycaps | `2dp` |
| Visible vertical gap between adjacent keycaps | `2dp` |
| Ordinary inset contribution per participating side | `1dp` |
| Visible outer left/right margin on full-width rows | `1dp` |
| Special upper/inner/lower compensation gaps | `0` |
| Default per-key padding and offset | `0` |

The preference value `2` must not be applied independently to both sides and render a `4dp` gap.

## Required scope

1. Make outer frame sizing and `TextKeyboard` internal placement consume the same solved result or the same immutable solution owner.
2. Remove production dependence on independently recomputing height in `FlorisImeSizing.keyboardUiHeight()` and `TextKeyboard.layout()`.
3. Represent gaps using semantic boundaries, including:
   - alpha to primary action;
   - primary action to Coding utility;
   - Coding utility to Coding utility;
   - final row to bottom edge.
4. Remove `N-2/N-1` structural mutation from Compose.
5. Derive:
   - structural allocations;
   - touch bounds;
   - visible bounds;
   as named, separately testable steps.
6. Preserve bottom-edge hitability through an explicit edge-touch policy.
7. Keep service/window bottom offset outside solved row geometry.
8. Route popup sizing and anchoring through documented solved/visual geometry inputs.
9. Update Compose state/memoization so profile/semantic/solver revisions recompute correctly and exactly when needed.
10. Keep **one** authoritative item-default policy. The legacy `TextKey.compute()` intrinsic width
    table must not remain active as a shadow authority beneath a new solver policy.
11. Remove obsolete comments describing historical Space compensation or Escape/Σ padding as
    intentional current behavior.

### Minimal solver extension

The Stage 02 solver already expresses the chosen alpha/utility policy: `ALPHA` can establish a shared
reference, `ALPHA` and `PRIMARY_ACTION` can consume it, and `CODING_UTILITY` can independently fill
its rows. Do not invent a new taxonomy or per-row policy hierarchy.

The missing capability is **flexible item growth**. Add the smallest general model expressing:

- a base width;
- an optional non-negative grow weight;
- distribution of positive row remainder among growable items;
- unchanged behavior for rows with no growers;
- unsatisfiable output when fixed demand genuinely exceeds the row budget;
- deterministic edge rounding and exact conservation.

Validate non-finite and negative growth. No Space-specific branch inside the solver.

### Structural layering

- Structural rectangles partition the available width conservatively.
- Touch bounds derive from structural allocation and remain forgiving.
- Visible keycaps derive from structural allocation and receive the visual inset.
- Visual spacing must not produce dead structural strips or overflow.
- Adjacent structural rectangles may share an edge but must never overlap.
- Rounding remains edge-based and centralized.

At non-default or invalid preference extremes the IME must not crash. Validate or clamp at the
preference/input boundary, or produce a deterministic safe canonical fallback with a clear
diagnostic. An unsatisfiable solve must never become corrupted or partially rendered geometry.

### Specialized surfaces

In scope: Characters, Symbols, Symbols2, Numeric, Numeric Advanced, Phone, Phone2, extension rows
(number/developer), and expanded plus compact Coding.

- Numeric rows retain `NUMERIC` identity; symbol rows retain `SYMBOL`; extension rows retain
  `EXTENSION`. No specialized row masquerades as Alpha or Coding utility to obtain convenient sizing.
- Numeric and Phone modes receive no Coding boundary gaps.
- The `2.68`, `1.26`, and `1.56` magic-width tables are removed as production placement authorities.
- Default specialized rows use their own declared row/grid policy and fill their available width
  without unexplained side or internal gaps.
- Ordinary default keys normalize to equal units unless the layout explicitly declares a real
  structural span, spacer, or user Layout Pack unit. Explicit Layout Pack units are preserved; do not
  erase authored asymmetry.
- All key contents and actions are preserved, and the common solver performs final placement.

Stage 05 concerns — stable cross-mode frame grouping, profile-scoped persistence, and the complete
Text/Coding profile system — remain out of scope. Stage 03 must nevertheless make current specialized
surfaces safe under the authoritative solver and eliminate positional Coding-gap contamination.

### Restore per-key defaults

Implement a clean control on the per-key customization screen with a deliberately narrow contract:

> "Restore per-key defaults" clears only `keyboard__key_customizations`, returning it to `{}`.

It must not alter row heights, region width settings, horizontal or vertical spacing, boundary gaps,
utility-row visibility, selected layout or subtype, theme, bottom offset, language, key actions, or
any other keyboard preference.

After clearing overrides, keys fall back through:

```
canonical semantic baseline -> row/role settings -> no per-key override
```

Do not automatically wipe existing saved customization during upgrade in this stage. The explicit
reset action is the safe way to expose the normalized baseline. Stage 07 owns the instance-aware
structural customization migration; the customization schema is not redesigned now.

## Compatibility policy

Default Coding **appearance is intentionally changed** to the normalized baseline described above.
Actions, labels, popups, mode switching, and terminal dispatch are preserved exactly.

Intentionally fixed:

- gaps landing inside alpha rows when utilities are hidden;
- positional gaps on Numeric/Phone;
- outer/inner disagreement on short keyboards;
- accidental bounds divergence caused by structural gap mutation;
- the doubled visible gap caused by applying the spacing preference to both sides;
- Space height enlargement and vertical centering;
- Escape/Σ padding compensation;
- narrow navigation, Undo/Redo, and wide Ctrl/Tmux widths.

Any change to key behavior — as opposed to geometry — requires an evidence-backed explanation.

## Required tests

**Alpha grid**

- Ten-key top row establishes the alpha unit.
- Both nine-key alpha rows use the same unit width and are centered.
- Shift/Delete solve to the same width and height as letters.
- No alpha row overflows at default settings.
- Odd pixel widths conserve the final edge.

**Primary action row**

- Five-key order preserved; Tab and Enter get `1.5`; punctuation gets `1.0`.
- Space has a base unit plus growth and absorbs the exact remaining structural width.
- Row fills the content width exactly.
- Growth is not keyed to item index; a changed synthetic composition still reallocates correctly.
- Fixed demand exceeding the row budget fails safely.

**Utility rows**

- Nine equal cells independently fill each row; both rows align under the same policy.
- Arrows, navigation, Undo/Redo, Ctrl, Tmux prefix, Escape, and Σ carry no accidental width
  differences.

**Heights and spacing**

- Alpha and Primary are `100%`; utility default is `75%`; utility at `100%` equals Alpha and Primary.
- Space has no individual height enlargement.
- A `2dp` setting produces a `2dp` visible gap, not `4dp`.
- Full-width rows have the intended `1dp` visible edge margin.
- Touch and visible rectangles remain distinct and inspectable; structural/touch rectangles do not
  overlap or overflow.

**Modes**

- Characters expanded and compact; Symbols and Symbols2; Numeric and Numeric Advanced; Phone and
  Phone2; relevant extension-row combinations.
- No specialized mode receives Coding boundary gaps.
- Portrait and landscape, with representative non-default height, spacing, and gap settings.

**Runtime integration**

- Frame height equals solved content plus declared gaps/insets.
- Outer and inner consumers use the same solved result.
- Preference changes invalidate and recompute correctly.
- Popup center and edge anchors use solved/visual geometry correctly.
- Bottom-edge taps remain assigned.
- Invalid persisted geometry cannot crash the IME.
- Per-key reset changes only the per-key JSON preference.
- Existing key actions and codes remain unchanged.

## Device checkpoint

Produce an installable debug build and a concise manual script covering:

1. Clear per-key overrides using the new reset control.
2. Normalized expanded Coding appearance.
3. Ten-column alpha grid and centered nine-key rows.
4. Shift/Delete matching alpha keys.
5. Equal full-width utility keys.
6. Space/Tab/Enter primary-row proportions.
7. Utility height 75% → 100% equals full rows.
8. Utility rows hidden/visible.
9. Tab, Enter, Ctrl, Tmux prefix, Escape, arrows, Home/End, Undo/Redo in Termux.
10. Symbols, Symbols2, Numeric, Numeric Advanced, Phone, Phone2 for gaps and overflow.
11. Bottom physical-edge taps.
12. Edge-key popups.
13. Portrait/landscape rotation.
14. Per-key reset did not change row settings, spacing, theme, subtype, or bottom offset.

Do not claim final interaction correctness without this device checkpoint.

## Acceptance criteria

1. Exactly one authoritative structural geometry authority; the legacy intrinsic width table is not
   an active production input.
2. The alpha, primary, and utility regions match the canonical normalized layout above.
3. The canonical height model holds, including utility `100%` equalling Alpha and Primary.
4. `2dp` means `2dp` on screen.
5. Structural, touch, and visible bounds are named, separately derived, and separately tested.
6. Specialized surfaces keep honest row identities and receive no Coding boundary gaps.
7. Per-key reset clears only `keyboard__key_customizations`.
8. Invalid or extreme preferences degrade deterministically and never crash the IME.
9. Every key action, label, popup, and terminal behavior is preserved.

## Non-goals

- No Text profile, profile selection UI, profile-scoped persistence, or on-keyboard Text/Coding gesture.
- No new keybinding system, hot-swappable primary-row UI, or stretch-to-fill customization checkbox.
- No layout-pack schema redesign.
- No instance-aware Stage 07 structural customization migration; no free-positioned keys.
- No dead asset deletion or broad layout-asset cleanup.
- No unrelated theme or autocorrect changes.
- No historical keyboard recreation and no upstream FlorisBoard pixel copying.
- Do not "simplify" the semantic row architecture back into alpha/mod/Space heuristics.

## Commit boundary

Separate the governing decision update, solver item growth, production cutover and bounds derivation,
per-key reset plus spacing cleanup, and canonical documentation. Every intermediate commit must build
and have one intelligible purpose.

Finish with the standard report-back and device checklist.

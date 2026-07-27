# OmniBoard Keyboard Geometry — Verified Current State

This is the compact repository truth record for the migration. It is not the target architecture.

## Inspection identity

- Repository: `yourowndog/keyboard`
- Branch: remote `dev`, inspected in a detached clean checkout
- Commit: `805d3e58e947215a9eb88ab9ed92b46366c54ef0`
- Inspection date: 2026-07-26
- Development checkout state: not inspected

## Construction inventory

| Path | Current output | Current semantic loss |
|---|---|---|
| `LayoutManager.mergeLayouts()` | Bundled layouts and specialized modes | Provenance known locally, then reduced to arrays, per-key `isAlpha`, and `bottomModRowCount` |
| `LayoutManager.computeKeyboardFromLayoutPack()` | User layout pack | Runtime loses row IDs; all keys default alpha; default claims two bottom modifier rows |
| `computeKeyboardAsync(EDITING)` | Empty sentinel | Constructor defaults survive despite no rows |
| `PlaceholderLoadingKeyboard` | Four loading rows | No explicit placeholder row semantics |
| `SmartbarQuickActionsKeyboard` | Empty sentinel | Constructor defaults survive despite no rows |

There is no `TextKeyboard` copy/clone path.

## Current geometry authorities

| Concern | Current authority | Verified problem |
|---|---|---|
| Outer frame | `FlorisImeSizing.keyboardUiHeight()` | Uses its own row arithmetic and mode substitution |
| Row/key allocation | `TextKeyboard.layout()` | Uses a different short-keyboard rule |
| Structural gaps | No true owner | Compose subtracts gap totals, then mutates final rows positionally |
| Intrinsic key factors | `TextKey.compute()` plus pack-unit replacement | Key-level alpha defaults affect row behavior |
| Legacy customization | `TextKeyboardLayout` | Mutates visible bounds only; no reflow or touch change |
| Touch lookup | `TextKeyboard.getKeyForPos()` and pointer logic | Consumes touch bounds that may diverge from visuals |
| Bottom edge | Last-row and lower-gap touch extensions plus service offset | Several separate mechanisms require preservation/clarification |
| Popup geometry | Desired-key and actual visible bounds | Moves when frame, row count, margins, or visible bounds move |

## Load-bearing heuristics

### `isAlpha`

- Defaults to `true`.
- A row is treated as alpha when any contained key is alpha.
- Affects width reference, spacing, touch expansion, and row hints.
- Extension and layout-pack rows inherit alpha accidentally.

It cannot be removed until every construction path supplies replacement semantics and downstream consumers are migrated.

### `bottomModRowCount`

The value is not a reliable count:

- with visible modifier rows, it stores the modifier asset's full three-row count, including the row consumed by merge;
- with hidden modifier rows, it stores only retained appended rows, currently zero;
- the always-present merged primary row is not represented consistently.

It cannot be renamed to a semantic count.

### Positional gaps

Current code targets `N-2`, `N-1`, and the last-row touch bounds. When Coding utilities are hidden, this inserts a Coding gap into the third alpha row. Numeric and Phone receive the same positional treatment.

### Space detection

One layout branch detects literal key code `32` to choose primary-row-like sizing. Hidden-row filtering also recognizes CJK Space, producing inconsistent behavior.

## Mode behavior

- Numeric, Numeric-Advanced, Phone, and Phone2 have four main-only rows whose keys all appear alpha.
- Numeric and Phone outer sizing interprets them as three alpha plus one top modifier, while internal layout gives uniform rows.
- Symbols and Numeric-Advanced borrow the last Characters frame.
- Active wide Symbols uses `symbols/western_wide.json` and `symbolsMod/western_wide_mod.json`.
- Hidden Coding keeps the primary row but reports zero bottom modifier rows.
- The default `symbols2` component ID is `western_wide`, but HEAD contains/registers no matching `symbols2/western_wide` component.

## Persistence and migration surface

- Existing preference keys store global widths, spacing, heights, gaps, offsets, row visibility, number/developer visibility, and customization JSON.
- `localization__subtypes` stores component IDs for eight layout families.
- `activeSubtypeId` selects the subtype.
- There is no persisted profile ID.
- Existing key customization is global by integer key code, not profile/layout/row/key instance.
- Layout Builder uses one hard-coded user filename.
- Saved layout packs are not restored as active at process startup; `loadInitialLayout()` returns an empty sentinel.
- Layout pack parsing ignores unknown JSON keys.

## Dead QWERTY row

`characters/qwerty_wide.json` row 3 is normally unreachable because `charactersMod/qwerty_wide_mod.json` row 0 has no code-0 placeholder and replaces it wholesale.

Commit `b4b8645b90618729cbe5211a19be0480778d115d` removed the placeholder on 2026-03-10. The dead row may still appear if modifier loading fails, which is an error fallback rather than normal behavior.

## Compose and cache facts

- Existing `remember` keys observe current dimensions and geometry preferences.
- No future profile ID, semantic-row revision, or solver revision is present.
- `modRowsVisible` invalidation clears Characters only even though merge-time filtering also affects symbol modifiers.
- Future solved geometry must be immutable/observable and included in relevant recomputation inputs.

## Unverified runtime facts

Repository inspection could not establish:

- the exact phone preferences, subtype, customization JSON, or in-memory pack;
- device pixel appearance and popup clipping;
- touch delivery at the bottom bezel and outside the layout box;
- behavior of the missing Symbols2 default on the installed build;
- cleanliness of the user's actual development checkout.

These require device/app-data inspection or instrumented validation.

## Baseline risks

1. Five constructor sites need explicit treatment.
2. `isAlpha` removal affects more than sizing.
3. Existing global preferences require scoped migration.
4. Existing subtype data must remain independent of profiles.
5. Short-keyboard behavior will change unless deliberately specified.
6. Stable Symbols/Numeric-Advanced frame behavior must become explicit policy.
7. Popup dimensions and anchors are geometry consumers.
8. Layout packs lack row semantics and are not restored on startup.
9. Dead-row cleanup can resurrect or duplicate content if done too early.
10. No existing repository tests cover these geometry behaviors.


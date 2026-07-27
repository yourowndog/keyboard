# Stage 00 — Baseline Contracts: complete

## Branch & commits
- **Branch:** `KeyGeo` (you confirmed this is the correct extant branch; nothing was created, switched, merged, rebased, or reset)
- **Base:** `79529463`
- **New commit:** `f071566c` — `test(geometry): add Stage 00 baseline geometry contracts`
- Not merged, not rebased, not pushed, no PR opened.

## Files changed (all in the commit)
| File | Kind |
|---|---|
| `ime/keyboard/KeyboardGeometryArithmetic.kt` | new — production seam |
| `ime/keyboard/FlorisImeSizing.kt` | modified — wired to seam |
| `ime/text/keyboard/TextKeyboard.kt` | modified — wired to seam |
| `test/.../geometry/GeometryContractMarkers.kt` | new — classification annotations |
| `test/.../geometry/GeometryFixtures.kt` | new — shape fixtures |
| `test/.../geometry/ConstructionSiteContractTest.kt` | new — 8 tests |
| `test/.../geometry/GeometryAuthorityCharacterizationTest.kt` | new — 22 tests |
| `test/.../geometry/LayoutAssetDiagnosticTest.kt` | new — 6 tests |
| `test/.../geometry/PersistenceMigrationFixtureTest.kt` | new — 8 tests |

## Contract classification (44 tests)
- **COMPATIBILITY — 12.** Behaviour that must survive the migration: default Coding's 6-row shape and 2 bottom mod rows; genuine 3-row frame arithmetic; 4-row placeholder in CHARACTERS mode; numeric/phone families at 4 rows; existing Symbols2 components; layout-pack defaults.
- **KNOWN_DEFECT — 18.** Incorrect behaviour pinned, **not fixed**:
  1. Default Coding invents a phantom top mod row (`topModRows=1`), misattributing its **first real alpha row** as a modifier row and giving it modifier height.
  2. `bottomModRowCount = 0` still bills one mod row *and* the full gap budget.
  3. 4-row keyboards ignore both height factors while the outer frame applies them — the two authorities disagree.
  4. Empty/short keyboards are framed as 4 rows via `coerceAtLeast(4)`.
  5. Gaps inflate the frame but the inner allocation swallows them.
  6. Alpha keys get an undocumented touch expansion (20 % of spacing) causing adjacent-key overlap of `expansion * 2`.
  7. Space-row detection keys off literal code `32`.
  8. `bottomModRowCount` is not a count — with utilities hidden the primary action row survives but the keyboard reports `0`.
  9. Three of five construction sites (Editing sentinel, loading placeholder, smartbar sentinel) inherit `bottomModRowCount = 2` and `isAlpha = true` without declaring them; two have no rows at all.
  10. Layout-pack rows lose row identity; every key reads as alpha.
  11. `charactersMod/qwerty_wide_mod.json` row 0 has no code-0 placeholder, so it replaces rather than merges — making `characters/qwerty_wide.json` row 3 unreachable.
  12. `Subtype.kt:133` declares `SYMBOLS2_DEFAULT = extCoreLayout("western_wide")` but `symbols2/western_wide.json` does not exist. The value round-trips fine; the asset is simply absent. Only `western.json` and `western_samsung.json` ship.
- **MIGRATION_FIXTURE — 14.** Upgrade-path pins: subtype JSON round-trip, all eight layout-family mappings surviving decode, unknown-key tolerance on both subtypes and layout packs, saved packs with spacers/units/disabled rows, and legacy key customization being keyed by **bare integer key code** with no profile/layout/row qualifier (Stage 04 must widen that key space without dropping stored values).

## Seams added, and why
One file, `KeyboardGeometryArithmetic.kt`, with three `internal` functions: `computeFrameRowPartition`, `computeKeyboardFrameHeight`, `computeLayoutRowHeight`. The arithmetic was moved verbatim out of `keyboardUiHeight()` and `layout()`; no constants, branches, or ordering changed. It was necessary because both authorities were previously reachable only through `@Composable` + `Context` + device, so their disagreement could not be observed at all. I verified against the alternative first: a throwaway probe test proved `TextKey`/`TextKeyboard.layout()` run in plain JVM despite `Key` using Compose `mutableStateOf`, so no Robolectric dependency was needed. That probe has been deleted.

## Validation
- **Focused tests:** 44/44 pass, 0 failures, 0 errors, 0 skipped.
- **Full unit suite** (`:app:testDebugUnitTest`): 18 suites, **122 tests, 0 failures, 0 errors, 0 skipped**. No pre-existing failures existed to conceal, and none were introduced.
- **Debug build** (`:app:assembleDebug`): BUILD SUCCESSFUL.
- **Remaining device validation:** none of the above exercises real composition. `LayoutManager(context)` needs Android and `mergeLayouts` is `private suspend`, so the composition fixtures are *shape* fixtures, not asset snapshots. On-device confirmation is still owed for: the Coding phantom-mod-row height on a real 6-row keyboard, the frame-vs-row gap discrepancy visually, and touch-expansion overlap at row edges.

## Newly discovered hazards
- The touch-expansion overlap (defect 6) was not in the governing documents and means touch bounds are already non-partitioning — Stage 05's bounds separation inherits an existing overlap, not a clean starting point.
- `bottomModRowCount` is used as both a count and a flag depending on the call site, which is why defects 1, 2 and 8 are three faces of one problem.
- The missing `symbols2/western_wide.json` means the declared default cannot resolve at all; whatever fallback currently runs is undocumented.

## Worktree
Clean apart from changes that were already yours at session start and remain untouched and unstaged: `AGENTS.md`, `docs/development/agent-reorientation.md`, and `CLAUDE.md` (which also carries the jDocMunch policy edit you asked for). `.serena/` remains untracked. No ignored file — `local.properties`, credentials, signing config, machine-local config — was read, moved, staged, or printed.

**Stage 01 has not been started.** Stopping here.

One open item from earlier: `AGENTS.md` carries a byte-identical copy of the doc-policy section I rewrote in `CLAUDE.md`. Say the word and I'll mirror it.

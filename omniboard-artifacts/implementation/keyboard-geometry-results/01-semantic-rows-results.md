# Stage 01 — Semantic Rows: complete

## Branch & commits
- **Branch:** `KeyGeo` — verified before any edit. Nothing created, switched, merged, rebased, reset, renamed, or pushed. No PR opened.
- **Base:** `b1d98334` (Stage 00 commit `f071566c` confirmed an ancestor of HEAD)
- **New commits:**
  - `b792eabd` — `feat(geometry): add the Stage 01 semantic row model`
  - `2f83b334` — `feat(geometry): declare row semantics at every TextKeyboard construction site`

Two commits, matching the stage document's preferred boundary: domain model first, construction-path adoption plus tests second. The first commit compiles standalone — nothing references the model yet.

## Files changed
| File | Kind |
|---|---|
| `ime/keyboard/SemanticRow.kt` | new — the normalized row model |
| `ime/text/keyboard/TextKeyboard.kt` | modified — required `semantics` parameter + `init` validation |
| `ime/keyboard/Keyboard.kt` | modified — placeholder + smartbar sentinel declare semantics |
| `ime/keyboard/LayoutManager.kt` | modified — role assignment in all three composition paths |
| `test/.../geometry/GeometryFixtures.kt` | modified — fixtures declare per-row roles and provenance |
| `test/.../geometry/ConstructionSiteContractTest.kt` | modified — 15 new tests |
| `test/.../geometry/GeometryAuthorityCharacterizationTest.kt` | modified — one empty-keyboard fixture now built as a sentinel |

## The model
- `SemanticRowRole` — ALPHA, PRIMARY_ACTION, CODING_UTILITY, EXTENSION, NUMERIC, SYMBOL, PLACEHOLDER.
- `RowProvenance` — `Bundled(layoutType, component, sourceRowIndex)`, `Merged(main, modifier)`, `Pack(packId, rowId, sourceRowIndex, roleSource)`, `Synthetic`.
- `GeometryPolicyRef.Unassigned` — the explicit placeholder the stage document asked for. Stage 02 replaces it with real policies.
- `NormalizedRow(stableId, role, provenance, geometryPolicy)`.
- `KeyboardSemantics` — `Rows(List<NormalizedRow>)` or `Sentinel(EDITING | SMARTBAR_QUICK_ACTIONS)`.
- `NormalizedRowsBuilder` — assigns role-scoped stable IDs.

`TextKeyboard.semantics` is **required, with no default**. That is deliberate: a default is exactly how three of the five construction sites acquired semantics they never declared. It also structurally satisfies scope item 7 — code added this stage cannot infer a role from row index, row count, literal Space, or filename, because the role is handed to it.

`init` validation rejects: a row/arrangement length mismatch, blank stable IDs, duplicate stable IDs, and a sentinel that carries rows.

**Stable IDs are role-scoped, not positional** — `alpha:0`, `alpha:1`, `primary_action`, `coding_utility:0`. Inserting the number and developer extension rows above the alpha block leaves every alpha ID unchanged. A positional scheme would have renamed them and reintroduced the exact fragility this stage exists to remove. A test pins this.

## Role assignment per construction path
1. **`mergeLayouts()`** — roles are assigned inline, while the function still knows each row's source layout and its splice history.
   - Extension-layout rows → `EXTENSION`.
   - Branch A (main + modifier), non-last main rows → mode-derived role.
   - Branch A, the last main row spliced into the modifier's first row → **`PRIMARY_ACTION`**, with `Merged` provenance naming both sources. It is identified **by the splice**, not by carrying Space, not by literal code 32, and not by which asset directory it came from.
   - Branch A, appended modifier rows → `CODING_UTILITY`. Only rows that actually survive the `modRowsHidden` filter are recorded, so hiding utility rows removes utility rows and nothing else.
   - Branch B (main only) → mode-derived role. Branch C (modifier only) → `CODING_UTILITY`.
   - Mode-derived role: NUMERIC/NUMERIC_ADVANCED/PHONE/PHONE2 → `NUMERIC`; SYMBOLS/SYMBOLS2 → `SYMBOL`; otherwise `ALPHA`. Numeric and symbol rows no longer have to claim to be alpha rows to receive ordinary geometry.
2. **`computeKeyboardFromLayoutPack()`** — an explicit compatibility decoder (`LayoutPackRowSemantics`) keyed on `LayoutRow.id`, the only per-row identity packs have. A row id naming a known role is taken at its word (`DECLARED_ROW_ID`); anything else is genuinely ambiguous and falls back to `ALPHA` — the role pack rows already receive — flagged `COMPATIBILITY_FALLBACK` and logged via `flogWarning`. No silent "every row is alpha, and here are two modifier rows". An inferred role is never presented as pack metadata.
3. **`PlaceholderLoadingKeyboard`** — four `PLACEHOLDER` rows with `Synthetic` provenance. Loading chrome is not an alpha row, an action row, or a utility row.
4. **`SmartbarQuickActionsKeyboard`** — `Sentinel(SMARTBAR_QUICK_ACTIONS)`.
5. **`LayoutManager` EDITING keyboard** — `Sentinel(EDITING)`.

Compilation proves the enumeration is exhaustive: a required constructor parameter breaks any site that was missed.

## Compatibility projections — how each is now interpreted
`isAlpha` and `bottomModRowCount` are **functionally untouched at every site**, and still drive all geometry. They are documented as deprecated derived projections.

`bottomModRowCount` is now *stated explicitly* where it was previously inherited (the layout-pack path, both sentinels, the placeholder), but every value is byte-identical to Stage 00. It is deliberately **not** derived from the semantic rows: the live formula is `modifierLayout.arrangement.size`, which counts the row consumed by the splice, so a three-row modifier asset yields 3 while the keyboard has 2 `CODING_UTILITY` rows. Deriving it would move pixels.

Gap preferences (`modRowUpperGap`, `modRowInnerGap`, `modRowLowerGap`) are **unchanged**, still summed into `gapTotal` in `FlorisImeSizing`. See "Divergence from the handoff summary" below.

## Validation
- `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL.
- `./gradlew :app:testDebugUnitTest --tests '*geometry*'` — 60 tests, 0 failures, 0 errors, 0 skipped (`ConstructionSiteContractTest` 24, `GeometryAuthorityCharacterizationTest` 22, `LayoutAssetDiagnosticTest` 6, `PersistenceMigrationFixtureTest` 8).
- `./gradlew clean :app:testDebugUnitTest :app:compileDebugKotlin` — BUILD SUCCESSFUL from clean. **138 tests, 0 failures, 0 errors.** Stage 00 finished at 122; the 16 additional are this stage's.

### Stage 00 tests
No Stage 00 assertion was weakened and no asserted value changed. Three `@KNOWN_DEFECT` reasons were reworded to record that the wrong value is now *stated* rather than *inherited* — the defect itself is unfixed and still pinned. The two direct `TextKeyboard(...)` calls in the tests now go through a `GeometryFixtures.sentinel(...)` helper.

### New tests
Explicit roles, stable IDs and provenance on the bundled path; the primary action row is neither alpha nor utility; hiding utilities removes exactly the utility rows and leaves every other row's identity untouched; compact Coding keeps its primary action row despite matching Text's shape; pack rows carry `Pack` provenance and flag inferred roles; declared pack row ids are honoured; pack resolution is deterministic (including whitespace/case and the empty id); both sentinels identify themselves; placeholder rows are all `PLACEHOLDER`; duplicate, blank, and missing row IDs each fail validation; a sentinel with rows fails validation; stable IDs survive extension-row insertion.

## Behaviour intentionally changed from the Stage 00 baseline
**None.** Rendered geometry is unchanged, and the stage exits with all production geometry still running on the compatibility projection — the stage document's stated exit condition.

## Newly discovered hazard: sentinel `bottomModRowCount` is pixel-coupled
The stage document permits a sentinel-only correction "if a test exposed constructor-default behavior for a sentinel". That correction was investigated and **deliberately not taken**, because it is not pixel-neutral.

`FlorisImeSizing.keyboardUiHeight()` routes EDITING and SMARTBAR_QUICK_ACTIONS through its `else ->` branch into `computeKeyboardFrameHeight`, where `rawRowCount = 0` is coerced up to 4. At `bottomModRowCount = 2` the partition is alphaRows=2 / modRows=2 **plus the full gap budget**; at 0 it is alphaRows=4 / modRows=0 / no gaps. Correcting 2 → 0 changes the frame height of two real surfaces.

Both sentinels therefore keep the value 2, now stated at the site with the coupling documented in-code, and the correction is deferred to whichever stage retargets the geometry authorities (Stage 03/05). This is Stage 00's defect 9 narrowed: the *inheritance* is fixed, the *value* is not.

## Divergence from the handoff summary — gap retargeting
The Stage 01 handoff summary described retargeting `modRowUpperGap` / `modRowInnerGap` / `modRowLowerGap` onto semantic transitions. `01-semantic-rows.md` Non-goals say plainly: **"Do not change gap behavior"** and **"Do not switch sizing/layout to semantic roles yet."** Gap retargeting is `03-structural-gaps-bounds.md` ("Replace `N-2`/`N-1` gap mutation with semantic boundaries"), which depends on this stage's roles existing first.

The handoff explicitly authorised this resolution: *"Do not mechanically apply this summary if the exact Stage 01 document specifies a more precise implementation boundary."* The staged document was followed. This is a scope divergence from the summary, not a conflict between the summary and the canonical architecture — no guessing was required.

## Hazards and later-stage work deliberately left untouched
- The `layout()` heuristics: `rowCount >= 5`, `row.any { it.isAlpha }`, and space-row detection on literal code `32`. All still live, all still driving geometry. Stage 02/03 territory.
- The bottom-bezel touch-extension overlap (Stage 00 defect 6). Not expanded into.
- `TextKey.kt:142` `hasSlimSpaceRow = keyboard.bottomModRowCount >= 2` — another compatibility-projection consumer, untouched.
- The phantom top-mod-row misattribution (Stage 00 defect 1) and the frame-vs-row gap disagreement (defects 3 and 5).
- The missing `symbols2/western_wide.json` asset — documented follow-up, not used as licence for unrelated cleanup.
- No profile persistence, no frontend editor, no keybinding system, no public-concept renames.

## Worktree
Initial status: `?? .serena/` only. Final status: `?? .serena/` only — untouched, unstaged, not deleted. No ignored file (`local.properties`, credentials, signing config) was read, moved, staged, or printed.

## Still requires real-device validation
None of the above exercises real asset composition: `LayoutManager(context)` needs Android and `mergeLayouts` is `private suspend`, so the fixtures remain *shape* fixtures. On-device confirmation is owed for:
- No size jump or visual change on any surface versus the pre-Stage-01 build.
- No `IllegalArgumentException` from the new `init` validation against real assets — the validation is strict and runs on every composition.
- Coding with utility rows visible and hidden; Text; Symbols and Symbols2; Numeric and Phone; the Editing keyboard; smartbar quick actions; a user layout pack; and the loading placeholder.
- The number-row extension toggle, on and off.

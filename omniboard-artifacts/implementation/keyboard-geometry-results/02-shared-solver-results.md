# Stage 02 — Shared Geometry Solver: complete

## Branch & commits

Branch `KeyGeo`.

- Starting commit: `361a56a8` (accepted Stage 01 tip, pushed to `origin/KeyGeo` before this stage)
- `eb007482` — `feat(geometry): add the Stage 02 shared geometry solver`
- `05d3b0eb` — `test(geometry): pin the Stage 02 solver contract and compare it to legacy`

The model/solver and the comparison wiring are separate commits, as the stage prompt asks. Neither
is pushed; `origin/KeyGeo` remains at `361a56a8` pending review.

## Files changed

New, production (`app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/geometry/`):

- `GeometryInput.kt` — the solver input and its policy types
- `SolvedGeometry.kt` — the immutable result, the coordinate convention, and the solution type
- `KeyboardGeometrySolver.kt` — the solver

New, test (`app/src/test/kotlin/dev/patrickgold/florisboard/ime/keyboard/geometry/`):

- `SolverFixtures.kt` — policy-free solver-input fixtures mirroring the Stage 00 shapes
- `KeyboardGeometrySolverTest.kt` — 28 contract tests
- `LegacyGeometryComparator.kt` — comparison mode: the role→policy bridge and the differ
- `LegacyGeometryComparisonTest.kt` — 9 classified comparison tests

Modified:

- `GeometryContractMarkers.kt` — adds the `EXPECTED_FIX` marker alongside `COMPATIBILITY`,
  `KNOWN_DEFECT` and `MIGRATION_FIXTURE`

No production call site changed. Nothing on the render path consults the solver.

## The input

Exhaustive and role-keyed. Every policy — row heights, boundary gaps, the shared width reference,
frame sizing, insets, spacing, and validated user overrides — is looked up by `SemanticRowRole`.

The input has no way to express `bottomModRowCount`, a row-count threshold, or a "contains Space"
flag. Geometry therefore cannot be re-derived from a row's position, and a keyboard cannot change
character by gaining or losing a row.

Two structures carry the load:

- **`RoleBlockGaps` / `BoundaryGapPolicy`.** A *block* is a maximal run of adjacent rows sharing a
  role. A block declares a gap `above` it, `within` it between adjacent pairs, and `below` it. The
  three legacy `modRow*Gap` preferences map onto the coding-utility block exactly, and disappear on
  their own when there is no utility block.
- **`SharedWidthReference`.** `measuredRoles` decide how wide one unit is; `consumerRoles` are laid
  out against it. Entry rows define the grid, the primary action row aligns to it without being able
  to widen it, and utility rows fit their own units to the full content width. This reproduces the
  legacy `baseAlphaUnitWidth` / space-row / mod-row split without consulting `isAlpha` or code `32`.

`FramePolicy` has two variants — `Intrinsic` (rows sized from a base height, frame follows) and
`FitToHeight` (frame fixed, rows share what gaps and insets leave). Both satisfy the same invariant.

## Coordinate convention and rounding ownership

One convention: **half-open integer-pixel rectangles, origin at the frame's top-left, x right, y
down.** `right` and `bottom` are the first pixel outside the rectangle, so adjacent rectangles share
an edge value without overlapping.

The solver owns rounding, exclusively and at one site. It solves in continuous `Double` space and
rounds **edges** — never widths or heights — from an exact running total. A size is always
`right - left`, derived after rounding. Error therefore cannot accumulate: the last edge of a run
rounds the exact total. Consumers must not re-round; a consumer needing a sub-pixel value reads the
declared inputs travelling with the result.

## Unsatisfiable rather than coerced

A solve either satisfies every invariant or returns `Unsatisfiable` with *all* failed constraints.
There is no clipping, no coercion, no partially valid result — quietly coping is what made the
legacy authorities impossible to reason about. Rejected cases include non-finite or negative
dimensions, insets leaving no content width, duplicate row or item IDs, a width reference defined by
a role it does not lay out, non-positive overrides, a fitted frame too small for its own gaps, a
frame exceeding a declared cap, and a row too wide for the content area.

## Comparison mode

`LegacyGeometryComparator` runs both authorities over the Stage 00 fixtures. It compares *structural*
allocation: it removes the legacy alpha hitbox expansion, and the scenarios run at zero spacing,
because touch and visual rectangles are Stage 03's subject. It does not distort semantic roles to
reproduce known defects.

| scenario | legacy | solver | delta | differing |
|---|---:|---:|---:|---|
| defaultCoding | 335.0 | 350.0 | +15.0 | FRAME_HEIGHT, ROW_TOP, ROW_HEIGHT |
| codingUtilitiesHidden | 245.0 | 240.0 | −5.0 | FRAME_HEIGHT, ROW_TOP, ROW_HEIGHT |
| codingWithNumberExtension | 380.0 | 395.0 | +15.0 | FRAME_HEIGHT, ROW_TOP, ROW_HEIGHT |
| codingWithBothExtensions | 425.0 | 440.0 | +15.0 | FRAME_HEIGHT, ROW_TOP, ROW_HEIGHT |
| characters | 230.0 | 240.0 | +10.0 | FRAME_HEIGHT, ROW_TOP, ROW_HEIGHT |
| wideSymbols | 230.0 | 240.0 | +10.0 | FRAME_HEIGHT, ROW_TOP, ROW_HEIGHT |
| numeric | 230.0 | 240.0 | +10.0 | FRAME_HEIGHT, ROW_TOP, ROW_HEIGHT |
| numericAdvanced | 230.0 | 240.0 | +10.0 | FRAME_HEIGHT, ROW_TOP, ROW_HEIGHT |
| phone | 230.0 | 240.0 | +10.0 | FRAME_HEIGHT, ROW_TOP, ROW_HEIGHT |
| layoutPack | 230.0 | 180.0 | −50.0 | FRAME_HEIGHT, ROW_TOP, ROW_HEIGHT |

At `rowBaseHeight = 60`, `alphaRowHeightFactor = 1.0`, `bottomRowHeightFactor = 0.75`, gaps 8/4/8,
width 1080, spacing 0.

**Every difference is vertical.** Horizontal allocation is byte-identical in all ten scenarios —
asserted, not observed in passing: `no scenario changes horizontal geometry` fails on any item-left
or item-width difference. Horizontal geometry is a compatibility target and the solver hits it.

Each vertical difference is classified as an expected fix against a Stage 00 `@KNOWN_DEFECT`:

- **defaultCoding, +15.** Legacy partitions 6 rows as 3 alpha + 3 mod from counts alone, charging the
  top alpha row modifier height and making the primary action row indistinguishable from an alpha
  row. The solver reads the declared roles: 3 alpha + 1 primary action at full height, 2 utility at
  the short height, plus 20 of declared gap.
- **codingUtilitiesHidden, −5.** Legacy still bills a phantom modifier row and the entire gap budget
  when `bottomModRowCount` is 0, because the count-based partition assigns the fourth row to
  `topModRows`. With no utility rows there is no block to declare gaps, so none are charged. The
  result is compact Coding — the primary action row and every alpha ID survive unchanged — not Text.
- **numeric / numericAdvanced / phone / characters / wideSymbols, +10.** Legacy splits four rows
  2 alpha / 2 mod by count and then allocates them uniformly, so the frame and the rows disagree.
  The solver reads `NUMERIC` (or `SYMBOL`, or alpha + primary action) on every row and frames what
  is actually there.
- **layoutPack, −50.** Legacy coerces any keyboard with fewer than four rows up to four for framing.
  The solver frames three rows as three.
- **extension rows.** Legacy already gives them the short height, but only because they fall outside
  a positional window. The solver gives it to them because they declare `EXTENSION`, so each
  extension row now costs exactly one short row and affects no other row.

Anything outside the declared per-scenario set fails as an unresolved difference. There are none.

## Validation

### Focused

`./gradlew :app:testDebugUnitTest --tests 'dev.patrickgold.florisboard.ime.keyboard.geometry.*'` —
**97 tests, 0 failures.** Stage 00's `GeometryAuthorityCharacterizationTest` (22),
`ConstructionSiteContractTest` (24), `LayoutAssetDiagnosticTest` (6) and
`PersistenceMigrationFixtureTest` (8) are unchanged and still pass; Stage 02 adds
`KeyboardGeometrySolverTest` (28) and `LegacyGeometryComparisonTest` (9).

### Complete

`./gradlew :app:testDebugUnitTest :app:assembleDebug` — **BUILD SUCCESSFUL, 175 tests, 0 failures,
0 skipped.** The stage ends buildable.

### Coverage the stage prompt required

Row counts 0–8; Coding with utilities visible and hidden; one and two extension rows; the numeric,
phone and symbol families; portrait and landscape widths; default and extreme preferences (extreme
ones report rather than clip); asymmetrical width units, spacers and insets; unsatisfiable
constraints; rounding conservation in both axes; determinism for equal inputs.

## Behaviour intentionally changed

None observable. No production code path calls the solver, no legacy formula was removed or altered,
and no preference was touched. The differences above exist only between two test-visible
computations.

## Carry-forward hazards

Left untouched, as the stage prompt requires:

- `bottomModRowCount` is still legacy geometry state and is still what `FlorisImeSizing` and
  `TextKeyboard.layout` consume. The solver simply cannot see it; the comparator reads it only to
  drive the legacy side.
- The editing and smartbar sentinels keep the legacy value `2`. Untouched — sentinels have no rows
  and never reach the solver.
- Legacy geometry still consumes `isAlpha`, `rowCount >= 5` and Space-code detection. Unchanged.
- Unknown layout-pack row IDs still take the logged `COMPATIBILITY_FALLBACK` to `ALPHA`. The
  `layoutPack` comparison scenario exercises exactly that path.
- Gap behaviour, touch overlap, bottom-bezel behaviour, `hasSlimSpaceRow`, and the missing
  `symbols2/western_wide.json` asset are all unchanged.

Resolved, because the stage explicitly asked:

- **Coding utility row IDs are stable across visibility changes.** Stage 01 tested alpha-ID stability
  under extension-row insertion but not under utility visibility. `surviving row IDs are stable when
  utility visibility changes` now pins it: `alpha:0`, `alpha:1`, `alpha:2` and `primary_action`
  survive with their roles intact when the utility rows are hidden.

## Discrepancy with Stage 01

`GeometryPolicyRef`'s doc comment says "Stage 02 replaces `Unassigned` with real policy references."
The committed Stage 02 prompt does not assign that: its solver input is a policy *bundle* passed by
value, and wiring a policy reference into `NormalizedRow` would mean touching every construction
site again with no consumer for the result. `GeometryPolicyRef.Unassigned` is therefore untouched and
the anticipation in that comment is deferred. A later stage that introduces named, persisted profiles
is the natural home for it.

## Worktree

Clean apart from the untracked `.serena/`, which was left alone. No ignored, machine-local, or
credential files were staged or modified.

## Still requires real-device validation

Strictly speaking, none: Stage 02 changes no observable behaviour, so a device smoke test can only
confirm that nothing moved. Worth one pass anyway to confirm the debug build installs and the
keyboard renders identically to the Stage 01 build, since the app now ships three new (unreferenced)
production classes.

The differences in the table above are what a later stage will make visible on a device. They are
not visible yet.

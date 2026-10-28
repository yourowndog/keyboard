# Reference keyboard — runtime reconciliation

Audit date: 2026-10-28. Read-only inspection of Sleeper `/home/sam/projects/keyboard` and fetched `origin/keygeo-stage06-verified`. No build, test execution, installation, device interaction, or source mutation was performed. Indexed navigation tools were not exposed in this session; scoped `rg`, `git show`, and narrow source reads were used under the repository's documented fallback.

## Baseline gate: these branches diverge

- Primary checkout: `device-validation`, `87dbba44c`, one voice commit ahead of `origin/device-validation`; multiple dirty LCARS/theme/rendering files and untracked `ime/theme/lcars/` implementation/tests.
- Stage 06 reference: `origin/keygeo-stage06-verified`, `998e9d9ca`, including `704609fd5` (layout-pack implementation) and its report commit.
- Shared merge base: `cf2b25c96` (Stage 05).
- The primary branch contains separate spatial-coordinate, v4 harvest, raw-editor autocorrect, and voice work that Stage 06 does not. A scoped geometry diff from current HEAD to Stage 06 removes `SpatialCoordinateFrame.kt` and changes preferences, solver, and `TextKeyboardLayout`.

**Implementation prerequisite:** build a deliberate integration branch preserving both Stage 06 layout-pack work and current coordinate/voice/harvest behavior. Do not simply reset the daily driver to Stage 06, cherry-pick every worktree blindly, or treat the dirty LCARS implementation as accepted runtime evidence. Record exact baseline and inspect uncommitted work before adopting any of it.

## What is already reusable

Source paths below are relative to the repository root. Line numbers refer to inspected primary checkout unless a ref is specified.

| Existing capability | Source evidence | Consequence for reference work |
|---|---|---|
| Explicit row identity/role | `ime/keyboard/SemanticRow.kt:40` under `app/src/main/kotlin/dev/patrickgold/florisboard/` defines `ALPHA`, `PRIMARY_ACTION`, `NUMBER_ROW`, `CODING_UTILITY`, `EXTENSION`, `NUMERIC`, `SYMBOL`, `PLACEHOLDER` | Reuse semantic topology; do not infer row purpose from Z-row position or a Space label |
| Pure solver and input model | `ime/keyboard/geometry/KeyboardGeometrySolver.kt:39,50,282,308`; `GeometryInput.kt:74,90,148,155,221` | Width units, explicit spacers, grow weights, row alignment, role heights, spacing and insets already exist; no reason to invent a second geometry engine |
| Authored width support | `TextKeyboardGeometryBridge.kt:80–112` | Pack-authored width units survive into solver input; authored keys intentionally do not acquire implicit growth |
| Three layers of bounds | `KeyBoundsDerivation.kt:48,69,78,104`; `TextKeyboardGeometryBridge.kt:200` | Structural cells, visible caps, and touch bounds are already separate; special outlines should not silently rewrite touch behavior |
| Stable solved records | `SolvedGeometry.kt:70,81,118` | Target compound cap drawing from solved geometry, not from separately guessed pixel arithmetic |
| Theme-selected corners | `lib/snygg/src/main/kotlin/org/florisboard/lib/snygg/value/SnyggShapeValue.kt:199,245` | Independent corner radii can already express rectangles, one-ended pills, and simple D silhouettes; do not build custom path infrastructure for every rounded key |
| Existing hints/popups | `TextKey.kt:41–43,144`; `TextKeyboardLayout.kt:382–429,520`; `PopupUi.kt:47,90` | Symbol/number hints and basic/extended popup behavior already exist; preserve their data and dispatch paths |
| Smartbar and voice state machine | `ime/smartbar/Smartbar.kt:292,428,504,783,823` | Reuse actions, candidates, phrase row, voice recording/provider controls rather than replacing functionality with decorative MENU/VOICE artwork |

All app Kotlin paths in the table share prefix `app/src/main/kotlin/dev/patrickgold/florisboard/` unless a full alternative path is supplied.

## Source path lookup

App source prefix: `app/src/main/kotlin/dev/patrickgold/florisboard/`. Resolve short filenames above/below using:

- `TextKey.kt`, `TextKeyboardLayout.kt`: `ime/text/keyboard/`.
- `TextKeyboardGeometryBridge.kt`, `GeometryInput.kt`, `SolvedGeometry.kt`, `KeyBoundsDerivation.kt`, `KeyboardGeometryPolicy.kt`: `ime/keyboard/geometry/`.
- `PopupUi.kt`: `ime/popup/`.
- `KeyCustomization.kt`, `SemanticRow.kt`: `ime/keyboard/`.
- `LayoutPackSchema.kt` on Stage06: `ime/keyboard/pack/`.

Line locations are snapshot locators, not stable permalinks; compare source at the recorded ref before editing.

## Concrete reference-to-code gaps

### 1. Split keycaps are not the existing hint presentation

`TextKeyboardLayout.kt:451–542` (`TextKeyButton`) draws a single `SnyggBox` per key, primary label centered and hinted label at `Alignment.TopEnd`. The reference visibly has separate upper and lower rectangular color surfaces with a black seam and different typography scale/placement. A color stylesheet alone cannot introduce that internal two-region composition.

Add a native **compound keycap presentation** with upper hint region, seam, and lower primary region, derived inside one solved key allocation. Its geometry should specify band ratio and seam separately from outer row gaps. Bind upper text to the existing computed hint data, not a second hardcoded character table. Check all 10/9/7 alpha keys and bottom comma/period/mode keys as separate cases.

Still images do not prove whether upper panels are independently tappable. Default proposal: one existing key action surface with unchanged long-press behavior until interaction evidence says otherwise. If independent upper-band taps become a product requirement, that is a separately reviewed dispatch/touch task.

### 2. Structural width/height edits must feed the solver

`TextKeyboardLayout.kt:329–350` parses `KeyCustomizationManager` values and expands/shrinks `visibleBounds` after solving, explicitly leaving structure and touch allocations unchanged. `ime/keyboard/KeyCustomization.kt:29–66` stores a map keyed by integer key code. Duplicate key instances cannot be styled or sized independently through that mechanism, and visual expansion can overlap neighbors.

Reference row stagger, wide shift/delete edge blocks, bottom control blocks, and central instrument span must be encoded as structural units/spacers or per-instance solver overrides. Align this with existing KeyGeo Stage 07 rather than disguising post-layout cap resizing as structural work. Use profile/layout/row/key-instance identity; preserve a documented migration for saved code-keyed customizations.

### 3. Stock composition differs from the reference

`KeyboardGeometryPolicy.kt:35–57,80,97,105–115` specifies a ten-unit alpha grid with centered nine-key rows, ordinary one-unit Shift/Delete, and primary composition `Tab | comma | Space | period | Enter`. Tab/Enter are 1.5 units and Space grows. Reference screenshots show different wide outer control blocks, a visually long circular-ended Shift, special Delete/Enter detailing, and a central bottom instrument instead of a conventional labeled space cap.

Build a reference-target layout variant explicitly. Preserve Coding actions and daily-driver fallback; do not globally overwrite canonical defaults to make one design fit. The reference's top labels do not establish how language, candidate, or modifier controls should disappear.

### 4. Stage 06 layout schema is intentionally structural

At `origin/keygeo-stage06-verified`, `ime/keyboard/pack/LayoutPackSchema.kt:32–36` states it describes rows, roles, ordered keys, spacers and width units, and cannot express colors/shapes/corners. `:74–80` lists allowed row/element/policy fields; `:153` defines rows, `:203` elements, `:283` row policy. `LayoutPackElement.style` is documented as popup grouping, **nothing visual** (`:200`). Do not repurpose it as a visual style identifier.

Introduce presentation identity as a separate documented contract resolving a layout/key instance to a cap renderer and theme roles. Avoid inserting renderer-specific colors/path syntax into serialized layout packs. Any required schema change needs a versioned migration and round-trip validation, not unknown-field reliance.

### 5. Special keys need composed foreground details, not just corner radius

Snygg corner shapes can cover simple flat rectangles and D-ended blocks. The coral concentric-circle Shift face, coral stripe and perforated-looking Delete header, Enter's inset curve, segmented voice control, and cyan bottom indicator row require native composed drawing/foreground assets with explicit insets and clipping. Keep a rectangular/declared touch allocation even where the visible outline is curved unless separately tested interaction requirements change it.

Use named presentation primitives shared by the key renderer and preview fixture; avoid a collection of code-number checks inside `TextKeyButton`.

### 6. Existing analytic chassis is not automatically the new target

Dirty `TextInputLayout.kt:67–103` mounts `drawLcarsChassis` through `drawBehind`, activated when theme ID contains `lcars`. Untracked `LcarsChassisGeometry.kt:51–123` defines primary/secondary top/left rails, shoulders, channels and a viewport. This is an elbow-chassis design. The supplied reference primarily reads as a black rectangular ground with distributed shaped controls; it does not visibly establish that large outer elbow.

Compare and selectively reuse paths, snapping, clipping and theme-role plumbing. Do not force the previous chassis silhouette underneath the reference. Theme-name substring detection also should not become the long-term presentation selector; use an explicit supported presentation configuration/fallback when implementing the new variant.

### 7. Smartbar must be a real layout, including non-idle states

The screenshot's MENU / EMOJI / LANG group and segmented VOICE block need explicit widths, gaps, labels, alignment and shape composition. `SmartbarMainRow` and `WhisperBar` currently serve different candidate/action/voice states. A new chrome layout should delegate into the existing actions while preserving voice recording, pause/cancel/submit, provider selection, candidates, overflow, phrase prediction and narrow-width behavior. Reserve honest instrument content: do not depict an animated signal as audio level unless it is driven by real data.

### 8. Popups need a separate design and acceptance slice

`TextKeyboardLayout.kt:382–405` sizes popups from a solved reference key: portrait width 1.1× and height 2.5×; landscape 1.0× and 3.0×. `PopupBaseBox`/`PopupExtBox` use existing Snygg elements and controller. Nothing in these stills proves a deployed popup is currently open; repeated upper key bands should not be interpreted as popup screenshots.

First implement persistent band geometry. Then verify popup anchoring from visible/solved geometry, screen-edge clamping, expanded selection, pressed/focus styling, hint choice and popup keys. Any new compound-popup artwork should preserve existing controller semantics.

### 9. Typography needs native placement plus theme values

The source centers primary labels and puts hints top-right; the reference uses tall, condensed uppercase primary glyphs and smaller differently aligned hint glyphs. Snygg can supply appearance values, but band-local placement, label baselines, icon replacement and clipping belong to native presentation. Identify a bundled/licensable font; screenshot pixels cannot establish its exact font identity. Do not achieve condensed type by shrinking the whole touch cell.

## Recommended infrastructure slices

1. **Baseline reconciliation:** document integration branch, port Stage 06 without dropping spatial/harvest/voice changes, establish clean source/test baseline and preserve current preferences.
2. **Presentation contract:** per-variant and per-key-instance cap identity, band geometry, shape/foreground primitive IDs, semantic color/type roles; separate structure from presentation.
3. **Structural reference layout:** ten-column unit grid, row stagger/spacers, wide controls, primary bottom span; solver conservation/non-overlap tests and landscape fallback.
4. **Split-cap renderer:** one key allocation, two surfaces/seam, existing hints, state-aware labels; fixture snapshots at known size.
5. **Special-key primitives:** Shift ring/D outline, Delete instrumentation, Enter inset, segmented voice, instrument space; individual deterministic geometry fixtures.
6. **Smartbar composition:** reference chrome with existing actions and full candidate/voice states.
7. **Popup/hit behavior:** preserve semantics, verify anchoring/clamping and curved-cap touch policy; device interactions.
8. **Slate theme/type finish:** semantic slate, coral and cyan tokens and typeface, then secondary palettes/names.
9. **Acceptance:** known APK/source hash, screenshot overlays against reference bounds, default/narrow/landscape/orientation modes, touch/sliding/long-press/space gesture/terminal and voice regressions.

Tasks 3–7 must not be collapsed into a theme-only assignment. Each worker should receive one slice, a bounded file surface, expected evidence, and a report distinguishing implemented/built/installed/accepted.

## Existing tests to extend (not executed in this audit)

Under `app/src/test/kotlin/dev/patrickgold/florisboard/ime/keyboard/geometry/`: `KeyboardGeometrySolverTest.kt`, `NormalizedGeometryTest.kt`, `ModeGeometryCharacterizationTest.kt`, `NumberRowGeometryTest.kt`, `PrimaryActionRowGeometryTest.kt`. Stage 06 adds `ime/keyboard/pack/LayoutPackSchemaTest.kt`, `LayoutPackCompositionTest.kt`, `LayoutPackValidationTest.kt`, `LayoutPackCompatibilityTest.kt`, `LayoutPackStoreTest.kt`.

The primary checkout also has untracked `ime/theme/lcars/LcarsChassisGeometryTest.kt`; inspect ownership/quality before adopting. These automated tests cannot establish actual popup positioning, IME clipping, special-key hit behavior, font rendering, or reference fidelity on the phone.

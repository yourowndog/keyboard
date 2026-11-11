# Stage 03 Results — Clean Solver Cutover and Canonical Geometry Normalization

Branch: `keygeo-phase3-normalization`, cut from `origin/KeyGeo` at
`97afeae9df7767a92ee450d7e1f344b0b1524406`.

Nothing was pushed. No remote branch was created, moved, or deleted. No APK was
installed. `local.properties` and every other ignored machine-local file are
untouched; `git clean` was never run. The abandoned
`forensics/keygeo-phase3-codex-attempt` branch was not inspected, cherry-picked,
merged, repaired, or deleted.

## Commits

| SHA | Purpose |
| --- | --- |
| `679a8063` | Governing decision and Stage 03 contract update |
| `0079c1ed` | Flexible solver-item growth (`growWeight`) and its tests |
| `821a9783` | Authoritative production cutover and bounds derivation |
| `06767298` | Per-key restore-defaults control and spacing cleanup |
| `d2e438dc` | Canonical normalized-geometry test suite |

## What was decided

Historical OmniBoard geometry is forensic evidence of old controls and
accumulated compensations. It is not a canonical default, not a visual target,
and not a migration seed. Decision 8 is superseded; Decision 16 records the
normalized policy. Ergonomic specialization returns later, through explicit
customization controls, on top of a mathematically normalized baseline.

## Files added

| File | Role |
| --- | --- |
| `ime/keyboard/geometry/KeyboardGeometryPolicy.kt` | The one item-default authority. Canonical width units, grow weights, role sets, and `GeometryPreferences` with boundary validation. |
| `ime/keyboard/geometry/KeyBoundsDerivation.kt` | Structural → touch and structural → visible as two named, independently testable steps. |
| `ime/keyboard/geometry/TextKeyboardGeometryBridge.kt` | The only road between `TextKeyboard` and pixels. Pure; three-way `Result`. |
| `ime/keyboard/geometry/GeometryPreferencesCompose.kt` | Reads the geometry preferences once, into one value both consumers key their caches on. |
| `test/.../geometry/NormalizedGeometryTest.kt` | 37 assertions pinning the canonical layout. |

## Files removed

| File | Why |
| --- | --- |
| `ime/keyboard/KeyboardGeometryArithmetic.kt` | The second height authority. The frame now comes from the same solve as the keys. |
| `Keyboard.layout()` (abstract) and `TextKeyboard.layout()` | The first authority: alpha/mod/Space heuristics, double-applied spacing, positional gap mutation. |
| `test/.../GeometryAuthorityCharacterizationTest.kt` | Existed to characterize the two authorities. |
| `test/.../LegacyGeometryComparator.kt` | Existed to compare the two authorities. |
| `test/.../LegacyGeometryComparisonTest.kt` | Same; and none of the three can compile against a `layout()` that no longer exists. |

## Hard-coded geometry inventory and disposition

| Historical constant | Where it lived | Disposition |
| --- | --- | --- |
| `2.68`, `1.56`, `1.26` mod widths | `TextKey.compute()` intrinsic table | Removed. Utility rows are nine equal `1.0` cells. |
| `5.00` Space width | `TextKey.compute()` | Removed. Space is `1.0` with a grow weight; five units is now an outcome of the ten-column grid. |
| `1.50` / `1.25` / `0.72` / `0.8` key widths | `TextKey.compute()` | Removed. `1.5` survives only as the Tab/Enter *semantic default of the primary action row* in `KeyboardGeometryPolicy`. |
| `1.1` spacebar height factor | `TextKey.compute()` | Removed, along with the comment claiming it compensated for `1.33`. Every key height factor is `1.0`. |
| `VerticalAlignment.CENTER` on Space | `TextKey.compute()` | Removed. Row role owns vertical extent. |
| `0.20f` Escape / `TOGGLE_NUMBER_ROW` padding | `TextKey.compute()` | Removed. The alignment trick it produced is gone. |
| Gap applied to rows `N-2` / `N-1` | `TextKeyboardLayout` | Replaced by `RoleBlockGaps` declared for `CODING_UTILITY` only. |
| Spacing applied whole to both sides | `TextKeyboard.layout()` | Replaced by a half-inset per side in `KeyBoundsDerivation`. One 2dp preference, one 2dp gap, 1dp outer margin. |
| `FALLBACK_ROW_COUNT = 4` | `FlorisImeSizing` | **Retained exception.** A sentinel keyboard declares no rows; four is what the loading and editing placeholders have always occupied. Used only when the solve returns no rows at all. |
| `BOTTOM_EDGE_TOUCH_EXTENSION_ROWS = 1.0f` | `KeyBoundsDerivation` | **Retained exception**, and now named. This is the bottom-edge touch policy, not a layout dimension: it extends touch bounds only. |
| `PRIMARY_ACTION_EDGE_UNITS = 1.5` | `KeyboardGeometryPolicy` | **Retained by design.** A declared semantic default for one row role, in the one policy file, not a per-key saved customization. |
| `bottomModRowCount` | `TextKeyboard` | **Deprecated compatibility projection.** No geometry code reads it. Stage 04 removes it. |

## Behavior intentionally preserved

- Every key action, label, popup mapping, and terminal behaviour, including the
  full Coding utility set (Ctrl, Tmux, Escape, Σ, Undo/Redo, navigation).
- Frame stability across Characters / Symbols / Symbols2 / Numeric-Advanced:
  those surfaces still take the frame of the Characters surface they opened from,
  now documented as deliberate policy rather than incidental.
- Bottom-edge hitability of the last row.
- Bottom/window offset handling, still outside solved row geometry.
- Forgiving touch targets: touch bounds cover the whole structural allocation.
- Layout Pack authored width units and spacers, verbatim.
- Existing saved per-key customizations. Nothing is wiped on upgrade.
- Compact Coding (utility rows hidden) is still Coding, not a Text profile.

## Behavior intentionally changed

- The three alpha rows share one unit; nine-key rows are centred, not stretched.
- Shift and Delete are one-unit alpha cells.
- Utility rows: nine equal cells, no narrow navigation keys, no `0.8` Undo/Redo,
  no wide Ctrl/Tmux, no Escape/Σ padding alignment.
- Utility rows are 75% of a full row via a role adjustment on a `1.0` base; at
  100% they equal alpha rows exactly.
- A 2dp spacing preference now produces a 2dp gap, not 4dp.
- Numeric, Phone and Symbols receive no Coding boundary gaps.
- The `alphaSpacing*` / `modSpacing*` sliders are removed from settings; they had
  stopped controlling anything.
- A new "Restore per-key defaults" control clears only
  `keyboard__key_customizations`, to `{}`.

## Tests and builds

| Command | Result |
| --- | --- |
| `:app:testDebugUnitTest --tests '*NormalizedGeometryTest*'` | 37 tests, 0 failures, 0 errors |
| `./gradlew --no-daemon testDebugUnitTest` | BUILD SUCCESSFUL — no failures in any module |
| `./gradlew --no-daemon assembleDebug` | BUILD SUCCESSFUL |

No pre-existing test failures were observed on this branch, so nothing here is
being distinguished from a regression. Warnings remain in unrelated files
(`BackupScreen.kt`, `RestoreScreen.kt`, `CrashUtility.kt`, a Room KSP warning);
all predate this stage.

## Device validation

**Not performed. Not passed.** No device was attached and no APK was installed;
installation was outside the granted authorization for this stage. Correctness of
real interaction cannot be claimed from source and unit tests alone. The
14-item manual script in `03-structural-gaps-bounds.md` remains outstanding in
full.

## Remaining dependencies

- **Stage 04** — remove `bottomModRowCount`; profile naming and persistence.
- **Stage 05** — stable cross-mode frame grouping, profile-scoped persistence,
  and the complete Text/Coding profile system.
- **Stage 07** — per-key customization schema redesign: instance-aware keys, and
  migration of the now-unread `alphaSpacing*` / `modSpacing*` stored values.

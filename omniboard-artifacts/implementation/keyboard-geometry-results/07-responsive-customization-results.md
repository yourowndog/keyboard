# Stage 07: Responsive Customization Results

## Summary

Stage 07 implemented responsive key customization with layered boundaries across geometry solver, bridge, preferences, and UI for OmniBoard. Key accomplishments:

1. **Layered Customization Model & Schema v2** (`KeyCustomization.kt`):
   - Partitioned customizations into three explicit architectural layers: `STRUCTURAL` (width/weight/height), `TOUCH` (hitbox expansions and touch margins), and `VISUAL` (padding insets and visual scales).
   - Implemented `CustomizationTarget` replacing bare global Int key codes with hierarchical targeting (profileId, layoutId, rowId, itemInstanceId, keyCode).
   - Schema version 2 document format with versioned serialization and migration from legacy JSON format (`migrateLegacyJson`). Ambiguous structural instances are safely segregated into `legacyUnresolved`, while visual styling is preserved.

2. **Responsive Geometry Solver Integration** (`GeometryInput.kt`, `KeyboardGeometrySolver.kt`):
   - Added per-item overrides to `GeometryOverrides`: `itemWidthMultiplierByStableId`, `itemGrowWeightDeltaByStableId`, and `itemHeightMultiplierByStableId`.
   - Solver applies structural scale and grow weight deltas during placement calculation, letting neighboring flexible keys (e.g. space bar) absorb width adjustments responsively without breaking row bounds or clipping.

3. **Bridge & Layout Integration** (`TextKeyboardGeometryBridge.kt`, `TextKeyboardLayout.kt`, `FlorisImeSizing.kt`):
   - Added `buildOverrides` helper translating customization documents to solver `GeometryOverrides`.
   - Updated `solve` and `frameHeight` to accept `overrides`.
   - Updated `applyTo` to apply touch layer margins to `touchBounds` and visual layer padding to `visibleBounds` independently, eliminating touch stealing and hitbox drift.

4. **Settings UI & Profile Scope Migration** (`KeyCustomizationScreen.kt`, `AppPrefs.kt`):
   - Settings editor updated to modify structural and visual layers cleanly.
   - Verified `key_customizations` is tracked under `AppPrefs.STAGE_04_PROFILE_SCOPED_KEYS` and migrates across profile scopes cleanly.

5. **Test Suite** (`ResponsiveCustomizationTest.kt`):
   - Unit tests covering schema v2 roundtrip, legacy JSON migration, hierarchical target matching, responsive solver absorption, and layer definitions.
   - Verified layout pack row policy inertness and profile migration test suites pass with 0 regressions.

## Verification

```bash
./gradlew --no-daemon :app:testDebugUnitTest \
  --tests 'dev.patrickgold.florisboard.ime.keyboard.geometry.*' \
  --tests 'dev.patrickgold.florisboard.ime.keyboard.pack.*' \
  --tests 'dev.patrickgold.florisboard.app.ProfileScopeMigrationTest'
```

All tests pass with 0 failures.

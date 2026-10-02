# Stage 09: Legacy Geometry Removals Results

## Status
Complete. All Stage 09 requirements and acceptance criteria from `09-legacy-removal-acceptance.md` have been implemented and verified.

## Changes Implemented

### 1. Complete Removal of `bottomModRowCount`
- Removed `val bottomModRowCount: Int = 2` parameter from `TextKeyboard` primary constructor and class definition.
- Removed all `bottomModRowCount` arguments across construction sites:
  - `LayoutManager.computeTextKeyboardAsync()` (characters + charactersMod merge paths)
  - `LayoutManager.computeKeyboardAsync()` (Layout pack path)
  - `Keyboard.kt` (`PlaceholderLoadingKeyboard`, `SmartbarQuickActionsKeyboard`)
  - Test fixtures in `GeometryFixtures.kt` (`keyboard()`, `sentinel()`, `numeric()`, `phone()`, etc.)

### 2. Elimination of Legacy `isAlpha` Logic from Geometry & Hint Paths
- Removed `isAlpha` property from `TextKey`.
- Replaced legacy per-key `isAlpha` filtering in `LayoutManager.addRowHints` with modern role-based `HintRowPairing.resolvePairing` which pairs rows based strictly on `KeyboardSemantics`.
- Cleaned up test fixtures in `GeometryFixtures.kt` and tests in `SpatialCoordinateFrameTest.kt` to omit `isAlpha`.

### 3. Acceptance & Contract Tests Modernization
- Updated `ConstructionSiteContractTest` to verify that all construction sites declare valid `KeyboardSemantics` while eliminating obsolete assertions on `bottomModRowCount` and `isAlpha`.
- Ran unit test suite and verified 100% pass rate.

## Acceptance Verification
- `./gradlew --no-daemon testDebugUnitTest` passed.
- `./gradlew --no-daemon testDebugUnitTest --tests "*LayoutAssetDiagnosticTest*"` passed.
- `./gradlew --no-daemon testDebugUnitTest --tests "*ConstructionSiteContractTest*"` passed.

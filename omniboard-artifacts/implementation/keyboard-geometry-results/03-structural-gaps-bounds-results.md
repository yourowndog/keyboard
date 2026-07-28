# Stage 03 — Structural Gaps and Bounds: complete

## Branch and commits

Branch: `KeyGeo`

- Starting commit: `97afeae9` (accepted Stage 02 tip)
- Pre-stage `origin/KeyGeo`: `361a56a8`
- Accepted Stage 02 history pushed and confirmed at `origin/KeyGeo`: `97afeae9`
- `62dce46c` — `feat(geometry): cut over Stage 03 layout bounds`

The Stage 03 commits are intentionally not pushed.

## Objective

Make the Stage 02 semantic solver the live authority for keyboard frame sizing,
row placement, structural gaps, visible bounds, touch bounds, and popup reference
geometry. Remove the parallel positional gap mutation without broad legacy
cleanup.

## Files changed

Production:

- `FlorisImeSizing.kt` — resolves live geometry and reads its frame height
- `TextKeyboardGeometry.kt` — maps semantic keyboards to solver input and derives
  touch, visible, spacer, and popup geometry
- `TextKeyboardLayout.kt` — resolves once at actual Compose width and applies the
  immutable result
- `TextKey.kt` — exposes structural allocation separately from touch and visual
  bounds
- `TextKeyboard.kt` — records that live geometry no longer reads the legacy
  modifier-row count
- `GeometryInput.kt`, `SolvedGeometry.kt`, and `KeyboardGeometrySolver.kt` —
  carry declared touch expansion and visual padding through the shared solve
- `SemanticRow.kt` — corrects the obsolete Stage 01 policy-reference comment

Tests:

- `TextKeyboardGeometryTest.kt` — 11 focused live-adapter and derived-bounds
  tests

Canonical documentation:

- `docs/keyboard/geometry-hitboxes.md` — describes the live semantic solver
  pipeline and its structural, touch, visible, and popup layers

## Live dependency chain

1. `FlorisImeSizing.keyboardUiHeight()` reads the solved frame height.
2. `TextKeyboardLayout` obtains one immutable solution at the actual Compose
   width and applies solved rows and items.
3. `TextKeyboard.applyGeometry()` assigns structural, touch, and visible bounds.
4. Semantic `CODING_UTILITY` block boundaries own the upper, inner, and lower
   gaps.
5. The final row's touch bounds explicitly extend to the solved frame bottom.
6. Popup sizing uses representative solved entry-key geometry and clamps edge
   previews horizontally to the frame.

Production no longer calls `TextKeyboard.layout()`. Its legacy arithmetic remains
for characterization and later removal; it is not another live authority.

## Important decisions

- Alpha, Primary Action, and Coding Utility remain distinct semantic roles.
- Coding gaps are attached only to `CODING_UTILITY` boundaries. Hidden utilities,
  Numeric, Phone, Symbols, and layout-pack rows do not acquire gaps by position.
- Symbols, Symbols2, and Numeric-Advanced solve their own rows into the current
  Characters frame height, preserving the established mode-transition contract
  while keeping one result authoritative within each rendered keyboard.
- Layout-pack spacers keep structural width but receive empty touch and visible
  bounds.
- Existing per-key customization remains a post-solve visual-only adjustment.
- Legacy width preferences above 100% are capped at 100% before solving. The
  solver's containment contract forbids the old overflow-and-clip behavior.
  Values at or below 100% retain their intended effect.

## `GeometryPolicyRef.Unassigned`

It remains deliberately `Unassigned`. The live consumer uses one role-keyed
policy bundle, so assigning a per-row reference would be ceremonial rather than
functional. The stale Stage 01 comment promising Stage 02 assignment was updated
to describe this deferral.

## Layout-pack 180 px result

The focused layout-pack test traces the fixture as exactly three semantic rows.
Unknown pack row IDs retain the logged `COMPATIBILITY_FALLBACK` classification to
`ALPHA`; the fixture therefore solves as three ordinary 60 px rows with no
Coding-utility gaps:

```text
3 rows × 60 px = 180 px
```

Spacers still consume their declared structural units and cannot receive touch or
visible bounds. Nothing legitimate is omitted. The legacy 230 px result came
from coercing a keyboard with fewer than four rows to four-row framing, so the
50 px reduction is an intentional correction rather than a compatibility target.

## Verification

- `:app:compileDebugKotlin` — successful
- Stage 03 focused class — 11 tests, 0 failures, 0 errors
- `./gradlew --no-daemon :app:testDebugUnitTest --tests 'dev.patrickgold.florisboard.ime.keyboard.geometry.*' --tests 'dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboardGeometryTest'`
  — `BUILD SUCCESSFUL`; 108 tests, 0 failures, 0 errors, 0 skipped
- `./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug`
  — `BUILD SUCCESSFUL`; 186 tests, 0 failures, 0 errors, 0 skipped
- `git diff --check` — clean

A debug APK was assembled. It was not installed, and no manual or device testing
was performed.

## Observable behavior

The solver now affects the live UI. At the Stage 02 comparison fixture values,
the characterized vertical corrections become observable:

- default Coding and Coding with extension rows: `+15 px`
- Coding with utilities hidden: `-5 px`
- Characters, wide Symbols, Numeric, Numeric-Advanced, and Phone: `+10 px`
- three-row layout pack: `-50 px`, from `230 px` to the proved `180 px`

Coding gaps now follow semantic utility boundaries rather than `N-2`/`N-1`.
Final-row bottom touch coverage follows the solved frame. Popup previews use
representative entry-key size and remain inside horizontal screen edges. Width
settings above 100% no longer overflow and clip.

## Preserved legacy hazards

- `bottomModRowCount`, including sentinel value `2`, remains in legacy models and
  comparison code but is no longer read by live sizing or placement.
- `isAlpha` remains only where the preserved alpha-key touch-expansion policy
  needs it; it does not reconstruct row identity.
- Legacy `rowCount >= 5`, positional modifier rows, and Space/code `32` logic
  remain in the disconnected legacy layout path and characterization tests.
- Unknown layout-pack row IDs retain the logged `COMPATIBILITY_FALLBACK` to
  `ALPHA`.
- Touch overlap policy beyond this stage, bottom-bezel behavior,
  `hasSlimSpaceRow`, and the missing `symbols2/western_wide.json` asset remain
  deferred.

No characterization test was weakened.

## Discrepancies and conflicts

No conflict with the Stage 03 contract or canonical architecture was found. The
only newly explicit compatibility difference is the containment cap for width
preferences above 100%; it is documented and tested rather than hidden.

## Worktree and Git operations

Initial worktree: only untracked `.serena/`.

Performed:

- verified repository, `KeyGeo`, `HEAD`, worktree, and remote refs
- reviewed the three accepted Stage 02 commits for coherence
- pushed accepted history through `97afeae9` to `origin/KeyGeo`
- confirmed local and remote tips matched before Stage 03
- created normal Stage 03 commits without merge, rebase, reset, force-push,
  branch rename, PR creation, or changes to `main`

Ignored machine-local configuration, credentials, signing files, and `.serena/`
were not touched.

## Later device validation

Device validation should cover:

- bottom-edge taps and the lower Coding gap
- non-zero upper, inner, and lower Coding gaps
- Coding utilities visible and hidden
- extension-row combinations
- Characters, Symbols, Symbols2, Numeric, Numeric-Advanced, Phone, and Coding
  transitions
- portrait and landscape placement
- popup previews at left, center, and right edges
- layout-pack rows and spacer dead zones
- width preferences below, at, and above 100%
- developer touch-boundary overlay versus rendered keycaps

## Device-validation reconciliation

The first installed Stage 03 build (`0.5.0-debug+5f525118`) exposed two
independent problems.

### Persisted legacy customization baseline

`KeyCustomization()` and the `keyboard__key_customizations` preference default
are neutral: zero padding and `1.0` width/height factors, encoded as `{}`. The
installed APK did not contain different defaults. Replacement installation
preserved this earlier app-data payload:

- Enter: top padding `20`
- Tab: top padding `20`
- Space: top padding `20`, height `1.1`, width `0.8`
- Shift: right padding `20`, height `0.7`, width `1.4`
- Delete: left padding `20`, height `0.7`, width `1.4`

No comma or period entry was present. The JSON is parsed once and applied once,
after solving, to `visibleBounds`. Global integer-key-code identity means the
same code receives the same visual override in every occurrence; Stage 07 owns
the instance-aware migration and solver-backed reflow.

The reconciliation adds an explicit reversible neutral baseline. Before active
customizations become `{}`, their exact JSON is saved to
`keyboard__key_customizations_backup`; the settings screen can restore it.
Nothing else in the preference store is cleared.

### Symbols crash

The crash was a Stage 03 adapter defect, not a font-scale effect and not a
per-key JSON override. On the test device:

```text
effective density = 396 / 160 = 2.475
2 dp alpha spacing = 4.95 px
entry reference = (1440 - 2 * 4.95) / 10 = 143.01 px
PRIMARY_ACTION units = 1.25 + 0.8 + 1 + 5 + 1 + 0.72 + 0.72 + 0.72 = 11.21
incorrect shared-grid request = 143.01 * 11.21 = 1603.1421 px
```

The live adapter incorrectly made `PRIMARY_ACTION` consume the ten-unit Symbols
entry reference. Primary Action now solves its own row reference. Every item is
preserved and reallocated within the 1440 px frame; nothing is clipped,
overlapped, or relabeled.

Malformed persisted geometry factors, spacings, and gaps are also sanitized at
the production boundary using neutral, contained values with a warning for each
correction. Stored values are not rewritten. Direct calls to the pure solver
retain their strict `Unsatisfiable` contract.

### Ownership retained

- Stage 04 owns profile-scoped migration and truthful Alpha / Primary Action /
  Coding Utility display terminology while compatibility storage keys remain.
- Stage 07 owns stable key-instance identity, complete role-level controls,
  eligible-key coverage including comma and period, structural reflow,
  validation feedback, and migration of the recoverable legacy JSON bucket.
- Width containment at 100% remains approved constrained behavior.
- Legacy visual width overlap remains visible until Stage 07; the neutral reset
  removes it from the baseline without pretending it has become structural.

### Reconciliation verification

- Focused geometry, persistence, and customization tests: 114 tests, 0
  failures, 0 errors, 0 skipped
- `./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug`:
  `BUILD SUCCESSFUL`; 192 tests, 0 failures, 0 errors, 0 skipped
- `git diff --check`: clean

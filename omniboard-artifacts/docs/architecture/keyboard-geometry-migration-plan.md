# OmniBoard Keyboard Geometry Migration Plan

Baseline: `yourowndog/keyboard@805d3e58e947215a9eb88ab9ed92b46366c54ef0`

## Strategy

The migration uses a compatibility-first strangler approach:

1. Characterize current behavior and persistence.
2. Introduce explicit semantic data without switching rendering.
3. Introduce the common solver beside legacy geometry.
4. Compare and deliberately approve differences.
5. Switch structural ownership.
6. Migrate profiles, layout packs, and customization.
7. Remove obsolete heuristics and assets only after their consumers disappear.

Every stage ends in a buildable, reviewable repository state. No prompt may combine unrelated cleanup or product redesign.

## Stage map

| Stage | Outcome | Main risk retired |
|---:|---|---|
| 0 | Pinned baseline and characterization harness | Migration without evidence |
| 1 | Normalized semantic rows on all construction paths | Lost identity and invented defaults |
| 2 | Shared solver in shadow/comparison mode | Conflicting frame and row arithmetic |
| 3 | Structural gaps and explicit geometry layers | Positional mutation and bounds divergence |
| 4 | Profile persistence and truthful Text/Coding naming | Global/untruthful product identity |
| 5 | Honest mode semantics and frame policy | Numeric/symbol alpha masquerading |
| 6 | Versioned layout-pack contract and restoration | Pack defaults and silent semantic loss |
| 7 | Responsive structural customization | Visual-only overlap |
| 8 | Real Text profile and profile selection | Text conflated with compact Coding |
| 9 | Legacy removal and device acceptance | Dead fields/assets and residual heuristics |

## Stage 0 — Baseline and contracts

Add tests/diagnostics before changing production geometry:

- enumerate all five constructor sites;
- snapshot normalized row sequences and legacy geometry for key mode/config combinations;
- record persisted preference and subtype upgrade fixtures;
- capture popup and bottom-edge behavior suitable for later device validation;
- file the independent missing-Symbols2 inconsistency if not fixed in this series.

Do not freeze known invalid behavior as a permanent requirement. Mark each assertion as either compatibility or intentional-defect evidence.

Exit condition: repeatable evidence exists for default Coding, compact Coding, extensions, symbols, numeric families, packs, popups, and persistence.

## Stage 1 — Semantic rows

Introduce the normalized row contract and compose roles/provenance at the last point where they are known.

- Normal bundled composition assigns stable IDs and honest roles.
- Layout packs use a compatibility mapper with explicit warnings/errors for ambiguity.
- Loading rows use `PLACEHOLDER`.
- Empty Editing and smartbar keyboards are explicit sentinels.
- Preserve `isAlpha` and `bottomModRowCount` only as deprecated compatibility projections while consumers remain.

Exit condition: every runtime row has explicit identity, or its keyboard is an explicit empty sentinel; production pixels remain intentionally unchanged.

## Stage 2 — Shared solver

Create a pure geometry solver and immutable result model.

- Input: available size, semantic rows/items, profile/mode policy, validated overrides, insets.
- Output: frame/content dimensions, row allocations, key/spacer structural rectangles, declared boundary gaps, data needed for touch/visual derivation.
- Run beside legacy logic first.
- Compare default Coding compatibility and explicitly enumerate expected fixes for short/specialized modes.

Exit condition: frame conservation and non-overlap properties hold across row counts and preference matrices.

## Stage 3 — Structural gaps and bounds

Switch layout to solved structural rectangles.

- Replace `N-2/N-1` gap mutation with semantic boundaries.
- Derive visual and touch bounds explicitly.
- Preserve deliberate bottom-edge hitability with a named edge-touch policy.
- Keep service bottom offset outside row geometry.
- Route popup sizing/anchoring through declared visual/solved inputs.

Exit condition: no post-layout structural gap mutation; visual/touch/structural differences are inspectable and tested.

## Stage 4 — Profiles and naming

Add profile identity and scoped persistence:

- `Text` and `Coding` are canonical user-facing profiles.
- Add global `activeProfileId`.
- Migrate old global geometry/visibility/customization into Coding.
- Seed Text defaults independently.
- Preserve subtype IDs and mappings.
- Keep compact Coding as a Coding state.
- Introduce truthful internal IDs with compatibility aliases/migration where persisted identifiers exist.

Exit condition: upgrading preserves the current Coding experience and subtype selection; profile state no longer depends on generic asset naming.

## Stage 5 — Mode semantics and frame policy

Migrate each specialized mode to explicit row roles and solver policy.

- Numeric/Phone rows become `NUMERIC`.
- Symbols become `SYMBOL`.
- Extensions remain `EXTENSION`.
- Characters/Symbols/Numeric-Advanced stable height is expressed through profile frame policy.
- Review Symbols cache invalidation.
- Resolve the missing Symbols2 default separately or within a tightly scoped commit.

Exit condition: no mode relies on alpha/mod masquerading, row counts, or last-Characters formula borrowing.

## Stage 6 — Layout packs

Version the serialized pack model:

- semantic row IDs and roles;
- spacers and structural units;
- validation of role/item combinations and geometry constraints;
- old-pack compatibility decoder;
- selected pack/profile persistence and startup restoration;
- explicit handling of unknown semantic fields.

Exit condition: old packs decode predictably; new packs round-trip without semantic loss and restore across process restart.

## Stage 7 — Structural customization

Replace global code-keyed structural width/height edits with profile/layout/row/key-instance-aware overrides.

- Structural controls feed the solver and reflow.
- Visual-only padding/insets remain explicitly visual.
- Legacy customization JSON gets a documented compatibility migration.
- Duplicate key codes in different locations can be customized independently.

Exit condition: widening a key cannot overlap a neighbor; controls operate on the declared layer.

## Stage 8 — Text profile

Build the conventional Text layout on the common architecture.

- Do not reuse the dead Coding-oriented QWERTY row.
- Provide settings-based profile selection.
- Keep subtype/language behavior intact.
- Validate switching, restart persistence, symbols, popups, and orientation.
- Leave the permanent on-keyboard gesture for a separate product decision.

Exit condition: Text and Coding are independently selectable, responsive, and customizable without positional special cases.

## Stage 9 — Removal and acceptance

Only after all production consumers use semantic rows and solved geometry:

- remove `bottomModRowCount`;
- remove row-level reliance on `isAlpha` while preserving any genuinely key-level hint concept under a truthful name;
- remove Space-content and last-row inference;
- remove old outer/inner formulas and gap mutation;
- remove or archive the unreachable QWERTY row after fallback tests;
- prune compatibility aliases once migration coverage permits.

Run full automated and physical-device acceptance.

## Required cross-stage test matrix

| Dimension | Required values |
|---|---|
| Profile | Coding, Text |
| Mode | Characters, Symbols, Symbols2, Numeric, Numeric-Advanced, Phone, Phone2 |
| Coding utilities | visible, hidden |
| Extensions | none, number, developer, both |
| Orientation | portrait, landscape |
| Geometry prefs | defaults, representative non-default extremes |
| Layout source | bundled, old pack, new pack, invalid pack |
| Persistence | fresh install, upgrade, process restart |
| Interaction | tap, bottom-edge tap, popup, long press |

## Stop conditions

Pause a stage if:

- current device state cannot be captured before a destructive preference migration;
- a persisted identifier is found that the plan does not preserve;
- shared solver inputs are unavailable at a required lifecycle point;
- a stage changes touch behavior without coordinate/device tests;
- a compatibility projection becomes a new source of truth;
- an implementation agent proposes a new taxonomy or profile model without contradictory evidence.


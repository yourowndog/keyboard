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
| 4.5 | Settings custodial pass — dead surfaces hidden, bones kept | Illegible menus obscuring the migration's own work |
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

## Stage 3 — Structural gaps, bounds, and canonical normalization

Switch layout to solved structural rectangles **and cut the shipped baseline over to canonical
normalized geometry**. This stage no longer preserves the old Coding appearance; see Decision 16.

- Replace `N-2/N-1` gap mutation with semantic boundaries.
- Derive structural, visual, and touch bounds explicitly as named steps.
- Preserve deliberate bottom-edge hitability with a named edge-touch policy.
- Keep service bottom offset outside row geometry.
- Route popup sizing/anchoring through declared visual/solved inputs.
- Retire the `TextKey.compute()` intrinsic width table as a production authority, along with the
  `2.68` / `1.26` / `1.56` specialized magic widths.
- Add flexible item growth to the solver so Space carries a base unit plus growth rather than a
  hard-coded `5.0`.
- Normalize the regions: uniform alpha cells on a shared ten-column unit with centered nine-key rows;
  `Tab 1.5 | , 1.0 | Space 1.0+grow | . 1.0 | Enter 1.5`; nine equal independently filling utility
  cells. All key-level height factors become `1.0`; role height owns the `75%` utility difference.
- Add a narrow "restore per-key defaults" control that clears only `keyboard__key_customizations`.

Exit condition: no post-layout structural gap mutation; one authoritative item-default policy;
visual/touch/structural differences inspectable and tested; every key action, label, popup, and
terminal behavior preserved.

## Stage 4 — Profiles and naming

Add profile identity and scoped persistence:

- `Text` and `Coding` are canonical user-facing profiles.
- Add global `activeProfileId`.
- Seed **both** Coding and Text from canonical normalized geometry. Old tuned global geometry is not
  migrated into Coding as its default (Decision 16 supersedes Decision 8).
- Preserve subtype IDs, mappings, language selection, row/utility visibility, and device-level
  settings such as bottom offset.
- Keep compact Coding as a Coding state.
- Introduce truthful internal IDs with compatibility aliases/migration where persisted identifiers exist.

Exit condition: upgrading preserves subtype selection and device-level settings; Coding geometry is
the normalized baseline plus whatever the user has explicitly customized; profile state no longer
depends on generic asset naming.

## Stage 4.5 — Settings custodial pass

Out of band with the geometry sequence, and deliberately so: the Languages and Layouts menu is
illegible enough that it obscures the work Stages 3 and 4 already landed. This stage buys legibility
and nothing else.

Method is subtraction at the surface only — hide the entry points, leave the plumbing. Nothing here
deletes a type, a repository, or a constructor parameter.

- Remove the layout builder entry points: `LayoutBuilderScreen`, its `HomeScreen` and `DevtoolsScreen`
  navigation, and its four `Routes.kt` declarations. It is the first rudimentary attempt at what
  Stages 6 and 7 now own.
- Leave `LayoutPack`, `LayoutPackRepository`, and `LayoutValidation` in place and wired.
  `KeyboardManager.loadInitialLayout` already returns an empty pack on purpose to bypass the
  repository at startup, and `KeyboardManager.setLayout` has no caller outside the builder screen —
  so the path is inert, not load-bearing. Stage 6 decides its fate.
- Hide the dead Languages and Layouts controls: display language names in system locale, display
  keyboard labels in subtype language, manage installed language packs.
- Make the subtype list readable without entering an add/edit flow: locale plus the layouts actually
  bound. A subtype is an eight-slot `SubtypeLayoutMap` plus composer, currency set, punctuation rule,
  popup mapping, and NLP providers; today the UI presents that as an unexplained dropdown salad.
- No change to subtype semantics, persistence, or `qwerty_wide*` component ids.

Exit condition: the settings tree contains no surface a user can reach that does nothing, and the
active subtypes can be audited without a backup export. No runtime behavior changes.

Deferred out of this stage on purpose:

- **Subtype becomes a type menu, unburied.** Depends on the Stage 6 layout contract and belongs with
  Stage 8's profile selection, since profile and subtype are the two axes a user actually picks.
  Designing that picker before Stage 6 settles means designing it twice.
- **Pruning unused bundled languages.** Wanted, but it is exactly the case where innocuous-looking
  assets turn out to be load-bearing — layout component ids are referenced from the localization
  extension and persisted inside subtypes. Scheduled into Stage 9 behind an explicit reference audit.

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
- Unbury the subtype menu and reframe it as a type menu, carried forward from Stage 4.5. Profile and
  subtype are the two axes a user picks; build that picker once, here, on the settled Stage 6 layout
  contract.
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
- prune compatibility aliases once migration coverage permits;
- prune unused bundled languages and their layout assets, carried forward from Stage 4.5. Gated on an
  explicit reference audit first: layout component ids are referenced from
  `org.florisboard.localization/extension.json` and persisted inside user subtypes, so an asset that
  looks unreferenced can still be named by a saved subtype. Removal is safe only for ids no bundled
  extension and no persisted subtype can name, and the audit output belongs in the results doc.
- retire the layout builder plumbing hidden in Stage 4.5 — `LayoutPack`, `LayoutPackRepository`,
  `LayoutValidation`, and their `FlorisApplication`/`KeyboardManager` wiring — if Stage 6 did not
  adopt it.

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


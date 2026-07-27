# OmniBoard Keyboard Geometry Decisions

Status: Accepted decisions for the migration baseline  
Baseline commit: `805d3e58e947215a9eb88ab9ed92b46366c54ef0`

## Decision 1 — Text and Coding are first-class profiles

**Accepted:** Text and Coding are explicit product experiences with independent defaults and customizable state.

**Rejected:** Treating the current generic QWERTY Wide asset name as the product architecture.

**Reason:** A subtype, asset name, keyboard mode, or layout pack cannot truthfully represent the complete user experience.

## Decision 2 — Row semantics survive composition

**Accepted:** Every normalized row has stable identity, semantic role, provenance, ordered contents, and geometry policy.

**Rejected:** Returning anonymous arrays and reconstructing meaning from position, `isAlpha`, Space detection, or row count.

**Reason:** Current downstream heuristics disagree and already corrupt short and specialized layouts.

## Decision 3 — Primary action and Coding utility are distinct

**Accepted:** The Space/punctuation/action row is `PRIMARY_ACTION`; lower Coding work rows are `CODING_UTILITY`.

**Rejected:** Calling all lower rows `MOD`.

**Reason:** Source provenance and historical FlorisBoard terminology do not describe their product responsibilities.

## Decision 4 — Specialized rows retain honest identities

**Accepted:** Numeric, symbol, and extension rows receive explicit roles and policy.

**Rejected:** Assigning `ALPHA` to receive ordinary height, spacing, or width behavior.

**Reason:** Meaning must not be falsified to obtain geometry.

## Decision 5 — One solver owns structural geometry

**Accepted:** One solved, immutable result governs the frame, rows, keys, structural gaps, and inputs used to derive touch/visual/popup geometry.

**Rejected:** Independent total-height and row-height formulas plus post-layout row mutation.

**Reason:** Multiple authorities cannot guarantee conservation of height or non-overlap.

## Decision 6 — Geometry layers remain separate

**Accepted:** Structural allocation, touch bounds, and visual bounds are explicit layers.

**Rejected:** Treating a visual-bounds resize as structural customization.

**Reason:** Structural edits must reflow neighbors; visual changes need not.

## Decision 7 — Asymmetry uses constrained parameters

**Accepted:** Keys, spacers, margins, row heights, and boundary gaps use validated proportional inputs.

**Rejected:** Free-positioning finished rectangles as the normal editor model.

**Reason:** Constrained parameters remain responsive, inspectable, and overlap-safe.

## Decision 8 — Existing settings migrate to Coding

**Accepted:** Existing geometry, visibility, and customization values seed Coding. Text receives clean defaults. Device/window offsets remain shared.

**Rejected:** Applying old global Coding-tuned values to both profiles.

**Reason:** This preserves the user's current keyboard while allowing Text to begin as a conventional compact layout.

## Decision 9 — Compact Coding remains Coding

**Accepted:** Hiding Coding utilities remains a Coding profile state.

**Rejected:** Reinterpreting `modRowsVisible=false` as Text.

**Reason:** Text is a separate layout experience, not merely Coding with two rows hidden.

## Decision 10 — Stable frame behavior is explicit policy

**Accepted:** A profile may declare a shared frame across Characters, Symbols, and Numeric-Advanced.

**Rejected:** Mode code borrowing the last Characters evaluator as an implicit second authority.

**Reason:** Stable transitions are valid behavior, but they must pass through the common solver.

## Decision 11 — The dead QWERTY row is not Text

**Accepted:** Preserve it only long enough to test fallback/history, then remove or archive it in verified cleanup.

**Rejected:** Promoting it into the Text profile because it already exists.

**Reason:** Its arrows, Tab, slash, and Ctrl content are not the intended conventional Text row, and normal runtime cannot reach it.

## Decision 12 — Profile persistence starts global

**Accepted:** Add a global `activeProfileId`; provide a safe settings selector first. Keep the model extensible for later per-app or per-field selection.

**Deferred:** The permanent on-keyboard switching gesture.

**Reason:** Geometry/profile migration should not be coupled to an unapproved gesture or automatic context policy.

## Decision 13 — Subtypes remain independent

**Accepted:** Preserve language subtype IDs and their eight layout-family component mappings.

**Rejected:** Replacing subtype identity with profile identity.

**Reason:** Profile answers “which experience?” while subtype answers language/layout mapping.

## Decision 14 — All construction paths are explicit

**Accepted:** Normal composition, layout packs, loading placeholder, Editing sentinel, and smartbar sentinel each receive a deliberate semantics contract or documented exemption.

**Rejected:** Updating only the two content-building paths.

**Reason:** Constructor defaults currently invent semantics.

## Decision 15 — Migration is evidence-driven and staged

**Accepted:** Preserve observable behavior intentionally, fix known invalid behavior explicitly, and validate each coherent stage.

**Rejected:** Pixel-freezing every historical bug or performing an all-at-once redesign.

**Reason:** The goal is safe migration to correct invariants, not preservation of accidents.


# OmniBoard Keyboard Geometry Implementation Prompts

These prompts convert the approved architecture and verified repository state into bounded execution stages.

## Authority order

Each implementation session must receive:

1. `docs/architecture/keyboard-row-geometry.md`
2. `docs/architecture/keyboard-geometry-decisions.md`
3. `docs/architecture/keyboard-geometry-current-state.md`
4. `docs/architecture/keyboard-geometry-migration-plan.md`
5. The single stage prompt being executed

If the repository has advanced beyond `805d3e58e947215a9eb88ab9ed92b46366c54ef0`, the agent must reconcile changed symbols and behavior before editing. It must not silently treat old line numbers as current.

## Execution rules

- Run stages in numeric order unless a stage explicitly records that a later dependency is already satisfied.
- One stage may contain several commits, but every commit must be coherent and every stage must end buildable.
- Use one primary implementation agent per stage.
- Review only concrete diffs, tests, invariants, and newly discovered migration hazards.
- Do not reopen approved architecture because another model prefers different names or abstractions.
- Reopen a decision only with contradictory repository evidence, a violated product requirement, or a concrete unhandled migration hazard.
- Device checkpoints are mandatory where touch, popup, frame, or visual behavior changes.

## Stage index

| Prompt | Purpose |
|---|---|
| `00-baseline-contracts.md` | Characterization tests and migration fixtures |
| `01-semantic-rows.md` | Explicit normalized rows on all construction paths |
| `02-shared-solver.md` | Pure common solver in comparison mode |
| `03-structural-gaps-bounds.md` | Switch structural ownership and bounds derivation |
| `04-profiles-persistence-naming.md` | Text/Coding identity and preference migration |
| `05-mode-semantics-frame-policy.md` | Numeric/symbol/extension honesty and stable frame policy |
| `06-layout-pack-schema.md` | Versioned semantic packs and restart restoration |
| `07-responsive-customization.md` | Solver-backed structural editing |
| `08-text-profile.md` | Conventional Text layout and safe selector |
| `09-legacy-removal-acceptance.md` | Remove obsolete authorities and validate on device |

## Required report-back format

Every implementation agent must finish with:

- branch and starting/ending commit;
- commits created;
- files changed;
- behavior intentionally preserved;
- behavior intentionally changed;
- tests/builds run and exact outcomes;
- device validation still required;
- discovered hazards or deviations;
- confirmation that the worktree contains no unrelated changes.


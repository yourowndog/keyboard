# Slate reference delegation backlog

Snapshot: 2026-10-28. This is the dispatch queue. [Detailed cards](task-proposals.md) define scope, acceptance, and non-goals; [programme brief](README.md) controls direction.

## Work completed in this session

| Assignment | Worker | Result |
|---|---|---|
| Visual decomposition | `reference_anatomy` | Original-image study, full element inventory, approximate normalized measurements, uncertainty register |
| Runtime reconciliation | `runtime_reconciliation` | Code and branch audit; reuse/gap map and source references |
| Dependency architecture | `design_task_architecture` | Nineteen bounded implementation/evidence cards and overlap with existing programmes |

These analysis results are complete when committed with this queue. Runtime implementation remains unstarted by this assignment. Reports are review inputs; none substitutes for a known-build device test.

## Queue

| Card | State | Prerequisites / next deliverable |
|---|---|---|
| RC-00 — reference preservation | Complete in this packet | Original JPGs, hashes, anatomy and obstruction notes |
| RC-01 — integration baseline and feature boundary | **Ready; first next assignment** | Accept branch reconciliation; select clean implementation base and reversible selector; record layout/profile boundary |
| RC-01a — missing baseline capability integration | Conditional; blocked on RC-01 | Emit and accept one scoped subcard per missing capability; gate all runtime implementation until required slices are integrated |
| RC-02 — measured composition contract | Draft evidence ready; contract pending RC-01 | Commit pure normalized specification and fixtures derived from anatomy |
| RC-03 — Stage07 structural identity slice | Waiting on RC-01 and accepted RC-01a gate | Reuse or implement instance-aware override schema and migration fixture slice |
| RC-03a — presentation identity contract | Waiting on RC-01/02 and accepted RC-01a gate | Separate keycap/primitive/role identity from Stage06 structural schema; pack `style` stays popup grouping |
| RC-04 — solver row allocations | Waiting on RC-02/03 | Shared-solver template, conservation/non-overlap and size constraints |
| RC-05 — one compound keycap | Waiting on RC-02/03a/04 | One key showing upper/lower surfaces in lab; no new actions |
| RC-06 — interaction/accessibility contract | Waiting on RC-05 | Preserve existing key action and alternate/long-press mapping; verify whole-key pointer ownership |
| RC-07 — special-shape primitives | Waiting on RC-03a/04 | Native D ends, rings, internal curves and segmented strips |
| RC-08 — Shift and Delete | Waiting on RC-06/07 | Reference shapes with existing Shift/caps/Delete behavior |
| RC-09 — bottom region and Enter | Waiting on RC-03a/04/07 | Full semantic Space hit region, decorative instrument, preserved editor action |
| RC-10 — condensed typography | Research ready; metrics depend on RC-02 | License-compatible font/fallback, measured label fixtures |
| RC-11 — command bar | Waiting on RC-03a/07/10 | Existing actions routed into reference composition; candidates/autofill remain available |
| RC-12 — actual popup integration | Waiting on RC-05/06/08/09 | Native transient previews/extended panels adapted to compound surfaces and edges |
| RC-13 — live monochrome integration | Waiting on RC-08/09/10/11/12 | Reversible live path, known-build evidence, retained classic fallback |
| RC-14 — slate/coral/cyan roles | Waiting on RC-13 | Snygg role mapping after structural review, including state contrast |
| RC-15 — fidelity/device acceptance | Waiting on RC-14 | Alignment/shape/type comparisons and behavior/performance evidence |
| RC-16 — secondary variants/names/rollout | Waiting on RC-15 | Same accepted infrastructure; bounded peach/teal treatments; rollout within explicit scope |

## Conservative defaults that allow autonomous progress

1. Upper panels are a presentation of existing secondary hints; they do not get separate tap actions in the first pass. A tap anywhere in a key's owned hit region invokes its existing primary action; long-press alternates retain their existing mapping.
2. The whole central bottom bay retains a meaningful Space target. Its rails/dots are decoration until a separate truthful data binding is implemented. Hidden/obscured details are not invented.
3. Preserve Shift/caps, repeat-delete, Enter/editor actions, terminal controls, suggestion access, voice provider/capture/recovery, and accessibility. If matching the four-band screenshot would remove Coding controls, use an explicit reference layout/profile binding instead of deleting them.
4. Primary design is opaque black between keys. Outer transparency work is not a prerequisite. Ordinary keys are square; special shapes use named native geometry and declared touch envelopes.
5. Work under descriptive LCARS Slate/Peach/Teal names. Exact font identity, original hex colors, indicator semantics and unseen popup shapes remain unknown; use documented, reversible approximations without claiming exact recovery.

## Ready-to-dispatch RC-01 prompt

Work in the OmniBoard repository on a fresh, clean worktree. Read AGENTS.md, this packet, and the code reconciliation. The primary target is the supplied slate/coral/cyan reference. Your bounded assignment is to establish the implementation baseline and feature boundary only.

Compare fetched `origin/keygeo-stage06-verified` (`998e9d9ca`) with `origin/device-validation`, local `device-validation` (`87dbba44c` at this snapshot), `dev`, and the identified candidate worktrees. Establish ancestry and the minimum set of required capabilities: Stage06 layout semantics/restoration; current spatial/autocorrect/harvest behavior; current voice reliability; existing opacity fixes. List dirty/untracked LCARS artifacts without adopting or discarding them. Do not treat source presence as device acceptance.

Choose and justify a clean base and a reversible renderer/layout selection boundary. Record whether the reference belongs to Text, a separate layout binding, or a presentation layer; preserve Coding's terminal utility access. Record concrete missing integration slices as RC-01a numbered subcards, one capability/commit slice each; the runtime queue remains blocked until they are reviewed and accepted, or source evidence establishes that the selected base already contains all required capabilities. Do not perform a sweeping merge or ship runtime changes as part of this card. Deliver a committed baseline decision, comparison commands/results, and one next eligible task. No build factory, device installation, default switch, rewrite of user changes, or paid-worker escalation belongs to this assignment.

## Execution and reporting rules

- Run one dependency-ready implementation card at a time. Independent review and research may run in parallel; serialize edits to shared runtime files.
- Before editing, verify branch, dirty state, ancestry, and the current contract. Snapshot dates and line numbers are not permanent truth.
- Each card returns base/tip commits, changed files, tests/checks, observed result, residual uncertainty, rollback, and next eligible card. Commit only that card's changes.
- Unit checks close pure model/schema claims. Touch, IME mounting, popup placement, window layers and editor behavior require device evidence on a recorded build. Do not mark acceptance from worker prose alone.
- If a task contains both structural rendering and final palette work, split it. If a task alters voice capture while changing decoration, narrow it.
- No worker remains scheduled merely because this Markdown queue exists. Dispatch requires an actual worker run. The queue is durable and restartable; it is not a claim of overnight unattended execution.

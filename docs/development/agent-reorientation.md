# Agent Reorientation

> Status: Canonical operating guide  
> Last verified: 2026-07-11

Use this when entering the repository for the first time or resuming a task
whose conversation predates the repository reorganization.

## Boot sequence

1. Read the root `AGENTS.md` and `docs/README.md`.
2. Verify and refresh indices: run jCodemunch (`resolve_repo` / `uvx jcodemunch-mcp index .`) for code (750 files) and jDocMunch (`jdocmunch-mcp`) for textual docs (995 files).
3. Use **jDocMunch** section retrieval and **jCodemunch** symbol navigation whenever appropriate to preserve context tokens.
4. Read `docs/architecture/system-map.md` for ownership and runtime flow.
5. Read the closest subsystem index before searching code:
   - keyboard construction: `docs/keyboard/README.md`
   - autocorrect and neural scoring: `docs/autocorrect/README.md`
   - themes: `docs/theming/README.md`
   - builds and validation: `docs/development/building.md` and `testing.md`
6. Inspect `git status`, the current branch, and recent commits. Never assume a
   previously dirty worktree is still dirty after another agent has organized
   or committed it.
7. State the task you believe you are resuming, the evidence for that belief,
   what is complete, what remains, and which live-test observations matter.
8. Ask Sam only about ambiguity that cannot be resolved from conversation,
   commits, canonical docs, tests, or current runtime evidence.

## Interrupted-task recovery

Do not reset, revert, cherry-pick, or recreate remembered edits merely because
their old paths or working-tree state changed. First inspect:

```bash
git status --short
git log --oneline --decorate -10
git show --stat <relevant-commit>
git diff <base>..<relevant-commit> -- <subsystem-paths>
```

The cleanup intentionally moved data, tools, research, and documentation. A
missing root file may have a canonical replacement under `docs/`, `data/`,
`tools/`, or `research/`. Use repository search before concluding it was lost.

If live tests were performed under time pressure, write down their results
before asking for repetition. Harvest history may contain subsequent typing,
so correlate by event timestamp, application, event type, and known test window
instead of relying on the last lines of a growing file.

## Handoffs

A useful handoff belongs beside the subsystem it explains and contains:

- objective and user-visible problem;
- branch and relevant commit IDs;
- verified facts versus hypotheses;
- exact live-test window and observations;
- changed files and why;
- tests already run and their outcomes;
- remaining task, safest next action, and explicit do-not-repeat warnings.

Do not create a permanent root session log. Fold durable truth into canonical
documentation and remove temporary recovery notes once the interrupted work is
finished.

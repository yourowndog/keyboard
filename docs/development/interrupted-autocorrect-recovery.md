# Interrupted Autocorrect Session Recovery

> Status: Temporary handoff prompt for the interrupted Fable session  
> Created: 2026-07-11  
> Remove after Fable's final task is reconciled

Paste or point the interrupted agent to the prompt below.

---

You were interrupted near the end of an OmniBoard autocorrect task after a
time-sensitive series of live tests with Sam. While you were unavailable, the
same working directory was comprehensively reorganized and its dirty work was
preserved in commits. Do not reset, revert, or reconstruct files from memory.

Start by reading:

1. `AGENTS.md`
2. `docs/README.md`
3. `docs/development/agent-reorientation.md`
4. `docs/autocorrect/README.md`
5. `docs/autocorrect/live-pipeline.md`
6. `docs/autocorrect/heuristic-scoring.md`
7. `docs/autocorrect/neural-scorer.md`
8. `docs/autocorrect/harvesting.md`

Then inspect these commits:

- `79624f9c` — repository organization, canonical documentation, harvest paths,
  and preservation of the interrupted working tree around it.
- `f8e00b58` — the pre-cleanup autocorrect/punctuation edits that had been left
  uncommitted were isolated and committed without being reverted.

Before changing code, respond with a recovery outline containing:

- the exact task you believe you were finishing;
- what you had already implemented;
- the one final task that remained;
- the deeper race/timing issue you were investigating;
- every immediate live test Sam performed, the order/window in which it was
  performed, and what each result established;
- any result that may now be buried beneath later continuous typing in the
  append-only harvest;
- files and functions involved;
- tests already run and what still requires confirmation.

Reconstruct evidence by timestamps and event types, not by tailing the harvest.
The immutable device-validation snapshot is ignored at
`data/harvest/inbox/20260711-031851/`; canonical harvest remains under
`data/harvest/raw/`. The recent cleanup validated 673 `NEURAL_SHADOW` events and
fixed `HarvestManager.flushSession()` so future neural events retain active
app/field categorization after a session flush.

Known later live-validation facts, which may overlap but do not replace your
earlier tests:

- ordinary autocorrect and phrase prediction worked;
- `742` incorrectly auto-corrected to `PS2` and was reverted;
- Ctrl and Tmux worked but lacked adequate visual state feedback;
- password-field content was excluded while ordinary marker text was captured;
- voice worked; glide and Gemma are intentionally shelved.

Do not assume those later observations answer your earlier timing/race question.
Explain that question explicitly. After the outline, compare your remembered
final task with current code and commits, propose the smallest completion step,
and wait for Sam's confirmation if the intended behavior is still ambiguous.

---

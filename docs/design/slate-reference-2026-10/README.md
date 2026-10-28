# LCARS slate reference programme

> Direction selected by Sam: 2026-10-28. Primary target: slate gray with coral and cyan accents. Peach and teal variants are secondary.
> Status: reference decomposition and implementation planning. No runtime changes, builds, installations, or visual acceptance are claimed by this packet.

## Assignment

Reproduce the supplied keyboard's composition as closely as the evidence supports. This requires layout, native key rendering, special-key shapes, typography, Smartbar composition, and popup integration before the final Snygg theme layer. Preserve OmniBoard's daily-driver behavior, terminal controls, dictation, correction, and recovery mechanisms.

Three workers were assigned during this session: visual anatomy; runtime/code reconciliation; and dependency/task architecture. Their reports are preserved alongside this brief. Worker analysis is evidence for planning, not runtime verification.

## Read in order

1. [Visual anatomy](reference-anatomy.md): element inventory, proportions, observations versus unknowns.
2. [Code reconciliation](runtime-reconciliation.md): source locations, existing capabilities, missing behavior, branch divergence.
3. [Delegation backlog](delegation-backlog.md): ordered tasks, acceptance gates, and execution rules.
4. [Architecture task analysis](task-proposals.md): detailed rationale and overlap with existing programmes.

## Reference evidence

The originals are preserved byte-for-byte. They are screenshots of a collage; surrounding themes, white collage gutters, navigation controls, and the blue “Latest videos” overlay are not keyboard design elements.

| File | Primary use | SHA-256 |
|---|---|---|
| [01-1000115648.jpg](references/01-1000115648.jpg) | Slate lower deck/instrument and overall silhouette; slight right crop | `edfa51db0e0ff87eb4a26ae89eb291180ab8709d173bee61c8ec08f85fb9ac8c` |
| [02-1000115650.jpg](references/02-1000115650.jpg) | Peach variant and shared layout family | `07138ffda7a0660e821a14669624def9c5c913234a97825c04348d34d1b8ca28` |
| [03-1000115652.jpg](references/03-1000115652.jpg) | Slate top bar/rows and width; bottom obscured by overlay | `85b1cc256dd92ca1834c15a0d3d1576f80f776f041f17c53a598d3a51d9bcc05` |

The screenshots do not establish precise original colors, font identity, animation, hit targets, or popup behavior. Approximate measurements must be tagged as estimates, and unreadable labels must stay unresolved rather than being invented.

## Design decisions for the first implementation

- Match the slate reference first. Use peach/teal only to check reusable structure later.
- Use a black, visually opaque rectangular deck with the shown row stagger and grouped special controls. The old asymmetric elbow chassis is not the required topology of this target.
- Treat the visible upper panels as persistent keycap presentation. Initially keep one existing semantic action and hit target per key. Any independent upper-panel action requires a separate explicit interaction contract.
- Audit and adapt existing real long-press previews and popup panels. Their reference appearance is unknown; do not mislabel the persistent upper panels as popup screenshots.
- Reuse the common geometry solver. Do not introduce a second layout engine, screen-coordinate drawing hack, or post-layout visible-bound enlargement to fake reflow.
- Keep decorative scanner lines, dots, and grilles non-interactive unless deliberately assigned behavior. Decorative state must not imply real audio, network, or processing progress.
- Preserve current actions. MENU, EMOJI, LANG, VOICE and special-key lettering are visual targets, not authorization to silently change routing, lose terminal controls, or remove reachable functions.
- Palette names are provisional: **LCARS Slate** (primary), **LCARS Peach**, **LCARS Teal**. Final naming is a later bounded task.

## Relationship to existing plans

KeyGeo 07 owns structural override/reflow semantics; 08 owns a real Text profile; 09 owns legacy removal and device acceptance. This programme consumes those capabilities and must not duplicate their mechanisms. Decide whether the reference is a Text layout, a separate layout binding, or a presentation of an existing profile during the baseline/contract work; do not force Coding's extra terminal rows into the screenshot's four-band arrangement.

The previous chassis branch actually specifies **C0–C17**. The earlier planning summary listed only C0–C5; that was incomplete. Reuse its model/lab, main/aux height separation, surface lifecycle, theme adapters, fallback, and validation lessons. Its elbow topology and elbow/deck join are superseded for this target. New truthful voice metering and processing animation remain optional later work, not prerequisites for a static visual clone.

This packet takes precedence for the new slate visual target. It does not rewrite the whole geometry migration or authorize deleting old experiments. Track accepted runtime behavior in the closest subsystem documentation as implementation lands.

## Completion standard

Completion means both reference fidelity and preserved behavior on a known build. Record crop alignment, width, density, font scale, orientation, layout/profile, theme, and build commit for every comparison. Review silhouette and proportions before palette detail. Record screenshot differences separately from input-behavior results. An isolated renderer screenshot cannot establish that it is mounted in the IME.

Advance one dependency-ready implementation task at a time. Commit a bounded result and its evidence before the next task. Preserve an immediately selectable classic fallback. Parallelize independent analysis/review, not conflicting writes to the same runtime surface.

## Current execution state

The three analysis assignments are complete when their reports and the reviewed backlog are committed. Implementation tasks are prepared for subsequent execution; this packet does not claim unattended workers remain running after the conversation ends. No physical-device modifications were made for this planning assignment.

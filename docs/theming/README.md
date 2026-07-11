# Snygg Theming

> Status: Canonical entry point  
> Last verified: 2026-07-11

Snygg styles the IME's rendered surfaces. It does not define keyboard rows,
dispatch key actions, or replace the geometry calculated by the keyboard
runtime.

Use these sources in order:

1. `lib/snygg/schemas/stylesheet.schema.json` for valid properties and values.
2. `lib/snygg/.../SnyggRule.kt` for selector grammar.
3. `app/.../ime/theme/FlorisImeUi.kt` for element and attribute names queried
   by the current IME.
4. Current packaged stylesheets for working examples.
5. [Hard-won lessons](hard-won-lessons.md) for operational and design knowledge.

Older `SNYGG/`, `SNYGG_REALITY/`, agent-workflow, and theme-skill documents were
evidence to mine, not independent authorities. They disagree on properties,
key codes, selector support, and UI coverage.

Read [engine and selectors](engine-selectors.md) before editing or generating a
stylesheet.

- [Theme authoring](theme-authoring.md)
- [Coverage checklist](coverage-checklist.md)
- [Hard-won lessons](hard-won-lessons.md)

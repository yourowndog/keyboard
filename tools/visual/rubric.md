# Visual critique rubric: LCARS Jefferies

What `critic.sh` judges screenshots against. The design reference is
`docs/theming/reference/design-reference-01.png`, and its measured palette and
geometry are in `docs/theming/reference/README.md`. Sam's approved decisions
are recorded in `docs/theming/lcars-jefferies.md`.

## Must hold

- Every label sits on one line, uncropped, with clear space on both sides. A
  label that wraps, touches its button edge, or is cut off is a failure.
- Labels on the command strip (MENU, EMOJI, LANG, VOICE) share one size, one
  weight, one baseline and one letter-spacing, and read as LCARS caps.
- Text contrast is legible at arm's length: light glyphs on dark buttons, dark
  glyphs on light plates.
- Coral and cyan are accents only, at mode boundaries (delete, voice, language,
  shift). They never fill large areas.
- Black field, small corner radius (about 5px at reference scale), except the
  stadium shift and the one large corner on Enter.
- Shift state is visible on the letter keys themselves, not only in the dial.
- Nothing collides: candidate words, buttons and decorative rules keep gaps.

## Taste

- Calm and engineered, not busy. Decoration supports structure.
- Consistent rhythm: gaps between strip elements match the gaps between keys.
- The command strip and the key field look like one machine.

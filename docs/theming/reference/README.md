# Theme design references

> Status: Reference material
> Last verified: 2026-11-10

Source imagery for in-progress theme work, plus measurements taken from it.
These are references, not descriptions of current OmniBoard behavior.

## design-reference-01.png

Third-party keyboard design supplied by Sam as the visual target for a new
theme. Provenance: supplied in-chat; original designer not recorded. Treat it
as reference only and do not redistribute it as project artwork.

Measurements below were sampled from the image at its native 778x665. They are
recorded here because they are not recoverable once the image is resized or
re-encoded.

### Palette

| Role | Hex | Share of pixels |
|---|---|---|
| Field and gaps | `#000000` | 30% |
| Outer bezel | `#0C1014` | 5% |
| Hint plate (lightest cap) | `#9EA4BA` | 7% |
| Alpha keycap | `#6E748C` | 11% |
| Command keycap | `#51596E` | - |
| Recessed structure (grille, shift housing) | `#303749` | 2% |
| Coral accent | `#FF6752` | 0.4% |
| Cyan accent | `#2A7194` | 0.1% |

Glyphs are near-black on light caps. Coral and cyan together are under 0.5% of
the image and appear only at mode boundaries: delete, voice, language, shift.

### Geometry

Measured by scanning rows of the source image for runs of cap-coloured pixels.

- Column pitch 75px; keycap width 62-63px; horizontal gap 12-13px (~17% of
  pitch).
- Main keycap height 66px. Hint plate height 42px, same width as the cap it
  sits above, separated by ~3px.
- Row-group pitch ~138px, giving ~27px between groups. The 3px intra-pair gap
  against the 27px inter-group gap is what makes each column read as one
  stacked object rather than as separate rows.
- Rows are staggered: the A row is inset half a pitch (37px) from the Q row.
- Cap corner radius is small, roughly 5px. The only shape exceptions are the
  stadium shift control and one oversized corner on Enter.

Normalised to a 1080px-wide portrait board, three row groups plus a bottom row
and smartbar come to roughly 300dp, which is close to the current OmniBoard
default height.

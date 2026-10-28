# Reference anatomy — slate / coral / cyan keyboard

Snapshot: 2026-10-28. Scope: visual evidence from the three supplied screenshots. The primary target is the slate keyboard with coral and cyan accents. Peach and teal are secondary directions. This is a measured reference study, not a claim about the source keyboard's implementation.

## 1. Evidence and confidence

| Reference | Best use | Limits |
|---|---|---|
| `01-1000115648.jpg` | Most complete slate bottom row, instrument/space region, shift, delete, and Enter | Slate keyboard extends slightly beyond the right screenshot edge; topmost blue keyboard is cut off; lower teal panel is obstructed |
| `02-1000115650.jpg` | Entire peach version and common structural grammar | Primary slate design appears only as a narrow neighboring sliver; lower teal version is obstructed |
| `03-1000115652.jpg` | Best complete-width slate top bar and alphabet geometry; complete blue comparator | Blue “Latest videos” overlay hides much of slate bottom-left/center; title and neighboring themes are collage material |

All three files are screenshot views of a larger collage, not clean keyboard exports. White dividers, the THEMES heading, gray back arrow, blue Latest videos header, black overlay, navigation icons, and purple account circle are external UI. They must never become keyboard assets.

The images establish appearance at one apparent state. They do not establish key press state, shift-state changes, long-press timing, popup contents or shape, gesture semantics, sound, animation, a transparent IME window, or device-independent physical size. The reference backdrop appears opaque black; whether that black is a theme fill or compositing result cannot be proved from stills.

## 2. Measurement convention

Use a horizontal key-center pitch **P** as the reference unit. This is the distance between adjacent QWERTY-column centers. Measure geometry before converting to dp; do not force the screenshot's pixel size onto the phone.

Approximate sampling anchors from reference 03: slate Q row spans x=77..755; adjacent columns repeat about every 69 px. Primary slate number panels occupy y≈891..927; letter panels y≈932..991. A-row panels occupy y≈1007..1045 and 1049..1107. Z-row panels occupy y≈1124..1160 and 1165..1222. These are low-resolution visual measurements, typically uncertain by 2–4 px, not final authored coordinates.

| Feature | Approximate normalized measure | Confidence |
|---|---:|---|
| Column pitch | 1 P | High |
| Ordinary face width | 0.84–0.87 P | High |
| Inter-column black gutter | 0.13–0.16 P | High |
| Total ten-column ink width | about 9.84 P | High |
| A-row horizontal offset relative to Q | 0.5 P | High |
| Z-row horizontal offset relative to Q | 1.5 P | High |
| Upper numeric/symbol panel height | 0.52–0.56 P | High |
| Lower alphabet face height | 0.83–0.89 P | High |
| Black slit between upper and lower panels | 0.06–0.08 P | High |
| Combined two-deck ordinary key height | about 1.45 P | High |
| Gap between alphabet row groups | about 0.22–0.25 P | High |
| Row-group top-to-top pitch | about 1.68–1.72 P | High |
| Top bar face height | about 0.85–0.90 P | Medium |
| Gap from top bar to numeric panel deck | about 0.22–0.25 P | Medium |
| Wide side/action face width | about 1.33–1.36 P | High |

The keyboard is not ten uniformly rectangular keys repeated across every row. Its geometry includes explicit half-column staggers, split vertical decks, wider side regions, a bottom center instrument, and independent endcap shapes.

## 3. Overall composition

The primary design is a flat, dense, black field carrying individually placed slate rectangles and a small number of colored instruments. It is not dominated by a large elbow chassis. Black channels organize the rows and separate every key. There is no visible shadow, material elevation, bevel, glow, or gradient on the main slate key faces. Colored outlines and stippled/rule details are deliberate local exceptions.

The hierarchy from top to bottom is: command bar; digit + QWERTY split row; symbol + ASDF split row; symbol + ZXCV split row with full-height Shift and Delete; bottom punctuation/mode row with center instrument/space region and Enter. The center column spaces and stagger are part of the silhouette.

## 4. Command bar inventory

| Element | Direct observation | Implementation implication |
|---|---|---|
| MENU | Filled slate rectangle at left; narrow black uppercase text | Action face and text role distinct from alphabet keys |
| Coral separator | Solid narrow coral vertical rectangle immediately after MENU, roughly 0.33 P wide | A geometric segment, not a text glyph |
| EMOJI | Black interior, thin coral rectangular outline, coral narrow uppercase text | Outlined-action style with stroke kept inside declared visual bounds |
| LANG | Black interior, thin cyan rectangular outline, cyan narrow uppercase text | Same structure, independent accent role |
| Center void | Broad deliberately empty black region | Flexible spacer, not a missing key |
| Right slate endcap | Darker slate filled left semicircle attached to a flat right edge | Directional endcap path; not a fully rounded pill |
| Cyan separator | Thin filled vertical cyan bar between endcap and VOICE | Independent segment geometry |
| VOICE | Filled slate rectangle with pale condensed uppercase text | Separate foreground-color role from dark alphabet text |

Reference 03 provides the best overall widths: MENU/EMOJI/LANG faces are each roughly 1.33 P; top bar occupies the same horizontal extent as the ten-key row. The right cluster occupies roughly the last 3 P. Exact spacer widths should be reconstructed against the cleanest reference crop, not guessed from the partially cropped reference 01.

## 5. Ordinary key decks and labels

Every ordinary visible alphabet key comprises an upper narrow rectangular numeric/symbol panel and a taller lower rectangular alphabet panel, separated by a thin black horizontal slit. Vertical boundaries line up exactly. These panels are present across the static rows in all blue, peach, and slate versions.

**Do not label the upper deck a popup in the data model yet.** Possibilities include a persistent long-press hint region, separately tappable alternate deck, or a single compound face with two labels. The screenshots cannot choose between them. The visual requirement can be implemented independently of interaction semantics until live evidence or Sam's decision resolves it.

| Row | Main alphabet labels | Upper deck labels visible |
|---|---|---|
| First | Q W E R T Y U I O P | 1 2 3 4 5 6 7 8 9 0 |
| Second | A S D F G H J K L | @ # $ & * - + ( ) (asterisk/hyphen glyph form slightly blurred) |
| Third | Z X C V B N M | ! double quote, apostrophe, colon, semicolon, slash, question mark |

Main letter labels are black, narrow, tall, uppercase sans-serif; centered horizontally and approximately centered vertically in the lower face. The visual letter height is around 0.55 P, much taller than the upper symbols. Upper deck glyphs are dark, small, and visibly offset toward the right side in many panels rather than centered like the alphabet. Numerals and symbols appear in the right half with a consistent inset; verify this from a higher-resolution source before final typography acceptance.

The main slate faces are cool gray with a slight blue/lavender cast. Upper panels are lighter gray than the lower faces. The same black gutters, equal face widths, and row staggers repeat through all three alphabet rows. Do not use one global all-key corner radius: ordinary keys are visibly square.

## 6. Shift control

The primary slate Shift area occupies the left of the Z-row, spanning the combined upper/lower key height. Its right edge is straight, matching the vertical boundary just before Z. Its left edge is a large semicircle, making a left-rounded D shape. It is darker slate than alphabet faces.

A coral filled disk sits centered in that shape, surrounded by a thin coral circular ring and a small dark gap. Reference 01 shows one obvious coral ring; compression may produce a second faint edge. Do not invent additional concentric-ring artwork. The disk diameter is roughly 0.50–0.55 P; ring outer diameter roughly 0.70–0.80 P. The shape's total width is about 1.33 P and height about 1.45 P.

No SHIFT text or arrow is visible in the slate version. Peach/blue versions instead have text SHIFT in a warm-filled left-rounded endcap. That is a variant-specific presentation, not a requirement to place text on the primary circular instrument.

No still proves whether the coral disk represents inactive shift, active shift, caps lock, recording, or a decorative indicator. Preserve Shift action semantics unless the product contract explicitly changes them.

## 7. Delete control

The slate Delete region sits to the right of M and spans the two-deck row height. It is a wide rectangular composition with: a thin coral top rule; black separation; a shallow slate band containing small dark stipple/perforation-like marks; another black slit; and a taller lower slate face bearing DELETE near the bottom/right.

The perforations appear as multiple tiny dark dots or dashes in rows. Exact count is not reliably recoverable at this resolution and reference 01 crops the right edge. Treat the motif as a parameterized strip whose final count is a review item. Do not duplicate it into an arbitrary emoji or fake text label.

This control is visually asymmetric with Shift. A normal rounded rectangle containing a backspace icon will not reproduce the reference.

## 8. Bottom row

| Region | Observation |
|---|---|
| Left mode block | Wide 123 lower panel with a blank lighter upper deck; square edges |
| Comma block | Ordinary-width comma lower panel and blank lighter upper deck |
| Center instrument / space region | Large black region with thin gray rails, stepped slate pieces, cyan indicator dots, and two vertical slate bars toward its right |
| Period block | Ordinary-width period lower panel and blank lighter upper deck |
| Enter block | Wide slate composition, ENTER small near upper right, with a lower curved/internal elbow line and rounded outer lower-right corner |

The bottom row follows the same overall row-group height as alphabet rows. Mode block and Enter are about 1.33 P wide; comma and period about 0.85 P wide. The center instrument fills the remaining width (around 4.8–5 P between comma and period, depending on whether adjacent rails are included).

Reference 01 is the best instrument evidence. Visible artwork includes a thin gray horizontal top rail around the center region; small short line details near its upper right; a long stepped slate bar in the middle with a downward/right bend; a dark inset slit in that bar; a lower slate bar with an angled step upward near its left-middle; approximately eight cyan/blue circular dots running horizontally, with brightness fading toward the right; and two adjacent vertical slate bars at the far right. Some central details are near-black and compressed. A faithful first pass should preserve their large-scale placement before reproducing tiny marks.

No SPACE label is visible in the primary slate instrument, while peach and blue clearly label their center control SPACE. Calling this the space region is a cross-variant interpretation, not proof of its hit regions. The implementation needs an explicit decision about which area commits a space, whether any of the decorative-looking controls are interactive, and how accessibility announces them.

Enter is not a stock key face. Reference 03 reveals a dark internal line descending from a rounded upper-left corner of its lower region; reference 01 shows the broad Enter face but clips the right edge. A vector reconstruction should make this line separate from the actual outer hit bounds. Do not reduce the tappable area to the narrow visible elbow.

## 9. Color and typography roles

Use sampled colors as provisional evidence only: JPEG compression, screenshot scaling, and display capture prevent trustworthy original token recovery. Extract representative interior samples from original images in the integration work; do not claim the source hex values are known.

Required semantic roles:
- Black field / gutters / outlined-control interior.
- Main slate alphabet fill.
- Lighter alternate deck fill.
- Dark slate endcaps/instrument fill.
- Dark key label foreground.
- Pale VOICE foreground.
- Coral accent fill, ring, outline, and top rule.
- Cyan accent outline/bar and indicator-light progression.
- Quiet gray instrument rails.

Typography must be evaluated independently of color. Most labels use an extremely condensed tall face; a normal Android sans-serif in matching color will still look wrong. No exact font identity is established from these images. The goal is measured glyph width, cap height, stroke weight, baseline, and alignment; use a license-compatible available typeface or authored asset only after comparison. The THEMES collage title uses a different broad sci-fi face and is irrelevant to keyboard label typography.

## 10. Secondary variant grammar

Peach reference 02 preserves the ten/nine/seven row pattern, split decks, black gutters, half-pitch and one-and-a-half-pitch staggers, wide side controls, and divided center space composition. Its main keys are pink/peach, alternate decks tan, command faces lavender/rose, and Shift/VOICE warm yellow-orange. Its Shift and Delete are horizontal text-bearing endcaps; its space region is a simple composite left endcap + narrow orange divider + lavender right-rounded SPACE face. It is structurally related but does not prove the slate instrument is purely cosmetic.

The teal panels lower in all images use very different exposed-grid/background imagery, open key labels, broader glyphs, icon toolbar, and large icon side controls. They are secondary inspiration and should not be allowed to pull the primary slate implementation toward a mixed style. Their obscured regions cannot support a complete clone specification.

## 11. Observable acceptance criteria

1. Compare a native rendered screenshot at the same aspect ratio and crop as reference 03, with external screenshot UI removed from comparison masks. Use reference 01 only for the central bottom artwork and missing edge detail.
2. Normalize by measured pitch P. Key-center positions should be within 0.03 P in the initial geometry acceptance; row and deck heights and gap widths within 0.04 P. These are proposed engineering tolerances, not facts about the source.
3. Show exactly ten/nine/seven alphabet columns, with A offset 0.5 P and Z offset 1.5 P; verify no positional inference from alphabet key values is required by the renderer.
4. Ensure upper/lower panels share x extents and are separated by a visible black slit; ordinary keys remain square at pixel scale.
5. Verify Shift D shape, coral disk/ring, Delete top rule/stipple band, compound Enter, three-part voice cluster, and outlined EMOJI/LANG individually in enlarged crops.
6. Confirm the bottom instrument stays within its declared visual bounds and does not reduce the semantic Space hit region unintentionally. Decorative lights must not become phantom key actions.
7. Evaluate typography and palette only after geometry is stable. Compare filled interiors and glyph crops, not whole-image averages dominated by black.
8. Repeat portrait/landscape and at least three configured heights; preserve ratios without shrinking touch targets below the agreed interaction minimum. When ratios conflict with usability, document the deliberate adaptation rather than silently calling it a clone.
9. Device-test taps, long presses, popups, Shift/caps behavior, Backspace repeat, Enter/editor action, alternate symbols, voice selection, and accessibility separately. Still-image fidelity cannot prove these behaviors.
10. Label every screenshot with commit, build, profile, layout, dimensions, scale/density, and preferences so approval belongs to a reproducible build.

## 12. Questions that should remain explicit in the implementation packet

- Are upper symbol decks visual hints or separate hit regions? What action/timing should expose popups?
- What do actual pressed, held, caps-lock, disabled, and recording states look like? No source state sheet is supplied.
- Is the central slate instrument entirely the Space key or a multi-control area?
- Are fading dots static decoration or a live indicator? If live, what truthful input drives them?
- What exact font and source palette should be licensed or approximated?
- Which reference aspect ratio and phone orientation is the canonical fidelity target?

These uncertainties should not block building a faithful static visual model, but they must block invented interaction or fake telemetry claims.

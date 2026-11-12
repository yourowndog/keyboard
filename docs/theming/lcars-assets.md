# LCARS assets: plan and rules

> Status: Roadmap with live stubs (backdrops work; sounds, skins and fonts are entry points only)
> Last verified: 2026-11-12

Sam owns licences for a set of LCARS and Star Trek material: UI sounds, fonts,
PNG interface pieces, animated display loops and computer-voice lines. This page
is how that material reaches OmniBoard without ever entering this repository.

## The rule: personal builds, gitignored input

The licences cover Sam's personal use. They almost certainly do not allow
redistribution, and this repository is on GitHub. So:

- **Never commit a licensed file.** Not in `app/src/main/assets`, not in
  `docs/`, not in a test fixture, not base64-inlined.
- Licensed files enter a build through **`private-assets/`** at the repository
  root. It is gitignored, and `app/build.gradle.kts` adds it as an extra assets
  source directory, so whatever is staged there lands under the APK's `assets/`
  in that one local build. A checkout without it builds and runs normally.
- Which files to stage is committed as *names only*, in
  `tools/assets/private-picks.txt`. `tools/assets/stage-private-assets.sh`
  copies them from the pack (on sleeper by default, over rsync) into
  `private-assets/`. It refuses to run if the folder is not ignored.
- Imports made on the phone (backdrop images, later sound packs and skins) live
  in the app's private storage and never touch the repository either.

The pack itself is on sleeper at `~/documents/lcars-trek-assets/`, catalogued in
its `CATALOGUE.md`. The study of the Sci-Fi Keyboard app is at
`~/omniboard-study/scifi-keyboard/` on sleeper, including the three
screenshots this plan was drawn from (`screens-2026-11-11/`).

## What the Sci-Fi Keyboard does

Pulled from icarion on 2026-11-12 (screenshots of 2026-11-11 20:55):

- Four galleries as tabs: **Themes, Background, Animation, Keys**, plus a DIY
  builder. One tap on a thumbnail applies it; there is no apply button.
- **Background** is a two-column grid of static 16:9 line-art scenes (wireframe
  rooms, quadrant maps, deck schematics, a wormhole). A floating button picks a
  custom photo from the gallery.
- **Animation** is the same grid of looping HUD scenes (sensor sweeps, radar,
  targeting tunnels, scanline glitch).
- On the keyboard the backdrop runs full-bleed behind the whole key area and
  shows only through the gutters. Keys are opaque and flat, and each letter key
  is a two-part stack (symbol tab over letter block), with LCARS MENU / EMOJI /
  LANG / VOICE strip, a DELETE grille and an L-shaped ENTER: the same anatomy as
  our LCARS Jefferies reference.
- No opacity, blur or crop controls are visible in those screens.

OmniBoard copies the *structure* (separate galleries, one-tap apply, backdrop
behind opaque keys, custom import) and none of the art.

## Roadmap, by asset kind

Each line says what exists now and the next step. Order is roughly the order to
do them in.

### 1. Backdrops (working first pass)

Now: [backdrops](backdrops.md) are selectable with built-in code-drawn scenes,
imports, private-asset files, a strength slider and animated GIF playback.
Six wide LCARS loops are listed in `private-picks.txt` as starters.

Next:
- Device validation on icarion: frame cost of an animated loop under fast
  typing, battery over a day, and whether 60% strength reads well in the
  gutters of the Text and Code profiles.
- A thumbnail gallery like the reference (static and animated tabs) replacing
  the list picker; long-press an import to delete it.
- Crop control per backdrop (anchor top, centre, bottom), since the wide loops
  are about 2.7:1 and the key field is closer to 1.6:1.
- Per-profile backdrop (Text calm, Code busier), and a theme-declared default.
- Pause or freeze an animated backdrop while keys are being pressed rapidly, if
  validation shows frame drops.
- Original backdrops: more code-drawn scenes in the LCARS idiom so a public
  build has good options with no licensed files.

### 2. Sounds (entry points from PR #74)

Now: `SoundEvent`, `SoundEngine` and a `SoundPackEngine` stub; see
[sound](../architecture/sound.md). Candidate picks per event are measured but
not yet heard, in `soundpack-candidates/` on sleeper.

Next: SoundPool playback; then a pack source that can read from
`assets/sounds/<pack>/` as well as an on-device folder, so a staged pack ships
inside a personal build exactly like a backdrop (add the pack to
`private-picks.txt` under `sounds/lcars-1/`). Clips need the 70-200 ms lead
silence trimmed and loudness normalised first, as in the pack's `audition/`.

### 3. Key skins (entry points from PR #74)

Now: `KeyRole` selectors and `background-image` on key, cap and plate; see
[key skins](key-skins.md). A skin is a theme extension imported on the device.

Next: Snygg needs a nine-patch or slice property before wide keys can share a
texture. The 385 PNG UI pieces in the trek app are the likely source for
elbows, pills and end caps; they would ship inside a private skin extension,
not the APK.

### 4. Fonts

Now: Chakra Petch and Oxanium (OFL) ship and are approved.

`lcars2.ttf` (LINO) could be staged as a private font for a personal-build
theme variant. The Tungsten faces are H&FJ commercial fonts embedded under the
trek app vendor's licence; do not use them in OmniBoard at all.

### 5. Computer-voice lines

The 138 voice lines ("input received", "access denied") are candidates for
feature `SoundEvent`s such as a refused action or a finished voice dictation.
They are long and loud, so they belong behind their own switch, off by default.

### 6. Not for the keyboard

The TREK Music set and the large (10-26 MB) GIF loops. The latter are too heavy
for an always-on IME surface unless re-encoded smaller.

## Adding an asset

1. Pick it from `CATALOGUE.md` (or the pack's `index.html` browse page).
2. Add one line to `tools/assets/private-picks.txt`: source path, then the
   destination under `private-assets/` (`backdrops/`, later `sounds/`, `fonts/`).
3. On sisyphus, run `tools/assets/stage-private-assets.sh`, then build as usual.
4. Never `git add -f` anything under `private-assets/`.

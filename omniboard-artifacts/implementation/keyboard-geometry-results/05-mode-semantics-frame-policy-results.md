# Stage 05 Results — Mode Semantics and Frame Policy

Branch `keygeo-stage05`, five commits ahead of `dev` at `2ac84759`:

| Commit | Subject |
| --- | --- |
| `992323a2` | Pin per-mode geometry before changing it |
| `96a981be` | Stop the alpha width control resizing keyboards with no letters |
| `489b15fc` | Declare which rows decide the frame, instead of remembering |
| `ffcb1f3f` | Pair hints by role, and invalidate every affected mode |
| `96bc27bf` | Point the Symbols2 default at a component that exists |

Nothing is pushed.

## Outcome

Every mode now says what its rows *are* and what decides the height of the window they are drawn
into, as two declarations that a reader can find. The alpha width control no longer reaches
keyboards that contain no letters. The frame for a layer switch is derived from the Characters
keyboard the current subtype and profile would produce right now, rather than from whichever
Characters keyboard happened to have been computed most recently. Symbol hints pair by row role
instead of by row index. Toggling a row's visibility invalidates every mode that row is merged
into, not only Characters. And the Symbols2 default names a layout component that exists.

Two of these repairs are correct but currently inert on Sam's install. That is recorded in
[Device validation](#device-validation) and again in [Follow-ups](#follow-ups); it is not a defect
in the change, but it does decide what the stage is worth in practice.

## Decisions taken

**The frame reference is recomputed, not remembered.** `KeyboardManager.frameReferenceKeyboard`
replaces `lastCharactersEvaluator`. The old flow was wrong in three reachable ways, all of which
the KDoc now states at the field: it held a four-row placeholder until the user had visited
Characters at least once, it kept the previous language's row count after a subtype switch made
from the Symbols layer, and it kept the previous row count after the number, developer or utility
rows were toggled from anywhere but Characters. `computeCharactersKeyboard` was extracted from
`updateActiveEvaluators` so the reference and the drawn surface come from one code path.

**`KeyboardFrameGroup` is exhaustive with no `else`.** `TEXT_ENTRY` is Characters, Symbols,
Symbols2 and Numeric-Advanced — the four surfaces reachable from one another by a single key press.
`OWN_ROWS` is everything else, including Numeric, Phone and Phone2, which are opened because the
editor asked for digits rather than because the user pressed a key on a keyboard they were already
looking at. A new mode has to be classified rather than falling into a group by omission.

**Both scale tables in `KeyboardGeometryPolicy` became exhaustive `when`s.** This is the actual fix
behind `96a981be`. The alpha width control reached numeric and symbol rows precisely because an
`else` branch swept up every role nobody had thought about. `NUMERIC` and `SYMBOL` now take `1.0`
for width — they are not reachable at the same time as the alpha block, so there is nothing for
them to align with — while still answering the row-*height* control, because that is the only
height authority those surfaces have and freezing them would leave them alone in not responding.

**`EXTENSION` tracks the alpha width scale.** The number and developer rows are inserted *into*
Characters and render directly above the letters, so narrowing the letters must not leave a
full-width number row sitting on top of an inset QWERTY block. They are still not on the alpha
*grid*: a nine-key developer row spreads across the full content width while the nine-key alpha row
below it is inset. That mismatch predates this stage and stays pinned as a known defect rather than
being quietly fixed here.

**Hints pair by role, and the pairing is its own testable object.** `HintRowPairing` takes the two
role lists and returns which symbol row feeds which letter row. It is a pure function with no
Android dependency, so the fixtures in `HintRowPairingTest` are derived from the shipped layout
assets on disk rather than hand-written — the test reads `characters/qwerty_wide.json` and friends
and fails if their shapes change underneath it.

**Cache invalidation is a full clear, not a declared list of modes.** `keyboardCache.clear()`
replaces `keyboardCache.clear(KeyboardMode.CHARACTERS)` in both row-visibility paths.
`modRowsVisible` is read in `mergeLayouts` and applies to whatever mode is being merged, `numberRow`
is read by the SYMBOLS branch as well as the CHARACTERS one, and SYMBOLS2 carries a modifier layout
of its own — so clearing only Characters left `?123` showing a keyboard with the row the user had
just toggled away. A declared list of affected modes would drift back into that bug the next time
someone reads a preference from a new mode branch; a full clear cannot. The cost is one lazy
recompute of whichever mode is asked for next, and `updateActiveEvaluators` only recomputes the
active one.

**The Symbols2 default was repaired in its own commit, as the stage required.**
`Subtype.SYMBOLS2_DEFAULT` pointed at `western_wide`, which has never existed. The Symbols and
Characters defaults were widened together in `27323ebe` and this one was widened with them, but
`symbols2` declares only cjk, eastern, ipa, persian, western and western_samsung. An unresolvable
main layout does not fail loudly: `mergeLayouts` falls through to its modifier-only branch, so `=\<`
rendered `symbols2Mod/default.json` alone — two rows of navigation keys and a blank placeholder,
with no symbols on it at all. The value is now `western`, which is what it held from 2021 until
that commit.

## Semantic row roles as assigned

| Surface | Rows | Role |
| --- | --- | --- |
| Numeric, Numeric-Advanced, Phone, Phone2 | all | `NUMERIC` |
| Symbols, Symbols2 | main arrangement | `SYMBOL` |
| Characters | letter rows | `ALPHA` |
| Characters, Symbols | inserted number / developer rows | `EXTENSION` |
| all merged surfaces | the space/enter row | `PRIMARY_ACTION` |
| Characters (Coding) | modifier rows below the letters | `CODING_UTILITY` |
| sentinels | — | `PLACEHOLDER` |

`NormalizedRowsBuilder.roles` was added so a caller mid-merge can ask what a row *is* instead of
counting how many rows precede it. That accessor is what makes the hint pairing possible.

## Correction carried in from Stage 03

Stage 03's results doc lists `ime/keyboard/KeyboardGeometryArithmetic.kt` under **Files removed**
with the justification *"The second height authority. The frame now comes from the same solve as
the keys."* The first half of that was true; the second half was not, and Stage 05 is where it
becomes true.

After Stage 03 the frame and the rows came from the same *solver*, but not from the same *solve*,
and for the text-entry modes not even from the same *keyboard*. `FlorisImeSizing.keyboardUiHeight()`
measured `lastCharactersEvaluator.keyboard` while `TextKeyboardLayout` laid out
`activeEvaluator.keyboard`. On the Symbols layer those were two different arrangements with
different row counts, and the "same solve" claim silently described a frame computed from a
keyboard the user was not looking at.

What is true now, and is stated at both sites:

- For an `OWN_ROWS` mode the frame is measured from the very keyboard that is then laid out, so the
  layout solve reproduces the frame solve exactly. `KeyboardFrameGroupTest` asserts that as a
  property rather than for a fixed set of shapes.
- For the `TEXT_ENTRY` group the frame is measured from a *declared* reference surface — what
  Characters would be right now for this subtype and profile — and the member being drawn fits
  inside it. The height is an input to the layout solve and is never re-derived there.

The Stage 03 claim is corrected in place in this document rather than in `03-*-results.md`, matching
how Stage 04 carried its own Stage 03 correction. No third height authority was reintroduced.

## Tests

`./gradlew --no-daemon :app:testDebugUnitTest` — **272 tests, 0 failures, 0 errors, 30 classes.**
`./gradlew --no-daemon :app:assembleDebug` — BUILD SUCCESSFUL. Warnings in `BackupScreen.kt`,
`RestoreScreen.kt` and `CrashUtility.kt` are unchanged and predate this stage.

Stage 05's own classes:

| Class | Tests | Covers |
| --- | --- | --- |
| `ModeGeometryCharacterizationTest` | 15 | per-mode semantic rows, the width/height/gap matrices at default and non-default preferences, extension combinations |
| `KeyboardFrameGroupTest` | 7 | the group declaration, stable-frame transitions, independent frames for Numeric/Phone |
| `HintRowPairingTest` | 9 | symbol hint pairing and alignment, against fixtures read from the shipped assets |
| `LayoutAssetDiagnosticTest` | 9 | Symbols2 default and fallback, plus the asset defects this stage did not fix |
| `PersistenceMigrationFixtureTest` | 9 | the persisted-subtype behaviour the Symbols2 repair does and does not reach |

Coverage against the stage's eight required tests:

| Required | Test |
| --- | --- |
| Per-mode semantic row assertions | `ModeGeometryCharacterizationTest` — every mode's role sequence is pinned |
| Default and non-default height/spacing/gap matrices | `alpha key width leaves numeric and symbol rows alone`, `numeric and symbol rows take the full row height`, `the utility height control moves only utility rows`, `no numeric or symbol surface receives coding boundary gaps`, plus the two over-100% fallback cases. Horizontal key spacing rides along as a `GeometryPreferences` parameter rather than getting a case of its own; spacing-vs-structure is Stage 03's `NormalizedGeometryTest`. |
| Characters/Symbols/Numeric-Advanced stable-frame transitions | `symbols fills the frame that coding characters establishes`, `numeric advanced fits the text entry frame`, `toggling an extension row moves the whole group's frame` |
| Numeric/Phone independent frame behavior | `numeric and phone size their own frame`, `an own rows surface reproduces its own frame` |
| Extension combinations | `a short extension row does not align with the alpha rows below it`, and `toggling an extension row moves the whole group's frame`, both on the `codingWithNumberExtension` fixture |
| Symbol hint pairing/alignment | all nine of `HintRowPairingTest` |
| Runtime utility-visibility changes without restart | **not unit-tested** — see [Not done](#not-done) |
| Symbols2 default/fallback behavior | `@EXPECTED_FIX every subtype layout default resolves to a component that exists`, `@EXPECTED_FIX subtype symbols2 default names a component that exists`, `@KNOWN_DEFECT symbols2 still has no wide component` |

Both `@EXPECTED_FIX` Symbols2 tests were confirmed to fail against the pre-fix value before the fix
landed, so they are regression tests rather than restatements.

Contract markers used in this stage: `@COMPATIBILITY` for behaviour deliberately preserved,
`@EXPECTED_FIX` for behaviour this stage changed on purpose, `@KNOWN_DEFECT` for defects pinned but
not fixed, `@MIGRATION_FIXTURE` for the persisted-state case below.

### Defects pinned, not fixed

- `@KNOWN_DEFECT symbols2 still has no wide component` — the Coding profile has no wide Symbols2
  arrangement to point at. Authoring one is layout-asset work, which this stage's non-goals forbid.
- `@KNOWN_DEFECT the wide symbol layer leaves its digit row untyped` — `symbols/western_wide.json`
  row 0 is `1234567890` with no `"type": "numeric"`, so those keys default to `CHARACTER`.
  `hintedNumberRowEnabled` has therefore never drawn anything for the Coding profile; the digits
  arrive through the symbol hint channel instead. The reference `numericRow/western_arabic.json`
  does type its keys, which is what gives the defect a contrast.
- `@MIGRATION_FIXTURE a persisted subtype keeps the symbols2 component it was saved with` —
  `SubtypeJsonConfig` sets `encodeDefaults = true`, so every already-persisted subtype has the
  broken value written out explicitly and keeps it. Correcting the default repairs new subtypes
  only.

## Device validation

Debug APK `0.5.0-debug+96bc27bf` built and installed on SM_S938U over the active IME
(`dev.patrickgold.florisboard.debug`). HoneyBoard, Gboard and pckeyboard were confirmed present as
fallbacks first. The IME remained default after install and the keyboard rendered correctly.

**Passed:**

- **Frame stability across a layer switch.** IME `touchableRegion=SkRegion((0,1852,1440,3120))` —
  1268px — byte-identical across CHARACTERS → SYMBOLS → CHARACTERS. This is the behaviour the frame
  group exists to protect, measured rather than inferred.
- **Merged Coding shape matches the unit fixtures exactly.** Number row, `qwertyuiop`, `asdfghjkl`,
  `⇧zxcvbnm⌫`, `⇥ , space . ↵`, `⎋ ~ : « ^ » - ' Σ`, `MOD ⚛ @ ‹ ˅ › / 📋 CTRL`. This also confirms
  live that `characters/qwerty_wide.json` row 3 is dead in the merge — the pinned `@KNOWN_DEFECT`
  from Stage 05.1 is real on the device, not only in the fixtures.
- **Symbols layer renders its four rows plus modifier row** — number row, `1234567890`,
  `!@#$%^&*()`, `\|_=[]{};:`, `CTRL ↰ < > « ^ »`, `ABC ▣ ⧉ 📋 " - _ ‹ ˅ ›`. Note the digits appear
  twice when the number row is on, which is the untyped-digit-row defect above showing on screen.

**Findings that change what this stage is worth in practice:**

1. **Hints do not render at all on Sam's install.** `keyboard__key_hints_visible` defaults to
   `false` (`AppPrefs.kt:653-656`) and is absent from his jetpref store, so the master gate at
   `TextKey.kt:221` is off. His `hinted_number_row_enabled=true` and `hinted_symbols_enabled=true`
   are downstream of it and cannot override it. The hint-pairing repair in `ffcb1f3f` is correct and
   unit-proven, and currently draws nothing on his device.
2. **Symbols2 is unreachable from the Coding profile.** `symbolsMod/western_wide_mod.json` carries
   no `-203` (VIEW_SYMBOLS2) key, and no `-205` (VIEW_NUMERIC_ADVANCED) either. `-203` appears only
   in `symbolsMod/{armenian,cjk,default,neo2}.json`. So `96bc27bf` repairs a layer Sam cannot
   currently navigate to.
3. **The corrected default does not repair his install.** All three of his persisted subtypes name
   `"symbols2":"org.florisboard.layouts:western_wide"` explicitly, for the `encodeDefaults` reason
   above. His active subtype (id `1785945150036`) also uses `characters: qwerty_wide_full`.

**Not performed:**

- **Landscape orientation.** Not tested.
- **Non-default height and gap preferences.** Not tested.

Both were stopped rather than deferred by choice. Sam was actively using the phone during the
session — his Telegram thread appeared in two screenshots and taps intended for the keyboard were
landing in his live chat compose field. Continuing would have meant typing into someone's real
conversation. The stage's device checkpoint asks for *all reachable transitions in portrait and
landscape with representative non-default geometry settings*; portrait at default geometry is what
was actually covered, and the rest is outstanding.

## Not done

- **Runtime utility-visibility change without restart is not unit-tested.** The change is a
  `keyboardCache.clear()` inside `updateActiveEvaluators`, which needs a live `KeyboardManager` with
  a real preference store and coroutine scope. Asserting it would mean an instrumented test, which
  this stage did not add. The reasoning is documented at the call site; the observable behaviour is
  in the device script that was cut short.
- **No wide Symbols2 arrangement was authored.** Out of scope by the stage's non-goals ("no broad
  symbol asset cleanup").
- **No migration for persisted subtypes carrying `western_wide` for symbols2.** Subtype migration
  belongs with the layout-pack schema work, not here.
- **The untyped digit row in `symbols/western_wide.json` was not typed.** Same non-goal; and doing
  it would change what the number-row hint draws, which is a visible behaviour change that wants its
  own commit and its own device check.

## Follow-ups

- **Stage 06** — layout-pack schema. The Symbols2 asset gap and the untyped digit row are both asset
  problems that the schema work is the right place to resolve.
- **Stage 07** — per-key customization. Unchanged dependency.
- **Decisions for Sam, not for a stage:**
  - Whether to turn on `keyboard__key_hints_visible`. Nothing in this stage's hint work is visible
    until he does.
  - Whether `symbolsMod/western_wide_mod.json` should carry a `view_symbols2` key. Right now the
    Coding profile has no route to Symbols2 or Numeric-Advanced at all.
  - Whether his three persisted subtypes should be rewritten to drop the stale `western_wide`
    symbols2 value, or left to a Stage 06 migration.
- **Outstanding device checks:** landscape, and non-default height/gap preferences. Worth running on
  a phone he is not holding.

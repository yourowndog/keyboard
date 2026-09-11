# Harvest Schema v4 — Specification

**Status:** Draft for review. No code written against this yet.
**Supersedes:** the v3 event stream (`"v":3`), which continues to be readable.
**Written:** 2026-09-11

---

## 0. What this document is

A complete specification for what the keyboard records while you type, why each
field exists, and what it is used for downstream. It is written to be read and
argued with *before* implementation, not after.

The short version: v3 logs **outcomes**. v4 logs **journeys**. The difference is
that an outcome tells you what happened, and a journey tells you what you meant.

---

## 1. Why v3 must be replaced

These are verified findings against the 63-day corpus
(`data/harvest/inbox/20260911-011828/usage_harvest.jsonl`, 635,404 events) and
against `HarvestManager.kt` at current HEAD. None of this is inference.

### 1.1 The struggle-logging API was written and never connected

`HarvestManager.kt` already defines these functions:

| function | line | call sites outside HarvestManager |
|---|---:|---:|
| `logPicked` | 233 | **0** |
| `logIntent` | 248 | **0** |
| `logNoSuggestion` | 257 | **0** |
| `logMultiAttempt` | 281 | **0** |
| `logSuggestionsIgnored` | 295 | **0** |
| `logBackspaceStorm` | 349 | **0** |
| `startAttemptTracking` | 358 | **0** |
| `addAttempt` | 367 | **0** |
| `finalizeAttemptSequence` | ~377 | **0** |

Nine functions. Zero callers. The entire struggle-capture surface exists as
dead code. It was specified, implemented at the API layer, and never wired into
the IME. That is why the corpus contains no `USER_PICKED`, `MULTI_ATTEMPT`,
`BACKSPACE_STORM`, `NO_SUGGESTION`, or `IGNORED_SUGGESTIONS` events — not
because the events are rare, but because nothing emits them.

Only three harvest entry points are actually live: `addToSession`,
`logAccepted`, `logRejected`/`logManualCorrection`/`logInsisted`, plus
`logSuggestionsShown` and `logNeuralShadow` from `NlpManager.kt`.

**v4 requirement:** the spec is not complete until every field has a named
emission site in a named file. §12 provides that table. A v4 implementation
that adds fields without adding call sites reproduces this exact failure.

### 1.2 There are zero touch coordinates in the corpus

`trace` is present on 6,640 of 271,787 alphabetic commits (2.4%), and its
content is a copy of the typed string:

```
AUTO_APPLIED   'whag'  -> trace: "whag"
WORD_COMMITTED 'What'  -> trace: "whag"
AUTO_APPLIED   'wifes' -> trace: "wifes"
```

No `(x, y)` has ever been recorded. This matters more than it looks: the
spatial candidate-generation design in `neural-autocorrect-plan.md` §Tier 1
opens with *"When you have (x,y) per keystroke"*. We do not. The single
largest planned improvement to the engine is written against data that does
not exist and has never existed.

### 1.3 Tapping a suggestion is indistinguishable from typing it

The `src` field on `WORD_COMMITTED` takes exactly two values across the entire
corpus:

```
TYPING: 244,737    VOICE: 112,882
```

There is no value for "the user tapped the smart bar." A bar tap is the single
highest-quality label the system can obtain — an unambiguous, unprompted
statement of *this one* — and it is currently recorded as ordinary typing, or
not at all.

### 1.4 `field` identifies a widget, not a conversation

| app | observed `field` values |
|---|---|
| messaging | 2131427998, 2131427999, 2131428000, 2131428002, 2131428009 |
| Slack | 2131363833, 2131364099, 2131364103, 2131364122, 2131364229 |
| messenger | -1 |
| chatgpt | -1 |
| termux | 2131231121 |

`field` is the Android resource ID of the text widget. Google Messages uses one
compose box for every conversation, so every thread collapses to the same ID.
Messenger and ChatGPT report nothing. Register cannot be recovered from what v3
captures. §8 addresses this.

### 1.5 One third of the corpus is dictation, silently mixed in

112,882 of 357,619 commits (31.6%) arrived via `src: VOICE`. These currently
enter word-frequency counts identically to typed words, which means roughly a
third of the corpus's frequency signal reflects speech, not typing. Dictated
words also have no keystrokes, so they must not be treated as spatial evidence.

### 1.6 The record shape cannot express what happened

v3 emits one event per *thing the engine did*. A word you fought with for eight
seconds produces a scatter of unlinked `SUGGESTIONS_SHOWN` rows and one
terminal `WORD_COMMITTED`, with the backspaces absent entirely and no key
joining them. There is no object representing "this word" and therefore nowhere
to put the story.

---

## 2. Design principles

1. **One record per word slot, not per event.** A slot opens when a word
   begins and closes when the cursor leaves it for good. Everything that
   happened to that word lives inside its record.
2. **Record evidence, derive conclusions.** Log raw keystrokes and offers; log
   derived signals too, but always alongside the evidence that produced them,
   so a derivation bug is auditable rather than baked in.
3. **Never discard a coordinate frame.** Log normalized *and* raw, plus the
   layout fingerprint. Any normalization mistake stays recoverable.
4. **Additive to v3.** No v3 field is dropped or renamed. A v3 row is readable
   as a degenerate v4 slot. The year of existing data keeps its value.
5. **Every field has an owner and a consumer.** If no downstream stage reads a
   field, it does not go in.
6. **Password fields emit nothing.** Unchanged from v3 and non-negotiable.

---

## 3. The core record

One JSON object per line, emitted when the slot closes.

```json
{
  "v": 4,
  "type": "WORD_SLOT",
  "id": 918273,
  "ts": "2026-09-11T14:22:31.882",
  "sess": "eb2f0637",
  "slot": "eb2f0637:1412",
  "prev": ["going", "to"],
  "ctx":     { },
  "layout":  { },
  "keys":    [ ],
  "offers":  [ ],
  "edits":   [ ],
  "outcome": { },
  "shadow":  { },
  "signals": { }
}
```

| field | meaning |
|---|---|
| `v` | schema version, `4` |
| `id` | monotonic event id, as v3 |
| `ts` | slot **open** time, ISO-8601 local |
| `sess` | input session id, as v3 |
| `slot` | `sess:n` — stable identity for this word position, referenced by late edits |
| `prev` | up to two preceding committed words (v3 `prev`/`prev2`, now a list) |

Slots are emitted on close. A slot that stays open past 60s or 512 keystrokes
is flushed as a partial with `signals.partial: true`, so a stuck slot cannot
consume unbounded memory or be lost entirely.

---

## 4. `ctx` — where the typing is happening

```json
"ctx": {
  "app": "com.google.android.apps.messaging",
  "fieldId": 2131427998,
  "inputType": "textShortMessage",
  "inputVariation": "normal",
  "flags": "autoCorrect",
  "imeOptions": "actionSend",
  "hint": "Text message",
  "label": null,
  "actionLabel": null,
  "privateImeOptions": null,
  "extrasKeys": ["com.google.android.inputmethod.latin.canary"],
  "inputSession": 4412,
  "register": null
}
```

`app`, `fieldId`, `inputType`, `inputVariation`, `flags` carry over unchanged
from v3's `AppContext`.

**New and free.** `hint`, `label`, `actionLabel`, `privateImeOptions` and
`extrasKeys` come straight off `EditorInfo` at no cost and require no
permission. Some apps put the recipient in the hint ("Message Sarah"); most do
not. We log them to find out empirically which of yours do, rather than
guessing. `extrasKeys` records only the *key names* present in
`EditorInfo.extras`, never their values.

**`inputSession`** increments on every `onStartInput` where `restarting == false`.
This is the load-bearing new field. It does not tell us *who* you are talking
to, but it marks precisely when you switched, which is what clustering needs.

**`register`** is reserved and always `null` on device. It is filled in offline
by the process in §8.

`isPassword` is not a field because a password field emits no record at all.

---

## 5. `layout` — the geometry the keys were in

```json
"layout": {
  "fp": "a7f31c92",
  "alphaW": 1080, "alphaH": 412,
  "aspect": 2.621,
  "alphaRows": 3,
  "modRowsTop": 1, "modRowsBottom": 3,
  "baseKeyH": 137,
  "dpi": 420,
  "orientation": "portrait",
  "oneHanded": null
}
```

`fp` is a stable hash over everything that changes key positions: row counts,
per-row height multipliers, padding, gaps, number-row presence, mod-row stack,
and one-handed offset. It is the join key that lets training group or condition
on layout configuration, so that reconfiguring the keyboard does not silently
poison the spatial model with incomparable samples.

`aspect` is called out separately because it is the specific quantity behind
the live defect in §7.2.

Emitted once per slot. Cheap, and it means every keystroke is self-describing
even if the layout changes mid-session.

---

## 6. `keys` — the actual journey

One entry per touch, in order, including corrections.

```json
"keys": [
  {"t":0,   "c":"r", "k":"r", "xn":0.312, "yn":0.166, "dx":-0.21, "dy":0.08,
   "px":337, "py":1584, "dur":54,  "src":"TAP"},
  {"t":118, "c":"e", "k":"e", "xn":0.219, "yn":0.171, "dx":0.11,  "dy":0.12,
   "px":236, "py":1586, "dur":47,  "src":"TAP"},
  {"t":241, "c":"i", "k":"i", "xn":0.716, "yn":0.160, "dx":0.38,  "dy":-0.04,
   "px":773, "py":1580, "dur":61,  "src":"TAP"},
  {"t":1902,"c":null,"k":"BKSP","xn":null,"yn":null,"dx":null,"dy":null,
   "px":1012,"py":1996,"dur":39, "src":"TAP"}
]
```

| field | meaning |
|---|---|
| `t` | milliseconds since slot open |
| `c` | character produced; `null` for non-producing keys |
| `k` | resolved key label, or `BKSP` / `SHIFT` / `SPACE` |
| `xn`,`yn` | position within the alpha block, 0–1 (§7) |
| `dx`,`dy` | offset from the resolved key's centre as a fraction of that key's width/height; sign convention: right/down positive |
| `px`,`py` | raw screen pixels |
| `dur` | touch-down to touch-up, ms |
| `src` | `TAP`, `GLIDE`, `LONGPRESS`, `POPUP`, `VOICE`, `PASTE` |

`dur` and inter-key `t` deltas are included because hesitation is signal: a long
pause before a key is correlated with uncertainty, and a very short one with a
fat-finger double.

For `src: GLIDE` the whole word arrives as one entry with `c` set to the full
string and the coordinate fields null; the glide path itself is out of scope
here and already handled by the glide classifier.

For `src: VOICE`, `keys` is empty. Dictated words carry no spatial evidence and
must never be used as such.

---

## 7. Coordinate frame

### 7.1 Normalization

Your keyboard's key heights, padding, mod-row stack and vertical position all
vary by configuration. Raw pixels are therefore meaningless across sessions.
The only invariant is that the alphabet is always QWERTY.

The codebase already solves this for glide typing, in
`FutoGlideTypingClassifier.kt:230-256`. v4 adopts the identical frame:

```kotlin
val letters = byChar.values                      // alpha keys ONLY
boardLeft = letters.minOf { it.visibleBounds.left }
boardTop  = letters.minOf { it.visibleBounds.top }
boardW    = max(1f, letters.maxOf { it.visibleBounds.right }  - boardLeft)
boardH    = max(1f, letters.maxOf { it.visibleBounds.bottom } - boardTop)
```

Then, per `docs/development/touch-coordinates-and-spatial-telemetry.md` §3:

```
xn = (touchX - boardLeft) / boardW
yn = (touchY - boardTop)  / boardH

dx = (touchX - key.centerX) / key.width
dy = (touchY - key.centerY) / key.height
```

Because the bounding box is computed from letter keys only, mod rows sit
outside it and do not perturb the frame. Key height, padding, DPI and screen
size all divide out. `xn`/`yn` answer "where on the alphabet"; `dx`/`dy` answer
"where inside the key you aimed for" — the second is what a spatial model needs
to learn your personal bias, and it is independent of how large that key was.

Using the same frame as the glide classifier is deliberate: tap and glide
telemetry become directly comparable, and any future fix to the frame improves
both at once.

### 7.2 The known distortion, which is a live bug

`touch-coordinates-and-spatial-telemetry.md` §4 documents that glide typing
degrades when key heights change or mod rows are expanded. Two named causes:

1. **Vertical aspect distortion.** `boardH` is derived from the alpha rows, so
   when `KeyboardGeometrySolver.kt` compresses or stretches them, a normalized
   vertical distance stops meaning what it meant at training time.
2. **Hardcoded thresholds.** `StatisticalGlideTypingClassifier.kt:160` derives
   `distanceThresholdSquared` from the *first key's width*, with no correction
   for non-uniform vertical stretching or mod-row insertion.

This is the same defect that would corrupt tap telemetry. Your swipe-typing
flakiness and the spatial-autocorrect problem share one root cause. Logging
`aspect` and `fp` does not fix it, but it makes it measurable and lets training
compensate; the structural fix belongs with the geometry hardening work and
should be scheduled as one job covering both.

---

## 8. Register — conditioning on who you are talking to

### 8.1 What is not possible

A keyboard cannot see the conversation. The thread lives in the app's own UI,
outside the IME's reach. Reading it would require an accessibility service,
which is a large permission with Samsung/Knox complications, and is explicitly
out of scope.

### 8.2 What v4 does instead

Two halves.

**On device (§4):** log `inputSession`, which marks every field attach and
therefore every conversation switch, plus the `hint`/`label`/`actionLabel`/
`extras` fields in case an app volunteers the recipient.

**Offline:** cluster input-sessions by writing style. The way you write to your
wife differs measurably from Slack and from your dad — vocabulary, message
length, punctuation density, contraction rate, emoji, formality. That structure
is **already present in the 74,849 `SESSION_TEXT` records you have today**; it
does not need new collection. Cluster it, label the clusters by hand once, and
`ctx.register` is populated at build time.

This yields the conditioning you want without new permissions, and it
generalizes past the threads you thought of — it will find your
code-writing and prompt-writing registers without being told they exist.

**Worth checking early:** Slack exposes five distinct `fieldId` values. If those
correspond to channel vs DM vs thread-reply, that is free register signal for
one of your highest-volume apps. Verifiable from existing data.

---

## 9. `offers`, `edits`, `outcome`, `shadow`, `signals`

### 9.1 `offers` — what the bar showed, and when

```json
"offers": [
  {"t":241,  "prefix":"rei",     "eng":"symspell+scorer",
   "cands":[["rein",2.10],["reign",2.44],["rec",3.01]], "shown":3},
  {"t":1450, "prefix":"reicieve","eng":"symspell+scorer",
   "cands":[["recieve",1.88],["relieve",2.90]], "shown":2}
]
```

One entry per suggestion refresh. `shown` records how many actually rendered,
which can be fewer than `cands` — without it we cannot distinguish "the engine
never found it" from "the engine found it but the bar was too narrow."

### 9.2 `edits` — deletions and late revisions

```json
"edits": [
  {"t":1902, "op":"BKSP",      "n":1, "left":"reicieve"},
  {"t":2011, "op":"BKSP",      "n":1, "left":"reiciev"},
  {"t":2140, "op":"BKSP",      "n":4, "left":"rei",     "burst":true},
  {"t":9820, "op":"RETURN_EDIT","n":0, "left":"receiving"}
]
```

`op` is one of `BKSP`, `BKSP_WORD`, `SELECT_DELETE`, `CURSOR_MOVE`,
`RETURN_EDIT`. `burst: true` marks rapid repeats coalesced from key-repeat.
`RETURN_EDIT` reopens a closed slot when you come back later and change it —
the strongest correction signal available, because it is entirely unprompted.

### 9.3 `outcome` — how the word finally landed

```json
"outcome": {
  "final": "receiving",
  "route": "TYPED_THROUGH",
  "barIndex": null,
  "autoFrom": null,
  "reverted": false,
  "revertedTo": null,
  "returnEdited": false,
  "committedAt": "2026-09-11T14:22:41.702",
  "commitChar": " "
}
```

`route` is one of:

| route | meaning |
|---|---|
| `TYPED_THROUGH` | typed out and committed as typed |
| `BAR_PICK` | **tapped from the smart bar** — closes §1.3 |
| `AUTO_APPLIED` | autocorrect fired; `autoFrom` holds what you typed |
| `GLIDE` | swipe input |
| `VOICE` | dictation |
| `PASTE` | pasted |
| `ABANDONED` | slot emptied and left empty |

`barIndex` is the 0-based position tapped. Position matters: picking slot 3
means the engine had it but ranked it badly, which is a different failure from
not having it at all.

### 9.4 `shadow` — what each scorer thought

```json
"shadow": {
  "heuristicTop": "relieve",
  "heuristicRanked": [["relieve",1.88],["receive",2.90]],
  "neuralTop": "receive",
  "neuralRanked": [["receive",0.71],["relieve",0.22]],
  "neuralMargin": 0.49,
  "wouldFire": true,
  "agrees": false,
  "policyBlockers": ["NOT_A_CORRECTION"],
  "reachable": false,
  "reachableAt": null
}
```

Both rankings are always recorded; only one acts. This is what makes it
possible to retire the heuristic on evidence rather than faith — replay the
corpus, compare, and flip when the model wins by an agreed margin. Not
half-and-half: one decides, both are recorded.

`policyBlockers` lists the `CommitPolicy.blockers()` result verbatim
(`VALID_WORD_IMMUNITY`, `TOO_SHORT`, `NUMERIC_TOKEN`, `NOT_A_CORRECTION`,
`NEURAL_VETO`, `ANTI_CORRECTION`, `PROTECTED_VOCAB`,
`INVALID_CONTRACTION_LICENSE`, `NO_CHANGE`). Today, when a correction silently
fails to fire, there is no way to know which of nine gates stopped it. This
field turns that from archaeology into a lookup.

**`reachable` is the most important single field in v4.** It answers: did
`outcome.final` appear *anywhere* in *any* candidate list during this slot?
`reachableAt` gives its best rank if so. Aggregated, this is a direct,
continuous measurement of the retrieval ceiling — the Layer 2 number that no
ranker can improve on and that we currently estimate at ~80% from a single
stale offline measurement. With this field it becomes a daily metric, measured
on your real typing, and Phase 3's success criterion is simply that it goes up.

### 9.5 `signals` — derived, and auditable

```json
"signals": {
  "attempts": 2,
  "totalBksp": 6,
  "maxBkspBurst": 4,
  "struggle": true,
  "capitulation": false,
  "unreachable": true,
  "dwellMs": 9180,
  "partial": false
}
```

Every one of these is recomputable from `keys`/`edits`/`offers`/`outcome`.
They are logged anyway so that a bug in the derivation is visible as a
disagreement rather than invisible as a silently wrong training label.

**`capitulation`** is set when the slot shows the fight-then-yield shape:
one or more reverts or rejections of the same target, followed by accepting it.
This exists because of the `fso` case — twelve commits that looked like
vocabulary evidence and were actually you giving up on `fos`. A bare
`WORD_COMMITTED` cannot distinguish intent from surrender. The chain can.

---

## 10. Voice

Dictated words get `outcome.route: "VOICE"`, empty `keys`, and:

```json
"voice": {
  "audioRef": "a91f0c33",
  "transcriptKind": "CLEANED",
  "asrConf": 0.91,
  "segIndex": 3
}
```

`audioRef` is an opaque id into retained audio; no path or filename is written
into the JSONL.

**Two transcripts per recording, both retained:**

- **verbatim** — fillers, false starts and repairs intact. Ground truth for
  speech; what you would use to evaluate or tune the transcriber.
- **cleaned** — fillers removed. This is what feeds the language model, because
  the keyboard must not learn to predict "um".

Same audio, two passes, a flag on the transcription run. This also fixes §1.5:
with `route` distinguishing them, dictated words stop contaminating typed-word
frequency counts, and the two corpora can be weighted independently.

---

## 11. Worked examples

Five real failure shapes from your corpus, written as v4 records. Abridged
coordinates for readability; `keys` entries are otherwise complete.

### 11.1 Type, fail, backspace six, retype — the case v3 discards entirely

You type `reicieve`, nothing useful is offered, you backspace six times and
type `receiving`. **v3 records one row: `WORD_COMMITTED "receiving"`.** The
attempt, the offers, and the six backspaces are gone. v4:

```json
{
  "v":4, "type":"WORD_SLOT", "slot":"eb2f0637:1412",
  "ts":"2026-09-11T14:22:31.882",
  "prev":["not","be"],
  "ctx":{"app":"com.google.android.apps.messaging","fieldId":2131427998,
         "flags":"autoCorrect","inputSession":4412,"hint":"Text message"},
  "layout":{"fp":"a7f31c92","aspect":2.621,"modRowsBottom":3,"baseKeyH":137},
  "keys":[
    {"t":0,    "c":"r","k":"r","xn":0.312,"yn":0.166,"dx":-0.21,"dy":0.08,"dur":54},
    {"t":118,  "c":"e","k":"e","xn":0.219,"yn":0.171,"dx":0.11, "dy":0.12,"dur":47},
    {"t":241,  "c":"i","k":"i","xn":0.716,"yn":0.160,"dx":0.38, "dy":-0.04,"dur":61},
    {"t":355,  "c":"c","k":"c","xn":0.330,"yn":0.833,"dx":0.02, "dy":0.19,"dur":50},
    {"t":492,  "c":"i","k":"i","xn":0.719,"yn":0.158,"dx":0.39, "dy":-0.06,"dur":44},
    {"t":605,  "c":"e","k":"e","xn":0.221,"yn":0.169,"dx":0.13, "dy":0.10,"dur":48},
    {"t":737,  "c":"v","k":"v","xn":0.418,"yn":0.840,"dx":-0.08,"dy":0.22,"dur":52},
    {"t":861,  "c":"e","k":"e","xn":0.218,"yn":0.174,"dx":0.10, "dy":0.15,"dur":45},
    {"t":1902, "c":null,"k":"BKSP","dur":39}, {"t":2011,"c":null,"k":"BKSP","dur":34},
    {"t":2140, "c":null,"k":"BKSP","dur":31}, {"t":2244,"c":null,"k":"BKSP","dur":29},
    {"t":2351, "c":null,"k":"BKSP","dur":30}, {"t":2460,"c":null,"k":"BKSP","dur":33},
    {"t":3120, "c":"c","k":"c","xn":0.331,"yn":0.829,"dx":0.03,"dy":0.15,"dur":49},
    {"t":3255, "c":"e","k":"e","xn":0.220,"yn":0.170,"dx":0.12,"dy":0.11,"dur":46},
    {"t":3390, "c":"i","k":"i","xn":0.714,"yn":0.162,"dx":0.36,"dy":-0.02,"dur":47},
    {"t":3521, "c":"v","k":"v","xn":0.419,"yn":0.836,"dx":-0.07,"dy":0.18,"dur":51},
    {"t":3660, "c":"i","k":"i","xn":0.717,"yn":0.159,"dx":0.38,"dy":-0.05,"dur":43},
    {"t":3792, "c":"n","k":"n","xn":0.612,"yn":0.841,"dx":0.05,"dy":0.20,"dur":48},
    {"t":3915, "c":"g","k":"g","xn":0.415,"yn":0.501,"dx":-0.04,"dy":0.09,"dur":50}
  ],
  "offers":[
    {"t":241, "prefix":"rei",     "cands":[["rein",2.10],["reign",2.44]], "shown":2},
    {"t":861, "prefix":"reicieve","cands":[["relieve",2.90]],             "shown":1}
  ],
  "edits":[
    {"t":1902,"op":"BKSP","n":1,"left":"reiciev"},
    {"t":2011,"op":"BKSP","n":1,"left":"reicie"},
    {"t":2140,"op":"BKSP","n":4,"left":"rei","burst":true}
  ],
  "outcome":{"final":"receiving","route":"TYPED_THROUGH","barIndex":null,
             "reverted":false,"commitChar":" "},
  "shadow":{"heuristicTop":"relieve","neuralTop":"relieve","agrees":true,
            "policyBlockers":["NOT_A_CORRECTION"],
            "reachable":false,"reachableAt":null},
  "signals":{"attempts":2,"totalBksp":6,"maxBkspBurst":4,"struggle":true,
             "capitulation":false,"unreachable":true,"dwellMs":4180}
}
```

What this row now teaches, none of which survives in v3:

- **`reachable:false`** — `receiving` was never in any candidate list. This is a
  Layer 2 retrieval failure, recorded as such, countable in aggregate.
- **A labeled repair pair** — keystroke evidence `reicieve` maps to target
  `receiving`. This is the only class of example where you supply the answer
  *after* the engine has already failed.
- **Your `i` bias** — `dx` of `+0.36` to `+0.39` on every `i`. You land
  consistently right-of-centre on that key. A spatial model learns this; the
  current engine cannot see it.
- **Hesitation** — a 1041ms gap before the first backspace. You read the bar,
  found nothing, and gave up. That pause distinguishes "considered and rejected"
  from "never looked."
- **`policyBlockers:["NOT_A_CORRECTION"]`** — even had `receiving` been
  retrieved, this gate would have suppressed it. Two independent failures, both
  now visible.

### 11.2 Capitulation — the `fso` case

Twelve commits of `fso` looked like vocabulary evidence. You told us they were
surrender: `fos` was the target. v3 cannot tell these apart; the chain can.

```json
{
  "slot":"3c1f88a2:207", "prev":["the"],
  "ctx":{"app":"com.Slack","fieldId":2131364099,"inputSession":1188},
  "keys":[{"t":0,"c":"f","k":"f"},{"t":109,"c":"o","k":"o"},{"t":227,"c":"s","k":"s"},
          {"t":880,"c":null,"k":"BKSP"},{"t":962,"c":null,"k":"BKSP"},
          {"t":1044,"c":null,"k":"BKSP"},
          {"t":1600,"c":"f","k":"f"},{"t":1712,"c":"o","k":"o"},{"t":1830,"c":"s","k":"s"},
          {"t":2510,"c":null,"k":"BKSP"},{"t":2588,"c":null,"k":"BKSP"},
          {"t":2670,"c":null,"k":"BKSP"},
          {"t":3200,"c":"f","k":"f"},{"t":3310,"c":"s","k":"s"},{"t":3425,"c":"o","k":"o"}],
  "offers":[{"t":227,"prefix":"fos","cands":[["fso",1.2],["for",1.9]],"shown":2},
            {"t":1830,"prefix":"fos","cands":[["fso",1.2],["for",1.9]],"shown":2}],
  "outcome":{"final":"fso","route":"TYPED_THROUGH","reverted":false},
  "shadow":{"heuristicTop":"fso","reachable":true,"reachableAt":0},
  "signals":{"attempts":3,"totalBksp":6,"struggle":true,
             "capitulation":true,"unreachable":false}
}
```

`capitulation:true` fires on the shape: same target attempted, erased, attempted
again, then abandoned in favour of what the engine wanted. The final keystrokes
`f-s-o` are typed in a different order from the two earlier `f-o-s` attempts —
you stopped typing the word and started typing the correction. Training must
weight this row **negatively** for `fso` as vocabulary, not positively. v3
weights it positively twelve times over.

### 11.3 Smart-bar tap — the label v3 never records

```json
{
  "slot":"eb2f0637:1580", "prev":["meeting","at"],
  "keys":[{"t":0,"c":"t","k":"t"},{"t":121,"c":"m","k":"m","dx":0.41},
          {"t":238,"c":"r","k":"r"},{"t":352,"c":"w","k":"w"}],
  "offers":[{"t":352,"prefix":"tmrw",
             "cands":[["tmrw",0.0],["term",2.4],["tomorrow",2.9]],"shown":3}],
  "outcome":{"final":"tomorrow","route":"BAR_PICK","barIndex":2,"commitChar":" "},
  "shadow":{"heuristicTop":"term","neuralTop":"tomorrow","agrees":false,
            "policyBlockers":["NOT_A_CORRECTION"],
            "reachable":true,"reachableAt":2},
  "signals":{"attempts":1,"totalBksp":0,"struggle":false,"unreachable":false}
}
```

Three facts, all currently invisible:

- **`route:BAR_PICK`** — you stated the answer explicitly. Highest-confidence
  label in the system. v3 records this as `src:TYPING`, identical to having
  typed `tomorrow` letter by letter.
- **`barIndex:2`** — it was there, ranked third. A ranking failure, not a
  retrieval failure. Distinguishable now; previously not.
- **`agrees:false` with `neuralTop` correct** — a concrete instance of the
  neural ranker beating the heuristic. Accumulate these and the flip in §9.4
  becomes arithmetic instead of a judgement call.

### 11.4 The Termux dead zone — the engine was never asked

Your single largest failure class: 275 of 424 common-typo survivors.

```json
{
  "slot":"91ab30ff:8842", "prev":["git","status"],
  "ctx":{"app":"com.termux","fieldId":2131231121,"flags":"none",
         "inputType":"textMultiLine","inputSession":9021},
  "keys":[{"t":0,"c":"t","k":"t"},{"t":98,"c":"h","k":"h"},
          {"t":205,"c":"s","k":"s","dx":0.44,"dy":0.11},{"t":312,"c":"t","k":"t"}],
  "offers":[],
  "outcome":{"final":"thst","route":"TYPED_THROUGH","commitChar":" "},
  "shadow":{"heuristicTop":null,"neuralTop":null,"policyBlockers":[],
            "reachable":null,"reachableAt":null},
  "signals":{"attempts":1,"totalBksp":0,"struggle":false,"unreachable":null}
}
```

`offers:[]`, `policyBlockers:[]`, `reachable:null`. Nothing was blocked because
nothing ran — no composing region, so the engine was never consulted. The
distinction between `reachable:false` (looked, did not find) and
`reachable:null` (never looked) is why this must be a three-valued field rather
than a boolean. Conflating them is precisely the error that made the autocorrect
look like a ranking problem when it is overwhelmingly a reachability problem.

Note `dx:0.44` on the `s` — you were reaching for `a` and landed on the right
edge of `s`. Recoverable spatial evidence from a slot where no correction was
even attempted. Every one of the 120,141 Termux commits is currently throwing
away evidence of exactly this kind.

### 11.5 Dictation

```json
{
  "slot":"eb2f0637:1601", "prev":["i","think"],
  "ctx":{"app":"com.openai.chatgpt","fieldId":-1,"inputSession":4419},
  "keys":[],
  "offers":[],
  "outcome":{"final":"agentic","route":"VOICE","commitChar":" "},
  "voice":{"audioRef":"a91f0c33","transcriptKind":"CLEANED",
           "asrConf":0.78,"segIndex":3},
  "signals":{"attempts":1,"totalBksp":0,"struggle":false}
}
```

`keys:[]` and `route:VOICE` together guarantee this row never reaches the
spatial model. `asrConf:0.78` is low — a candidate for review against the
verbatim transcript. And because `agentic` is in Pile A (48 commits, currently
out of dictionary), this row also shows dictation surfacing vocabulary gaps
independently of typing.

---

## 12. Emission sites

The spec is not complete without this table. §1.1 is what happens when it is
omitted.

| block | emitted from | trigger |
|---|---|---|
| slot open | `EditorInstance.kt` | first key of a new word |
| `ctx` | `FlorisImeService.kt` `onStartInput` | field attach; `inputSession` increments |
| `layout` | `TextKeyboardLayout.kt` → geometry | on layout build; cached, stamped per slot |
| `keys` | `KeyboardManager.kt` touch handling | every touch-up, with resolved key + coords |
| `offers` | `NlpManager.kt:95` | each suggestion refresh (extends existing call) |
| `edits` | `EditorInstance.kt` | backspace / selection delete / cursor move |
| `outcome` | `EditorInstance.kt:324,349,360,415,462,476` | commit, by route |
| `outcome.route=BAR_PICK` | smart bar tap handler | **new call site — the §1.3 gap** |
| `shadow` | `NlpManager.kt:105` + `CommitPolicy` | at decision time |
| `signals` | `HarvestManager` on slot close | derived |
| slot close | `EditorInstance.kt` | cursor leaves word, or flush |

The nine dead functions in §1.1 are either wired here or deleted. Leaving them
present and uncalled is not an option — it is what produced the false
impression that this was already done.

---

## 13. Migration and salvage

**Nothing is discarded.** The reader handles both versions:

- A v3 `WORD_COMMITTED` reads as a slot with `outcome.route` inferred from
  `src` (`TYPING` → `TYPED_THROUGH`, `VOICE` → `VOICE`), empty `keys`, empty
  `offers`, empty `edits`, and `signals` absent.
- A v3 `AUTO_APPLIED` reads as `route: AUTO_APPLIED` with `autoFrom` from
  `typed` and `offers` reconstructed from its `candidates` array.
- v3 `REVERTED` sets `outcome.reverted` on the preceding slot.
- v3 `SUGGESTIONS_SHOWN` rows join to slots by `sess` + timestamp adjacency.

Training treats missing blocks as missing features, not as zeros. Rows without
coordinates train every part of the model that does not need coordinates —
which is all of the language modelling and all of the ranking. The year of data
keeps its full value for those; it simply cannot contribute to the spatial
model, because it never contained spatial information.

`v3 → v4` is additive at the field level: no v3 field is renamed or removed.
`trace` is retained as-is for one release and then dropped, since its content
is redundant.

---

## 14. Privacy and safety

- Password, `visiblePassword`, and email-entry fields emit **no record**.
  Current v3 behavior, preserved, checked at `HarvestManager.jsonl()` before any
  field is assembled.
- `extrasKeys` logs key names only, never values.
- `hint`/`label` may contain a recipient name where an app supplies one. This
  is intended, stays on device, and travels only through the existing harvest
  pull.
- `audioRef` is opaque; no audio path appears in the log.
- No network transmission is part of this spec. Any live relay is tailnet-only
  and specified separately.

---

## 15. Acceptance criteria

An implementation is complete when, on a device build:

1. Every field in §§4–10 has a non-null value in at least one real slot, and
   the emission table in §12 has no unwired row.
2. `grep -c '"route":"BAR_PICK"'` is greater than zero after deliberately
   tapping the smart bar.
3. A deliberate type-then-backspace-six-then-retype produces **one** slot
   record containing all keystrokes, all six backspaces, both attempts, and
   `signals.struggle: true`.
4. `xn`/`yn` for the same physical key agree within 2% across three
   deliberately different layout configurations (key height, mod-row count,
   number-row on/off), and `layout.fp` differs across all three.
5. `shadow.reachable` is populated on 100% of slots where a correction was
   possible, so the retrieval ceiling can be computed for any date range.
6. A password field produces zero records.
7. The v3 corpus still parses, and total word counts from it are unchanged.

---

## 16. Open questions

1. **`herdr`** — resolved as vocabulary (agent-based multiplexer). Recorded
   here because it was the motivating example for why OOV frequency alone
   cannot classify a word.
2. **Slot flush bounds** — 60s / 512 keystrokes are proposed, not measured.
   Worth revisiting once real dwell-time data exists.
3. **Log volume.** v4 is materially larger per word than v3. The corpus is
   ~200MB for 63 days at v3 density; v4 with coordinates is a rough 3–5x.
   Rotation policy needs deciding before this runs for a month.
4. **Glide slots.** Whether the glide path itself should be recorded, or only
   the resolved word, is deferred.
5. **Register clustering method** — deferred to the offline tooling spec; the
   on-device half (§4) does not depend on the answer.

---

## 17. What this unblocks

- **Layer 2 (retrieval)** becomes measurable via `shadow.reachable`, and
  fixable via `keys` coordinates. This is the ~20% of cases where the right
  word never enters the candidate list, which no dictionary edit and no ranker
  can reach.
- **Retiring the heuristic** becomes an evidence-based flip rather than a leap,
  via `shadow` recording both rankings on every decision.
- **Silent correction failures** become diagnosable in one lookup, via
  `policyBlockers`.
- **Register-conditioned prediction** becomes possible without new permissions,
  via `inputSession` plus offline clustering.
- **Voice stops contaminating typing statistics**, via `route` and the
  two-transcript split.
- **The backspace-and-retype case** — currently discarded entirely — becomes a
  complete labeled training example, and it is the only case where you supply
  the right answer *after* the engine has already failed.

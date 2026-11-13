# Keylog Pipeline: Phone to Titan to Training Set

> Status: Canonical  
> Last verified: 2026-11-13  
> Verified against: `HarvestShipper.kt`, `tools/keylog_archive/`, the live
> `keylog-archive.service` on Titan, and the first dataset build

The keyboard's harvest log (`usage_harvest.jsonl`, see
[Harvesting](harvesting.md)) is relayed to Titan automatically, archived
losslessly, and rebuilt every six hours into a versioned, validated dataset
that a neural autocorrect model can train on directly.

```
icarion                         Titan (100.104.232.94, tailnet only)
usage_harvest.jsonl ──HarvestShipper──▶ keylog-archive.service :8791
  (append-only,       gzip chunks,        raw/<stream>/<offset>.jsonl.gz
   never deleted)     sha256 receipts     manifests/chunks.jsonl
                                          streams/<stream>.json
                                                 │ keylog-dataset.timer (6h)
                                                 ▼
                                          derived/builds/<id>/ ─▶ derived/current
```

Before 2026-11-13 no keylog relay existed: keylogs reached Titan only by hand
(`snapshot_device.py` plus an rsync). Voice has its own archive
(`voice-archive.service` on :8790, `~/datasets/sam-voice`). Both share one
token.

## Contract

The phone's file is append-only, so the whole protocol is "how many bytes of
this file does Titan hold?"

- **Stream.** One physical harvest file, named by `sha256(first line)[:16]`. A
  deleted and recreated file gets a new first line and so a new stream. Nothing
  is spliced across files.
- **Chunk.** Bytes `[offset, offset+n)` of the file, ending on a newline,
  gzipped. Titan accepts a chunk only at exactly its committed offset; anything
  else gets `409` with the committed offset, and the client resynchronises.
- **Receipt.** Titan hashes what it actually decompressed. The phone advances
  its cursor only when the receipt's `sha256` equals its own.
- **Anchor.** Each chunk carries the SHA-256 of the 4 KiB before its offset.
  A file rewritten in place with the same first line is refused as `diverged`
  instead of being silently stitched together.
- **Resume after reinstall.** A fresh install has no cursor, so it asks
  `GET /v1/keylog/streams/<stream>` and continues from there, after checking
  the anchor against its own file.
- **Idempotency.** Re-sending a stored chunk returns `duplicate: true` and
  writes nothing.

The phone never deletes or rewrites the harvest file. An unreachable Titan
only means the cursor waits.

### Phone side

`HarvestShipper` (`ime/nlp/HarvestShipper.kt`) is started by
`HarvestManager.init` and poked each time the keyboard closes. It runs at most
every 10 minutes (30 after a failure), sends up to 48 MiB per run in 2 MiB
chunks (~150 KB on the wire each), and sends the device time zone with each
chunk. Its cursor and last error live in the `keylog_shipper` shared
preferences.

The endpoint is `KEYLOG_ARCHIVE_URL` (default
`http://100.104.232.94:8791/v1/keylog`), authenticated with `ARCHIVE_TOKEN`.
A build without `ARCHIVE_TOKEN` prints a Gradle warning and cannot upload
voice or keylogs. That happened from 2026-10-05 to 2026-11-13: every APK built
on sisyphus lacked the token, and Titan refused 111k voice uploads.

### Titan side

Code lives in `tools/keylog_archive/` and is installed to `/opt/keylog-archive`
by `sudo tools/keylog_archive/install.sh` from a checkout on Titan.

| unit | role |
|---|---|
| `keylog-archive.service` | receiver on `100.104.232.94:8791`; token `/etc/voice-archive/token` |
| `keylog-dataset.timer` | runs `build_dataset.py` every 6 hours |
| `keylog-adb-pull.timer` (user unit) | hourly `client.py` pull through sleeper's adb: a second path that works even when the installed APK cannot ship |

`GET /v1/keylog/health` (no token) lists each stream's committed offset, line
count and last event time. It is the quickest check that the phone is
shipping.

`import_file.py` backfills from a local copy (plain or `.gz`) through the same
ingest path. A snapshot of the phone's file lands in the *same* stream, so the
phone resumes from the snapshot's end. The 2026-11-12 manual snapshot was
imported this way.

`client.py` speaks the phone's protocol. It is the end-to-end test for a live
receiver and the fallback shipper when the installed APK predates
`HarvestShipper`:

```bash
# on Titan; reads only the missing bytes off the phone through sleeper's adb
python3 tools/keylog_archive/client.py \
    --device-cmd 'ssh -o BatchMode=yes sleeper /home/sam/.local/bin/icarion-shell'
```

## Dataset builds

`build_dataset.py` reads `raw/` and `manifests/` without modifying them and
publishes an immutable build. `derived/current` is repointed only when the
build passes its gates, so a trainer reading `current` never sees a failed or
half-written build. The last four builds are kept.

1. **Integrity:** each chunk's gzip and content hashes match the manifest, and
   each stream's chunks tile the file from offset 0. A stream stops at its
   first broken chunk.
2. **Validation:** `schema.py` checks every line (v3 events and v4
   `WORD_SLOT`). Failures go to `quarantine.jsonl.gz` with reasons. Unknown
   event types pass the common checks, so a new emitter cannot cause data
   loss.
3. **Dedup:** byte-identical lines are kept once across streams. Distinct
   lines sharing a `(stream, id)` are kept and counted.
4. **Gates:** integrity OK, under 1% invalid, and some typing slots present.

| file | contents |
|---|---|
| `events.jsonl.gz` | every valid event, plus `_uid`, `_stream`, `_day`, `_split`, `_ts_utc` |
| `typing_slots.jsonl.gz` | v4 slots with a letter tap: keys as `[t, c, k, xn, yn, dx, dy, dur, src, kb, x0, y0, mv, maj, pr, roll, shift, mode]`, replayed `typed`, `intended`, route, revert fields, `preds`/`predKind`/`predRank`, signals, and `quality` (`clean`, `self_corrected`, `struggle`, `reverted`, `partial`) |
| `corrections.jsonl.gz` | every `AUTO_APPLIED` with its candidates and a `reverted` label joined from `REVERTED.undoes` |
| `words.jsonl.gz` | committed words in order, with `input`: `TYPED`, `GLIDE`, `VOICE`, `PASTE`, or `TYPING_V3` (before v4 slots, where pasted text is mixed in) |
| `quarantine.jsonl.gz` | invalid lines and why |
| `DATASET.json` | the dataset card: provenance, integrity, gates, counts, split rule, file hashes |

**Splits are by day:** `sha256("YYYY-MM-DD")[0] % 20` puts buckets 0-1 in
test, 2-3 in val, and the rest in train. Whole days stay together, so no
sentence straddles train and test, and a day's assignment never changes as
new days arrive.

**Time:** the phone writes local time without an offset. `ts_utc` uses the
zone the phone sent with the chunk, otherwise `America/Chicago`. The card
records which.

First build (2026-11-13, backfilled 2026-11-12 snapshot): 1,600,876 events
over 120 days (2026-07-09 to 2026-11-12), 0 invalid, 0 duplicates, 37,660
touch-bearing typing slots, 11,596 labelled autocorrect decisions.

## Capture additions (2026-11-13)

Builds from 2026-11-13 on add three things to each `WORD_SLOT`. Older lines
simply lack them.

- **Every key has a position.** Space, backspace, punctuation and enter now
  carry `xn`/`yn`/`dx`/`dy` like letters. Before this, 23% of taps had none.
  `xn`/`yn` stay in the alpha frame, so keys below the letters have `yn > 1`.
- **`kb` on non-letter keys:** the key's visible rectangle in the alpha frame
  as `[left, top, right, bottom]`. The bottom row resizes, so a space-bar tap
  is only meaningful next to the bar's own bounds at that moment.
- **`preds`:** predictions on the bar before the word's first key, as
  `{kind, cands, lead}`. `kind` is `next` (candidate bar) or `phrase` (phrase
  row, only when that row is enabled), and `lead` is how many ms before the
  word they were shown. `outcome.predKind`/`predRank` give the committed word's
  best place among them; route `BAR_PICK` means it was tapped, and any other
  route means it was shown but typed or ignored.

Later the same day each key also gained touch-path and state fields:

| field | meaning |
|---|---|
| `x0`, `y0` | where the finger went down, alpha frame (`xn`/`yn` is where it lifted) |
| `mv` | travel from down to up, in key widths: a slide or rolled tap |
| `maj` | contact size in px (thumb pad vs tip), larger of down and up |
| `pr` | pressure on down; often constant on Samsung panels |
| `roll` | other fingers down at release: two-thumb rollover |
| `shift` | shift state when the key went down |
| `mode` | keyboard mode (`CHARACTERS`, `SYMBOLS`, ...) |

App and field context (`ctx.app`, `inputType`, `inputVariation`, `flags`,
`imeOptions`, `hint`) was already on every slot.

## Analysis

`analyze.py` turns a build into an aggregate-only JSON summary (rates,
distributions and top-N lists, no message text) for reports. Its first
report is in the project's artifacts, not in the repository.

## Privacy

Password and email fields are never harvested (see [Harvesting](harvesting.md)).
The relay is tailnet-only and token-gated. Raw data and builds live only
under `~/datasets/sam-keylog` on Titan and are never committed. This
repository is public.

#!/usr/bin/env python3
"""Build the training-ready keylog dataset from the raw archive.

Reads ``raw/`` + ``manifests/chunks.jsonl`` (written only by the receiver and
the importer), never modifies them, and publishes an immutable build under
``derived/builds/<build_id>/``. ``derived/current`` is repointed only when the
build passes its gates, so a trainer that reads ``current`` never sees a
half-written or failed build.

Pipeline, in order:

1. **Integrity.** Every chunk must exist, its gzip must hash to the manifest's
   ``gz_sha256``, its contents to ``sha256``, and each stream's chunks must tile
   the file from offset 0 with no gap or overlap. A stream stops at its first
   broken chunk; everything after it is excluded and reported.
2. **Validation.** Each line is parsed and checked by ``schema.validate``.
   Failures go to ``quarantine.jsonl.gz`` with their reasons, never dropped.
3. **Dedup.** Byte-identical lines are kept once across all streams (a copied
   snapshot and the live file overlap completely). Distinct lines that share a
   ``(stream, id)`` are kept and counted as id collisions.
4. **Views.** One canonical event log plus task-shaped views for training:

   - ``events.jsonl.gz``        every valid event, + ``_uid``, ``_stream``, ``_day``, ``_split``
   - ``typing_slots.jsonl.gz``  WORD_SLOTs with touch evidence: the neural model's examples
   - ``corrections.jsonl.gz``   AUTO_APPLIED decisions labelled kept / reverted
   - ``words.jsonl.gz``         committed words in order, with how each arrived (LM text)
   - ``quarantine.jsonl.gz``    invalid lines and why
   - ``DATASET.json``           the card: provenance, counts, gates, split rule, file hashes

Splits are by *day*: ``sha256(day) % 20`` puts days 0-1 in test, 2-3 in val,
the rest in train. Whole days stay together so a test word never shares a
sentence with a training word, and the assignment of an existing day never
changes as new days arrive.

Timestamps on the phone are local without an offset. ``ts_utc`` uses the
zone the phone reported with the chunk when it sent one, otherwise the device zone
(America/Chicago) is assumed, and the card says which.

    python3 build_dataset.py                 # build + promote if gates pass
    python3 build_dataset.py --verify-only   # integrity check, no build
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import io
import json
import os
import shutil
import subprocess
import sys
import time
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import schema  # noqa: E402
from keylog_store import KeylogStore, now_iso, sha256_hex  # noqa: E402

try:
    from zoneinfo import ZoneInfo
    DEVICE_TZ = ZoneInfo(os.environ.get("KEYLOG_DEVICE_TZ", "America/Chicago"))
except Exception:  # pragma: no cover - zoneinfo/tzdata missing
    DEVICE_TZ = None

KEEP_BUILDS = 4
MAX_INVALID_FRACTION = 0.01
KEY_COLUMNS = ["t", "c", "k", "xn", "yn", "dx", "dy", "dur", "src", "kb"]
LETTERS_PUNCT = ".,!?;:\"()[]{}"


def split_for_day(day: str) -> str:
    bucket = hashlib.sha256(day.encode()).digest()[0] % 20
    if bucket < 2:
        return "test"
    if bucket < 4:
        return "val"
    return "train"


_ZONES: dict[str, object] = {}


def zone_for(name: str | None):
    if not name:
        return None
    if name not in _ZONES:
        try:
            _ZONES[name] = ZoneInfo(name)
        except Exception:
            _ZONES[name] = None
    return _ZONES[name]


def to_utc(ts: str, tz=None) -> str | None:
    try:
        local = datetime.fromisoformat(ts)
    except ValueError:
        return None
    zone = tz or DEVICE_TZ
    if zone is None:
        return None
    aware = local.replace(tzinfo=zone)
    return aware.astimezone(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def replay_typed(keys: list[dict]) -> str:
    """Characters that survived backspacing: what the user had typed at commit."""
    buf: list[str] = []
    for k in keys:
        name = k.get("k")
        if name == "BKSP":
            if buf:
                buf.pop()
            continue
        if name == "BKSP_WORD":
            buf.clear()
            continue
        c = k.get("c")
        if isinstance(c, str) and k.get("src") in (None, "TAP", "LONGPRESS", "POPUP"):
            buf.append(c)
    return "".join(buf)


def label_quality(obj: dict) -> str:
    out = obj["outcome"]
    sig = obj["signals"]
    if sig.get("partial"):
        return "partial"
    if out.get("reverted"):
        return "reverted"
    if sig.get("struggle") or sig.get("capitulation"):
        return "struggle"
    if (sig.get("totalBksp") or 0) > 0:
        return "self_corrected"
    return "clean"


def typing_example(obj: dict, uid: str, day: str, split: str, ts_utc: str | None) -> dict:
    ctx = obj["ctx"]
    lay = obj.get("layout") or {}
    out = obj["outcome"]
    final = out.get("final")
    return {
        "uid": uid,
        "ts": obj["ts"],
        "ts_utc": ts_utc,
        "day": day,
        "split": split,
        "app": ctx.get("app"),
        "inputVariation": ctx.get("inputVariation"),
        "flags": ctx.get("flags"),
        "imeOptions": ctx.get("imeOptions"),
        "register": ctx.get("register"),
        "layout": {k: lay.get(k) for k in ("fp", "alphaW", "alphaH", "baseKeyH", "dpi", "orientation",
                                            "numberRowPresent", "oneHanded")},
        "prev": obj["prev"],
        "keys": [[k.get(c) for c in KEY_COLUMNS] for k in obj["keys"]],
        "typed": replay_typed(obj["keys"]),
        "final": final,
        "intended": final.strip().strip(LETTERS_PUNCT) if isinstance(final, str) else None,
        "route": out.get("route"),
        "autoFrom": out.get("autoFrom"),
        "barIndex": out.get("barIndex"),
        "reverted": bool(out.get("reverted")),
        "revertedTo": out.get("revertedTo"),
        "revertedFrom": out.get("revertedFrom"),
        "commitChar": out.get("commitChar"),
        "preds": obj.get("preds") or [],
        "predKind": out.get("predKind"),
        "predRank": out.get("predRank"),
        "signals": obj["signals"],
        "quality": label_quality(obj),
    }


TYPED_ROUTES = {"TYPED_THROUGH", "AUTO_APPLIED", "BAR_PICK", "ABANDONED", "AUTO_REVERTED"}


def word_input(route: str | None, src: str | None) -> str:
    """How a committed word got into the field: TYPED, GLIDE, VOICE, PASTE, or TYPING_V3 (unknown mix)."""
    if route in TYPED_ROUTES:
        return "TYPED"
    if route in ("GLIDE", "VOICE", "PASTE"):
        return route
    if route == "CLIPBOARD":
        return "PASTE"
    if src == "VOICE":
        return "VOICE"
    return "TYPING_V3"


class Writer:
    """gzip JSONL writer that tracks rows, split counts and the file digest."""

    def __init__(self, path: Path):
        self.path = path
        self.fh = gzip.open(path, "wt", encoding="utf-8", compresslevel=6)
        self.rows = 0
        self.splits: Counter = Counter()

    def write(self, row: dict) -> None:
        self.fh.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")
        self.rows += 1
        if "split" in row:
            self.splits[row["split"]] += 1
        elif "_split" in row:
            self.splits[row["_split"]] += 1

    def close(self) -> dict:
        self.fh.close()
        h = hashlib.sha256()
        with self.path.open("rb") as fh:
            for block in iter(lambda: fh.read(1 << 20), b""):
                h.update(block)
        return {"rows": self.rows, "bytes": self.path.stat().st_size, "sha256": h.hexdigest(),
                "splits": dict(self.splits)}


def verify_streams(store: KeylogStore) -> tuple[dict[str, list[dict]], list[dict]]:
    """Return (good chunks per stream in offset order, problems)."""
    by_stream: dict[str, list[dict]] = defaultdict(list)
    for rec in store.manifest():
        by_stream[rec["stream"]].append(rec)
    good: dict[str, list[dict]] = {}
    problems: list[dict] = []
    for stream, chunks in sorted(by_stream.items()):
        chunks.sort(key=lambda r: r["offset"])
        expect = 0
        ok_chunks: list[dict] = []
        for rec in chunks:
            problem = None
            if rec["offset"] != expect:
                problem = f"tiling: expected offset {expect}, found {rec['offset']}"
            else:
                path = store.root / rec["path"]
                try:
                    gz = path.read_bytes()
                except OSError as exc:
                    problem = f"missing chunk file: {exc}"
                else:
                    if sha256_hex(gz) != rec["gz_sha256"]:
                        problem = "gz_sha256 mismatch"
                    else:
                        try:
                            data = gzip.decompress(gz)
                        except (OSError, EOFError) as exc:
                            problem = f"gzip error: {exc}"
                        else:
                            if sha256_hex(data) != rec["sha256"] or len(data) != rec["length"]:
                                problem = "content sha256/length mismatch"
            if problem:
                problems.append({"stream": stream, "offset": rec["offset"], "problem": problem})
                break
            ok_chunks.append(rec)
            expect = rec["offset"] + rec["length"]
        good[stream] = ok_chunks
    return good, problems


def git_rev() -> str | None:
    try:
        return subprocess.run(["git", "-C", str(Path(__file__).parent), "rev-parse", "--short", "HEAD"],
                              capture_output=True, text=True, timeout=5).stdout.strip() or None
    except Exception:
        return None


def build(store: KeylogStore, verify_only: bool = False, promote: bool = True) -> dict:
    started = time.time()
    good, problems = verify_streams(store)
    integrity = {
        "streams": {s: {"chunks": len(c), "bytes": sum(r["length"] for r in c)} for s, c in good.items()},
        "problems": problems,
        "ok": not problems,
    }
    if verify_only:
        return {"integrity": integrity}

    build_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    builds = store.derived / "builds"
    builds.mkdir(parents=True, exist_ok=True)
    base, n = build_id, 1
    while (builds / build_id).exists() or (builds / f".{build_id}.tmp").exists():
        build_id = f"{base}-{n}"
        n += 1
    tmp_dir = builds / f".{build_id}.tmp"
    out_dir = builds / build_id
    tmp_dir.mkdir(parents=True, exist_ok=False)

    writers = {name: Writer(tmp_dir / f"{name}.jsonl.gz")
               for name in ("events", "typing_slots", "words_unjoined", "quarantine")}
    slot_route: dict[str, str] = {}
    corrections: list[dict] = []
    undone: dict[str, str] = {}

    seen: set[bytes] = set()
    seen_ids: set[tuple[str, int]] = set()
    stats = Counter()
    by_type: Counter = Counter()
    by_version: Counter = Counter()
    errors: Counter = Counter()
    quality: Counter = Counter()
    days: Counter = Counter()
    tz_source: Counter = Counter()
    first_ts = last_ts = None

    for stream, chunks in sorted(good.items()):
        for rec in chunks:
            data = gzip.decompress((store.root / rec["path"]).read_bytes())
            chunk_tz = zone_for(rec.get("device_tz"))
            for raw in io.BytesIO(data):
                raw = raw.rstrip(b"\n")
                if not raw:
                    continue
                stats["lines"] += 1
                key = hashlib.blake2b(raw, digest_size=16).digest()
                if key in seen:
                    stats["duplicate_lines"] += 1
                    continue
                seen.add(key)
                try:
                    obj = json.loads(raw)
                    problems_ = schema.validate(obj)
                except (ValueError, UnicodeDecodeError):
                    obj, problems_ = None, ["invalid_json"]
                if problems_:
                    stats["quarantined"] += 1
                    errors.update(problems_)
                    writers["quarantine"].write({
                        "stream": stream, "chunk_offset": rec["offset"], "errors": problems_,
                        "line": raw.decode("utf-8", "replace")[:20000],
                    })
                    continue

                ev_id = obj["id"]
                if (stream, ev_id) in seen_ids:
                    stats["id_collisions"] += 1
                seen_ids.add((stream, ev_id))
                uid = f"{stream}:{ev_id}"
                day = obj["ts"][:10]
                split = split_for_day(day)
                ts_utc = to_utc(obj["ts"], chunk_tz)
                tz_source["phone_reported_zone" if chunk_tz else ("assumed_device_tz" if ts_utc else "none")] += 1
                typ = obj["type"]
                by_type[typ] += 1
                by_version[obj["v"]] += 1
                days[day] += 1
                first_ts = obj["ts"] if first_ts is None or obj["ts"] < first_ts else first_ts
                last_ts = obj["ts"] if last_ts is None or obj["ts"] > last_ts else last_ts

                obj["_uid"], obj["_stream"], obj["_day"], obj["_split"], obj["_ts_utc"] = uid, stream, day, split, ts_utc
                writers["events"].write(obj)

                if typ == "WORD_SLOT":
                    slot_route[f"{stream}:{obj['slot']}"] = obj["outcome"]["route"]
                if typ == "WORD_SLOT" and schema.is_typing_slot(obj):
                    ex = typing_example(obj, uid, day, split, ts_utc)
                    quality[ex["quality"]] += 1
                    writers["typing_slots"].write(ex)
                elif typ == "WORD_COMMITTED":
                    writers["words_unjoined"].write({
                        "uid": uid, "stream": stream, "ts": obj["ts"], "day": day, "split": split, "sess": obj["sess"],
                        "app": obj.get("app"), "word": obj["word"], "src": obj.get("src"), "slot": obj.get("slot"),
                    })
                elif typ == "AUTO_APPLIED":
                    corrections.append({
                        "uid": uid, "ts": obj["ts"], "day": day, "split": split, "app": obj.get("app"),
                        "typed": obj["typed"], "applied": obj["applied"], "prev": obj.get("prev"),
                        "prev2": obj.get("prev2"), "auto": obj.get("auto"), "candidates": obj.get("candidates"),
                        "shadow": f"{stream}:{obj['shadow']}" if obj.get("shadow") else None,
                        "slot": obj.get("slot"),
                    })
                elif typ == "REVERTED" and obj.get("undoes"):
                    undone[f"{stream}:{obj['undoes']}"] = uid

    cw = Writer(tmp_dir / "corrections.jsonl.gz")
    for row in corrections:
        row["reverted"] = row["uid"] in undone
        row["reverted_by"] = undone.get(row["uid"])
        cw.write(row)

    files = {name: w.close() for name, w in writers.items()}

    # WORD_COMMITTED is written before its WORD_SLOT closes, so the route that says
    # how the word really arrived (typed, pasted, dictated, picked) is joined here.
    # Before v4 (2026-09) there is no slot: ``input`` then falls back to the v3
    # ``src``, where TYPING still includes pasted text.
    unjoined = writers.pop("words_unjoined").path
    files.pop("words_unjoined")
    ww = Writer(tmp_dir / "words.jsonl.gz")
    with gzip.open(unjoined, "rt", encoding="utf-8") as fh:
        for line in fh:
            row = json.loads(line)
            stream_ = row.pop("stream")
            route = slot_route.get(f"{stream_}:{row['slot']}") if row.get("slot") else None
            row["route"] = route
            row["input"] = word_input(route, row.get("src"))
            ww.write(row)
    unjoined.unlink()
    files["words"] = ww.close()
    files["corrections"] = cw.close()
    files = {f"{k}.jsonl.gz": v for k, v in files.items()}

    valid = stats["lines"] - stats["duplicate_lines"]
    invalid_fraction = stats["quarantined"] / valid if valid else 0.0
    gates = {
        "integrity_ok": integrity["ok"],
        "invalid_fraction": round(invalid_fraction, 6),
        "invalid_fraction_ok": invalid_fraction <= MAX_INVALID_FRACTION,
        "has_typing_slots": files["typing_slots.jsonl.gz"]["rows"] > 0,
    }
    gates["passed"] = all(v for k, v in gates.items() if k.endswith("_ok") or k == "has_typing_slots")

    card = {
        "dataset": "sam-keylog",
        "build_id": build_id,
        "built_at": now_iso(),
        "build_seconds": round(time.time() - started, 1),
        "tool_rev": git_rev(),
        "schema": schema.SCHEMA_VERSION,
        "source_root": str(store.root),
        "integrity": integrity,
        "gates": gates,
        "counts": {
            "lines_read": stats["lines"],
            "duplicate_lines_dropped": stats["duplicate_lines"],
            "quarantined": stats["quarantined"],
            "id_collisions": stats["id_collisions"],
            "events_by_type": dict(by_type.most_common()),
            "events_by_version": {str(k): v for k, v in sorted(by_version.items())},
            "typing_slot_quality": dict(quality.most_common()),
            "corrections_reverted": sum(1 for r in corrections if r["uid"] in undone),
            "days": len(days),
        },
        "validation_errors": dict(errors.most_common(50)),
        "time_range_local": [first_ts, last_ts],
        "timezone": {
            "ts": "device local time, no offset (as written by the phone)",
            "ts_utc_source": dict(tz_source),
            "assumed_device_tz": str(DEVICE_TZ) if DEVICE_TZ else None,
        },
        "split_rule": "sha256(YYYY-MM-DD)[0] % 20: 0-1 test, 2-3 val, else train; whole days stay together",
        "split_days": dict(Counter(split_for_day(d) for d in days)),
        "typing_slot_key_columns": KEY_COLUMNS,
        "files": files,
    }
    (tmp_dir / "DATASET.json").write_text(json.dumps(card, indent=2, ensure_ascii=False))
    tmp_dir.rename(out_dir)

    if gates["passed"] and promote:
        current = store.derived / "current"
        link_tmp = store.derived / ".current.tmp"
        if link_tmp.is_symlink() or link_tmp.exists():
            link_tmp.unlink()
        link_tmp.symlink_to(Path("builds") / build_id)
        link_tmp.replace(current)
        card["promoted"] = True
    else:
        card["promoted"] = False
    (store.derived / "last_build.json").write_text(json.dumps(
        {k: card[k] for k in ("build_id", "built_at", "gates", "promoted", "counts", "time_range_local")}, indent=2))
    prune_builds(store)
    return card


def prune_builds(store: KeylogStore) -> None:
    builds = store.derived / "builds"
    current = (store.derived / "current").resolve() if (store.derived / "current").exists() else None
    dirs = sorted(d for d in builds.iterdir() if d.is_dir() and not d.name.startswith("."))
    for old in dirs[:-KEEP_BUILDS]:
        if current and old.resolve() == current:
            continue
        shutil.rmtree(old, ignore_errors=True)
    for stale in builds.glob(".*.tmp"):
        if time.time() - stale.stat().st_mtime > 6 * 3600:
            shutil.rmtree(stale, ignore_errors=True)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--root", type=Path, default=None)
    ap.add_argument("--verify-only", action="store_true")
    ap.add_argument("--no-promote", action="store_true")
    args = ap.parse_args()
    store = KeylogStore(args.root)
    store.ensure_dirs()
    result = build(store, verify_only=args.verify_only, promote=not args.no_promote)
    if args.verify_only:
        print(json.dumps(result, indent=2))
        return 0 if result["integrity"]["ok"] else 1
    print(json.dumps({k: result[k] for k in ("build_id", "gates", "promoted", "counts")}, indent=2))
    return 0 if result["gates"]["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Backfill the keylog archive from a harvest file that already exists on disk.

Feeds a local ``usage_harvest.jsonl`` (plain or ``.gz``, e.g. a manual
snapshot) through exactly the same ``KeylogStore.ingest`` path the receiver
uses, so a backfilled stream is indistinguishable from one the phone uploaded.
Because the stream id is derived from the file's first line, a snapshot of the
phone's file lands in the *same* stream the phone will later resume, and the
phone continues from the snapshot's end instead of re-sending it.

Resumable and idempotent: it starts at the stream's committed offset and checks
that the bytes before that offset match the stored tail anchor.

    python3 import_file.py ~/omniboard-harvest/snap-20261112/usage_harvest.jsonl.gz
"""

from __future__ import annotations

import argparse
import gzip
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from keylog_store import ANCHOR_BYTES, ChunkRejected, KeylogStore, anchor_for, sha256_hex, stream_id_for_head  # noqa: E402


def open_any(path: Path):
    return gzip.open(path, "rb") if path.suffix == ".gz" else path.open("rb")


def import_file(path: Path, store: KeylogStore, chunk_bytes: int, source: str, dry_run: bool = False) -> dict:
    with open_any(path) as fh:
        first = fh.readline()
    if not first.endswith(b"\n"):
        raise SystemExit("file has no complete first line")
    stream = stream_id_for_head(first)
    state = store.public_state(stream)
    committed = state["committed_offset"]
    print(f"stream {stream}: committed offset {committed}")

    stats = {"stream": stream, "start_offset": committed, "chunks": 0, "bytes": 0, "lines": 0}
    with open_any(path) as fh:
        # Skip (streaming, works for gzip) to the committed offset, keeping the
        # tail so the anchor can be checked.
        tail = b""
        remaining = committed
        while remaining > 0:
            block = fh.read(min(remaining, 8 * 1024 * 1024))
            if not block:
                raise SystemExit(f"file is shorter ({committed - remaining} bytes) than the committed offset")
            tail = (tail + block)[-ANCHOR_BYTES:]
            remaining -= len(block)
        if committed and state.get("tail_anchor_sha256") and anchor_for(tail) != state["tail_anchor_sha256"]:
            raise SystemExit("file diverges from the archived stream before the committed offset; refusing")

        offset = committed
        carry = b""
        while True:
            block = fh.read(chunk_bytes)
            buf = carry + block
            if not buf:
                break
            cut = buf.rfind(b"\n") + 1
            if cut == 0:
                if not block:
                    print(f"stopping at incomplete final line ({len(buf)} bytes)")
                    break
                carry = buf
                continue
            data, carry = buf[:cut], buf[cut:]
            if dry_run:
                receipt = {"lines": data.count(b"\n"), "committed_offset": offset + len(data)}
            else:
                try:
                    receipt = store.ingest(
                        stream=stream, offset=offset, data=data, claimed_sha256=sha256_hex(data),
                        anchor_sha256=anchor_for(tail) if offset else None, source=source,
                    )
                except ChunkRejected as exc:
                    raise SystemExit(f"rejected at offset {offset}: {exc.message}")
            tail = (tail + data)[-ANCHOR_BYTES:]
            offset = receipt["committed_offset"]
            stats["chunks"] += 1
            stats["bytes"] += len(data)
            stats["lines"] += receipt.get("lines", 0)
            if not block:
                if carry:
                    print(f"stopping at incomplete final line ({len(carry)} bytes)")
                break
    stats["end_offset"] = offset
    return stats


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("path", type=Path)
    ap.add_argument("--root", type=Path, default=None)
    ap.add_argument("--chunk-mb", type=float, default=4.0)
    ap.add_argument("--source", default=None, help="provenance label (default import:<filename>)")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()
    store = KeylogStore(args.root)
    store.ensure_dirs()
    stats = import_file(
        args.path, store, int(args.chunk_mb * 1024 * 1024),
        args.source or f"import:{args.path.parent.name}/{args.path.name}", args.dry_run,
    )
    print(stats)
    return 0


if __name__ == "__main__":
    sys.exit(main())

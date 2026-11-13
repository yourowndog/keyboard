"""On-disk store for the OmniBoard keylog archive on Titan.

The phone appends to one ``usage_harvest.jsonl`` file and never rewrites it. The
archive mirrors that file as a series of byte ranges ("chunks"), so the whole
contract reduces to one question: *how many bytes of this file does Titan hold?*

Layout under ``ROOT`` (default ``~/datasets/sam-keylog``)::

    raw/<stream>/<offset:015d>.jsonl.gz   exact bytes [offset, offset+length), gzipped
    streams/<stream>.json                 committed offset, tail anchor, counters
    manifests/chunks.jsonl                append-only ledger, one line per chunk
    derived/                              built datasets (see build_dataset.py)

A *stream* is one physical harvest file, identified by the SHA-256 of its first
line. If Sam deletes the file the keyboard starts a new one with a new first
line, which becomes a new stream; nothing is ever spliced across files.

Integrity rules, enforced here so that the receiver and the backfill importer
cannot disagree:

* A chunk is accepted only at exactly the committed offset. Anything else is a
  gap or an overlap and is refused with the committed offset, so the client
  resynchronises instead of guessing.
* The client states the SHA-256 of the uncompressed bytes; Titan recomputes it
  from what it actually decompressed and refuses a mismatch. The phone advances
  its own offset only when the receipt carries the same digest.
* A chunk must end on a newline, so no JSON line is ever split across chunks.
* The first chunk of a stream must hash its own first line to the stream id.
* An optional anchor (SHA-256 of up to 4 KiB immediately before the offset)
  detects a file that was rewritten in place with the same first line.

Validation of individual lines is recorded but never blocks storage: raw data is
the irreplaceable half, and schema problems are handled at build time by
quarantining lines, not by losing them.

Standard library only, matching the voice receiver.
"""

from __future__ import annotations

import base64
import fcntl
import gzip
import hashlib
import json
import os
import re
from contextlib import contextmanager
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path

STREAM_RE = re.compile(r"^[0-9a-f]{16}$")
ANCHOR_BYTES = 4096
MAX_CHUNK_BYTES = 64 * 1024 * 1024


def default_root() -> Path:
    return Path(os.environ.get("KEYLOG_ARCHIVE_ROOT", Path.home() / "datasets" / "sam-keylog"))


def sha256_hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def stream_id_for_head(first_line: bytes) -> str:
    """Stream id = first 16 hex chars of SHA-256 of the first line, newline included."""
    return sha256_hex(first_line)[:16]


def now_iso() -> str:
    return datetime.now().astimezone().isoformat(timespec="seconds")


class ChunkRejected(Exception):
    """A chunk the store will not accept. ``status`` maps onto an HTTP code."""

    def __init__(self, status: int, message: str, committed_offset: int | None = None):
        super().__init__(message)
        self.status = status
        self.message = message
        self.committed_offset = committed_offset


@dataclass
class LineSummary:
    lines: int = 0
    invalid_json: int = 0
    first_id: int | None = None
    last_id: int | None = None
    first_ts: str | None = None
    last_ts: str | None = None
    id_regressions: int = 0
    types: dict[str, int] = field(default_factory=dict)


def summarise_lines(data: bytes) -> LineSummary:
    """Cheap structural pass over a chunk: JSON-parseable, id order, type counts."""
    out = LineSummary()
    prev_id: int | None = None
    for raw in data.split(b"\n"):
        if not raw:
            continue
        out.lines += 1
        try:
            obj = json.loads(raw)
        except (ValueError, UnicodeDecodeError):
            out.invalid_json += 1
            continue
        if not isinstance(obj, dict):
            out.invalid_json += 1
            continue
        ev_id = obj.get("id")
        ts = obj.get("ts")
        typ = obj.get("type")
        if isinstance(typ, str):
            out.types[typ] = out.types.get(typ, 0) + 1
        if isinstance(ev_id, int) and not isinstance(ev_id, bool):
            if out.first_id is None:
                out.first_id = ev_id
            if prev_id is not None and ev_id <= prev_id:
                out.id_regressions += 1
            prev_id = ev_id
            out.last_id = ev_id
        if isinstance(ts, str):
            if out.first_ts is None:
                out.first_ts = ts
            out.last_ts = ts
    return out


class KeylogStore:
    def __init__(self, root: Path | None = None):
        self.root = Path(root) if root else default_root()
        self.raw = self.root / "raw"
        self.streams = self.root / "streams"
        self.manifests = self.root / "manifests"
        self.manifest_file = self.manifests / "chunks.jsonl"
        self.derived = self.root / "derived"

    def ensure_dirs(self) -> None:
        for d in (self.raw, self.streams, self.manifests, self.derived):
            d.mkdir(parents=True, exist_ok=True)

    # ---- stream state -------------------------------------------------

    def _state_path(self, stream: str) -> Path:
        return self.streams / f"{stream}.json"

    def stream_state(self, stream: str) -> dict:
        if not STREAM_RE.match(stream):
            raise ChunkRejected(400, "bad stream id")
        path = self._state_path(stream)
        if not path.exists():
            return {"stream": stream, "committed_offset": 0, "chunks": 0, "lines": 0}
        return json.loads(path.read_text())

    def all_streams(self) -> list[dict]:
        if not self.streams.exists():
            return []
        out = []
        for path in sorted(self.streams.glob("*.json")):
            try:
                out.append(json.loads(path.read_text()))
            except ValueError:
                continue
        return out

    @contextmanager
    def _locked(self, stream: str):
        self.ensure_dirs()
        lock_path = self.streams / f".{stream}.lock"
        with lock_path.open("a") as fh:
            fcntl.flock(fh.fileno(), fcntl.LOCK_EX)
            try:
                yield
            finally:
                fcntl.flock(fh.fileno(), fcntl.LOCK_UN)

    # ---- ingest -------------------------------------------------------

    def ingest(
        self,
        *,
        stream: str,
        offset: int,
        data: bytes,
        claimed_sha256: str,
        anchor_sha256: str | None = None,
        source: str = "phone",
        utc_offset_minutes: int | None = None,
        gz_payload: bytes | None = None,
    ) -> dict:
        """Store ``data`` as bytes [offset, offset+len) of ``stream``.

        Returns a receipt dict. Raises :class:`ChunkRejected` for anything the
        client must react to (gap, overlap, digest mismatch, diverged file).
        """
        if not STREAM_RE.match(stream):
            raise ChunkRejected(400, "bad stream id")
        if offset < 0:
            raise ChunkRejected(400, "negative offset")
        if not data:
            raise ChunkRejected(400, "empty chunk")
        if len(data) > MAX_CHUNK_BYTES:
            raise ChunkRejected(413, "chunk too large")
        digest = sha256_hex(data)
        if digest != claimed_sha256.lower():
            raise ChunkRejected(422, f"sha256 mismatch: received {digest}")
        if not data.endswith(b"\n"):
            raise ChunkRejected(400, "chunk does not end on a line boundary")
        if offset == 0:
            first_line = data[: data.index(b"\n") + 1]
            if stream_id_for_head(first_line) != stream:
                raise ChunkRejected(400, "first line does not hash to stream id")

        with self._locked(stream):
            state = self.stream_state(stream)
            committed = int(state.get("committed_offset", 0))

            if offset < committed:
                existing = self.raw / stream / f"{offset:015d}.jsonl.gz"
                if existing.exists() and _gz_digest(existing) == digest:
                    return {
                        "ok": True, "stream": stream, "sha256": digest, "bytes": len(data),
                        "committed_offset": committed, "duplicate": True,
                    }
                raise ChunkRejected(409, "offset already committed", committed)
            if offset > committed:
                raise ChunkRejected(409, "offset gap", committed)

            if anchor_sha256 and committed > 0:
                expected = state.get("tail_anchor_sha256")
                if expected and expected != anchor_sha256.lower():
                    state["diverged_attempts"] = int(state.get("diverged_attempts", 0)) + 1
                    state["last_divergence_at"] = now_iso()
                    self._write_state(stream, state)
                    raise ChunkRejected(409, "stream diverged: bytes before offset differ", committed)

            summary = summarise_lines(data)
            gz = gz_payload if gz_payload is not None else gzip.compress(data, compresslevel=6, mtime=0)
            target = self.raw / stream / f"{offset:015d}.jsonl.gz"
            _write_atomic(target, gz)

            prev_tail = base64.b64decode(state.get("tail_bytes_b64", "")) if committed else b""
            tail = (prev_tail + data)[-ANCHOR_BYTES:]
            new_committed = committed + len(data)

            record = {
                "stream": stream,
                "offset": offset,
                "length": len(data),
                "end": new_committed,
                "sha256": digest,
                "gz_sha256": sha256_hex(gz),
                "path": str(target.relative_to(self.root)),
                "lines": summary.lines,
                "invalid_json": summary.invalid_json,
                "id_regressions": summary.id_regressions,
                "first_id": summary.first_id,
                "last_id": summary.last_id,
                "first_ts": summary.first_ts,
                "last_ts": summary.last_ts,
                "types": summary.types,
                "source": source,
                "utc_offset_minutes": utc_offset_minutes,
                "received_at": now_iso(),
            }
            self._append_manifest(record)

            state.update({
                "stream": stream,
                "committed_offset": new_committed,
                "chunks": int(state.get("chunks", 0)) + 1,
                "lines": int(state.get("lines", 0)) + summary.lines,
                "invalid_json": int(state.get("invalid_json", 0)) + summary.invalid_json,
                "first_ts": state.get("first_ts") or summary.first_ts,
                "last_ts": summary.last_ts or state.get("last_ts"),
                "last_id": summary.last_id if summary.last_id is not None else state.get("last_id"),
                "tail_anchor_sha256": sha256_hex(tail),
                "tail_bytes_b64": base64.b64encode(tail).decode(),
                "updated_at": record["received_at"],
                "last_source": source,
            })
            state.setdefault("created_at", record["received_at"])
            self._write_state(stream, state)

        return {
            "ok": True, "stream": stream, "sha256": digest, "bytes": len(data),
            "committed_offset": new_committed, "duplicate": False, "lines": summary.lines,
            "invalid_json": summary.invalid_json,
        }

    def _write_state(self, stream: str, state: dict) -> None:
        _write_atomic(self._state_path(stream), json.dumps(state, indent=1, sort_keys=True).encode())

    def _append_manifest(self, record: dict) -> None:
        self.manifests.mkdir(parents=True, exist_ok=True)
        line = json.dumps(record, ensure_ascii=False, sort_keys=True)
        with self.manifest_file.open("a", encoding="utf-8") as fh:
            fcntl.flock(fh.fileno(), fcntl.LOCK_EX)
            fh.write(line + "\n")
            fh.flush()
            os.fsync(fh.fileno())
            fcntl.flock(fh.fileno(), fcntl.LOCK_UN)

    def manifest(self) -> list[dict]:
        if not self.manifest_file.exists():
            return []
        out = []
        with self.manifest_file.open(encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if line:
                    out.append(json.loads(line))
        return out

    def public_state(self, stream: str) -> dict:
        state = self.stream_state(stream)
        return {
            "stream": stream,
            "committed_offset": int(state.get("committed_offset", 0)),
            "tail_anchor_sha256": state.get("tail_anchor_sha256"),
            "last_id": state.get("last_id"),
            "last_ts": state.get("last_ts"),
            "chunks": state.get("chunks", 0),
        }


def anchor_for(prefix_tail: bytes) -> str:
    """Anchor the client sends: SHA-256 of up to the last 4 KiB before the offset."""
    return sha256_hex(prefix_tail[-ANCHOR_BYTES:])


def _gz_digest(path: Path) -> str | None:
    try:
        return sha256_hex(gzip.decompress(path.read_bytes()))
    except (OSError, EOFError):
        return None


def _write_atomic(path: Path, payload: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".part")
    with tmp.open("wb") as fh:
        fh.write(payload)
        fh.flush()
        os.fsync(fh.fileno())
    tmp.replace(path)

#!/usr/bin/env python3
"""Keylog archive receiver: the phone-to-Titan half of the keylog pipeline.

Runs on Titan beside voice-archive.service and shares its token file, so one
``ARCHIVE_TOKEN`` in the build's ``local.properties`` authorises both.

Contract with the client (``HarvestShipper`` on the phone)::

  GET  /v1/keylog/health
       200 {"ok": true, "service": "keylog-archive", "streams": [...]}

  GET  /v1/keylog/streams/<stream>
       200 {"stream", "committed_offset", "tail_anchor_sha256", "last_id", ...}
       How a freshly installed keyboard (which has lost its local cursor)
       learns where to resume instead of re-uploading 800 MB.

  POST /v1/keylog/chunk
       body: gzip of bytes [offset, offset+n) of usage_harvest.jsonl
       headers:
         X-Archive-Token        shared secret
         X-Keylog-Stream        16 hex: sha256(first line of the file)[:16]
         X-Keylog-Offset        byte offset of the chunk in the file
         X-Keylog-Sha256        sha256 of the *uncompressed* bytes
         X-Keylog-Anchor        optional sha256 of the <=4 KiB before offset
         X-Keylog-Utc-Offset    optional device UTC offset in minutes
       200 {"ok": true, "sha256", "committed_offset", "duplicate"}
       409 {"ok": false, "error", "committed_offset"}  resync to that offset
       4xx otherwise; the client keeps its cursor and retries later.

The client advances its cursor only when the receipt's sha256 equals its own.

Standard library only. See docs/autocorrect/keylog-pipeline.md.
"""

from __future__ import annotations

import json
import logging
import os
import re
import socket
import sys
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from keylog_store import MAX_CHUNK_BYTES, ChunkRejected, KeylogStore  # noqa: E402

TOKEN_FILE = Path(os.environ.get("KEYLOG_ARCHIVE_TOKEN_FILE", "/etc/voice-archive/token"))
BIND_HOST = os.environ.get("KEYLOG_ARCHIVE_HOST", "127.0.0.1")
BIND_PORT = int(os.environ.get("KEYLOG_ARCHIVE_PORT", "8791"))
MAX_BODY = 32 * 1024 * 1024
STREAM_PATH = re.compile(r"^/v1/keylog/streams/([0-9a-f]{16})$")

STORE = KeylogStore()


def load_token() -> str | None:
    try:
        return TOKEN_FILE.read_text().strip() or None
    except OSError:
        return None


def gunzip_bounded(payload: bytes, limit: int) -> bytes:
    """Decompress, refusing anything that inflates beyond ``limit`` bytes."""
    d = zlib.decompressobj(16 + zlib.MAX_WBITS)
    out = d.decompress(payload, limit + 1)
    if len(out) > limit or d.unconsumed_tail:
        raise ValueError("decompressed chunk too large")
    if not d.eof:
        raise ValueError("truncated gzip stream")
    return out


class Handler(BaseHTTPRequestHandler):
    server_version = "KeylogArchive/1.0"
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt: str, *args) -> None:
        logging.info("%s - %s", self.client_address[0], fmt % args)

    def _json(self, code: int, payload: dict, close: bool = False) -> None:
        body = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        if close:
            self.send_header("Connection", "close")
            self.close_connection = True
        self.end_headers()
        self.wfile.write(body)

    def _authorised(self) -> bool:
        expected = load_token()
        if not expected:
            return True
        supplied = self.headers.get("X-Archive-Token", "")
        if len(supplied) != len(expected):
            return False
        mismatch = 0
        for a, b in zip(supplied, expected):
            mismatch |= ord(a) ^ ord(b)
        return mismatch == 0

    def do_GET(self) -> None:
        if self.path == "/v1/keylog/health":
            streams = [
                {k: s.get(k) for k in ("stream", "committed_offset", "lines", "chunks", "last_ts", "updated_at",
                                       "diverged_attempts")}
                for s in STORE.all_streams()
            ]
            self._json(200, {"ok": True, "service": "keylog-archive", "root": str(STORE.root), "streams": streams})
            return
        m = STREAM_PATH.match(self.path)
        if m:
            if not self._authorised():
                self._json(401, {"ok": False, "error": "bad token"})
                return
            self._json(200, {"ok": True, **STORE.public_state(m.group(1))})
            return
        self._json(404, {"ok": False, "error": "not found"})

    def do_POST(self) -> None:
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            self._json(400, {"ok": False, "error": "bad content-length"}, close=True)
            return
        if length <= 0 or length > MAX_BODY:
            self._json(413, {"ok": False, "error": "bad or oversized payload"}, close=True)
            return
        # Always drain the body before answering. Replying early on a keep-alive
        # connection leaves the body in the socket, where it is parsed as the
        # next request (the voice receiver's "Bad request syntax" log spam).
        body = self.rfile.read(length)
        if len(body) != length:
            self._json(400, {"ok": False, "error": "truncated body"}, close=True)
            return

        if self.path != "/v1/keylog/chunk":
            self._json(404, {"ok": False, "error": "not found"})
            return
        if not self._authorised():
            self._json(401, {"ok": False, "error": "bad token"})
            return

        h = self.headers
        stream = h.get("X-Keylog-Stream", "")
        claimed = h.get("X-Keylog-Sha256", "")
        try:
            offset = int(h.get("X-Keylog-Offset", ""))
        except ValueError:
            self._json(400, {"ok": False, "error": "bad X-Keylog-Offset"})
            return
        utc_offset = None
        if h.get("X-Keylog-Utc-Offset"):
            try:
                utc_offset = int(h["X-Keylog-Utc-Offset"])
                if not -16 * 60 <= utc_offset <= 16 * 60:
                    utc_offset = None
            except ValueError:
                utc_offset = None

        try:
            data = gunzip_bounded(body, MAX_CHUNK_BYTES)
        except (ValueError, zlib.error, OSError, EOFError) as exc:
            self._json(400, {"ok": False, "error": f"bad gzip: {exc}"})
            return

        try:
            receipt = STORE.ingest(
                stream=stream,
                offset=offset,
                data=data,
                claimed_sha256=claimed,
                anchor_sha256=h.get("X-Keylog-Anchor") or None,
                source="phone",
                utc_offset_minutes=utc_offset,
                gz_payload=body,
            )
        except ChunkRejected as exc:
            payload = {"ok": False, "error": exc.message}
            if exc.committed_offset is not None:
                payload["committed_offset"] = exc.committed_offset
            logging.warning("rejected chunk stream=%s offset=%s: %s", stream, offset, exc.message)
            self._json(exc.status, payload)
            return
        except OSError as exc:
            logging.exception("write failed")
            self._json(500, {"ok": False, "error": f"write failed: {exc}"})
            return

        logging.info(
            "stored stream=%s offset=%d bytes=%d lines=%s dup=%s",
            stream, offset, receipt["bytes"], receipt.get("lines"), receipt["duplicate"],
        )
        self._json(200, receipt)


class Server(ThreadingHTTPServer):
    daemon_threads = True
    address_family = socket.AF_INET
    allow_reuse_address = True


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    STORE.ensure_dirs()
    if not load_token():
        logging.warning("no token at %s - accepting unauthenticated uploads", TOKEN_FILE)
    server = Server((BIND_HOST, BIND_PORT), Handler)
    logging.info("keylog-archive listening on %s:%d, root=%s", BIND_HOST, BIND_PORT, STORE.root)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    return 0


if __name__ == "__main__":
    sys.exit(main())

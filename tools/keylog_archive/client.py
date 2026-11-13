#!/usr/bin/env python3
"""Reference client for the keylog archive, and an adb fallback shipper.

Speaks exactly the protocol ``HarvestShipper.kt`` speaks, so it doubles as the
end-to-end test of a live receiver and as a way to ship the harvest when the
installed APK predates the shipper (or has no ARCHIVE_TOKEN).

The source is either a local file or the phone itself through any command that
runs a shell on it, so only the missing bytes cross the network::

    # local copy
    python3 client.py --file usage_harvest.jsonl

    # straight off the phone through sleeper's adb (run on Titan)
    python3 client.py --device-cmd 'ssh sleeper ~/.local/bin/icarion-shell'

The token is read from ``--token-file`` (default /etc/voice-archive/token).
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import shlex
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

ANCHOR_BYTES = 4096
DEVICE_PATH = "/sdcard/Documents/usage_harvest.jsonl"


class LocalSource:
    def __init__(self, path: Path):
        self.path = path

    def length(self) -> int:
        return self.path.stat().st_size

    def read(self, start: int, end: int) -> bytes:
        with self.path.open("rb") as fh:
            fh.seek(start)
            return fh.read(end - start)


class DeviceSource:
    """Reads byte ranges of the phone's file through ``<cmd> '<shell command>'``."""

    def __init__(self, cmd: str, path: str = DEVICE_PATH):
        self.cmd = shlex.split(cmd)
        self.path = path

    def _run(self, shell: str) -> bytes:
        # ssh joins its arguments and the remote shell parses them again, so the
        # device command needs one extra level of quoting to arrive as one argument.
        arg = shlex.quote(shell) if Path(self.cmd[0]).name == "ssh" else shell
        return subprocess.run(self.cmd + [arg], check=True, capture_output=True, timeout=600).stdout

    def length(self) -> int:
        return int(self._run(f"stat -c %s {self.path}").strip())

    def read(self, start: int, end: int) -> bytes:
        n = end - start
        data = self._run(f"tail -c +{start + 1} {self.path} | head -c {n}")
        if len(data) != n:
            raise IOError(f"short read from device: wanted {n}, got {len(data)}")
        return data


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def request(url: str, token: str, data: bytes | None = None, headers: dict | None = None) -> tuple[int, dict]:
    req = urllib.request.Request(url, data=data, method="POST" if data is not None else "GET",
                                 headers={"X-Archive-Token": token, "Accept": "application/json", **(headers or {})})
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            return resp.status, json.loads(resp.read())
    except urllib.error.HTTPError as err:
        return err.code, json.loads(err.read() or b"{}")


def ship(source, base_url: str, token: str, chunk_bytes: int, max_bytes: int | None, tz: str | None) -> dict:
    length = source.length()
    head = source.read(0, min(length, 1 << 20))
    first = head[: head.index(b"\n") + 1]
    stream = sha(first)[:16]
    code, state = request(f"{base_url}/streams/{stream}", token)
    if code != 200:
        raise SystemExit(f"stream state failed: {code} {state}")
    offset = state["committed_offset"]
    if offset > length:
        raise SystemExit("archive holds more than the source file")
    if offset and state.get("tail_anchor_sha256"):
        if sha(source.read(max(0, offset - ANCHOR_BYTES), offset)) != state["tail_anchor_sha256"]:
            raise SystemExit(f"source diverges from the archive before offset {offset}")
    print(f"stream {stream}: archive has {offset} of {length} bytes")
    sent = 0
    resyncs = 0
    while offset < length and (max_bytes is None or sent < max_bytes):
        buf = source.read(offset, min(length, offset + chunk_bytes))
        cut = buf.rfind(b"\n") + 1
        if cut == 0:
            print("no complete line left; stopping")
            break
        data = buf[:cut]
        headers = {
            "X-Keylog-Stream": stream, "X-Keylog-Offset": str(offset), "X-Keylog-Sha256": sha(data),
            "Content-Type": "application/gzip",
        }
        if offset:
            headers["X-Keylog-Anchor"] = sha(source.read(max(0, offset - ANCHOR_BYTES), offset))
        if tz:
            headers["X-Keylog-Tz"] = tz
        code, body = request(f"{base_url}/chunk", token, gzip.compress(data), headers)
        if code == 200 and body.get("sha256") == sha(data):
            offset = body["committed_offset"]
            sent += len(data)
            print(f"  stored {len(data)} bytes, {body.get('lines')} lines -> {offset}")
        elif code == 409 and "committed_offset" in body and "diverged" not in body.get("error", ""):
            resyncs += 1
            if resyncs > 3:
                raise SystemExit("too many resyncs")
            offset = body["committed_offset"]
        else:
            raise SystemExit(f"upload failed at {offset}: {code} {body}")
    return {"stream": stream, "offset": offset, "length": length, "sent": sent}


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    src = ap.add_mutually_exclusive_group(required=True)
    src.add_argument("--file", type=Path)
    src.add_argument("--device-cmd", help="command prefix that runs one shell command on the phone")
    ap.add_argument("--url", default="http://100.104.232.94:8791/v1/keylog")
    ap.add_argument("--token-file", type=Path, default=Path("/etc/voice-archive/token"))
    ap.add_argument("--chunk-mb", type=float, default=2.0)
    ap.add_argument("--max-mb", type=float, default=None)
    ap.add_argument("--tz", default="America/Chicago", help="device zone sent with each chunk")
    args = ap.parse_args()
    token = args.token_file.read_text().strip()
    source = LocalSource(args.file) if args.file else DeviceSource(args.device_cmd)
    result = ship(source, args.url.rstrip("/"), token, int(args.chunk_mb * 1024 * 1024),
                  int(args.max_mb * 1024 * 1024) if args.max_mb else None, args.tz)
    print(json.dumps(result))
    return 0


if __name__ == "__main__":
    sys.exit(main())

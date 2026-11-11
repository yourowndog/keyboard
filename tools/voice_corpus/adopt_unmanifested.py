#!/usr/bin/env python3
"""Append recoverable raw audio/sidecar pairs missing from the corpus manifest."""

from __future__ import annotations

import argparse
import fcntl
import json
import os
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

from segment_verbatim import AUDIO_SUFFIXES, read_json, read_jsonl, sha256_file


CAPTURE_NAME = re.compile(r"whisper_(\d{10,16})", re.IGNORECASE)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("/home/sam/datasets/sam-voice"))
    parser.add_argument("--timezone", default="America/Chicago")
    parser.add_argument("--apply", action="store_true")
    return parser.parse_args()


def probe(path: Path) -> dict[str, Any]:
    result = subprocess.run(
        [
            "ffprobe",
            "-v",
            "error",
            "-show_entries",
            "format=duration:stream=sample_rate,channels",
            "-of",
            "json",
            str(path),
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    value = json.loads(result.stdout)
    stream = value["streams"][0]
    return {
        "duration_ms": round(float(value["format"]["duration"]) * 1000),
        "sample_rate": int(stream["sample_rate"]),
        "channels": int(stream["channels"]),
    }


def capture_epoch(path: Path, sidecar: dict[str, Any]) -> int:
    match = CAPTURE_NAME.search(path.stem)
    if match:
        return int(match.group(1))
    for key in ("captured_epoch_ms", "timestamp"):
        value = sidecar.get(key)
        if isinstance(value, int) and not isinstance(value, bool):
            return value
    raise ValueError(f"cannot determine capture time: {path}")


def build_row(root: Path, audio: Path, tz: ZoneInfo) -> dict[str, Any]:
    sidecar_path = audio.with_suffix(".json")
    if not sidecar_path.is_file():
        raise ValueError(f"paired sidecar missing: {audio}")
    sidecar = read_json(sidecar_path)
    digest = sha256_file(audio)
    epoch_ms = capture_epoch(audio, sidecar)
    captured = datetime.fromtimestamp(epoch_ms / 1000, timezone.utc).astimezone(tz)
    offset = captured.utcoffset()
    offset_minutes = round(offset.total_seconds() / 60) if offset is not None else None
    ident = f"{captured.strftime('%Y%m%dT%H%M%S%z')}_{digest[:6]}"
    info = probe(audio)
    capture = sidecar.get("capture") if isinstance(sidecar.get("capture"), dict) else {}
    archive = sidecar.get("archive") if isinstance(sidecar.get("archive"), dict) else {}
    transcript = sidecar.get("transcript_display") or sidecar.get("transcript")
    return {
        "id": ident,
        "captured_at": captured.isoformat(),
        "captured_epoch_ms": epoch_ms,
        "captured_utc_offset_minutes": offset_minutes,
        "raw_audio": str(audio.relative_to(root)),
        "raw_sidecar": str(sidecar_path.relative_to(root)),
        "sha256": digest,
        "bytes": audio.stat().st_size,
        "duration_ms": info["duration_ms"],
        "sample_rate": info["sample_rate"],
        "audio_source": capture.get("audio_source"),
        "transcript_display": transcript,
        "transcript_raw": sidecar.get("transcript_raw") or sidecar.get("transcript"),
        "source_filename": archive.get("source_filename") or audio.name,
        "ingested_at": archive.get("ingested_at")
        or datetime.fromtimestamp(audio.stat().st_mtime, timezone.utc).isoformat(),
        "adopted_from_unmanifested": True,
    }


def main() -> int:
    args = parse_args()
    root = args.root.resolve()
    raw = (root / "raw").resolve()
    manifest = root / "manifests" / "utterances.jsonl"
    rows = read_jsonl(manifest)
    paths = {row.get("raw_audio") for row in rows}
    hashes = {row.get("sha256") for row in rows}
    candidates: list[dict[str, Any]] = []
    for audio in sorted(path for path in raw.rglob("*") if path.suffix.lower() in AUDIO_SUFFIXES):
        relative = str(audio.relative_to(root))
        digest = sha256_file(audio)
        if relative in paths or digest in hashes:
            continue
        candidates.append(build_row(root, audio, ZoneInfo(args.timezone)))

    summary = {
        "schema": "sam-voice-manifest-adoption-summary/1",
        "candidates": len(candidates),
        "ids": [row["id"] for row in candidates],
        "applied": bool(args.apply),
    }
    if args.apply and candidates:
        with manifest.open("a+", encoding="utf-8") as stream:
            fcntl.flock(stream.fileno(), fcntl.LOCK_EX)
            stream.seek(0)
            current = [json.loads(line) for line in stream if line.strip()]
            current_paths = {row.get("raw_audio") for row in current}
            current_hashes = {row.get("sha256") for row in current}
            append = [
                row
                for row in candidates
                if row["raw_audio"] not in current_paths and row["sha256"] not in current_hashes
            ]
            stream.seek(0, os.SEEK_END)
            for row in append:
                stream.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")
            stream.flush()
            os.fsync(stream.fileno())
            summary["appended"] = len(append)
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

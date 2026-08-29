#!/usr/bin/env python3
"""Create immutable CrisperWhisper verbatim annotations for incomplete corpus rows.

Raw audio and raw sidecars are never changed. Each successful result is written atomically to
``annotations/<recipe>/<source-id>.json`` and is never overwritten. Re-running the same recipe
therefore resumes safely. Long inputs are converted to temporary 16 kHz PCM and divided at the
quietest 100 ms window before a 20-second hard boundary, matching OmniBoard's transport policy.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import uuid
import wave
from array import array
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from segment_verbatim import lexical_tokens, read_json, read_jsonl, render_words, sha256_file


SAMPLE_RATE = 16_000
MAX_CHUNK_SECONDS = 20
CORE_CHUNK_SECONDS = 19
OVERLAP_SECONDS = 0.5
SEARCH_SECONDS = 5
WINDOW_SAMPLES = SAMPLE_RATE // 10
SIMPLE_NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]*")


@dataclass(frozen=True)
class UploadChunk:
    path: Path
    offset: float
    core_start: float
    core_end: float


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("/home/sam/datasets/sam-voice"))
    parser.add_argument("--recipe", default="crisper2-large-v1")
    parser.add_argument(
        "--endpoint",
        default="http://100.104.232.94:8791/v1/audio/transcriptions",
    )
    parser.add_argument("--credential-file", type=Path)
    parser.add_argument("--max-sources", type=int)
    parser.add_argument("--apply", action="store_true")
    return parser.parse_args()


def credential_path(args: argparse.Namespace) -> Path | None:
    if args.credential_file:
        return args.credential_file
    directory = os.environ.get("CREDENTIALS_DIRECTORY")
    return Path(directory) / "relay-token" if directory else None


def load_token(args: argparse.Namespace) -> str:
    path = credential_path(args)
    token = path.read_text(encoding="utf-8").strip() if path else os.environ.get(
        "WHISPER_RELAY_TOKEN", ""
    ).strip()
    if not token:
        raise ValueError("no relay credential supplied")
    return token


def source_is_complete(sidecar: dict[str, Any]) -> bool:
    text = sidecar.get("transcript_verbatim")
    engine = sidecar.get("engine")
    response = engine.get("response") if isinstance(engine, dict) else None
    words = response.get("verbatim_words") if isinstance(response, dict) else None
    if not isinstance(text, str) or not text.strip() or not isinstance(words, list) or not words:
        return False
    rendered = " ".join(
        str(word.get("word", "")).strip()
        for word in words
        if isinstance(word, dict) and str(word.get("word", "")).strip()
    )
    return lexical_tokens(text) == lexical_tokens(rendered)


def patch_placeholder_header(path: Path) -> None:
    size = path.stat().st_size
    if size <= 44 or (size - 44) % 2:
        raise ValueError("WAV has no recoverable PCM payload")
    with path.open("r+b") as stream:
        header = stream.read(44)
        if any(header):
            raise ValueError("unrecognized audio; refusing derived header repair")
        pcm_bytes = size - 44
        repaired = struct.pack(
            "<4sI4s4sIHHIIHH4sI",
            b"RIFF",
            size - 8,
            b"WAVE",
            b"fmt ",
            16,
            1,
            1,
            48_000,
            96_000,
            2,
            16,
            b"data",
            pcm_bytes,
        )
        stream.seek(0)
        stream.write(repaired)
        stream.flush()
        os.fsync(stream.fileno())


def prepare_16k(source: Path, work: Path) -> Path:
    ffmpeg_source = source
    probe = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", str(source)],
        capture_output=True,
    )
    if probe.returncode != 0:
        repaired = work / "repaired-source.wav"
        shutil.copyfile(source, repaired)
        patch_placeholder_header(repaired)
        ffmpeg_source = repaired

    output = work / "prepared-16k.wav"
    subprocess.run(
        [
            "ffmpeg",
            "-nostdin",
            "-v",
            "error",
            "-y",
            "-i",
            str(ffmpeg_source),
            "-map_metadata",
            "-1",
            "-ac",
            "1",
            "-ar",
            str(SAMPLE_RATE),
            "-c:a",
            "pcm_s16le",
            str(output),
        ],
        check=True,
    )
    return output


def quiet_boundary(samples: array[int], start: int, target: int) -> int:
    lower = max(start + WINDOW_SAMPLES, target - SEARCH_SECONDS * SAMPLE_RATE)
    upper = min(target - WINDOW_SAMPLES // 2, len(samples) - WINDOW_SAMPLES)
    if upper <= lower:
        return target
    best = target
    best_energy: int | None = None
    for position in range(lower, upper + 1, WINDOW_SAMPLES):
        energy = sum(abs(value) for value in samples[position : position + WINDOW_SAMPLES])
        if best_energy is None or energy < best_energy:
            best_energy = energy
            best = position + WINDOW_SAMPLES // 2
    return best


def split_uploads(prepared: Path, work: Path) -> list[UploadChunk]:
    with wave.open(str(prepared), "rb") as source:
        if source.getnchannels() != 1 or source.getsampwidth() != 2 or source.getframerate() != SAMPLE_RATE:
            raise ValueError("prepared upload is not mono 16-bit 16 kHz PCM")
        frames = source.readframes(source.getnframes())
    samples = array("h")
    samples.frombytes(frames)
    if sys.byteorder != "little":
        samples.byteswap()

    maximum = CORE_CHUNK_SECONDS * SAMPLE_RATE
    boundaries = [0]
    start = 0
    while len(samples) - start > maximum:
        remaining = len(samples) - start
        target = start + (remaining // 2 if remaining < maximum * 2 else maximum)
        boundary = quiet_boundary(samples, start, target)
        if boundary <= start or boundary - start > maximum:
            raise ValueError("could not find safe ASR transport boundary")
        boundaries.append(boundary)
        start = boundary
    boundaries.append(len(samples))

    uploads: list[UploadChunk] = []
    overlap = round(OVERLAP_SECONDS * SAMPLE_RATE)
    for index, (left, right) in enumerate(zip(boundaries, boundaries[1:])):
        path = work / f"chunk-{index:04d}.wav"
        actual_left = max(0, left - overlap)
        actual_right = min(len(samples), right + overlap)
        chunk = samples[actual_left:actual_right]
        if sys.byteorder != "little":
            chunk.byteswap()
        with wave.open(str(path), "wb") as output:
            output.setnchannels(1)
            output.setsampwidth(2)
            output.setframerate(SAMPLE_RATE)
            output.writeframes(chunk.tobytes())
        if len(chunk) > MAX_CHUNK_SECONDS * SAMPLE_RATE:
            raise ValueError("overlapped ASR transport chunk exceeds hard limit")
        uploads.append(
            UploadChunk(
                path=path,
                offset=actual_left / SAMPLE_RATE,
                core_start=left / SAMPLE_RATE,
                core_end=right / SAMPLE_RATE,
            )
        )
    return uploads


def multipart(audio: Path) -> tuple[bytes, str]:
    boundary = f"----sam-voice-{uuid.uuid4().hex}"
    payload = audio.read_bytes()
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{audio.name}"\r\n'
        "Content-Type: audio/wav\r\n\r\n"
    ).encode() + payload + f"\r\n--{boundary}--\r\n".encode()
    return body, f"multipart/form-data; boundary={boundary}"


def transcribe(endpoint: str, token: str, audio: Path) -> dict[str, Any]:
    body, content_type = multipart(audio)
    request = urllib.request.Request(
        endpoint,
        data=body,
        headers={"Authorization": f"Bearer {token}", "Content-Type": content_type},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=600) as response:
            value = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        message = exc.read().decode("utf-8", "replace")[:300]
        raise RuntimeError(f"Crisper HTTP {exc.code}: {message}") from exc
    if not isinstance(value, dict):
        raise ValueError("Crisper response is not a JSON object")
    return value


def join_text(values: list[str]) -> str:
    return " ".join(value.strip() for value in values if value.strip())


def merge_responses(parts: list[tuple[UploadChunk, dict[str, Any]]]) -> dict[str, Any]:
    if not parts:
        raise ValueError("no Crisper responses")
    words: list[dict[str, Any]] = []
    verbatim_words: list[dict[str, Any]] = []
    segments: list[dict[str, Any]] = []

    def shifted_words(values: Any, chunk: UploadChunk, final: bool) -> list[dict[str, Any]]:
        output: list[dict[str, Any]] = []
        if not isinstance(values, list):
            return output
        for value in values:
            if not isinstance(value, dict):
                continue
            item = dict(value)
            for key in ("start", "end"):
                if isinstance(item.get(key), (int, float)):
                    item[key] = round(float(item[key]) + chunk.offset, 3)
            start = item.get("start")
            end = item.get("end")
            if not isinstance(start, (int, float)) or not isinstance(end, (int, float)):
                continue
            midpoint = (float(start) + float(end)) / 2.0
            if midpoint < chunk.core_start or midpoint > chunk.core_end:
                continue
            if not final and midpoint == chunk.core_end:
                continue
            output.append(item)
        return output

    for index, (chunk, response) in enumerate(parts):
        final = index == len(parts) - 1
        words.extend(shifted_words(response.get("words"), chunk, final))
        verbatim_words.extend(shifted_words(response.get("verbatim_words"), chunk, final))
        segments.extend(response.get("segments") if isinstance(response.get("segments"), list) else [])
    if not verbatim_words:
        raise ValueError("Crisper returned no verbatim transcript or word timing")
    verbatim_text = " ".join(
        str(word.get("word") or "").strip() for word in verbatim_words
    ).strip()
    intended_text = " ".join(str(word.get("word") or "").strip() for word in words).strip()
    first = parts[0][1]
    return {
        "task": "transcribe",
        "language": first.get("language"),
        "duration": round(parts[-1][0].core_end, 3),
        "text": intended_text,
        "words": words,
        "segments": segments,
        "provider": first.get("provider", "titan"),
        "model": first.get("model", "CrisperWhisper"),
        "verbatim_text": verbatim_text,
        "verbatim_words": verbatim_words,
        "chunked": len(parts) > 1,
        "chunks": [
            {
                "index": index,
                "offset_seconds": chunk.offset,
                "core_start_seconds": chunk.core_start,
                "core_end_seconds": chunk.core_end,
                "response": response,
            }
            for index, (chunk, response) in enumerate(parts)
        ],
    }


def write_atomic_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.part-{os.getpid()}")
    with temporary.open("w", encoding="utf-8") as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2, sort_keys=True)
        stream.write("\n")
        stream.flush()
        os.fsync(stream.fileno())
    temporary.replace(path)


def validate_existing(path: Path, source_id: str, source_sha: str) -> None:
    value = read_json(path)
    parent = value.get("parent")
    if (
        value.get("schema") != "sam-voice-verbatim-annotation/1"
        or value.get("id") != source_id
        or not isinstance(parent, dict)
        or parent.get("sha256") != source_sha
    ):
        raise ValueError(f"existing annotation does not match source: {path}")


def main() -> int:
    args = parse_args()
    if not SIMPLE_NAME.fullmatch(args.recipe):
        raise ValueError("recipe must be a simple directory name")
    if args.max_sources is not None and args.max_sources <= 0:
        raise ValueError("max-sources must be positive")
    root = args.root.resolve()
    raw_root = (root / "raw").resolve()
    rows = read_jsonl(root / "manifests" / "utterances.jsonl")
    annotation_root = root / "annotations" / args.recipe

    candidates: list[tuple[dict[str, Any], Path, Path, str]] = []
    already_annotated = 0
    complete_in_raw = 0
    for row in rows:
        source_id = str(row.get("id") or "")
        source = (root / str(row.get("raw_audio"))).resolve()
        sidecar = (root / str(row.get("raw_sidecar"))).resolve()
        source.relative_to(raw_root)
        sidecar.relative_to(raw_root)
        if not source.is_file() or not sidecar.is_file():
            continue
        source_sha = sha256_file(source)
        if source_sha != row.get("sha256"):
            raise ValueError(f"source hash mismatch: {source_id}")
        annotation = annotation_root / f"{source_id}.json"
        if annotation.exists():
            validate_existing(annotation, source_id, source_sha)
            already_annotated += 1
            continue
        if source_is_complete(read_json(sidecar)):
            complete_in_raw += 1
            continue
        candidates.append((row, source, sidecar, source_sha))

    if args.max_sources is not None:
        candidates = candidates[: args.max_sources]
    plan = {
        "schema": "sam-voice-verbatim-backfill-summary/1",
        "recipe": args.recipe,
        "source_rows": len(rows),
        "complete_in_raw": complete_in_raw,
        "already_annotated": already_annotated,
        "candidates": len(candidates),
        "applied": bool(args.apply),
    }
    if not args.apply:
        print(json.dumps(plan, indent=2, sort_keys=True))
        return 0

    token = load_token(args)
    failures: list[dict[str, str]] = []
    generated = 0
    started = time.monotonic()
    for index, (row, source, sidecar, source_sha) in enumerate(candidates, 1):
        source_id = str(row["id"])
        print(f"[{index}/{len(candidates)}] {source_id}", file=sys.stderr, flush=True)
        try:
            with tempfile.TemporaryDirectory(prefix=f"voice-backfill-{source_id}-") as temporary:
                work = Path(temporary)
                prepared = prepare_16k(source, work)
                uploads = split_uploads(prepared, work)
                parts = [(chunk, transcribe(args.endpoint, token, chunk.path)) for chunk in uploads]
                try:
                    response = merge_responses(parts)
                except Exception:
                    write_atomic_json(
                        annotation_root / "diagnostics" / f"{source_id}.json",
                        {
                            "schema": "sam-voice-verbatim-diagnostic/1",
                            "recipe": args.recipe,
                            "id": source_id,
                            "parent_sha256": source_sha,
                            "chunks": [
                                {
                                    "offset_seconds": chunk.offset,
                                    "core_start_seconds": chunk.core_start,
                                    "core_end_seconds": chunk.core_end,
                                    "response": response,
                                }
                                for chunk, response in parts
                            ],
                        },
                    )
                    raise
                value = {
                    "schema": "sam-voice-verbatim-annotation/1",
                    "recipe": args.recipe,
                    "id": source_id,
                    "created_at": datetime.now(timezone.utc).isoformat(),
                    "parent": {
                        "raw_audio": str(source.relative_to(root)),
                        "raw_sidecar": str(sidecar.relative_to(root)),
                        "sha256": source_sha,
                        "raw_sidecar_sha256": sha256_file(sidecar),
                    },
                    "transcript_verbatim": response["verbatim_text"],
                    "engine": {
                        "provider": response.get("provider", "titan"),
                        "model": response.get("model", "CrisperWhisper"),
                        "response": response,
                    },
                    "transport": {
                        "sample_rate": SAMPLE_RATE,
                        "max_chunk_seconds": MAX_CHUNK_SECONDS,
                        "core_chunk_seconds": CORE_CHUNK_SECONDS,
                        "overlap_seconds": OVERLAP_SECONDS,
                        "chunks": len(parts),
                        "boundary_policy": "quietest-100ms-core-with-overlap-below-20s-hard-limit",
                    },
                }
                write_atomic_json(annotation_root / f"{source_id}.json", value)
                generated += 1
        except Exception as exc:  # keep successful annotations and make the batch resumable
            failures.append({"id": source_id, "error": str(exc)[:500]})
            print(f"FAILED {source_id}: {exc}", file=sys.stderr, flush=True)

    plan.update(
        {
            "generated": generated,
            "failures": len(failures),
            "elapsed_seconds": round(time.monotonic() - started, 3),
        }
    )
    reports = annotation_root / "reports"
    write_atomic_json(reports / "summary.json", plan)
    write_atomic_json(reports / "failures.json", {"failures": failures})
    print(json.dumps(plan, indent=2, sort_keys=True))
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Build a trainer-neutral, verbatim-only segmented view of Sam's voice corpus.

The source ``raw/`` tree is immutable. This tool reads the canonical source
manifest and Schema 2 sidecars, then writes a new versioned recipe under
``derived/``. A source is eligible only when it has a verbatim transcript and
verbatim word timing. Long takes are cut only between timed words; no audio is
discarded from an eligible take, including pauses before, between, or after
words.

Run without ``--apply`` for a read-only plan. An applied recipe is created in a
staging directory and atomically renamed into place. Existing recipes are never
overwritten: choose a new recipe name after changing labels or segmentation.
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
import tempfile
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


AUDIO_SUFFIXES = {".wav", ".mp4", ".m4a", ".flac", ".aac"}
PUNCTUATION_ONLY = re.compile(r"^[^\w\s]+$")


@dataclass(frozen=True)
class Word:
    text: str
    start: float
    end: float


@dataclass(frozen=True)
class Segment:
    index: int
    start: float
    end: float
    word_start: int
    word_end: int

    @property
    def duration(self) -> float:
        return self.end - self.start


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--root",
        type=Path,
        default=Path("/home/sam/datasets/sam-voice"),
        help="Corpus root containing raw/, manifests/, and derived/.",
    )
    parser.add_argument(
        "--recipe",
        default="segments-v1",
        help="New directory name under derived/. Existing recipes are refused.",
    )
    parser.add_argument(
        "--annotation-recipe",
        help=(
            "Optional immutable verbatim overlay under annotations/<name>/. "
            "An annotation for a source takes precedence over its raw sidecar."
        ),
    )
    parser.add_argument("--target-seconds", type=float, default=24.0)
    parser.add_argument("--max-seconds", type=float, default=30.0)
    parser.add_argument("--min-seconds", type=float, default=3.0)
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Encode audio and atomically publish the recipe. Default is plan-only.",
    )
    parser.add_argument(
        "--details",
        action="store_true",
        help="Include awaiting/rejected/unmanifested rows in the printed plan.",
    )
    return parser.parse_args()


def validate_args(args: argparse.Namespace) -> None:
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*", args.recipe):
        raise ValueError("recipe must be a simple directory name")
    if args.annotation_recipe and not re.fullmatch(
        r"[A-Za-z0-9][A-Za-z0-9._-]*", args.annotation_recipe
    ):
        raise ValueError("annotation-recipe must be a simple directory name")
    if args.min_seconds <= 0:
        raise ValueError("min-seconds must be positive")
    if not args.min_seconds < args.target_seconds < args.max_seconds:
        raise ValueError("require min-seconds < target-seconds < max-seconds")


def read_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"expected JSON object: {path}")
    return value


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open(encoding="utf-8") as stream:
        for line_number, line in enumerate(stream, 1):
            if not line.strip():
                continue
            value = json.loads(line)
            if not isinstance(value, dict):
                raise ValueError(f"expected object at {path}:{line_number}")
            rows.append(value)
    return rows


def write_json(path: Path, value: Any) -> None:
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def write_jsonl(path: Path, rows: Iterable[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8") as stream:
        for row in rows:
            stream.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def ffprobe(path: Path) -> dict[str, Any]:
    result = subprocess.run(
        [
            "ffprobe",
            "-v",
            "error",
            "-show_entries",
            "format=duration:stream=codec_name,sample_rate,channels,sample_fmt",
            "-of",
            "json",
            str(path),
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    value = json.loads(result.stdout)
    streams = value.get("streams")
    duration = float(value["format"]["duration"])
    if not isinstance(streams, list) or not streams or duration <= 0:
        raise ValueError(f"no playable audio stream: {path}")
    stream = streams[0]
    return {
        "duration": duration,
        "codec": stream.get("codec_name"),
        "sample_rate": int(stream.get("sample_rate") or 0),
        "channels": int(stream.get("channels") or 0),
        "sample_fmt": stream.get("sample_fmt"),
        "placeholder_header": False,
    }


def probe_source_audio(path: Path) -> dict[str, Any]:
    try:
        return ffprobe(path)
    except subprocess.CalledProcessError:
        if path.suffix.lower() != ".wav" or path.stat().st_size <= 44:
            raise
        with path.open("rb") as stream:
            header = stream.read(44)
        pcm_bytes = path.stat().st_size - 44
        if any(header) or pcm_bytes % 2:
            raise
        return {
            "duration": pcm_bytes / 96_000.0,
            "codec": "pcm_s16le",
            "sample_rate": 48_000,
            "channels": 1,
            "sample_fmt": "s16",
            "placeholder_header": True,
        }


def repaired_placeholder_copy(source: Path, target: Path) -> None:
    shutil.copyfile(source, target)
    size = target.stat().st_size
    pcm_bytes = size - 44
    header = struct.pack(
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
    with target.open("r+b") as stream:
        stream.write(header)
        stream.flush()
        os.fsync(stream.fileno())


def resolve_source(root: Path, raw_root: Path, relative: Any) -> Path:
    if not isinstance(relative, str) or not relative:
        raise ValueError("manifest source path is missing")
    source = (root / relative).resolve()
    try:
        source.relative_to(raw_root)
    except ValueError as exc:
        raise ValueError(f"source escapes immutable raw tree: {relative}") from exc
    return source


def parse_words(sidecar: dict[str, Any], duration: float) -> list[Word]:
    engine = sidecar.get("engine")
    response = engine.get("response") if isinstance(engine, dict) else None
    values = response.get("verbatim_words") if isinstance(response, dict) else None
    if not isinstance(values, list) or not values:
        raise ValueError("missing_verbatim_words")

    words: list[Word] = []
    previous_start = -1.0
    for value in values:
        if not isinstance(value, dict):
            raise ValueError("invalid_verbatim_word")
        text = value.get("word")
        start = value.get("start")
        end = value.get("end")
        if not isinstance(text, str) or not text.strip():
            raise ValueError("invalid_verbatim_word_text")
        if not isinstance(start, (int, float)) or not isinstance(end, (int, float)):
            raise ValueError("invalid_verbatim_word_time")
        start_f = float(start)
        end_f = float(end)
        # CrisperWhisper can assign a point timestamp to very short fillers or
        # punctuation-adjacent words. Preserve those tokens; only negative
        # duration or backwards ordering is invalid.
        if start_f < 0 or end_f < start_f or start_f < previous_start:
            raise ValueError("non_monotonic_verbatim_words")
        if end_f > duration + 0.25:
            raise ValueError("verbatim_word_exceeds_audio")
        words.append(Word(text.strip(), start_f, min(end_f, duration)))
        previous_start = start_f
    return words


def safe_boundaries(words: list[Word]) -> list[tuple[int, float, float]]:
    """Return (next_word_index, boundary_seconds, silence_gap_seconds)."""
    boundaries: list[tuple[int, float, float]] = []
    for index in range(1, len(words)):
        left = words[index - 1]
        right = words[index]
        if right.start < left.end:
            continue
        gap = right.start - left.end
        boundaries.append((index, left.end + gap / 2.0, gap))
    return boundaries


def plan_segments(
    words: list[Word],
    duration: float,
    *,
    minimum: float,
    target: float,
    maximum: float,
) -> list[Segment]:
    if duration < minimum:
        raise ValueError("audio_shorter_than_minimum")
    if duration <= maximum:
        return [Segment(0, 0.0, duration, 0, len(words))]

    boundaries = safe_boundaries(words)
    segments: list[Segment] = []
    segment_start = 0.0
    word_start = 0

    while duration - segment_start > maximum:
        candidates: list[tuple[float, int, float, float]] = []
        for next_word, boundary, gap in boundaries:
            if next_word <= word_start:
                continue
            segment_duration = boundary - segment_start
            remaining = duration - boundary
            if not (minimum <= segment_duration <= maximum):
                continue
            if remaining < minimum:
                continue
            # Prefer a natural pause, then closeness to the target. A two-second
            # pause may move the cut several seconds away from the target.
            score = abs(segment_duration - target) - min(gap, 2.0) * 3.0
            candidates.append((score, next_word, boundary, gap))
        if not candidates:
            raise ValueError("no_safe_boundary_below_maximum")
        _, next_word, boundary, _ = min(candidates, key=lambda item: (item[0], item[2]))
        segments.append(
            Segment(len(segments), segment_start, boundary, word_start, next_word)
        )
        segment_start = boundary
        word_start = next_word

    tail = Segment(len(segments), segment_start, duration, word_start, len(words))
    if tail.duration < minimum or tail.duration > maximum:
        raise ValueError("invalid_tail_duration")
    segments.append(tail)

    if segments[0].start != 0.0 or abs(segments[-1].end - duration) > 1e-6:
        raise AssertionError("segment plan does not cover source")
    for left, right in zip(segments, segments[1:]):
        if abs(left.end - right.start) > 1e-6:
            raise AssertionError("segment plan has a gap or overlap")
    return segments


def render_words(words: list[Word]) -> str:
    rendered = ""
    for word in words:
        if not rendered:
            rendered = word.text
        elif PUNCTUATION_ONLY.fullmatch(word.text):
            rendered += word.text
        else:
            rendered += " " + word.text
    return rendered.strip()


def lexical_tokens(text: str) -> list[str]:
    """Compare transcript content while ignoring punctuation/casing differences."""
    return re.findall(r"\w+(?:['’]\w+)?", text.casefold(), flags=re.UNICODE)


def encode_segment(source: Path, output: Path, segment: Segment) -> None:
    subprocess.run(
        [
            "ffmpeg",
            "-nostdin",
            "-v",
            "error",
            "-i",
            str(source),
            "-ss",
            f"{segment.start:.6f}",
            "-t",
            f"{segment.duration:.6f}",
            "-map_metadata",
            "-1",
            "-ac",
            "1",
            "-ar",
            "48000",
            "-c:a",
            "pcm_s16le",
            str(output),
        ],
        check=True,
    )


def verify_segment_audio(path: Path, expected_duration: float) -> dict[str, Any]:
    info = ffprobe(path)
    if info["codec"] != "pcm_s16le" or info["sample_rate"] != 48000 or info["channels"] != 1:
        raise ValueError(f"unexpected derived audio format: {path}")
    if abs(info["duration"] - expected_duration) > 0.075:
        raise ValueError(f"derived duration mismatch: {path}")
    return info


def build(args: argparse.Namespace) -> dict[str, Any]:
    root = args.root.resolve()
    raw_root = (root / "raw").resolve()
    source_manifest = root / "manifests" / "utterances.jsonl"
    derived_root = root / "derived"
    output_root = derived_root / args.recipe
    annotation_root = (
        root / "annotations" / args.annotation_recipe if args.annotation_recipe else None
    )

    if not raw_root.is_dir() or not source_manifest.is_file():
        raise ValueError("corpus root is missing raw/ or manifests/utterances.jsonl")
    if output_root.exists():
        raise FileExistsError(f"recipe already exists; choose a new version: {output_root}")
    if annotation_root is not None and not annotation_root.is_dir():
        raise ValueError(f"annotation recipe does not exist: {annotation_root}")

    rows = read_jsonl(source_manifest)
    plans: list[dict[str, Any]] = []
    awaiting: list[dict[str, Any]] = []
    rejected: list[dict[str, Any]] = []
    manifest_sources: set[str] = set()
    annotations_used = 0

    for row in rows:
        source_id = str(row.get("id") or "unknown")
        source_relative = row.get("raw_audio")
        sidecar_relative = row.get("raw_sidecar")
        if isinstance(source_relative, str):
            manifest_sources.add(source_relative)
        try:
            source = resolve_source(root, raw_root, source_relative)
            sidecar = resolve_source(root, raw_root, sidecar_relative)
            if not source.is_file() or not sidecar.is_file():
                raise ValueError("source_or_sidecar_missing")
            label_path = sidecar
            label_data = read_json(sidecar)
            annotation_path = (
                annotation_root / f"{source_id}.json" if annotation_root is not None else None
            )
            if annotation_path is not None and annotation_path.is_file():
                annotation = read_json(annotation_path)
                if annotation.get("schema") != "sam-voice-verbatim-annotation/1":
                    raise ValueError("invalid_verbatim_annotation_schema")
                if annotation.get("id") != source_id:
                    raise ValueError("verbatim_annotation_id_mismatch")
                parent = annotation.get("parent")
                if not isinstance(parent, dict) or parent.get("sha256") != row.get("sha256"):
                    raise ValueError("verbatim_annotation_parent_mismatch")
                label_path = annotation_path
                label_data = annotation
                annotations_used += 1
            verbatim = label_data.get("transcript_verbatim")
            if not isinstance(verbatim, str) or not verbatim.strip():
                raise ValueError("missing_verbatim_text")
            info = probe_source_audio(source)
            words = parse_words(label_data, info["duration"])
            if lexical_tokens(verbatim) != lexical_tokens(render_words(words)):
                raise ValueError("verbatim_text_word_timing_mismatch")
            segments = plan_segments(
                words,
                info["duration"],
                minimum=args.min_seconds,
                target=args.target_seconds,
                maximum=args.max_seconds,
            )
            source_sha = sha256_file(source)
            expected_sha = row.get("sha256")
            if isinstance(expected_sha, str) and expected_sha and source_sha != expected_sha:
                raise ValueError("source_sha256_mismatch")
            plans.append(
                {
                    "id": source_id,
                    "row": row,
                    "source": source,
                    "sidecar": sidecar,
                    "label_source": label_path,
                    "source_sha256": source_sha,
                    "source_duration": info["duration"],
                    "source_placeholder_header": info["placeholder_header"],
                    "source_transcript_verbatim": verbatim.strip(),
                    "source_transcript_verbatim_sha256": hashlib.sha256(
                        verbatim.strip().encode("utf-8")
                    ).hexdigest(),
                    "words": words,
                    "segments": segments,
                }
            )
        except (OSError, ValueError, subprocess.CalledProcessError, json.JSONDecodeError) as exc:
            reason = str(exc) or type(exc).__name__
            target = awaiting if reason.startswith("missing_verbatim") else rejected
            target.append(
                {
                    "id": source_id,
                    "raw_audio": source_relative,
                    "raw_sidecar": sidecar_relative,
                    "reason": reason,
                }
            )

    unmanifested: list[dict[str, Any]] = []
    for source in sorted(path for path in raw_root.rglob("*") if path.suffix.lower() in AUDIO_SUFFIXES):
        relative = str(source.relative_to(root))
        if relative not in manifest_sources:
            unmanifested.append({"raw_audio": relative, "reason": "not_in_source_manifest"})

    summary = {
        "schema": "sam-voice-segmentation-summary/1",
        "recipe": args.recipe,
        "parameters": {
            "min_seconds": args.min_seconds,
            "target_seconds": args.target_seconds,
            "max_seconds": args.max_seconds,
            "output_sample_rate": 48000,
            "label_policy": "verbatim-only",
            "boundary_policy": "between-timed-verbatim-words; preserve-complete-audio",
            "annotation_recipe": args.annotation_recipe,
        },
        "source_manifest": str(source_manifest.relative_to(root)),
        "source_rows": len(rows),
        "annotations_used": annotations_used,
        "eligible_sources": len(plans),
        "planned_segments": sum(len(plan["segments"]) for plan in plans),
        "eligible_source_hours": round(
            sum(plan["source_duration"] for plan in plans) / 3600.0, 6
        ),
        "awaiting_verbatim_sources": len(awaiting),
        "awaiting_verbatim_reasons": dict(
            sorted(Counter(row["reason"] for row in awaiting).items())
        ),
        "rejected_sources": len(rejected),
        "rejected_reasons": dict(sorted(Counter(row["reason"] for row in rejected).items())),
        "unmanifested_audio": len(unmanifested),
        "applied": bool(args.apply),
    }
    if args.details:
        summary["awaiting_verbatim"] = awaiting
        summary["rejected"] = rejected
        summary["unmanifested"] = unmanifested

    if not args.apply:
        return summary

    derived_root.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix=f".{args.recipe}.part-", dir=derived_root))
    try:
        audio_dir = staging / "audio"
        sidecar_dir = staging / "sidecars"
        manifest_dir = staging / "manifests"
        report_dir = staging / "reports"
        work_dir = staging / ".work"
        for directory in (audio_dir, sidecar_dir, manifest_dir, report_dir, work_dir):
            directory.mkdir(parents=True, exist_ok=True)

        segment_manifest: list[dict[str, Any]] = []
        for plan in plans:
            words: list[Word] = plan["words"]
            encoding_source = plan["source"]
            if plan["source_placeholder_header"]:
                encoding_source = work_dir / f"{plan['id']}.repaired.wav"
                repaired_placeholder_copy(plan["source"], encoding_source)
            for segment in plan["segments"]:
                segment_id = f"{plan['id']}__s{segment.index:04d}"
                audio_path = audio_dir / f"{segment_id}.wav"
                sidecar_path = sidecar_dir / f"{segment_id}.json"
                encode_segment(encoding_source, audio_path, segment)
                output_info = verify_segment_audio(audio_path, segment.duration)
                output_sha = sha256_file(audio_path)
                segment_words = words[segment.word_start : segment.word_end]
                text = (
                    plan["source_transcript_verbatim"]
                    if len(plan["segments"]) == 1
                    else render_words(segment_words)
                )
                if not text:
                    raise ValueError(f"empty segment transcript: {segment_id}")
                shifted_words = [
                    {
                        "word": word.text,
                        "start": round(max(0.0, word.start - segment.start), 3),
                        "end": round(min(segment.duration, word.end - segment.start), 3),
                    }
                    for word in segment_words
                ]
                segment_relative = Path("derived") / args.recipe / "audio" / audio_path.name
                sidecar_relative = Path("derived") / args.recipe / "sidecars" / sidecar_path.name
                sidecar_value = {
                    "schema": "sam-voice-segment/1",
                    "recipe": args.recipe,
                    "id": segment_id,
                    "parent": {
                        "id": plan["id"],
                        "raw_audio": str(plan["source"].relative_to(root)),
                        "raw_sidecar": str(plan["sidecar"].relative_to(root)),
                        "label_source": str(plan["label_source"].relative_to(root)),
                        "derived_header_repair": plan["source_placeholder_header"],
                        "sha256": plan["source_sha256"],
                        "duration_seconds": round(plan["source_duration"], 6),
                        "transcript_verbatim_sha256": plan[
                            "source_transcript_verbatim_sha256"
                        ],
                    },
                    "segment": {
                        "index": segment.index,
                        "start_seconds": round(segment.start, 6),
                        "end_seconds": round(segment.end, 6),
                        "duration_seconds": round(segment.duration, 6),
                        "word_start_index": segment.word_start,
                        "word_end_index_exclusive": segment.word_end,
                    },
                    "audio": {
                        "path": str(segment_relative),
                        "sha256": output_sha,
                        "bytes": audio_path.stat().st_size,
                        "sample_rate": output_info["sample_rate"],
                        "channels": output_info["channels"],
                        "codec": output_info["codec"],
                    },
                    "transcript_verbatim": text,
                    "verbatim_words": shifted_words,
                }
                write_json(sidecar_path, sidecar_value)
                segment_manifest.append(
                    {
                        "id": segment_id,
                        "audio": str(segment_relative),
                        "sidecar": str(sidecar_relative),
                        "text": text,
                        "duration": round(segment.duration, 6),
                        "parent_id": plan["id"],
                        "parent_start_seconds": round(segment.start, 6),
                        "parent_end_seconds": round(segment.end, 6),
                        "sha256": output_sha,
                    }
                )

        write_jsonl(manifest_dir / "segments.jsonl", segment_manifest)
        write_jsonl(report_dir / "awaiting-verbatim.jsonl", awaiting)
        write_jsonl(report_dir / "rejected.jsonl", rejected)
        write_jsonl(report_dir / "unmanifested-audio.jsonl", unmanifested)
        shutil.rmtree(work_dir)
        summary["generated_segments"] = len(segment_manifest)
        summary["generated_audio_hours"] = round(
            sum(row["duration"] for row in segment_manifest) / 3600.0, 6
        )
        write_json(report_dir / "summary.json", summary)
        staging.replace(output_root)
    except Exception:
        shutil.rmtree(staging, ignore_errors=True)
        raise

    return summary


def main() -> int:
    args = parse_args()
    validate_args(args)
    summary = build(args)
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

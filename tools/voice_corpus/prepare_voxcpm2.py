#!/usr/bin/env python3
"""Build an immutable, trainer-specific VoxCPM2 recipe from verified segments."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import random
import re
import shutil
import tempfile
import wave
from collections import Counter
from pathlib import Path
from typing import Any, Iterable


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("/home/sam/datasets/sam-voice"))
    parser.add_argument("--source-recipe", default="segments-v2")
    parser.add_argument("--recipe", default="voxcpm2-v1-curated")
    parser.add_argument("--edge-padding-seconds", type=float, default=0.4)
    parser.add_argument("--min-seconds", type=float, default=3.0)
    parser.add_argument("--max-seconds", type=float, default=30.0)
    parser.add_argument("--max-clipped-fraction", type=float, default=0.001)
    parser.add_argument("--min-rms", type=float, default=100.0)
    parser.add_argument("--allow-lossy-sources", action="store_true")
    parser.add_argument("--validation-fraction", type=float, default=0.1)
    parser.add_argument("--reference-fraction", type=float, default=0.4)
    parser.add_argument("--seed", type=int, default=20260829)
    parser.add_argument("--apply", action="store_true")
    return parser.parse_args()


def read_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"expected JSON object: {path}")
    return value


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [
        json.loads(line)
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def write_jsonl(path: Path, rows: Iterable[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        "".join(json.dumps(row, sort_keys=True) + "\n" for row in rows),
        encoding="utf-8",
    )


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def audio_metrics(path: Path) -> dict[str, float | int]:
    clipped = 0
    samples = 0
    sum_squares = 0
    with wave.open(str(path), "rb") as stream:
        if (
            stream.getnchannels() != 1
            or stream.getsampwidth() != 2
            or stream.getframerate() != 48_000
            or stream.getcomptype() != "NONE"
        ):
            raise ValueError(f"unsupported source WAV format: {path}")
        while True:
            block = stream.readframes(48_000 * 10)
            if not block:
                break
            values = memoryview(block).cast("h")
            for value in values:
                magnitude = abs(value)
                clipped += magnitude >= 32_767
                sum_squares += value * value
            samples += len(values)
    return {
        "samples": samples,
        "clipped_samples": clipped,
        "clipped_fraction": clipped / samples if samples else 1.0,
        "rms": math.sqrt(sum_squares / samples) if samples else 0.0,
    }


def trainer_bounds(sidecar: dict[str, Any], duration: float, padding: float) -> tuple[float, float]:
    words = sidecar.get("verbatim_words")
    if not isinstance(words, list) or not words:
        raise ValueError("segment has no timed verbatim words")
    first = words[0]
    last = words[-1]
    start = max(0.0, float(first["start"]) - padding)
    end = min(duration, float(last["end"]) + padding)
    if end <= start:
        raise ValueError("invalid trainer trim bounds")
    return start, end


def split_parents(
    rows: list[dict[str, Any]], validation_fraction: float, seed: int
) -> tuple[set[str], set[str]]:
    durations: dict[str, float] = {}
    for row in rows:
        parent = str(row["parent_id"])
        durations[parent] = durations.get(parent, 0.0) + float(row["trainer_duration"])
    parents = sorted(durations)
    random.Random(seed).shuffle(parents)
    target = sum(durations.values()) * validation_fraction
    validation: set[str] = set()
    current = 0.0
    for parent in parents:
        if current >= target and validation:
            break
        validation.add(parent)
        current += durations[parent]
    return set(parents) - validation, validation


def assign_references(
    rows: list[dict[str, Any]], reference_fraction: float, seed: int
) -> None:
    pool = [row for row in rows if 3.0 <= float(row["trainer_duration"]) <= 12.0]
    if not pool and reference_fraction:
        raise ValueError("no eligible reference clips")
    for row in rows:
        chooser = random.Random(f"{seed}:use:{row['id']}")
        if chooser.random() >= reference_fraction:
            continue
        candidates = [item for item in pool if item["parent_id"] != row["parent_id"]]
        if not candidates:
            raise ValueError("no reference clip from a different parent take")
        reference = candidates[random.Random(f"{seed}:ref:{row['id']}").randrange(len(candidates))]
        row["ref_id"] = reference["id"]
        row["ref_duration"] = reference["trainer_duration"]


def encode_slice(source: Path, output: Path, start: float, end: float) -> float:
    with wave.open(str(source), "rb") as input_stream:
        rate = input_stream.getframerate()
        start_frame = round(start * rate)
        end_frame = round(end * rate)
        input_stream.setpos(start_frame)
        frames = input_stream.readframes(end_frame - start_frame)
        with wave.open(str(output), "wb") as output_stream:
            output_stream.setnchannels(1)
            output_stream.setsampwidth(2)
            output_stream.setframerate(rate)
            output_stream.writeframes(frames)
    return (end_frame - start_frame) / rate


def build(args: argparse.Namespace) -> dict[str, Any]:
    root = args.root.resolve()
    source_root = root / "derived" / args.source_recipe
    output_root = root / "derived" / args.recipe
    if output_root.exists():
        raise FileExistsError(f"recipe already exists: {output_root}")

    candidates: list[dict[str, Any]] = []
    rejected: list[dict[str, Any]] = []
    for row in read_jsonl(source_root / "manifests" / "segments.jsonl"):
        source = (root / row["audio"]).resolve()
        sidecar = read_json((root / row["sidecar"]).resolve())
        parent_audio = Path(sidecar["parent"]["raw_audio"])
        start, end = trainer_bounds(sidecar, float(row["duration"]), args.edge_padding_seconds)
        trainer_duration = end - start
        reason = None
        metrics: dict[str, Any] = {}
        if not args.allow_lossy_sources and parent_audio.suffix.lower() != ".wav":
            reason = "legacy_lossy_source"
        elif trainer_duration < args.min_seconds:
            reason = "trimmed_below_minimum"
        elif trainer_duration > args.max_seconds:
            reason = "trimmed_above_maximum"
        else:
            metrics = audio_metrics(source)
            if metrics["clipped_fraction"] > args.max_clipped_fraction:
                reason = "clipped_above_threshold"
            elif metrics["rms"] < args.min_rms:
                reason = "very_low_level"
        if reason:
            rejected.append(
                {
                    "id": row["id"],
                    "parent_id": row["parent_id"],
                    "reason": reason,
                    "trainer_duration": round(trainer_duration, 6),
                    **metrics,
                }
            )
            continue
        candidates.append(
            {
                **row,
                "source": source,
                "sidecar": sidecar,
                "trim_start": start,
                "trim_end": end,
                "trainer_duration": trainer_duration,
                "metrics": metrics,
            }
        )

    train_parents, validation_parents = split_parents(
        candidates, args.validation_fraction, args.seed
    )
    train_rows = [row for row in candidates if row["parent_id"] in train_parents]
    validation_rows = [row for row in candidates if row["parent_id"] in validation_parents]
    assign_references(train_rows, args.reference_fraction, args.seed)

    summary = {
        "schema": "sam-voice-voxcpm2-summary/1",
        "recipe": args.recipe,
        "source_recipe": args.source_recipe,
        "applied": bool(args.apply),
        "parameters": {
            "edge_padding_seconds": args.edge_padding_seconds,
            "min_seconds": args.min_seconds,
            "max_seconds": args.max_seconds,
            "max_clipped_fraction": args.max_clipped_fraction,
            "min_rms": args.min_rms,
            "allow_lossy_sources": args.allow_lossy_sources,
            "validation_fraction": args.validation_fraction,
            "reference_fraction": args.reference_fraction,
            "seed": args.seed,
            "normalization": "none; preserve within-take dynamics",
            "split_policy": "parent-take-disjoint",
        },
        "eligible_segments": len(candidates),
        "eligible_hours": round(sum(row["trainer_duration"] for row in candidates) / 3600, 6),
        "train_segments": len(train_rows),
        "train_hours": round(sum(row["trainer_duration"] for row in train_rows) / 3600, 6),
        "validation_segments": len(validation_rows),
        "validation_hours": round(
            sum(row["trainer_duration"] for row in validation_rows) / 3600, 6
        ),
        "train_parent_takes": len(train_parents),
        "validation_parent_takes": len(validation_parents),
        "reference_conditioned_train_segments": sum("ref_id" in row for row in train_rows),
        "rejected_segments": len(rejected),
        "rejected_reasons": dict(sorted(Counter(row["reason"] for row in rejected).items())),
    }
    if not args.apply:
        return summary

    derived_root = root / "derived"
    staging = Path(tempfile.mkdtemp(prefix=f".{args.recipe}.part-", dir=derived_root))
    try:
        audio_dir = staging / "audio"
        sidecar_dir = staging / "sidecars"
        audio_dir.mkdir(parents=True)
        sidecar_dir.mkdir(parents=True)
        manifest_by_id: dict[str, dict[str, Any]] = {}
        for row in candidates:
            output = audio_dir / f"{row['id']}.wav"
            actual_duration = encode_slice(
                row["source"], output, row["trim_start"], row["trim_end"]
            )
            audio_sha = sha256_file(output)
            relative_audio = Path("derived") / args.recipe / "audio" / output.name
            sidecar_value = {
                "schema": "sam-voice-voxcpm2-sample/1",
                "recipe": args.recipe,
                "id": row["id"],
                "parent_segment": {
                    "id": row["id"],
                    "audio": row["audio"],
                    "sidecar": row["sidecar"],
                    "sha256": row["sha256"],
                    "parent_take_id": row["parent_id"],
                },
                "trim": {
                    "start_seconds": round(row["trim_start"], 6),
                    "end_seconds": round(row["trim_end"], 6),
                    "edge_padding_seconds": args.edge_padding_seconds,
                },
                "audio": {
                    "path": str(relative_audio),
                    "sha256": audio_sha,
                    "duration_seconds": round(actual_duration, 6),
                    "sample_rate": 48_000,
                    "channels": 1,
                    "codec": "pcm_s16le",
                },
                "text": row["text"],
                "quality": row["metrics"],
            }
            write_json(sidecar_dir / f"{row['id']}.json", sidecar_value)
            manifest_by_id[row["id"]] = {
                "audio": str(root / relative_audio),
                "text": row["text"],
                "duration": round(actual_duration, 6),
                "dataset_id": 0,
            }

        def trainer_manifest(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
            output = []
            for row in rows:
                item = dict(manifest_by_id[row["id"]])
                if "ref_id" in row:
                    item["ref_audio"] = manifest_by_id[row["ref_id"]]["audio"]
                    item["ref_duration"] = round(float(row["ref_duration"]), 6)
                output.append(item)
            return output

        write_jsonl(staging / "manifests" / "train.jsonl", trainer_manifest(train_rows))
        write_jsonl(staging / "manifests" / "validation.jsonl", trainer_manifest(validation_rows))
        write_jsonl(staging / "reports" / "rejected.jsonl", rejected)
        write_json(staging / "reports" / "summary.json", summary)
        staging.replace(output_root)
    except Exception:
        shutil.rmtree(staging, ignore_errors=True)
        raise
    return summary


def main() -> int:
    args = parse_args()
    for name, value in (("recipe", args.recipe), ("source-recipe", args.source_recipe)):
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*", value):
            raise ValueError(f"{name} must be a simple directory name")
    if not 0 < args.validation_fraction < 1:
        raise ValueError("validation-fraction must be between zero and one")
    if not 0 <= args.reference_fraction <= 1:
        raise ValueError("reference-fraction must be between zero and one")
    if not 0 <= args.max_clipped_fraction <= 1:
        raise ValueError("max-clipped-fraction must be between zero and one")
    if not 0 <= args.edge_padding_seconds < args.min_seconds < args.max_seconds:
        raise ValueError("require edge-padding < min-seconds < max-seconds")
    summary = build(args)
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

from __future__ import annotations

import hashlib
import json
import math
import struct
import sys
import tempfile
import unittest
import wave
from argparse import Namespace
from pathlib import Path


TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

from backfill_verbatim import MAX_CHUNK_SECONDS, split_uploads, source_is_complete  # noqa: E402
from segment_verbatim import build, probe_source_audio  # noqa: E402


def write_wav(path: Path, seconds: float, sample_rate: int) -> None:
    frames = bytearray()
    for index in range(round(seconds * sample_rate)):
        value = round(math.sin(2 * math.pi * 220 * index / sample_rate) * 1000)
        frames.extend(struct.pack("<h", value))
    with wave.open(str(path), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(sample_rate)
        output.writeframes(frames)


class BackfillToolsTest(unittest.TestCase):
    def test_split_uploads_preserves_all_samples_under_limit(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            prepared = root / "prepared.wav"
            write_wav(prepared, 45.0, 16_000)
            uploads = split_uploads(prepared, root)
            self.assertGreater(len(uploads), 2)
            total = 0
            for path, _ in uploads:
                with wave.open(str(path), "rb") as stream:
                    self.assertLessEqual(stream.getnframes(), MAX_CHUNK_SECONDS * 16_000)
                    total += stream.getnframes()
            with wave.open(str(prepared), "rb") as stream:
                self.assertEqual(total, stream.getnframes())

    def test_complete_sidecar_requires_matching_verbatim_words(self) -> None:
        valid = {
            "transcript_verbatim": "Um, hello.",
            "engine": {
                "response": {
                    "verbatim_words": [
                        {"word": "Um,", "start": 0.0, "end": 0.2},
                        {"word": "hello.", "start": 0.3, "end": 0.8},
                    ]
                }
            },
        }
        self.assertTrue(source_is_complete(valid))
        valid["transcript_verbatim"] = "hello"
        self.assertFalse(source_is_complete(valid))


class SegmentOverlayTest(unittest.TestCase):
    def test_placeholder_header_is_inspected_without_mutating_raw(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "placeholder.wav"
            path.write_bytes(bytes(44) + bytes(96_000))
            before = hashlib.sha256(path.read_bytes()).hexdigest()
            info = probe_source_audio(path)
            self.assertTrue(info["placeholder_header"])
            self.assertEqual(info["duration"], 1.0)
            self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), before)

    def test_annotation_overlay_makes_missing_raw_label_eligible(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            raw = root / "raw" / "2026" / "08" / "28"
            raw.mkdir(parents=True)
            (root / "manifests").mkdir()
            annotation_root = root / "annotations" / "test-v1"
            annotation_root.mkdir(parents=True)
            audio = raw / "sample.wav"
            sidecar = raw / "sample.json"
            write_wav(audio, 5.0, 48_000)
            sidecar.write_text(json.dumps({"transcribed": False}), encoding="utf-8")
            digest = hashlib.sha256(audio.read_bytes()).hexdigest()
            row = {
                "id": "sample",
                "raw_audio": str(audio.relative_to(root)),
                "raw_sidecar": str(sidecar.relative_to(root)),
                "sha256": digest,
            }
            (root / "manifests" / "utterances.jsonl").write_text(
                json.dumps(row) + "\n", encoding="utf-8"
            )
            annotation = {
                "schema": "sam-voice-verbatim-annotation/1",
                "recipe": "test-v1",
                "id": "sample",
                "parent": {"sha256": digest},
                "transcript_verbatim": "Um hello",
                "engine": {
                    "response": {
                        "verbatim_words": [
                            {"word": "Um", "start": 0.5, "end": 0.8},
                            {"word": "hello", "start": 1.0, "end": 1.5},
                        ]
                    }
                },
            }
            (annotation_root / "sample.json").write_text(
                json.dumps(annotation), encoding="utf-8"
            )
            before = hashlib.sha256(audio.read_bytes()).hexdigest()
            summary = build(
                Namespace(
                    root=root,
                    recipe="segments-test",
                    annotation_recipe="test-v1",
                    min_seconds=3.0,
                    target_seconds=24.0,
                    max_seconds=30.0,
                    apply=False,
                    details=False,
                )
            )
            self.assertEqual(summary["eligible_sources"], 1)
            self.assertEqual(summary["annotations_used"], 1)
            self.assertEqual(hashlib.sha256(audio.read_bytes()).hexdigest(), before)


if __name__ == "__main__":
    unittest.main()

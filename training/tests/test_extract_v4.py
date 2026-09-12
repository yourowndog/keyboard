import importlib.util
import json
import sys
import tempfile
import unittest
from collections import Counter
from pathlib import Path


TRAINING = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TRAINING))
SPEC = importlib.util.spec_from_file_location("training_extract", TRAINING / "extract.py")
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class ExtractV4Test(unittest.TestCase):
    def parse_rows(self, rows):
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / "mixed.jsonl"
            source.write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")
            original = MODULE.JSONL_PATH
            MODULE.JSONL_PATH = source
            try:
                stats = Counter()
                result = MODULE.parse_jsonl(stats)
            finally:
                MODULE.JSONL_PATH = original
        return (*result, stats)

    def test_v4_slot_is_consumed_and_dual_write_v3_is_dropped(self):
        slot = {
            "v": 4,
            "type": "WORD_SLOT",
            "slot": "s:1",
            "prev": ["saw"],
            "keys": [
                {"k": "t", "c": "t"},
                {"k": "e", "c": "e"},
                {"k": "x", "c": "x"},
                {"k": "BKSP", "c": None},
                {"k": "h", "c": "h"},
                {"k": "SPACE", "c": " "},
            ],
            "outcome": {
                "route": "AUTO_APPLIED",
                "final": "the",
                "autoFrom": "teh",
                "reverted": False,
            },
        }
        legacy = {
            "v": 3,
            "type": "AUTO_APPLIED",
            "id": 7,
            "slot": "s:1",
            "typed": "teh",
            "applied": "the",
        }
        _corpus, pairs, _negatives, traces, stats = self.parse_rows([slot, legacy])

        self.assertEqual(1, len(pairs))
        self.assertEqual("v4-auto", pairs[0]["src"])
        self.assertEqual("saw", pairs[0]["prev"])
        self.assertEqual(1, len(traces))
        self.assertEqual(1, stats["dual_write_v3_dropped"])

    def test_v4_original_bar_pick_and_return_edit_replace_legacy_labels(self):
        slot = {
            "v": 4,
            "type": "WORD_SLOT",
            "slot": "s:2",
            "prev": ["a"],
            "keys": [],
            "edits": [{"op": "RETURN_EDIT", "before": "teh", "left": "the"}],
            "outcome": {
                "route": "BAR_PICK",
                "final": "the",
                "autoFrom": "the",
                "reverted": False,
            },
        }
        mirrors = [
            {"v": 3, "type": "INSISTED", "slot": "s:2", "word": "the"},
            {"v": 3, "type": "MANUAL_EDIT", "slot": "s:2", "before": "teh", "after": "the"},
        ]

        _corpus, pairs, negatives, traces, stats = self.parse_rows([slot, *mirrors])

        self.assertEqual([], pairs)
        self.assertEqual("v4-bar-pick-original", negatives[0]["src"])
        self.assertEqual("v4-return-edit", traces[0]["src"])
        self.assertEqual(2, stats["dual_write_v3_dropped"])


if __name__ == "__main__":
    unittest.main()

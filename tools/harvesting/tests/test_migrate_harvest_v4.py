import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "migrate_harvest_v4.py"
SPEC = importlib.util.spec_from_file_location("migrate_harvest_v4", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class MigrateHarvestV4Test(unittest.TestCase):
    def run_conversion(self, rows):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        root = Path(temp.name)
        source = root / "raw.jsonl"
        slots = root / "slots.jsonl"
        sessions = root / "sessions.jsonl"
        source.write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")
        stats = MODULE.convert(source, slots, sessions)
        slot_rows = [json.loads(line) for line in slots.read_text(encoding="utf-8").splitlines()]
        session_rows = [json.loads(line) for line in sessions.read_text(encoding="utf-8").splitlines()]
        return stats, slot_rows, session_rows

    def test_v3_auto_word_and_revert_become_one_slot(self):
        rows = [
            {"v": 3, "id": 1, "sess": "a", "ts": "t1", "type": "SUGGESTIONS_SHOWN",
             "typed": "teh", "candidates": [["the", 0.9]]},
            {"v": 3, "id": 2, "sess": "a", "ts": "t2", "type": "AUTO_APPLIED",
             "typed": "teh", "applied": "the", "prev": "saw", "candidates": [["the", 0.9]]},
            {"v": 3, "id": 3, "sess": "a", "ts": "t3", "type": "WORD_COMMITTED",
             "word": "the", "src": "TYPING"},
            {"v": 3, "id": 4, "sess": "a", "ts": "t4", "type": "REVERTED",
             "typed": "teh", "rejected": "the", "undoes": 2},
            {"v": 3, "id": 5, "sess": "a", "ts": "t5", "type": "SESSION_TEXT",
             "text": "saw teh", "src": "TYPING"},
        ]
        stats, slots, sessions = self.run_conversion(rows)
        self.assertEqual(1, stats["v3_word_rows_preserved"])
        self.assertEqual(1, len(slots))
        self.assertEqual("AUTO_APPLIED", slots[0]["outcome"]["route"])
        self.assertTrue(slots[0]["outcome"]["reverted"])
        self.assertEqual("teh", slots[0]["outcome"]["final"])
        self.assertEqual([], slots[0]["keys"])
        self.assertIsNone(slots[0]["layout"])
        self.assertEqual("teh", slots[0]["offers"][0]["prefix"])
        self.assertEqual(1, len(sessions))

    def test_native_v4_wins_over_dual_written_v3(self):
        native = {
            "v": 4, "id": 10, "sess": "b", "ts": "t", "type": "WORD_SLOT", "slot": "b:1",
            "prev": [], "ctx": {}, "layout": {}, "keys": [], "offers": [], "edits": [],
            "outcome": {"final": "hello", "route": "TYPED_THROUGH"}, "shadow": {}, "signals": {},
        }
        rows = [
            {"v": 3, "id": 9, "sess": "b", "ts": "t", "type": "WORD_COMMITTED",
             "word": "hello", "src": "TYPING", "slot": "b:1"},
            native,
        ]
        stats, slots, _sessions = self.run_conversion(rows)
        self.assertEqual(1, len(slots))
        self.assertEqual(native, slots[0])
        self.assertEqual(1, stats["dual_write_v3_words_suppressed"])
        self.assertEqual(0, stats["v3_word_rows_preserved"])


if __name__ == "__main__":
    unittest.main()

"""Tests for the keylog archive: store integrity, receiver HTTP contract, importer, builder."""

from __future__ import annotations

import gzip
import json
import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(HERE))

import build_dataset  # noqa: E402
import privacy  # noqa: E402
import import_file  # noqa: E402
import receiver  # noqa: E402
import schema  # noqa: E402
from keylog_store import ChunkRejected, KeylogStore, anchor_for, sha256_hex, stream_id_for_head  # noqa: E402


def slot(i: int, day: str = "2026-11-01", final: str = "the", keys=None, route="TYPED_THROUGH", **sig) -> dict:
    keys = keys if keys is not None else [
        {"t": 0, "c": "t", "k": "t", "xn": 0.4, "yn": 0.1, "dx": 0.1, "dy": 0.0, "px": 1, "py": 2, "dur": 40, "src": "TAP"},
        {"t": 120, "c": "h", "k": "h", "xn": 0.6, "yn": 0.5, "dx": -0.2, "dy": 0.1, "px": 1, "py": 2, "dur": 45, "src": "TAP"},
        {"t": 230, "c": "e", "k": "e", "xn": 0.2, "yn": 0.1, "dx": 0.0, "dy": 0.0, "px": 1, "py": 2, "dur": 50, "src": "TAP"},
    ]
    signals = {"attempts": 1, "totalBksp": 0, "struggle": False, "capitulation": False, "partial": False}
    signals.update(sig)
    return {
        "v": 4, "id": i, "ts": f"{day}T10:00:{i % 60:02d}.000", "sess": "abcd1234", "type": "WORD_SLOT",
        "slot": f"abcd1234:{i}", "prev": ["of"], "ctx": {"app": "com.example"}, "layout": {"fp": "x"},
        "keys": keys, "offers": [], "edits": [],
        "outcome": {"final": final, "route": route, "reverted": False}, "shadow": {}, "signals": signals,
    }


def lines(objs) -> bytes:
    return b"".join(json.dumps(o, separators=(",", ":")).encode() + b"\n" for o in objs)


def sample_file() -> bytes:
    objs = [{"v": 3, "id": 1, "ts": "2026-11-01T09:00:00.000", "sess": "abcd1234", "type": "WORD_COMMITTED", "app": "com.example",
             "word": "hello", "src": "TYPING"}]
    for i in range(2, 40):
        objs.append(slot(i, day="2026-11-0%d" % (1 + i % 5)))
    objs.append({"v": 3, "id": 40, "ts": "2026-11-02T09:00:00.000", "sess": "abcd1234", "type": "AUTO_APPLIED", "app": "com.example",
                 "typed": "teh", "applied": "the", "candidates": [["the", 1.0], ["teh", 0.1]]})
    objs.append({"v": 3, "id": 41, "ts": "2026-11-02T09:00:01.000", "sess": "abcd1234", "type": "REVERTED", "app": "com.example",
                 "typed": "teh", "rejected": "the", "undoes": 40})
    objs.append({"v": 3, "id": 43, "ts": "2026-11-02T09:00:03.000", "sess": "abcd1234", "type": "WORD_COMMITTED", "app": "com.example",
                 "word": "the", "src": "TYPING", "slot": "abcd1234:2"})
    objs.append({"v": 3, "id": 42, "ts": "2026-11-02T09:00:02.000", "sess": "abcd1234", "type": "AUTO_APPLIED", "app": "com.example",
                 "typed": "recieve", "applied": "receive"})
    return lines(objs)


class StoreTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.store = KeylogStore(Path(self.tmp.name))
        self.data = sample_file()
        self.stream = stream_id_for_head(self.data[: self.data.index(b"\n") + 1])

    def tearDown(self):
        self.tmp.cleanup()

    def ingest(self, offset, chunk, **kw):
        return self.store.ingest(stream=self.stream, offset=offset, data=chunk, claimed_sha256=sha256_hex(chunk), **kw)

    def test_sequential_chunks_and_duplicate_retry(self):
        cut = self.data.index(b"\n", 500) + 1
        a, b = self.data[:cut], self.data[cut:]
        r1 = self.ingest(0, a)
        self.assertEqual(r1["committed_offset"], len(a))
        r2 = self.ingest(len(a), b, anchor_sha256=anchor_for(a))
        self.assertEqual(r2["committed_offset"], len(self.data))
        # A retry after a lost response is a no-op success, not a second copy.
        again = self.ingest(0, a)
        self.assertTrue(again["duplicate"])
        self.assertEqual(len(self.store.manifest()), 2)

    def test_gap_and_overlap_report_committed_offset(self):
        cut = self.data.index(b"\n", 500) + 1
        self.ingest(0, self.data[:cut])
        later = self.data.index(b"\n", cut + 10) + 1
        with self.assertRaises(ChunkRejected) as gap:
            self.ingest(later, self.data[later:])
        self.assertEqual((gap.exception.status, gap.exception.committed_offset), (409, cut))
        with self.assertRaises(ChunkRejected) as overlap:
            self.ingest(10, self.data[10:cut])
        self.assertEqual(overlap.exception.committed_offset, cut)

    def test_digest_mismatch_and_partial_line_rejected(self):
        with self.assertRaises(ChunkRejected) as bad:
            self.store.ingest(stream=self.stream, offset=0, data=self.data, claimed_sha256="0" * 64)
        self.assertEqual(bad.exception.status, 422)
        with self.assertRaises(ChunkRejected):
            self.ingest(0, self.data[:-1])

    def test_first_chunk_must_match_stream_id(self):
        with self.assertRaises(ChunkRejected):
            self.store.ingest(stream="0123456789abcdef", offset=0, data=self.data, claimed_sha256=sha256_hex(self.data))

    def test_diverged_file_detected_by_anchor(self):
        cut = self.data.index(b"\n", 500) + 1
        self.ingest(0, self.data[:cut])
        with self.assertRaises(ChunkRejected) as div:
            self.ingest(cut, self.data[cut:], anchor_sha256="f" * 64)
        self.assertIn("diverged", div.exception.message)
        self.assertEqual(self.store.stream_state(self.stream)["diverged_attempts"], 1)


class ImportAndBuildTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        root = Path(self.tmp.name)
        self.store = KeylogStore(root / "archive")
        self.store.ensure_dirs()
        self.data = sample_file()
        self.src = root / "usage_harvest.jsonl.gz"
        self.src.write_bytes(gzip.compress(self.data))

    def tearDown(self):
        self.tmp.cleanup()

    def test_import_is_resumable_and_build_produces_views(self):
        half = self.data[: self.data.index(b"\n", len(self.data) // 2) + 1]
        partial = Path(self.tmp.name) / "partial.jsonl"
        partial.write_bytes(half + b'{"v":3,"id":99')  # trailing incomplete line is left alone
        s1 = import_file.import_file(partial, self.store, 700, "test")
        self.assertEqual(s1["end_offset"], len(half))
        s2 = import_file.import_file(self.src, self.store, 700, "test")
        self.assertEqual(s2["start_offset"], len(half))
        self.assertEqual(s2["end_offset"], len(self.data))

        card = build_dataset.build(self.store)
        self.assertTrue(card["gates"]["passed"], card["gates"])
        self.assertTrue(card["promoted"])
        current = self.store.derived / "current"
        typing = [json.loads(l) for l in gzip.open(current / "typing_slots.jsonl.gz", "rt")]
        self.assertEqual(len(typing), 38)
        self.assertEqual(typing[0]["typed"], "the")
        self.assertEqual(typing[0]["keys"][0][:3], [0, "t", "t"])
        self.assertTrue(all(r["split"] == build_dataset.split_for_day(r["day"]) for r in typing))
        corr = {r["typed"]: r for r in (json.loads(l) for l in gzip.open(current / "corrections.jsonl.gz", "rt"))}
        self.assertTrue(corr["teh"]["reverted"])
        self.assertFalse(corr["recieve"]["reverted"])
        self.assertEqual(card["counts"]["quarantined"], 0)
        words = {r["uid"].split(":")[1]: r for r in (json.loads(l) for l in gzip.open(current / "words.jsonl.gz", "rt"))}
        self.assertEqual((words["1"]["input"], words["43"]["input"]), ("TYPING_V3", "TYPED"))
        self.assertEqual(words["43"]["route"], "TYPED_THROUGH")
        self.assertFalse((current / "words_unjoined.jsonl.gz").exists())

    def test_corrupted_chunk_blocks_promotion(self):
        import_file.import_file(self.src, self.store, 700, "test")
        first = build_dataset.build(self.store)
        self.assertTrue(first["promoted"])
        chunk = self.store.root / self.store.manifest()[1]["path"]
        chunk.write_bytes(gzip.compress(b'{"tampered":true}\n'))
        card = build_dataset.build(self.store)
        self.assertFalse(card["gates"]["integrity_ok"])
        self.assertFalse(card["promoted"])
        self.assertEqual((self.store.derived / "current").resolve().name, first["build_id"])

    def test_invalid_lines_are_quarantined_not_dropped(self):
        bad = self.data + b'{"v":4,"id":77,"ts":"2026-11-03T00:00:00","sess":"x","type":"WORD_SLOT"}\nnot json\n'
        p = Path(self.tmp.name) / "bad.jsonl"
        p.write_bytes(bad)
        import_file.import_file(p, self.store, 1 << 20, "test")
        card = build_dataset.build(self.store, promote=False)
        self.assertEqual(card["counts"]["quarantined"], 2)
        q = [json.loads(l) for l in gzip.open(self.store.derived / "builds" / card["build_id"] / "quarantine.jsonl.gz", "rt")]
        self.assertEqual(len(q), 2)


def ctx_slot(i: int, sess: str = "abcd1234", field: int = 7, **ctx) -> dict:
    obj = slot(i)
    obj["sess"] = sess
    obj["ctx"] = {"app": "com.example", "fieldId": field, "inputType": "text", "inputVariation": "normal",
                  "flags": "none", "hint": None, "label": None, **ctx}
    return obj


def legacy(i: int, sess: str = "abcd1234", field: int = 7, **fields) -> dict:
    row = {"v": 3, "id": i, "ts": "2026-11-01T09:00:00.000", "sess": sess, "type": "WORD_COMMITTED",
           "app": "com.example", "field": field, "inputType": "normal", "flags": "none", "word": "ok", "src": "TYPING"}
    row.update(fields)
    return row


class PrivacyTests(unittest.TestCase):
    """Fixture rows only: no real harvest content is used or printed."""

    def reason(self, obj, blocked=frozenset()):
        return privacy.exclusion_reason(obj, "s", blocked)

    def test_ordinary_rows_are_kept(self):
        self.assertIsNone(self.reason(ctx_slot(1)))
        self.assertIsNone(self.reason(ctx_slot(2, inputVariation="uri", hint="Search or type URL")))
        self.assertIsNone(self.reason(legacy(3)))
        self.assertIsNone(self.reason(legacy(4, word="2026s")))

    def test_v4_context_rules(self):
        cases = {
            "terminal": [ctx_slot(1, app="com.termux"), ctx_slot(2, inputType="null")],
            "password": [ctx_slot(3, inputVariation="web_password"), ctx_slot(4, inputType="number", inputVariation="password")],
            "email": [ctx_slot(5, inputVariation="email_address")],
            "non_text": [ctx_slot(6, inputType="number"), ctx_slot(7, inputType="phone")],
            "no_learning": [ctx_slot(8, flags="autoCorrect,noPersonalizedLearning")],
            "secret_hint": [ctx_slot(9, hint="Enter OTP"), ctx_slot(10, label="Verification code")],
        }
        for want, rows in cases.items():
            for row in rows:
                self.assertEqual(self.reason(row), want, row["ctx"])

    def test_legacy_rows_use_app_variation_and_flags(self):
        self.assertEqual(self.reason(legacy(1, app="com.termux")), "terminal")
        self.assertEqual(self.reason(legacy(2, inputType="password")), "password")
        self.assertEqual(self.reason(legacy(3, inputType="web_email_address")), "email")
        self.assertEqual(self.reason(legacy(4, flags="noPersonalizedLearning")), "no_learning")
        no_app = legacy(5)
        del no_app["app"]
        self.assertEqual(self.reason(no_app), "no_context")

    def test_legacy_rows_inherit_their_fields_v4_verdict(self):
        rows = [ctx_slot(1, sess="s1", field=9, inputType="number"), legacy(2, sess="s1", field=9, word="ok")]
        blocked = privacy.sensitive_fields("s", rows)
        self.assertEqual(self.reason(rows[1], blocked), "same_field")
        # Same field id in another session is a different field.
        self.assertIsNone(self.reason(legacy(3, sess="s2", field=9), blocked))

    def test_unknown_class_digit_strings_are_excluded(self):
        self.assertEqual(self.reason(legacy(1, word="482913")), "otp_shaped")
        self.assertEqual(self.reason(legacy(2, type="SESSION_TEXT", text="code is 482913 thanks")), "otp_shaped")
        self.assertIsNone(self.reason(legacy(3, type="SESSION_TEXT", text="see you in 2026")))
        # A v4 slot knows its class, so digits in a text field are kept.
        self.assertIsNone(self.reason(ctx_slot(4)))

    def test_rules_are_deterministic(self):
        rows = [ctx_slot(i, inputType=t) for i, t in enumerate(["text", "number", "null", "text"])]
        first = [self.reason(r) for r in rows]
        self.assertEqual(first, [self.reason(r) for r in rows])


class PrivacyBuildTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.store = KeylogStore(Path(self.tmp.name) / "store")
        self.store.ensure_dirs()
        rows = [slot(i) for i in range(1, 6)]
        for r in rows:
            r["ctx"] = {"app": "com.example", "fieldId": 1, "inputType": "text", "inputVariation": "normal"}
        secret = ctx_slot(6, sess="bank", field=4, inputType="number")
        rows += [
            secret,
            legacy(7, sess="bank", field=4, word="ok"),            # legacy row from the same number field
            ctx_slot(8, sess="term", app="com.termux", inputType="null"),
            legacy(9, sess="term", app="com.termux", type="SESSION_TEXT", text="ls"),
            legacy(10, word="482913"),
            legacy(11),
        ]
        self.src = Path(self.tmp.name) / "h.jsonl"
        self.src.write_bytes(lines(rows))
        import_file.import_file(self.src, self.store, 1 << 20, "test")

    def tearDown(self):
        self.tmp.cleanup()

    def read(self, card, name):
        path = self.store.derived / "builds" / card["build_id"] / name
        return [json.loads(l) for l in gzip.open(path, "rt")]

    def test_build_leaves_sensitive_rows_out_of_every_view(self):
        card = build_dataset.build(self.store, promote=False)
        ids = {r["id"] for r in self.read(card, "events.jsonl.gz")}
        self.assertEqual(ids, {1, 2, 3, 4, 5, 11})
        self.assertEqual(len(self.read(card, "typing_slots.jsonl.gz")), 5)
        words = {r["uid"].split(":")[1] for r in self.read(card, "words.jsonl.gz")}
        self.assertEqual(words, {"11"})
        self.assertEqual(card["privacy"]["excluded_by_reason"],
                         {"terminal": 2, "non_text": 1, "same_field": 1, "otp_shaped": 1})
        self.assertEqual(card["privacy"]["unclassified_class_kept"], 1)
        # Raw chunks are untouched.
        self.assertEqual(self.store.manifest()[0]["sha256"], build_dataset.sha256_hex(self.src.read_bytes()))

    def test_rebuild_is_idempotent(self):
        a = build_dataset.build(self.store, promote=False)
        b = build_dataset.build(self.store, promote=False)
        self.assertEqual(a["privacy"], b["privacy"])
        self.assertEqual(a["files"]["events.jsonl.gz"]["sha256"], b["files"]["events.jsonl.gz"]["sha256"])

    def test_report_matches_build_and_writes_nothing(self):
        before = sorted(p.name for p in (self.store.derived).iterdir())
        report = build_dataset.privacy_report(self.store)
        self.assertEqual(sorted(p.name for p in (self.store.derived).iterdir()), before)
        card = build_dataset.build(self.store, promote=False)
        self.assertEqual(report["excluded_by_reason"], card["privacy"]["excluded_by_reason"])
        self.assertEqual(report["kept"], 6)

    def test_quarantine_keeps_no_text(self):
        p = Path(self.tmp.name) / "bad.jsonl"
        p.write_bytes(b'{"v":3,"id":99,"ts":"x","type":"SESSION_TEXT","text":"hunter2"}\nnot json hunter2\n')
        import_file.import_file(p, self.store, 1 << 20, "test")
        card = build_dataset.build(self.store, promote=False)
        q = self.read(card, "quarantine.jsonl.gz")
        self.assertEqual(len(q), 2)
        self.assertNotIn("hunter2", json.dumps(q))


class SchemaTests(unittest.TestCase):
    def test_typing_slot_and_voice_slot(self):
        self.assertEqual(schema.validate(slot(5)), [])
        voice = slot(6, keys=[], route="VOICE")
        self.assertEqual(schema.validate(voice), [])
        self.assertFalse(schema.is_typing_slot(voice))
        self.assertTrue(schema.is_typing_slot(slot(7)))

    def test_backspace_only_slot_is_not_typing_even_with_coordinates(self):
        bksp = slot(9, keys=[{"t": 0, "c": None, "k": "BKSP", "xn": 0.9, "yn": 1.2, "dx": 0.1, "dy": 0.0,
                              "px": 1, "py": 2, "dur": 40, "src": "TAP", "kb": [0.85, 1.0, 1.0, 1.3]}])
        self.assertEqual(schema.validate(bksp), [])
        self.assertFalse(schema.is_typing_slot(bksp))

    def test_predictions_validate(self):
        ok = slot(10)
        ok["preds"] = [{"kind": "next", "cands": ["the", "a"], "lead": 300}]
        self.assertEqual(schema.validate(ok), [])
        bad = slot(11)
        bad["preds"] = [{"kind": "next", "cands": [1, 2]}]
        self.assertIn("type:preds[]", schema.validate(bad))

    def test_problems_are_named(self):
        broken = slot(8)
        broken["keys"][0]["t"] = "zero"
        broken["outcome"]["route"] = "TELEPATHY"
        errs = schema.validate(broken)
        self.assertIn("enum:outcome.route", errs)
        self.assertIn("keys.type:t", errs)
        self.assertEqual(schema.validate({"v": 3, "id": True, "ts": "x", "sess": "s", "type": "T"}), ["type:id"])

    def test_replay_typed_handles_backspace(self):
        keys = [{"c": "t", "k": "t", "src": "TAP"}, {"c": "j", "k": "j", "src": "TAP"},
                {"c": None, "k": "BKSP", "src": "TAP"}, {"c": "h", "k": "h", "src": "TAP"}]
        self.assertEqual(build_dataset.replay_typed(keys), "th")


class ReceiverTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        receiver.STORE = KeylogStore(Path(self.tmp.name))
        receiver.STORE.ensure_dirs()
        receiver.TOKEN_FILE = Path(self.tmp.name) / "token"
        receiver.TOKEN_FILE.write_text("secret\n")
        self.server = receiver.Server(("127.0.0.1", 0), receiver.Handler)
        self.port = self.server.server_address[1]
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.data = sample_file()
        self.stream = stream_id_for_head(self.data[: self.data.index(b"\n") + 1])

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.tmp.cleanup()

    def post(self, offset, chunk, token="secret", sha=None):
        req = urllib.request.Request(
            f"http://127.0.0.1:{self.port}/v1/keylog/chunk", data=gzip.compress(chunk), method="POST",
            headers={"X-Archive-Token": token, "X-Keylog-Stream": self.stream, "X-Keylog-Offset": str(offset),
                     "X-Keylog-Sha256": sha or sha256_hex(chunk), "Content-Type": "application/gzip"})
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                return resp.status, json.loads(resp.read())
        except urllib.error.HTTPError as err:
            return err.code, json.loads(err.read())

    def test_upload_resume_and_auth(self):
        self.assertEqual(self.post(0, self.data, token="wrong")[0], 401)
        cut = self.data.index(b"\n", 300) + 1
        code, body = self.post(0, self.data[:cut])
        self.assertEqual(code, 200)
        self.assertEqual(body["sha256"], sha256_hex(self.data[:cut]))
        code, body = self.post(cut + 50, self.data[cut + 50:])
        self.assertEqual((code, body["committed_offset"]), (409, cut))
        req = urllib.request.Request(f"http://127.0.0.1:{self.port}/v1/keylog/streams/{self.stream}",
                                     headers={"X-Archive-Token": "secret"})
        with urllib.request.urlopen(req, timeout=10) as resp:
            state = json.loads(resp.read())
        self.assertEqual(state["committed_offset"], cut)
        self.assertEqual(state["tail_anchor_sha256"], anchor_for(self.data[:cut]))

    def test_gzip_bomb_refused(self):
        with self.assertRaises(ValueError):
            receiver.gunzip_bounded(gzip.compress(b"\n" * 5000), 1000)


if __name__ == "__main__":
    unittest.main()

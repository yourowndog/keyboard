#!/usr/bin/env python3
"""Convert mixed OmniBoard harvest JSONL into canonical v4 training inputs.

Raw harvest files are never rewritten. Schema-v4 WORD_SLOT rows pass through;
schema-v3 word events are losslessly projected into degenerate slots, with
unavailable spatial features left missing rather than manufactured as zeros.
Dual-written v3 rows carrying a ``slot`` join key are suppressed when their v4
counterpart exists anywhere in the input.
"""

from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable


def iter_rows(path: Path) -> Iterable[tuple[int, dict[str, Any]]]:
    with path.open(encoding="utf-8", errors="replace") as handle:
        for line_no, raw in enumerate(handle, 1):
            raw = raw.strip()
            if not raw:
                continue
            try:
                row = json.loads(raw)
            except json.JSONDecodeError:
                yield line_no, {"_invalid": True}
                continue
            if isinstance(row, dict):
                yield line_no, row


def preflight(path: Path) -> tuple[set[str], dict[int, dict[str, Any]], Counter]:
    v4_slots: set[str] = set()
    reverted: dict[int, dict[str, Any]] = {}
    stats: Counter = Counter()
    for _line_no, row in iter_rows(path):
        if row.get("_invalid"):
            stats["invalid_lines"] += 1
            continue
        if row.get("v") == 4 and row.get("type") == "WORD_SLOT" and row.get("slot"):
            v4_slots.add(str(row["slot"]))
        if row.get("v", 3) == 3 and row.get("type") == "REVERTED" and row.get("undoes") is not None:
            try:
                reverted[int(row["undoes"])] = row
            except (TypeError, ValueError):
                stats["bad_undo_ids"] += 1
    return v4_slots, reverted, stats


def legacy_context(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "app": row.get("app"),
        "fieldId": row.get("field"),
        "inputType": row.get("inputType"),
        "inputVariation": row.get("inputType"),
        "flags": row.get("flags"),
        "imeOptions": None,
        "hint": None,
        "label": None,
        "actionLabel": None,
        "privateImeOptions": None,
        "extrasKeys": [],
        "inputSession": None,
        "register": None,
    }


def legacy_slot(
    row: dict[str, Any],
    line_no: int,
    *,
    route: str,
    final: str,
    typed: str | None = None,
    offers: list[dict[str, Any]] | None = None,
    extra_event: tuple[int, int] | None = None,
    reverted: dict[str, Any] | None = None,
) -> dict[str, Any]:
    event_ids = [row.get("id")]
    source_lines = [line_no]
    if extra_event is not None:
        event_ids.append(extra_event[0])
        source_lines.append(extra_event[1])
    event_ids = [event_id for event_id in event_ids if event_id is not None]
    prev = [value for value in (row.get("prev2"), row.get("prev")) if value]
    was_reverted = reverted is not None
    if was_reverted:
        final = str(reverted.get("typed") or typed or final)
        if reverted.get("id") is not None:
            event_ids.append(reverted["id"])
    return {
        "v": 4,
        "type": "WORD_SLOT",
        "id": row.get("id"),
        "ts": row.get("ts"),
        "sess": row.get("sess"),
        "slot": f"legacy:{row.get('sess', 'unknown')}:{row.get('id', line_no)}",
        "prev": prev[-2:],
        "ctx": legacy_context(row),
        "layout": None,
        "keys": [],
        "offers": offers or [],
        "edits": [],
        "outcome": {
            "final": final,
            "route": route,
            "barIndex": None,
            "autoFrom": typed,
            "reverted": was_reverted,
            "revertedTo": final if was_reverted else None,
            "revertedFrom": reverted.get("rejected") if was_reverted else None,
            "returnEdited": False,
            "committedAt": row.get("ts"),
            "commitChar": None,
            "reachable": None,
            "reachableAt": None,
        },
        "shadow": {},
        "signals": None,
        "provenance": {
            "sourceSchema": 3,
            "eventIds": event_ids,
            "sourceLines": source_lines,
            "inferred": ["outcome.route", "slot"],
            "unavailableFeatures": [
                "layout", "keys", "touchCoordinates", "inputSession",
                "editorMetadata", "editJourney", "signals",
            ],
        },
    }


def offer_from(row: dict[str, Any], line_no: int) -> dict[str, Any]:
    candidates = row.get("candidates") or []
    return {
        "t": None,
        "prefix": row.get("typed"),
        "eng": "legacy-v3",
        "cands": candidates,
        "shown": len(candidates),
        "sourceEventId": row.get("id"),
        "sourceLine": line_no,
    }


def convert(input_path: Path, slots_path: Path, sessions_path: Path) -> Counter:
    v4_slots, reverted_by_undo, stats = preflight(input_path)
    slots_path.parent.mkdir(parents=True, exist_ok=True)
    sessions_path.parent.mkdir(parents=True, exist_ok=True)
    offers: dict[tuple[str, str], list[dict[str, Any]]] = defaultdict(list)
    pending_auto: dict[str, tuple[int, dict[str, Any]]] = {}

    with slots_path.open("w", encoding="utf-8") as slot_out, sessions_path.open("w", encoding="utf-8") as session_out:
        def write_slot(slot: dict[str, Any], from_word: bool = False) -> None:
            slot_out.write(json.dumps(slot, ensure_ascii=False, separators=(",", ":")) + "\n")
            stats["slots_written"] += 1
            if from_word:
                stats["v3_word_rows_preserved"] += 1

        def flush_auto(session: str) -> None:
            pending = pending_auto.pop(session, None)
            if pending is None:
                return
            auto_line, auto = pending
            typed = str(auto.get("typed") or "")
            applied = str(auto.get("applied") or "")
            key = (session, typed.lower())
            slot = legacy_slot(
                auto,
                auto_line,
                route="AUTO_APPLIED",
                final=applied,
                typed=typed,
                offers=offers.pop(key, []),
                reverted=reverted_by_undo.get(auto.get("id")),
            )
            write_slot(slot)
            stats["orphan_auto_slots"] += 1

        for line_no, row in iter_rows(input_path):
            if row.get("_invalid"):
                continue
            version = row.get("v", 3)
            event_type = row.get("type")
            session = str(row.get("sess") or "unknown")

            if version == 4 and event_type == "WORD_SLOT":
                write_slot(row)
                stats["native_v4_slots"] += 1
                continue

            if version != 3:
                stats["unknown_versions"] += 1
                continue

            if event_type == "WORD_COMMITTED":
                stats["v3_word_rows"] += 1

            joined_slot = row.get("slot")
            if joined_slot and str(joined_slot) in v4_slots:
                stats["dual_write_v3_rows_suppressed"] += 1
                if event_type == "WORD_COMMITTED":
                    stats["dual_write_v3_words_suppressed"] += 1
                continue

            if event_type == "SESSION_TEXT":
                normalized = {
                    "v": 1,
                    "type": "SESSION_TEXT",
                    "ts": row.get("ts"),
                    "sess": row.get("sess"),
                    "text": row.get("text"),
                    "route": "VOICE" if row.get("src") == "VOICE" else "TYPED_THROUGH",
                    "ctx": legacy_context(row),
                    "provenance": {"sourceSchema": 3, "eventId": row.get("id"), "sourceLine": line_no},
                }
                session_out.write(json.dumps(normalized, ensure_ascii=False, separators=(",", ":")) + "\n")
                stats["sessions_written"] += 1
            elif event_type == "SUGGESTIONS_SHOWN":
                typed = str(row.get("typed") or "").lower()
                if typed:
                    offers[(session, typed)].append(offer_from(row, line_no))
            elif event_type == "AUTO_APPLIED":
                flush_auto(session)
                pending_auto[session] = (line_no, row)
            elif event_type == "WORD_COMMITTED":
                word = str(row.get("word") or "")
                pending = pending_auto.get(session)
                if pending is not None and str(pending[1].get("applied") or "").lower() == word.lower():
                    auto_line, auto = pending_auto.pop(session)
                    typed = str(auto.get("typed") or "")
                    slot = legacy_slot(
                        auto,
                        auto_line,
                        route="AUTO_APPLIED",
                        final=word,
                        typed=typed,
                        offers=offers.pop((session, typed.lower()), []),
                        extra_event=(row.get("id"), line_no),
                        reverted=reverted_by_undo.get(auto.get("id")),
                    )
                else:
                    flush_auto(session)
                    route = "VOICE" if row.get("src") == "VOICE" else "TYPED_THROUGH"
                    slot = legacy_slot(
                        row,
                        line_no,
                        route=route,
                        final=word,
                        offers=offers.pop((session, word.lower()), []),
                    )
                write_slot(slot, from_word=True)

        for session in list(pending_auto):
            flush_auto(session)

    if stats["v3_word_rows"] != stats["v3_word_rows_preserved"] + stats["dual_write_v3_words_suppressed"]:
        raise RuntimeError(
            "v3 word-count preservation failed: "
            f"read={stats['v3_word_rows']} preserved={stats['v3_word_rows_preserved']} "
            f"deduped={stats['dual_write_v3_words_suppressed']}"
        )
    return stats


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="raw or mixed usage_harvest.jsonl")
    parser.add_argument("output", type=Path, help="canonical WORD_SLOT JSONL (new file)")
    parser.add_argument(
        "--sessions-output",
        type=Path,
        help="normalized language-context records (default: <output>.sessions.jsonl)",
    )
    args = parser.parse_args()
    sessions = args.sessions_output or args.output.with_suffix(".sessions.jsonl")
    stats = convert(args.input, args.output, sessions)
    print(f"slots: {stats['slots_written']} -> {args.output}")
    print(f"sessions: {stats['sessions_written']} -> {sessions}")
    print(
        "v3 word rows: "
        f"{stats['v3_word_rows']} "
        f"(preserved={stats['v3_word_rows_preserved']}, "
        f"dual-write deduped={stats['dual_write_v3_words_suppressed']})"
    )
    if stats["invalid_lines"]:
        print(f"warning: {stats['invalid_lines']} invalid JSON lines skipped")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

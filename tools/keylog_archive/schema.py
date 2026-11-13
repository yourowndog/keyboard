"""Schema validation for harvest events (v3 legacy events and v4 WORD_SLOT).

Hand-written rather than JSON Schema so it runs on Titan's bare python3 with no
dependencies, and so each failure reads as one short, countable reason.

``validate(obj)`` returns a list of problems; an empty list means the event is
usable for training. Unknown event types pass the common checks only: the phone
may start emitting a new type before this file learns about it, and that must
not quarantine data.

The source of truth for field meanings is docs/development/harvest-schema-v4-spec.md
and HarvestJsonl.kt / HarvestV4.kt.
"""

from __future__ import annotations

import re

SCHEMA_VERSION = "keylog-schema-1"

TS_RE = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,6})?$")
SLOT_RE = re.compile(r"^[0-9a-f]{8}:\d+$")

KNOWN_ROUTES = {
    "TYPED_THROUGH", "BAR_PICK", "AUTO_APPLIED", "GLIDE", "VOICE", "PASTE", "ABANDONED",
    "AUTO_REVERTED", "CLIPBOARD",
}
KEY_SOURCES = {"TAP", "GLIDE", "LONGPRESS", "POPUP", "VOICE", "PASTE", "CLIPBOARD"}

_STR = (str,)
_NUM = (int, float)


def _is_int(v) -> bool:
    return isinstance(v, int) and not isinstance(v, bool)


def _req(obj: dict, key: str, types: tuple, errors: list[str], nullable: bool = False) -> None:
    if key not in obj:
        errors.append(f"missing:{key}")
        return
    v = obj[key]
    if v is None:
        if not nullable:
            errors.append(f"null:{key}")
        return
    if types == (int,):
        ok = _is_int(v)
    elif types is _NUM:
        ok = _is_int(v) or isinstance(v, float)
    else:
        ok = isinstance(v, types)
    if not ok:
        errors.append(f"type:{key}")


def _candidates(v, key: str, errors: list[str]) -> None:
    if not isinstance(v, list):
        errors.append(f"type:{key}")
        return
    for item in v:
        if not (isinstance(item, list) and len(item) == 2 and isinstance(item[0], str)
                and (item[1] is None or _is_int(item[1]) or isinstance(item[1], float))):
            errors.append(f"shape:{key}")
            return


V3_FIELDS: dict[str, list[tuple[str, tuple, bool]]] = {
    "WORD_COMMITTED": [("word", _STR, False)],
    "SESSION_TEXT": [("text", _STR, False)],
    "SUGGESTIONS_SHOWN": [("typed", _STR, False)],
    "NEURAL_SHADOW": [("typed", _STR, False)],
    "AUTO_APPLIED": [("typed", _STR, False), ("applied", _STR, False)],
    "REVERTED": [("typed", _STR, False), ("rejected", _STR, False)],
    "MANUAL_EDIT": [("before", _STR, False), ("after", _STR, False)],
    "NEW_WORD": [("word", _STR, False)],
    "INSISTED": [("word", _STR, False)],
}


def validate(obj) -> list[str]:
    errors: list[str] = []
    if not isinstance(obj, dict):
        return ["not_object"]
    _req(obj, "v", (int,), errors)
    _req(obj, "id", (int,), errors)
    _req(obj, "ts", _STR, errors)
    _req(obj, "sess", _STR, errors)
    _req(obj, "type", _STR, errors)
    if errors:
        return errors
    if obj["id"] <= 0:
        errors.append("range:id")
    if not TS_RE.match(obj["ts"]):
        errors.append("format:ts")
    typ = obj["type"]
    ver = obj["v"]

    if typ == "WORD_SLOT":
        if ver != 4:
            errors.append("version:WORD_SLOT")
        _validate_slot(obj, errors)
        return errors

    if ver not in (3, 4):
        errors.append("version")
    for key, types, nullable in V3_FIELDS.get(typ, []):
        _req(obj, key, types, errors, nullable)
    if "candidates" in obj:
        _candidates(obj["candidates"], "candidates", errors)
    if "ranked" in obj:
        _candidates(obj["ranked"], "ranked", errors)
    if "slot" in obj and obj["slot"] is not None and not (isinstance(obj["slot"], str) and SLOT_RE.match(obj["slot"])):
        errors.append("format:slot")
    return errors


def _validate_slot(obj: dict, errors: list[str]) -> None:
    _req(obj, "slot", _STR, errors)
    if isinstance(obj.get("slot"), str) and not SLOT_RE.match(obj["slot"]):
        errors.append("format:slot")
    _req(obj, "prev", (list,), errors)
    _req(obj, "ctx", (dict,), errors)
    _req(obj, "layout", (dict,), errors, nullable=True)
    _req(obj, "keys", (list,), errors)
    _req(obj, "outcome", (dict,), errors)
    _req(obj, "signals", (dict,), errors)
    if errors:
        return
    if not all(isinstance(p, str) for p in obj["prev"]):
        errors.append("type:prev[]")

    ctx = obj["ctx"]
    _req(ctx, "app", _STR, errors)

    out = obj["outcome"]
    _req(out, "final", _STR, errors, nullable=True)
    _req(out, "route", _STR, errors)
    route = out.get("route")
    if isinstance(route, str) and route not in KNOWN_ROUTES:
        errors.append("enum:outcome.route")

    for i, key in enumerate(obj["keys"]):
        if not isinstance(key, dict):
            errors.append("type:keys[]")
            break
        kerr: list[str] = []
        _req(key, "t", (int,), kerr)
        _req(key, "k", _STR, kerr, nullable=True)
        _req(key, "c", _STR, kerr, nullable=True)
        for coord in ("xn", "yn", "dx", "dy"):
            if coord in key and key[coord] is not None and not isinstance(key[coord], (int, float)):
                kerr.append(f"type:{coord}")
        if _is_int(key.get("t")) and key["t"] < 0:
            kerr.append("range:t")
        if kerr:
            errors.extend(f"keys.{e}" for e in kerr[:2])
            break

    preds = obj.get("preds")
    if preds is not None:
        if not isinstance(preds, list):
            errors.append("type:preds")
        else:
            for pred in preds:
                if not (isinstance(pred, dict) and isinstance(pred.get("kind"), str)
                        and isinstance(pred.get("cands"), list)
                        and all(isinstance(c, str) for c in pred["cands"])):
                    errors.append("type:preds[]")
                    break

    sig = obj["signals"]
    if "partial" in sig and not isinstance(sig["partial"], bool):
        errors.append("type:signals.partial")


def is_typing_slot(obj: dict) -> bool:
    """A WORD_SLOT whose keys carry touch evidence: the neural model's core example."""
    if obj.get("type") != "WORD_SLOT":
        return False
    route = obj.get("outcome", {}).get("route")
    if route in ("VOICE", "PASTE", "CLIPBOARD"):
        return False
    # Since 2026-11-13 every key carries xn/yn, so require a letter tap: a slot of backspaces alone
    # is an edit, not a typed word.
    return any(isinstance(k, dict) and k.get("src") in (None, "TAP", "LONGPRESS", "POPUP") and k.get("xn") is not None
               and isinstance(k.get("k"), str) and len(k["k"]) == 1 and k["k"].isalpha()
               for k in obj.get("keys", []))

#!/usr/bin/env python3
"""Validate packaged Snygg stylesheets against the things that actually break.

Static checks only. This cannot prove a theme looks right, and it is not a
substitute for device validation; it catches the three failure modes that have
silently shipped before:

  1. a rule naming an element no composable queries (dead rule, no effect)
  2. a value the generated schema rejects (rule dropped at parse time)
  3. a key code that does not exist in KeyCode.kt (rule matches nothing)

    python3 tools/theme/validate_stylesheets.py
"""
from __future__ import annotations

import json
import pathlib
import re
import sys

REPO = pathlib.Path(__file__).resolve().parents[2]
SCHEMA = REPO / "lib" / "snygg" / "schemas" / "stylesheet.schema.json"
UI = REPO / "app/src/main/kotlin/dev/patrickgold/florisboard/ime/theme/FlorisImeUi.kt"
CODES = REPO / "app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/key/KeyCode.kt"
THEMES = REPO / "app/src/main/assets/ime/theme"


def known_elements() -> set[str]:
    return set(re.findall(r'elementName = "([a-z0-9-]+)"', UI.read_text()))


def known_codes() -> set[int]:
    text = CODES.read_text()
    codes = {int(v) for v in re.findall(r"const val [A-Z_0-9]+ += +(-?\d+)", text)}
    # Literal character keys are valid targets too (letters, digits, punctuation).
    codes |= set(range(1, 65536))
    return codes


def rule_codes(rule: str) -> list[int]:
    out: list[int] = []
    for group in re.findall(r"\[code=([-0-9,.]+)\]", rule):
        for part in group.split(","):
            part = part.strip()
            if ".." in part:
                lo, hi = part.split("..")
                out.extend([int(lo), int(hi)])
            elif part:
                out.append(int(part))
    return out


def main() -> int:
    try:
        import jsonschema
    except ImportError:
        print("jsonschema not installed: pip install jsonschema", file=sys.stderr)
        return 2

    # The schema's patterns use ECMA named groups "(?<name>...)"; Python's re
    # spells those "(?P<name>...)". Lookbehind "(?<=" / "(?<!" must be left
    # alone, so only rewrite when a name character follows.
    raw_schema = re.sub(r"\(\?<(?=[A-Za-z_])", "(?P<", SCHEMA.read_text())
    schema = json.loads(raw_schema)
    validator = jsonschema.Draft202012Validator(schema)
    elements, codes = known_elements(), known_codes()

    failures = 0
    for path in sorted(THEMES.glob("*/stylesheets/*.json")):
        name = f"{path.parent.parent.name}/{path.name}"
        problems: list[str] = []
        data = json.loads(path.read_text())

        for err in validator.iter_errors(data):
            loc = "/".join(str(p) for p in err.absolute_path) or "(root)"
            problems.append(f"schema: {loc}: {err.message.splitlines()[0][:150]}")

        for rule in data:
            if rule.startswith(("$", "@")):
                continue
            element = re.split(r"[\[:]", rule)[0]
            if element not in elements:
                problems.append(f"dead element: {rule!r} -> no composable queries {element!r}")
            for code in rule_codes(rule):
                if code not in codes:
                    problems.append(f"unknown key code {code} in {rule!r}")

        status = "FAIL" if problems else "ok"
        count = len([k for k in data if not k.startswith(("$", "@"))])
        print(f"[{status:>4}] {name:<48} {count:>3} rules")
        for p in problems:
            print(f"         - {p}")
        failures += bool(problems)

    print()
    print("all stylesheets passed" if not failures else f"{failures} stylesheet(s) with problems")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())

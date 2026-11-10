#!/usr/bin/env python3
"""Generate the LCARS Jefferies stylesheet variants from a single base.

The font bakeoff needs several otherwise-identical stylesheets that differ only
in their bundled typeface. Hand-maintaining those copies would mean editing the
same 120 rules four times, so the base is authored once with placeholders and
the variants are generated.

Outputs are committed because the app loads them from assets, but they are
generated files: edit jefferies/jefferies.base.json or jefferies/variants.json
and rerun this script rather than editing the generated stylesheets.

    python3 tools/theme/build_jefferies.py
"""
from __future__ import annotations

import collections
import json
import pathlib
import sys

REPO = pathlib.Path(__file__).resolve().parents[2]
SRC = REPO / "tools" / "theme" / "jefferies"
EXT = REPO / "app" / "src" / "main" / "assets" / "ime" / "theme" / "org.florisboard.themes"
STYLESHEETS = EXT / "stylesheets"


def load(path: pathlib.Path):
    with path.open() as fh:
        return json.load(fh, object_pairs_hook=collections.OrderedDict)


def render(base: dict, variant: dict) -> collections.OrderedDict:
    raw = json.dumps(base)
    raw = raw.replace("__FONT_FAMILY__", variant["font_family"])
    raw = raw.replace("__FONT_FILE__", variant["font_file"])
    # No provenance banner is embedded: the stylesheet schema sets
    # additionalProperties=false and allows only "$schema" at the root, so any
    # extra key makes the file invalid. Generation is recorded in
    # tools/theme/README.md and docs/theming/ instead.
    return json.loads(raw, object_pairs_hook=collections.OrderedDict)


def main() -> int:
    base = load(SRC / "jefferies.base.json")
    spec = load(SRC / "variants.json")

    written, entries = [], []
    for variant in spec["variants"]:
        theme_id = spec["base_id"] + variant["suffix"]
        target = STYLESHEETS / f"{theme_id}.json"
        with target.open("w") as fh:
            json.dump(render(base, variant), fh, indent=2, ensure_ascii=False)
            fh.write("\n")
        written.append(target.name)
        entries.append(
            collections.OrderedDict(
                [
                    ("id", theme_id),
                    ("label", spec["base_label"] + variant["label_suffix"]),
                    ("authors", ["BrokenTooth"]),
                    ("stylesheet", f"stylesheets/{theme_id}.json"),
                    ("isNight", True),
                ]
            )
        )

    manifest_path = EXT / "extension.json"
    manifest = load(manifest_path)
    generated_ids = {e["id"] for e in entries}
    # Drop any previously generated Jefferies entries so removing a variant from
    # variants.json actually unregisters it.
    kept = [t for t in manifest["themes"] if not t["id"].startswith(spec["base_id"])]
    manifest["themes"] = sorted(kept + entries, key=lambda t: t["id"])
    with manifest_path.open("w") as fh:
        json.dump(manifest, fh, indent=2, ensure_ascii=False)
        fh.write("\n")

    print(f"wrote {len(written)} stylesheet(s): {', '.join(written)}")
    print(f"registered: {', '.join(sorted(generated_ids))}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

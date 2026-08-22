#!/usr/bin/env python3
"""Convert the unified dictionary into the AOSP-style `.combined` word list that
FUTO's swipe engine parses.

FUTO's trie loader (`parse_combined_vocab` + `apply_parsed_to_trie`) reads lines of the
form `word=<surface>,f=<freq>` and ignores anything else, so headers and blank lines are
harmless. Two details drive the choices here:

* `f` is stored *directly* as the node's log-frequency -- `apply_parsed_to_trie` passes
  `log_freq_direct=true` -- so it must already be on AOSP's compressed 1..255 scale rather
  than a raw count. Feeding raw counts would swamp the beam-search score, which weights this
  term by lambda=0.0176 on the assumption that it tops out around 255.
* Surface forms are written verbatim, keeping case and apostrophes. The engine derives a
  lowercase "alpha" form for trie traversal itself and hands the surface back as the result,
  so `don't` stays reachable by swiping `dont`, and a proper noun can come back capitalised
  instead of being flattened the way the runtime dictionary map flattens it.

Usage: tools/build_futo_swipe_vocab.py [input.tsv] [output.combined]
"""

from __future__ import annotations

import math
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DEFAULT_IN = REPO / "app/src/main/assets/ime/dict/unified_dictionary.tsv"
DEFAULT_OUT = REPO / "app/src/main/assets/futo/en.combined"

# AOSP dictionaries express unigram frequency as a single byte.
MAX_F = 255
MIN_F = 1


def main() -> int:
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_IN
    dst = Path(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_OUT

    entries: list[tuple[str, int]] = []
    skipped = 0
    for line in src.read_text(encoding="utf-8").splitlines():
        word, _, count = line.partition("\t")
        if not word or not count:
            skipped += 1
            continue
        try:
            n = int(count)
        except ValueError:
            skipped += 1
            continue
        if n <= 0:
            skipped += 1
            continue
        entries.append((word, n))

    if not entries:
        print(f"error: no usable entries in {src}", file=sys.stderr)
        return 1

    # Log-compress counts into 1..255. A linear map would put all but the top few hundred
    # words in the bottom bucket; counts here span ~4 orders of magnitude.
    ceiling = math.log(max(n for _, n in entries) + 1.0)
    out = [
        "dictionary=main:en,locale=en,description=OmniBoard unified,version=1",
    ]
    for word, n in entries:
        f = round(MAX_F * math.log(n + 1.0) / ceiling)
        f = max(MIN_F, min(MAX_F, f))
        out.append(f" word={word},f={f}")

    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_text("\n".join(out) + "\n", encoding="utf-8")

    size_mb = dst.stat().st_size / (1024 * 1024)
    print(f"wrote {len(entries)} entries ({skipped} skipped) -> {dst} [{size_mb:.1f} MB]")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

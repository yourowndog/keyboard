#!/usr/bin/env python3
"""Build the personal lexicon asset from a harvest snapshot.

The keyboard does not learn on the device (roadmap invariant 1). Instead this
turns the harvest into an asset packed into every APK, so each install already
knows the words you use, the corrections you undid and the words you insisted
on. PersonalLexicon.kt applies the rival test at lookup time, so a typo you
committed often next to a far more frequent word stays correctable.

    python3 tools/harvesting/build_personal_lexicon.py SNAPSHOT/usage_harvest.jsonl[.gz]

writes private-assets/ime/personal/lexicon.tsv, which the Gradle build adds to
the APK's assets. The output is personal data: it may only be written to the
gitignored private-assets/ folder or outside the repository. Pass a second
argument to choose another path.

Counts come from v4 WORD_SLOT outcomes (typed, auto-applied, bar picks and voice)
and v3 WORD_COMMITTED events from before the first v4 slot, so the two schemas
are not double-counted. Paste and abandoned slots are skipped. Password and
email fields were never harvested.
"""
import gzip
import json
import re
import sys
from collections import Counter
from pathlib import Path

DEFAULT_OUT = Path("private-assets/ime/personal/lexicon.tsv")
WORD = re.compile(r"^[a-z][a-z']{1,31}$")


def norm(word):
    if not word:
        return None
    w = word.strip().strip('.,!?;:"()[]{}').lower()
    return w if WORD.match(w) else None


def open_any(path):
    return gzip.open(path, "rt", errors="replace") if str(path).endswith(".gz") else open(path, errors="replace")


def main():
    if len(sys.argv) not in (2, 3):
        print(__doc__)
        sys.exit(2)
    repo = Path(__file__).resolve().parents[2]
    src = Path(sys.argv[1])
    out = Path(sys.argv[2]) if len(sys.argv) == 3 else repo / DEFAULT_OUT
    resolved = out.resolve()
    if resolved.is_relative_to(repo) and not resolved.is_relative_to(repo / "private-assets"):
        sys.exit("refusing to write personal data inside the repository outside private-assets/")

    counts, learned, rejected = Counter(), set(), set()
    first_v4 = None
    v3_counts = Counter()
    with open_any(src) as f:
        for line in f:
            if '"WORD_SLOT"' not in line and '"WORD_COMMITTED"' not in line and '"REVERTED"' not in line \
                    and '"INSISTED"' not in line:
                continue
            try:
                r = json.loads(line)
            except ValueError:
                continue
            t = r.get("type")
            if t == "WORD_SLOT":
                first_v4 = first_v4 or r.get("ts")
                o = r.get("outcome") or {}
                if o.get("route") in ("PASTE", "ABANDONED", None):
                    continue
                if o.get("reverted"):
                    typed, rej = norm(o.get("revertedTo")), (o.get("revertedFrom") or "").strip().lower()
                    if typed:
                        learned.add(typed)
                        if rej and rej != typed:
                            rejected.add((typed, rej))
                w = norm(o.get("final"))
                if w:
                    counts[w] += 1
            elif t == "WORD_COMMITTED":
                if first_v4 is None:
                    w = norm(r.get("word") or r.get("final") or r.get("text"))
                    if w:
                        v3_counts[w] += 1
            elif t == "REVERTED":
                typed, rej = norm(r.get("typed")), (r.get("rejected") or "").strip().lower()
                if typed:
                    learned.add(typed)
                    if rej and rej != typed:
                        rejected.add((typed, rej))
            elif t == "INSISTED":
                w = norm(r.get("word") or r.get("typed"))
                if w:
                    learned.add(w)
    counts.update(v3_counts)

    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        for w, n in counts.most_common(40_000):
            f.write(f"c\t{w}\t{n}\n")
        for w in sorted(learned):
            f.write(f"l\t{w}\n")
        for typed, rej in sorted(rejected):
            f.write(f"r\t{typed}\t{rej}\n")
    print(f"{len(counts)} words ({sum(counts.values())} uses, {sum(v3_counts.values())} from v3), "
          f"{len(learned)} learned, {len(rejected)} rejected pairs -> {out}")


if __name__ == "__main__":
    main()

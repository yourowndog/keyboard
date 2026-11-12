#!/usr/bin/env python3
"""Measure letter-key geometry in a keyboard image and compare it with the design reference.

    tools/visual/measure.py <image> [--reference <image>] [--threshold N] [--ref-threshold N]

Finds the Q row (the first band with ten keycap-sized runs), then measures the Q key's stack
(hint plate, gap, cap), the column pitch and the row pitch down to the A row. Every length is
reported as a share of the column pitch, so a 778px reference and a 1440px screenshot compare
directly. Needs only ImageMagick (`magick`) and Python 3; no Python imaging library.

Lengths in the output:
  key_w / gap_h    keycap width and the horizontal gap between keys
  plate / gap_in / cap   the Q key's hint plate, the gap under it, and its letter cap
  stack            plate + gap_in + cap
  row_gap          black band between the Q cap and the A plate
  row_pitch        Q plate top to A plate top
and two shape ratios: stack/key_w (how tall a key reads) and plate/cap.
"""
import argparse
import shutil
import subprocess
import sys

REFERENCE = "docs/theming/reference/design-reference-01.png"


def load_gray(path):
    out = subprocess.run(
        [shutil.which("magick") or "convert", path, "-colorspace", "gray", "-depth", "8", "-compress", "none", "pgm:-"],
        check=True, capture_output=True,
    ).stdout.split()
    w, h = int(out[1]), int(out[2])
    return w, h, list(map(int, out[4:]))


def runs(values, threshold, min_len):
    found, start = [], None
    for i, v in enumerate(values + [0]):
        lit = v > threshold
        if lit and start is None:
            start = i
        elif not lit and start is not None:
            if i - start >= min_len:
                found.append((start, i - start))
            start = None
    return found


def measure(path, threshold):
    w, h, px = load_gray(path)
    row = lambda y: px[y * w:(y + 1) * w]
    col = lambda x: [px[y * w + x] for y in range(h)]
    min_key = int(w * 0.05)

    def keycap_row(y, count):
        r = runs(row(y), threshold, min_key)
        if len(r) != count:
            return None
        widths = [n for _, n in r]
        return r if max(widths) - min(widths) <= max(4, w * 0.01) else None

    q_top = next((y for y in range(h) if keycap_row(y, 10)), None)
    if q_top is None:
        sys.exit(f"{path}: no row of ten keys found")
    # Widths are read below the plate's rounded top corners, which would otherwise shave them.
    probe = keycap_row(q_top, 10)
    rough_pitch = (probe[-1][0] - probe[0][0]) / 9
    keys = keycap_row(q_top + int(0.2 * rough_pitch), 10) or probe
    pitch = (keys[-1][0] - keys[0][0]) / 9
    key_w = sum(n for _, n in keys) / 10
    q_left = keys[0][0]

    stack = [r for r in runs(col(int(q_left + 0.15 * key_w)), threshold, 3) if r[0] >= q_top - 1]
    plate, cap = stack[0], stack[1]
    a_top = next(
        (y for y in range(cap[0] + cap[1], h) if keycap_row(y, 9)), None,
    )
    m = {
        "key_w": key_w,
        "gap_h": pitch - key_w,
        "plate": plate[1],
        "gap_in": cap[0] - (plate[0] + plate[1]),
        "cap": cap[1],
        "stack": cap[0] + cap[1] - plate[0],
        "row_gap": (a_top - (cap[0] + cap[1])) if a_top else float("nan"),
        "row_pitch": (a_top - plate[0]) if a_top else float("nan"),
    }
    shares = {k: v / pitch for k, v in m.items()}
    shares["stack/key_w"] = m["stack"] / m["key_w"]
    shares["plate/cap"] = m["plate"] / m["cap"]
    return pitch, shares


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("image")
    ap.add_argument("--reference", default=REFERENCE)
    ap.add_argument("--threshold", type=int, default=40)
    ap.add_argument("--ref-threshold", type=int, default=90)
    args = ap.parse_args()

    ref_pitch, ref = measure(args.reference, args.ref_threshold)
    pitch, cur = measure(args.image, args.threshold)
    print(f"column pitch: reference {ref_pitch:.1f}px, image {pitch:.1f}px; lengths are shares of it")
    print(f"{'':14}{'reference':>10}{'image':>10}{'off':>8}")
    for k in ref:
        off = (cur[k] / ref[k] - 1) * 100 if ref[k] else float("nan")
        print(f"{k:14}{ref[k]:10.3f}{cur[k]:10.3f}{off:+7.0f}%")


if __name__ == "__main__":
    main()

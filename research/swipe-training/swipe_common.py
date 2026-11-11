#!/usr/bin/env python3
"""
swipe_common.py

Shared foundation for the two-stage swipe model pipeline.

Both Stage 1 (Seq2Traj generator) and Stage 2 (NeuroSwipe classifier) import
from here so that keyboard geometry, tokenization, FUTO normalization, and
kinematic feature extraction are defined exactly once.

Coordinate contract
-------------------
Everything downstream of `load_futo()` lives in *keyboard-normalized* space:
x in [0,1] across the keyboard width, y in [0,1] across the keyboard height,
t in seconds relative to touch-down. FUTO stores raw pixels plus canvas
dimensions, so normalization is `x / canvas_width`, `y / canvas_height`.

Apostrophe contract
-------------------
Swipe keyboards do not require the user to cross an apostrophe key: you swipe
d-o-n-t and the decoder emits "don't". So apostrophes are stripped from the
*geometry* (the key sequence a finger traverses) but retained in the *label*
(what the model must output). This is why the decoder charset includes "'"
while QWERTY_LAYOUT does not.
"""

from __future__ import annotations

import json
import math
import re
from dataclasses import dataclass
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

import numpy as np

# =============================================================================
# KEYBOARD GEOMETRY
# =============================================================================

# Key centers in keyboard-normalized space. Row centers sit at 1/6, 3/6, 5/6.
QWERTY_LAYOUT: Dict[str, Tuple[float, float]] = {
    'q': (0.05, 0.167), 'w': (0.15, 0.167), 'e': (0.25, 0.167), 'r': (0.35, 0.167),
    't': (0.45, 0.167), 'y': (0.55, 0.167), 'u': (0.65, 0.167), 'i': (0.75, 0.167),
    'o': (0.85, 0.167), 'p': (0.95, 0.167),

    'a': (0.075, 0.50), 's': (0.175, 0.50), 'd': (0.275, 0.50), 'f': (0.375, 0.50),
    'g': (0.475, 0.50), 'h': (0.575, 0.50), 'j': (0.675, 0.50), 'k': (0.775, 0.50),
    'l': (0.875, 0.50),

    'z': (0.15, 0.833), 'x': (0.25, 0.833), 'c': (0.35, 0.833), 'v': (0.45, 0.833),
    'b': (0.55, 0.833), 'n': (0.65, 0.833), 'm': (0.75, 0.833),
}

KEY_WIDTH = 0.10
KEY_HEIGHT = 0.333

# =============================================================================
# TOKENIZER
# =============================================================================

PAD, SOS, EOS = "<pad>", "<sos>", "<eos>"
# Label alphabet: specials + apostrophe + a-z. Geometry alphabet is a-z only.
CHARS: List[str] = [PAD, SOS, EOS, "'"] + [chr(ord('a') + i) for i in range(26)]
CHAR2ID: Dict[str, int] = {c: i for i, c in enumerate(CHARS)}
ID2CHAR: Dict[int, str] = {i: c for i, c in enumerate(CHARS)}
NUM_CLASSES = len(CHARS)  # 30

PAD_ID, SOS_ID, EOS_ID = CHAR2ID[PAD], CHAR2ID[SOS], CHAR2ID[EOS]

# Geometry alphabet — what a finger can actually traverse.
GEOM_CHARS = set(QWERTY_LAYOUT)

_WORD_RE = re.compile(r"^[a-z]+(?:'[a-z]+)*$")


class UnsupportedWord(ValueError):
    """Raised when a word contains characters outside the model alphabet.

    Deliberately loud: silently mapping unknown characters onto <pad> corrupts
    training targets in a way that is invisible until accuracy is unexplainably
    poor.
    """


def normalize_word(word: str) -> str:
    """Lowercase, strip, and normalize unicode apostrophes to ASCII."""
    return word.strip().lower().replace("’", "'").replace("ʼ", "'")


def is_supported(word: str) -> bool:
    w = normalize_word(word)
    return bool(w) and bool(_WORD_RE.match(w))


def key_sequence(word: str) -> str:
    """The letters a finger actually traverses (apostrophes removed)."""
    return normalize_word(word).replace("'", "")


def encode_label(word: str, add_eos: bool = True) -> List[int]:
    """Target character ids, apostrophes retained."""
    w = normalize_word(word)
    if not is_supported(w):
        raise UnsupportedWord(f"word {word!r} outside alphabet")
    ids = [CHAR2ID[c] for c in w]
    if add_eos:
        ids.append(EOS_ID)
    return ids


def decode_label(ids: Iterable[int]) -> str:
    out = []
    for i in ids:
        i = int(i)
        if i in (PAD_ID, SOS_ID):
            continue
        if i == EOS_ID:
            break
        out.append(ID2CHAR.get(i, ""))
    return "".join(out)


def encode_geometry(word: str) -> Tuple[np.ndarray, np.ndarray]:
    """Return (char_ids, key_coords) for the traversed key sequence.

    char_ids indexes the shared CHARS table so Stage 1 and Stage 2 embeddings
    stay interchangeable.
    """
    seq = key_sequence(word)
    if not seq or any(c not in GEOM_CHARS for c in seq):
        raise UnsupportedWord(f"word {word!r} has no valid key path")
    ids = np.array([CHAR2ID[c] for c in seq], dtype=np.int64)
    coords = np.array([QWERTY_LAYOUT[c] for c in seq], dtype=np.float32)
    return ids, coords


# =============================================================================
# VOCABULARY HYGIENE
# =============================================================================

_VOWEL = re.compile(r"[aeiouy]")
_CONSONANT_RUN = re.compile(r"[bcdfghjklmnpqrstvwxz]{5,}")


def is_plausible_word(word: str, max_len: int = 14) -> bool:
    """Heuristic filter for harvested vocabulary.

    The harvest pass captured concatenated sentences (spaces stripped), lowercased
    Java/Kotlin class names, and hash fragments alongside genuine vocabulary.
    Synthesizing swipes for those wastes GPU time and teaches the classifier
    trajectories no human will ever draw.

    Short consonant clusters are kept on purpose: acronyms like "mcp" and "adb"
    are exactly the vocabulary this project exists to cover.
    """
    w = normalize_word(word)
    if not is_supported(w):
        return False
    body = w.replace("'", "")
    if not (1 <= len(body) <= max_len):
        return False
    if not _VOWEL.search(body) and len(body) > 5:
        return False
    if _CONSONANT_RUN.search(body):
        return False
    # Long unbroken tokens with ordinary vowel density are almost always
    # space-stripped sentences rather than real words.
    if len(body) >= 12 and "'" not in w:
        if len(_VOWEL.findall(body)) / len(body) > 0.30:
            return False
    return True


def load_vocabulary(*paths: str, filter_junk: bool = True) -> List[str]:
    """Load and dedupe a target word list from one or more files."""
    seen: Dict[str, None] = {}
    for path in paths:
        try:
            fh = open(path, "r", encoding="utf-8")
        except FileNotFoundError:
            continue
        with fh:
            for line in fh:
                w = normalize_word(line.split("\t")[0])
                if not w:
                    continue
                if filter_junk and not is_plausible_word(w):
                    continue
                if not filter_junk and not is_supported(w):
                    continue
                seen.setdefault(w, None)
    return list(seen)


# =============================================================================
# TRAJECTORY UTILITIES
# =============================================================================


def resample_trajectory(
    xy: np.ndarray, t: np.ndarray, num_points: int
) -> Tuple[np.ndarray, np.ndarray]:
    """Arc-length resample a trajectory to a fixed point count.

    Returns (xy_resampled[num_points,2], t_resampled[num_points]). Resampling by
    arc length rather than by time keeps spatial shape intact while letting the
    time channel carry the velocity profile — which is the signal we care about
    for corner deceleration.
    """
    xy = np.asarray(xy, dtype=np.float64)
    t = np.asarray(t, dtype=np.float64)
    if len(xy) == 1:
        return (np.repeat(xy, num_points, axis=0).astype(np.float32),
                np.repeat(t, num_points).astype(np.float32))

    seg = np.linalg.norm(np.diff(xy, axis=0), axis=1)
    cum = np.concatenate([[0.0], np.cumsum(seg)])
    total = cum[-1]
    if total <= 0:
        return (np.repeat(xy[:1], num_points, axis=0).astype(np.float32),
                np.linspace(t[0], t[-1], num_points).astype(np.float32))

    targets = np.linspace(0.0, total, num_points)
    x = np.interp(targets, cum, xy[:, 0])
    y = np.interp(targets, cum, xy[:, 1])
    tt = np.interp(targets, cum, t)
    return np.stack([x, y], axis=1).astype(np.float32), tt.astype(np.float32)


def trajectory_features(xy: np.ndarray, t: np.ndarray) -> np.ndarray:
    """Build the 7-channel input tensor for the Stage 2 encoder.

    Channels: x, y, dx, dy, speed, curvature-weighted turn, dt.

    Velocity and turn are supplied explicitly rather than left for the encoder
    to infer, because corner deceleration is the discriminative signal that
    distinguishes a real swipe from a spline and we want it available at layer 0.
    """
    xy = np.asarray(xy, dtype=np.float32)
    t = np.asarray(t, dtype=np.float32)
    n = len(xy)

    d = np.zeros_like(xy)
    d[1:] = xy[1:] - xy[:-1]

    dt = np.zeros(n, dtype=np.float32)
    dt[1:] = np.maximum(t[1:] - t[:-1], 1e-4)
    dt[0] = dt[1] if n > 1 else 1e-2

    step = np.linalg.norm(d, axis=1)
    speed = step / dt

    # Turn magnitude: 1 - cos(angle between consecutive step vectors).
    turn = np.zeros(n, dtype=np.float32)
    if n > 2:
        a, b = d[1:-1], d[2:]
        na = np.linalg.norm(a, axis=1) + 1e-8
        nb = np.linalg.norm(b, axis=1) + 1e-8
        cos = np.sum(a * b, axis=1) / (na * nb)
        turn[2:] = 1.0 - np.clip(cos, -1.0, 1.0)

    return np.stack(
        [xy[:, 0], xy[:, 1], d[:, 0], d[:, 1], speed, turn, dt], axis=1
    ).astype(np.float32)


FEATURE_DIM = 7


# =============================================================================
# CORNER KINEMATICS — the Stage 1 acceptance metric
# =============================================================================


@dataclass
class CornerStats:
    n_trajectories: int
    n_corners: int
    mean_corner_ratio: float   # speed at corner / mean speed of trajectory
    median_corner_ratio: float
    p10_corner_ratio: float
    p90_corner_ratio: float
    mean_speed: float
    mean_duration_s: float
    mean_points: float

    def as_dict(self) -> dict:
        return self.__dict__.copy()


def corner_speed_ratios(
    xy: np.ndarray, t: np.ndarray, turn_threshold_deg: float = 45.0
) -> List[float]:
    """Speed at each direction-change vertex, normalized by trajectory mean speed.

    A human finger brakes into a corner, so this ratio sits well below 1.0. A
    Bezier/spring model carries speed through the vertex and lands near or above
    1.0 — that discrepancy is the +1531% corner-velocity error the earlier
    validation suite reported.
    """
    xy = np.asarray(xy, dtype=np.float64)
    t = np.asarray(t, dtype=np.float64)
    if len(xy) < 5:
        return []

    d = np.diff(xy, axis=0)
    dt = np.maximum(np.diff(t), 1e-4)
    step = np.linalg.norm(d, axis=1)
    speed = step / dt
    mean_speed = float(np.mean(speed))
    if mean_speed <= 0:
        return []

    nrm = np.linalg.norm(d, axis=1) + 1e-9
    unit = d / nrm[:, None]
    cos = np.sum(unit[:-1] * unit[1:], axis=1)
    angles = np.degrees(np.arccos(np.clip(cos, -1.0, 1.0)))

    ratios = []
    thresh = turn_threshold_deg
    for i, ang in enumerate(angles):
        if ang < thresh:
            continue
        # Local maximum of turning, so a single corner is not counted twice.
        if i > 0 and angles[i - 1] > ang:
            continue
        if i < len(angles) - 1 and angles[i + 1] > ang:
            continue
        v = 0.5 * (speed[i] + speed[i + 1])
        ratios.append(float(v / mean_speed))
    return ratios


def corner_stats(
    trajectories: Sequence[Tuple[np.ndarray, np.ndarray]],
    turn_threshold_deg: float = 45.0,
) -> CornerStats:
    all_ratios: List[float] = []
    speeds, durations, npts = [], [], []
    for xy, t in trajectories:
        all_ratios.extend(corner_speed_ratios(xy, t, turn_threshold_deg))
        xy = np.asarray(xy, dtype=np.float64)
        t = np.asarray(t, dtype=np.float64)
        if len(xy) < 2:
            continue
        dist = float(np.sum(np.linalg.norm(np.diff(xy, axis=0), axis=1)))
        dur = float(t[-1] - t[0])
        if dur > 0:
            speeds.append(dist / dur)
            durations.append(dur)
        npts.append(len(xy))

    arr = np.array(all_ratios) if all_ratios else np.array([float("nan")])
    return CornerStats(
        n_trajectories=len(trajectories),
        n_corners=len(all_ratios),
        mean_corner_ratio=float(np.nanmean(arr)),
        median_corner_ratio=float(np.nanmedian(arr)),
        p10_corner_ratio=float(np.nanpercentile(arr, 10)),
        p90_corner_ratio=float(np.nanpercentile(arr, 90)),
        mean_speed=float(np.mean(speeds)) if speeds else float("nan"),
        mean_duration_s=float(np.mean(durations)) if durations else float("nan"),
        mean_points=float(np.mean(npts)) if npts else float("nan"),
    )


# =============================================================================
# FUTO LOADER
# =============================================================================


@dataclass
class SwipeSample:
    word: str
    xy: np.ndarray   # [N,2] keyboard-normalized
    t: np.ndarray    # [N] seconds from touch-down
    synthetic: bool = False


def load_futo(
    parquet_path: str,
    max_samples: Optional[int] = None,
    min_points: int = 6,
    require_supported: bool = True,
    verbose: bool = True,
) -> List[SwipeSample]:
    """Stream FUTO parquet row groups into normalized SwipeSamples."""
    import pyarrow.parquet as pq  # imported lazily: not needed for synthesis-only runs

    pf = pq.ParquetFile(parquet_path)
    out: List[SwipeSample] = []
    skipped = {"short": 0, "unsupported": 0, "bad_canvas": 0}

    for rg in range(pf.metadata.num_row_groups):
        table = pf.read_row_group(rg, columns=["word", "data", "canvas_width", "canvas_height"])
        words = table["word"].to_pylist()
        datas = table["data"].to_pylist()
        cws = table["canvas_width"].to_pylist()
        chs = table["canvas_height"].to_pylist()

        for w, d, cw, ch in zip(words, datas, cws, chs):
            if not w or not d or len(d) < min_points:
                skipped["short"] += 1
                continue
            if not cw or not ch or cw <= 0 or ch <= 0:
                skipped["bad_canvas"] += 1
                continue
            wn = normalize_word(w)
            if require_supported and not is_supported(wn):
                skipped["unsupported"] += 1
                continue
            if require_supported and not set(key_sequence(wn)) <= GEOM_CHARS:
                skipped["unsupported"] += 1
                continue

            xy = np.array([[p["x"], p["y"]] for p in d], dtype=np.float32)
            # FUTO stores x/y already normalized to the keyboard canvas;
            # canvas_width/canvas_height are dp metadata (e.g. 422x170), not
            # divisors. Dividing by them shrinks every coordinate ~400x. Detect
            # the convention per row so either encoding loads correctly.
            if xy.size and float(np.nanmax(np.abs(xy))) > 1.5:
                xy = xy / np.array([cw, ch], dtype=np.float32)
            t0 = d[0]["t"]
            t = np.array([(p["t"] - t0) / 1000.0 for p in d], dtype=np.float32)
            # Timestamps must be monotone for velocity to mean anything.
            if not np.all(np.diff(t) >= 0):
                order = np.argsort(t, kind="stable")
                xy, t = xy[order], t[order]

            out.append(SwipeSample(word=wn, xy=xy, t=t))
            if max_samples and len(out) >= max_samples:
                if verbose:
                    print(f"[futo] loaded {len(out)} samples (capped), skipped={skipped}")
                return out

        if verbose:
            print(f"[futo] row group {rg + 1}/{pf.metadata.num_row_groups}: {len(out)} kept")

    if verbose:
        print(f"[futo] loaded {len(out)} samples, skipped={skipped}")
    return out


def calibrate_layout(samples: Sequence[SwipeSample], sample_cap: int = 20000) -> dict:
    """Sanity-check QWERTY_LAYOUT against the observed FUTO coordinate frame.

    Fits an axis-aligned affine map (scale + offset per axis) taking layout key
    centers to observed touch positions, anchored on each swipe's first and last
    point (which reliably sit on the first and last key). A near-identity fit
    confirms FUTO's canvas is the keyboard view; anything else means the layout
    table needs adjusting before training.
    """
    src, dst = [], []
    for s in samples[:sample_cap]:
        seq = key_sequence(s.word)
        if len(seq) < 2 or len(s.xy) < 2:
            continue
        src.append(QWERTY_LAYOUT[seq[0]]); dst.append(s.xy[0])
        src.append(QWERTY_LAYOUT[seq[-1]]); dst.append(s.xy[-1])

    if len(src) < 100:
        return {"ok": False, "reason": "insufficient anchor points", "n": len(src)}

    S = np.asarray(src, dtype=np.float64)
    D = np.asarray(dst, dtype=np.float64)
    result = {"ok": True, "n_anchors": len(src)}
    for axis, name in ((0, "x"), (1, "y")):
        A = np.stack([S[:, axis], np.ones(len(S))], axis=1)
        (scale, offset), *_ = np.linalg.lstsq(A, D[:, axis], rcond=None)
        resid = float(np.sqrt(np.mean((A @ [scale, offset] - D[:, axis]) ** 2)))
        result[name] = {"scale": float(scale), "offset": float(offset), "rmse": resid}

    result["identity_like"] = bool(
        abs(result["x"]["scale"] - 1) < 0.15 and abs(result["x"]["offset"]) < 0.10
        and abs(result["y"]["scale"] - 1) < 0.20 and abs(result["y"]["offset"]) < 0.12
    )
    return result


# =============================================================================
# SYNTHETIC I/O
# =============================================================================


def write_synthetic_jsonl(samples: Iterable[SwipeSample], path: str) -> int:
    n = 0
    with open(path, "w", encoding="utf-8") as f:
        for s in samples:
            f.write(json.dumps({
                "word": s.word,
                "curve": {
                    "x": [round(float(v), 5) for v in s.xy[:, 0]],
                    "y": [round(float(v), 5) for v in s.xy[:, 1]],
                    "t": [int(round(float(v) * 1000)) for v in s.t],
                    "grid_name": "qwerty_en",
                },
                "synthetic": True,
            }) + "\n")
            n += 1
    return n


def read_synthetic_jsonl(path: str) -> List[SwipeSample]:
    out: List[SwipeSample] = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            rec = json.loads(line)
            c = rec["curve"]
            xy = np.stack([np.array(c["x"], dtype=np.float32),
                           np.array(c["y"], dtype=np.float32)], axis=1)
            t = np.array(c["t"], dtype=np.float32) / 1000.0
            out.append(SwipeSample(word=normalize_word(rec["word"]), xy=xy, t=t, synthetic=True))
    return out


if __name__ == "__main__":
    # Self-test that needs no torch, no parquet, no GPU.
    assert key_sequence("don't") == "dont"
    assert decode_label(encode_label("don't")) == "don't"
    assert is_plausible_word("mcp") and is_plausible_word("i'm")
    assert not is_plausible_word("accessibilitynodeinfo")
    ids, coords = encode_geometry("we're")
    assert len(ids) == 4 and coords.shape == (4, 2)

    # A square-cornered path should register a corner; a straight one should not.
    corner_xy = np.array([[0.1, 0.5], [0.3, 0.5], [0.5, 0.5], [0.5, 0.3], [0.5, 0.1]])
    corner_t = np.linspace(0, 0.4, 5)
    assert len(corner_speed_ratios(corner_xy, corner_t)) >= 1
    straight_xy = np.stack([np.linspace(0.1, 0.9, 8), np.full(8, 0.5)], axis=1)
    assert corner_speed_ratios(straight_xy, np.linspace(0, 0.5, 8)) == []

    xy_r, t_r = resample_trajectory(corner_xy, corner_t, 32)
    assert xy_r.shape == (32, 2)
    assert trajectory_features(xy_r, t_r).shape == (32, FEATURE_DIM)

    print("swipe_common self-test OK")

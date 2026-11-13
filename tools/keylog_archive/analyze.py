#!/usr/bin/env python3
"""Aggregate typing patterns from a keylog dataset build into one JSON summary.

Reads ``derived/current`` (or ``--build``) and writes only aggregates: counts,
rates, distributions and top-N lists. No sentences or message text leave this
script, so the output can feed a private report without carrying the corpus.

    python3 analyze.py --dict path/to/unified_dictionary.tsv --out summary.json
"""

from __future__ import annotations

import argparse
import gzip
import json
import math
import re
import statistics
import sys
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from keylog_store import default_root  # noqa: E402

LETTER = re.compile(r"^[a-z]$")
WORD = re.compile(r"^[A-Za-z][A-Za-z']*$")
EMOJI = re.compile("[\U0001F300-\U0001FAFF☀-➿]")
STRIP = ".,!?;:\"()[]{}*_`~<>"
TAP_SRC = (None, "TAP")


def rows(path: Path):
    with gzip.open(path, "rt", encoding="utf-8") as fh:
        for line in fh:
            yield json.loads(line)


def pct(values: list[float], q: float) -> float | None:
    if not values:
        return None
    values = sorted(values)
    i = min(len(values) - 1, max(0, int(round(q * (len(values) - 1)))))
    return values[i]


def hist(values: list[float], lo: float, hi: float, bins: int) -> list[int]:
    out = [0] * bins
    width = (hi - lo) / bins
    for v in values:
        if lo <= v < hi:
            out[int((v - lo) / width)] += 1
    return out


def load_dict(path: Path | None) -> set[str]:
    words: set[str] = set()
    if not path or not path.exists():
        return words
    for line in path.open(encoding="utf-8"):
        w = line.split("\t", 1)[0].strip().lower()
        if w:
            words.add(w)
    return words


def attempts_before_backspace(keys: list[list]) -> list[str]:
    """Letters on screen right before each backspace burst (the erroneous attempts)."""
    buf: list[str] = []
    out: list[str] = []
    prev_bk = False
    for t, c, k, xn, yn, dx, dy, dur, src in keys:
        if k in ("BKSP", "BKSP_WORD"):
            if not prev_bk and buf:
                out.append("".join(buf))
            if k == "BKSP_WORD":
                buf.clear()
            elif buf:
                buf.pop()
            prev_bk = True
            continue
        prev_bk = False
        if isinstance(c, str) and src in (None, "TAP", "LONGPRESS", "POPUP"):
            buf.append(c)
    return out


def first_mismatch(attempt: str, target: str) -> tuple[str, str] | None:
    a, b = attempt.lower(), target.lower()
    for x, y in zip(a, b):
        if x != y:
            if LETTER.match(x) and LETTER.match(y):
                return x, y
            return None
    return None


def analyze(build: Path, dictionary: set[str]) -> dict:
    card = json.loads((build / "DATASET.json").read_text())
    out: dict = {"dataset": {k: card[k] for k in ("build_id", "built_at", "time_range_local", "counts", "gates",
                                                  "split_days")}}
    out["dataset"]["files"] = {k: {"rows": v["rows"], "bytes": v["bytes"]} for k, v in card["files"].items()}

    # ---------------- words: input mix, vocabulary, style ----------------
    by_week_src: dict[str, Counter] = defaultdict(Counter)
    by_day_src: dict[str, Counter] = defaultdict(Counter)
    hour_dow = [[0] * 24 for _ in range(7)]
    app_src: dict[str, Counter] = defaultdict(Counter)
    typed_vocab: Counter = Counter()
    voice_vocab: Counter = Counter()
    style = Counter()
    typed_len: list[int] = []
    voice_len: list[int] = []
    first_seen: dict[str, str] = {}
    growth: list[tuple[str, int, int]] = []
    tokens = 0
    last_day = None

    for r in rows(build / "words.jsonl.gz"):
        # "TYPED" means the keyboard's own keys produced it (v4 slots, 2026-09 on).
        # v3 rows only say TYPING, which also covers pasted text, so they count
        # in the input mix but not in the vocabulary and style statistics.
        src = r.get("input") or r.get("src") or "?"
        ts = r["ts"]
        dt = datetime.fromisoformat(ts)
        week = dt.strftime("%G-W%V")
        by_week_src[week][src] += 1
        by_day_src[r["day"]][src] += 1
        hour_dow[dt.weekday()][dt.hour] += 1
        app_src[r.get("app") or "?"][src] += 1
        raw = r["word"]
        bare = raw.strip(STRIP)
        low = bare.lower()
        if src == "TYPED":
            if WORD.match(bare):
                typed_vocab[low] += 1
                typed_len.append(len(bare))
            style["typed_words"] += 1
            if bare == "i":
                style["lowercase_i"] += 1
            if bare == "I":
                style["capital_I"] += 1
            if WORD.match(bare) and bare.isupper() and len(bare) > 1:
                style["all_caps"] += 1
            if WORD.match(bare) and bare[0].isupper() and len(bare) > 1 and not bare.isupper():
                style["capitalized"] += 1
            if "'" in bare or "’" in bare:
                style["apostrophe_words"] += 1
            if low in ("dont", "cant", "wont", "im", "ive", "didnt", "doesnt", "isnt", "thats", "youre", "theyre",
                       "wasnt", "shouldnt", "couldnt", "wouldnt", "id", "ill", "lets"):
                style["apostrophe_dropped"] += 1
                style["drop:" + low] += 1
            if low in ("don't", "can't", "won't", "i'm", "i've", "didn't", "doesn't", "isn't", "that's", "you're",
                       "they're", "wasn't", "shouldn't", "couldn't", "wouldn't", "i'd", "i'll", "let's"):
                style["apostrophe_kept"] += 1
            if raw.endswith("?"):
                style["ends_question"] += 1
            if raw.endswith("!"):
                style["ends_exclaim"] += 1
            if raw.endswith("."):
                style["ends_period"] += 1
            if raw.endswith(","):
                style["ends_comma"] += 1
            if raw.endswith("..."):
                style["ellipsis"] += 1
            if EMOJI.search(raw):
                style["emoji_words"] += 1
        elif src == "VOICE":
            if WORD.match(bare):
                voice_vocab[low] += 1
                voice_len.append(len(bare))
        if WORD.match(bare):
            tokens += 1
            if low not in first_seen:
                first_seen[low] = r["day"]
            if r["day"] != last_day:
                if last_day is not None:
                    growth.append((last_day, tokens, len(first_seen)))
                last_day = r["day"]
    if last_day:
        growth.append((last_day, tokens, len(first_seen)))

    weeks = sorted(by_week_src)
    out["input_mix_weekly"] = [{"week": w, **by_week_src[w]} for w in weeks]
    out["words_per_day"] = [{"day": d, **by_day_src[d]} for d in sorted(by_day_src)]
    out["hour_dow"] = hour_dow
    top_apps = sorted(app_src.items(), key=lambda kv: -sum(kv[1].values()))[:14]
    out["apps"] = [{"app": a, **c} for a, c in top_apps]
    out["vocab"] = {
        "typed_unique": len(typed_vocab),
        "voice_unique": len(voice_vocab),
        "typed_tokens": sum(typed_vocab.values()),
        "voice_tokens": sum(voice_vocab.values()),
        "typed_top": typed_vocab.most_common(60),
        "voice_top": voice_vocab.most_common(40),
        "typed_mean_len": round(statistics.mean(typed_len), 2) if typed_len else None,
        "voice_mean_len": round(statistics.mean(voice_len), 2) if voice_len else None,
        "typed_len_hist": hist(typed_len, 1, 16, 15),
        "voice_len_hist": hist(voice_len, 1, 16, 15),
        "growth": growth[:: max(1, len(growth) // 120)] + ([growth[-1]] if growth else []),
    }
    if dictionary:
        oov = Counter({w: n for w, n in typed_vocab.items() if w not in dictionary and len(w) > 1})
        out["vocab"]["typed_oov_top"] = oov.most_common(60)
        out["vocab"]["typed_oov_unique"] = len(oov)
        out["vocab"]["typed_oov_token_share"] = round(sum(oov.values()) / max(1, sum(typed_vocab.values())), 4)
        # Words typed only the way people text: the personal dialect the engine must not fight.
    # distinctive: typed far more than dictated, relative to each channel's size
    tt, vt = max(1, sum(typed_vocab.values())), max(1, sum(voice_vocab.values()))
    lift = []
    for w, n in typed_vocab.items():
        if n >= 25:
            lift.append((w, n, math.log((n / tt) / ((voice_vocab.get(w, 0) + 1) / vt))))
    lift.sort(key=lambda x: -x[2])
    out["vocab"]["typed_more_than_spoken"] = [(w, n, round(l, 2)) for w, n, l in lift[:30]]
    vlift = []
    for w, n in voice_vocab.items():
        if n >= 25:
            vlift.append((w, n, math.log((n / vt) / ((typed_vocab.get(w, 0) + 1) / tt))))
    vlift.sort(key=lambda x: -x[2])
    out["vocab"]["spoken_more_than_typed"] = [(w, n, round(l, 2)) for w, n, l in vlift[:30]]
    out["style"] = dict(style)

    # ---------------- typing slots: speed, accuracy, touch ----------------
    iki: list[int] = []
    iki_by_hour: dict[int, list[int]] = defaultdict(list)
    dwell: list[int] = []
    notice: list[int] = []
    bigram_t: dict[str, list[int]] = defaultdict(list)
    key_stats: dict[str, dict] = defaultdict(lambda: {"n": 0, "sx": 0.0, "sy": 0.0, "sxx": 0.0, "syy": 0.0,
                                                    "xn": 0.0, "yn": 0.0, "edge": 0})
    density = [[0] * 40 for _ in range(16)]
    dx_all: list[float] = []
    dy_all: list[float] = []
    confusions: Counter = Counter()
    slot_quality: Counter = Counter()
    by_hour_q: dict[int, Counter] = defaultdict(Counter)
    by_week_q: dict[str, Counter] = defaultdict(Counter)
    routes: Counter = Counter()
    bar_index: Counter = Counter()
    layouts: Counter = Counter()
    word_speed_by_week: dict[str, list[float]] = defaultdict(list)
    n_slots = 0

    for r in rows(build / "typing_slots.jsonl.gz"):
        n_slots += 1
        dt = datetime.fromisoformat(r["ts"])
        q = r["quality"]
        slot_quality[q] += 1
        by_hour_q[dt.hour][q] += 1
        by_week_q[dt.strftime("%G-W%V")][q] += 1
        routes[r["route"]] += 1
        if r.get("barIndex") is not None:
            bar_index[r["barIndex"]] += 1
        layouts[(r["layout"] or {}).get("fp")] += 1
        keys = r["keys"]
        prev = None
        clean = q == "clean"
        letters = 0
        for key in keys:
            t, c, k, xn, yn, dx, dy, dur, src = key
            if k in ("BKSP", "BKSP_WORD"):
                if prev is not None and prev[2] not in ("BKSP", "BKSP_WORD") and isinstance(t, int):
                    gap = t - prev[0]
                    if 0 <= gap < 5000:
                        notice.append(gap)
                prev = key
                continue
            if src not in TAP_SRC or not isinstance(c, str):
                prev = key
                continue
            if isinstance(dur, int) and 0 < dur < 1500:
                dwell.append(dur)
            if prev is not None and prev[8] in TAP_SRC and isinstance(prev[1], str) and prev[2] not in ("BKSP",):
                gap = t - prev[0]
                if 0 < gap < 3000:
                    iki.append(gap)
                    iki_by_hour[dt.hour].append(gap)
                    if LETTER.match(c.lower()) and LETTER.match(prev[1].lower()):
                        bigram_t[prev[1].lower() + c.lower()].append(gap)
            low = c.lower()
            if (LETTER.match(low) and dx is not None and dy is not None and xn is not None and yn is not None
                    and abs(dx) <= 1 and abs(dy) <= 1):
                letters += 1
                if clean:
                    ks = key_stats[low]
                    ks["n"] += 1
                    ks["sx"] += dx
                    ks["sy"] += dy
                    ks["sxx"] += dx * dx
                    ks["syy"] += dy * dy
                    ks["xn"] += xn
                    ks["yn"] += yn
                    if abs(dx) > 0.4 or abs(dy) > 0.4:
                        ks["edge"] += 1
                    if len(dx_all) < 400000:
                        dx_all.append(dx)
                        dy_all.append(dy)
                if 0 <= xn < 1 and 0 <= yn < 1:
                    density[int(yn * 16)][int(xn * 40)] += 1
            prev = key
        if clean and letters >= 3 and len(keys) >= 3:
            span = keys[-1][0] - keys[0][0]
            if span > 0:
                cpm = (letters - 1) / span * 60000
                if 30 < cpm < 1500:
                    word_speed_by_week[dt.strftime("%G-W%V")].append(cpm / 5)
        target = r.get("intended") or r.get("final")
        if isinstance(target, str) and q in ("self_corrected", "struggle"):
            for att in attempts_before_backspace(keys):
                pair = first_mismatch(att, target)
                if pair:
                    confusions[pair] += 1

    out["typing"] = {
        "slots": n_slots,
        "quality": dict(slot_quality),
        "routes": dict(routes),
        "bar_index": {str(k): v for k, v in sorted(bar_index.items())},
        "iki_median": pct(iki, 0.5), "iki_p10": pct(iki, 0.1), "iki_p90": pct(iki, 0.9),
        "iki_hist": hist(iki, 0, 1000, 50),
        "dwell_median": pct(dwell, 0.5), "dwell_hist": hist(dwell, 0, 300, 30),
        "notice_median": pct(notice, 0.5), "notice_hist": hist(notice, 0, 2000, 40),
        "burst_wpm": round(60000 / pct(iki, 0.5) / 5, 1) if iki else None,
        "by_hour": [
            {"hour": h, "iki_median": pct(iki_by_hour[h], 0.5), "taps": len(iki_by_hour[h]),
             "slots": sum(by_hour_q[h].values()),
             "fix_rate": round((by_hour_q[h]["self_corrected"] + by_hour_q[h]["struggle"]) / max(1, sum(by_hour_q[h].values())), 4)}
            for h in range(24)
        ],
        "weekly": [
            {"week": w, "slots": sum(by_week_q[w].values()),
             "fix_rate": round((by_week_q[w]["self_corrected"] + by_week_q[w]["struggle"]) / max(1, sum(by_week_q[w].values())), 4),
             "wpm_median": round(pct(word_speed_by_week[w], 0.5), 1) if word_speed_by_week[w] else None}
            for w in sorted(by_week_q)
        ],
        "layouts": layouts.most_common(6),
    }
    common_bi = {b: v for b, v in bigram_t.items() if len(v) >= 150}
    ranked = sorted(((b, statistics.median(v), len(v)) for b, v in common_bi.items()), key=lambda x: x[1])
    out["typing"]["fastest_bigrams"] = ranked[:15]
    out["typing"]["slowest_bigrams"] = ranked[-15:][::-1]

    keys_out = {}
    for k, s in sorted(key_stats.items()):
        n = s["n"]
        if n < 50:
            continue
        mx, my = s["sx"] / n, s["sy"] / n
        keys_out[k] = {
            "n": n, "dx": round(mx, 4), "dy": round(my, 4),
            "sdx": round(math.sqrt(max(0.0, s["sxx"] / n - mx * mx)), 4),
            "sdy": round(math.sqrt(max(0.0, s["syy"] / n - my * my)), 4),
            "xn": round(s["xn"] / n, 4), "yn": round(s["yn"] / n, 4),
            "edge_rate": round(s["edge"] / n, 4),
        }
    out["touch"] = {
        "keys": keys_out,
        "density": density,
        "dx_hist": hist(dx_all, -0.8, 0.8, 32),
        "dy_hist": hist(dy_all, -0.8, 0.8, 32),
        "dx_sd": round(statistics.pstdev(dx_all), 4) if dx_all else None,
        "dy_sd": round(statistics.pstdev(dy_all), 4) if dy_all else None,
        "dx_mean": round(statistics.mean(dx_all), 4) if dx_all else None,
        "dy_mean": round(statistics.mean(dy_all), 4) if dy_all else None,
    }
    out["errors"] = {"confusions": [(a, b, n) for (a, b), n in confusions.most_common(40)],
                     "confusion_total": sum(confusions.values())}

    # ---------------- autocorrect decisions ----------------
    corr_week: dict[str, Counter] = defaultdict(Counter)
    reverted_pairs: Counter = Counter()
    kept_pairs: Counter = Counter()
    sub_pairs: Counter = Counter()
    for r in rows(build / "corrections.jsonl.gz"):
        week = datetime.fromisoformat(r["ts"]).strftime("%G-W%V")
        corr_week[week]["applied"] += 1
        pair = (r["typed"], r["applied"])
        if r["reverted"]:
            corr_week[week]["reverted"] += 1
            reverted_pairs[pair] += 1
        else:
            kept_pairs[(r["typed"].lower(), r["applied"].lower())] += 1
            a, b = r["typed"].lower(), r["applied"].lower()
            if len(a) == len(b):
                diffs = [(x, y) for x, y in zip(a, b) if x != y]
                if len(diffs) == 1 and LETTER.match(diffs[0][0]) and LETTER.match(diffs[0][1]):
                    sub_pairs[diffs[0]] += 1
    out["autocorrect"] = {
        "weekly": [{"week": w, **corr_week[w]} for w in sorted(corr_week)],
        "reverted_top": [(a, b, n) for (a, b), n in reverted_pairs.most_common(30)],
        "kept_top": [(a, b, n) for (a, b), n in kept_pairs.most_common(30) if a != b],
        "substitutions_fixed": [(a, b, n) for (a, b), n in sub_pairs.most_common(25)],
        "applied": sum(c["applied"] for c in corr_week.values()),
        "reverted": sum(c["reverted"] for c in corr_week.values()),
    }
    return out


def report_rows(o: dict) -> dict:
    """Flatten a summary into named row tables, one per chart, for a dashboard."""
    DOW = ["Mon","Tue","Wed","Thu","Fri","Sat","Sun"]
    ADJ = {}
    rows_ = ["qwertyuiop", "asdfghjkl", "zxcvbnm"]
    pos = {}
    for r, row in enumerate(rows_):
        for c, ch in enumerate(row):
            pos[ch] = (r, c + 0.5 * r)
    def adjacent(a, b):
        if a not in pos or b not in pos: return False
        (r1, c1), (r2, c2) = pos[a], pos[b]
        return abs(r1 - r2) <= 1 and abs(c1 - c2) <= 1.0 and (a != b)
    t = o["typing"]; v = o["vocab"]; ac = o["autocorrect"]; st = o["style"]; ds = o["dataset"]
    mix = o["input_mix_weekly"]
    tot = {}
    for w in mix:
        for k, n in w.items():
            if k != "week": tot[k] = tot.get(k, 0) + n
    words_total = sum(tot.values())
    rep = {}
    rep["overview"] = [{
        "days": ds["counts"]["days"],
        "first_day": ds["time_range_local"][0][:10], "last_day": ds["time_range_local"][1][:10],
        "events": ds["counts"]["lines_read"],
        "words": words_total,
        "typed_words": tot.get("TYPED", 0), "voice_words": tot.get("VOICE", 0), "paste_words": tot.get("PASTE", 0),
        "glide_words": tot.get("GLIDE", 0), "v3_words": tot.get("TYPING_V3", 0),
        "voice_share": round(tot.get("VOICE", 0) / words_total, 4),
        "burst_wpm": t["burst_wpm"], "iki_median": t["iki_median"], "dwell_median": t["dwell_median"],
        "notice_median": t["notice_median"],
        "typing_slots": t["slots"],
        "clean_share": round(t["quality"].get("clean", 0) / t["slots"], 4),
        "ac_applied": ac["applied"], "ac_reverted": ac["reverted"],
        "ac_revert_rate": round(ac["reverted"] / ac["applied"], 4),
        "dy_mean": o["touch"]["dy_mean"], "dx_mean": o["touch"]["dx_mean"],
        "dx_sd": o["touch"]["dx_sd"], "dy_sd": o["touch"]["dy_sd"],
        "typed_unique": v["typed_unique"], "voice_unique": v["voice_unique"],
        "typed_mean_len": v["typed_mean_len"], "voice_mean_len": v["voice_mean_len"],
        "oov_unique": v.get("typed_oov_unique"), "oov_share": v.get("typed_oov_token_share"),
        "bar_picks": sum(t["bar_index"].values()), "bar_pick_slot1": t["bar_index"].get("1", 0),
    }]
    rep["input_weekly"] = [{"week": w["week"], "TYPED": w.get("TYPED", 0), "GLIDE": w.get("GLIDE", 0), "TYPING_V3": w.get("TYPING_V3", 0),
                            "VOICE": w.get("VOICE", 0), "PASTE": w.get("PASTE", 0)} for w in mix]
    rep["hour_dow"] = [{"dow": DOW[d], "dow_i": d, "hour": h, "words": o["hour_dow"][d][h]} for d in range(7) for h in range(24)]
    rep["typing_hourly"] = [{"hour": r["hour"], "iki_median": r["iki_median"], "fix_rate": r["fix_rate"], "slots": r["slots"],
                             "wpm": round(60000 / r["iki_median"] / 5, 1) if r["iki_median"] else None} for r in t["by_hour"]]
    rep["typing_weekly"] = t["weekly"]
    rep["key_bias"] = [{"key": k, **s} for k, s in sorted(o["touch"]["keys"].items())]
    conf = []
    for a, b, n in o["errors"]["confusions"][:25]:
        conf.append({"typed": a, "meant": b, "count": n, "adjacent": adjacent(a, b), "how": "you fixed it"})
    rep["slips_self"] = conf
    rep["slips_auto"] = [{"typed": a, "meant": b, "count": n, "adjacent": adjacent(a, b)} for a, b, n in ac["substitutions_fixed"][:20]]
    rep["ac_weekly"] = [{"week": w["week"], "applied": w.get("applied", 0), "reverted": w.get("reverted", 0),
                         "revert_rate": round(w.get("reverted", 0) / w["applied"], 4) if w.get("applied") else None} for w in ac["weekly"]]
    rep["ac_reverted"] = [{"typed": a, "applied": b, "count": n} for a, b, n in ac["reverted_top"][:20]]
    rep["ac_kept"] = [{"typed": a, "applied": b, "count": n} for a, b, n in ac["kept_top"][:20]]
    rep["vocab_typed_lift"] = [{"word": w, "count": n, "lift": l} for w, n, l in v["typed_more_than_spoken"][:20]]
    rep["vocab_spoken_lift"] = [{"word": w, "count": n, "lift": l} for w, n, l in v["spoken_more_than_typed"][:20]]
    rep["vocab_oov"] = [{"word": w, "count": n} for w, n in v.get("typed_oov_top", [])[:40]]
    rep["vocab_top"] = [{"word": w, "count": n} for w, n in v["typed_top"][:30]]
    rep["word_len"] = [{"length": i + 1, "typed": v["typed_len_hist"][i], "voice": v["voice_len_hist"][i]} for i in range(15)]
    rep["vocab_growth"] = [{"day": d, "tokens": tk, "unique": u} for d, tk, u in v["growth"]]
    tw = max(1, st.get("typed_words", 1))
    rep["style"] = [
        {"metric": "Lowercase “i” as a word", "group": "i", "value": st.get("lowercase_i", 0)},
        {"metric": "Capital “I”", "group": "i", "value": st.get("capital_I", 0)},
        {"metric": "Contractions typed without the apostrophe", "group": "apos", "value": st.get("apostrophe_dropped", 0)},
        {"metric": "Contractions typed with it", "group": "apos", "value": st.get("apostrophe_kept", 0)},
        {"metric": "Words ending a sentence with a period", "group": "punct", "value": st.get("ends_period", 0)},
        {"metric": "Words followed by a comma", "group": "punct", "value": st.get("ends_comma", 0)},
        {"metric": "Questions", "group": "punct", "value": st.get("ends_question", 0)},
        {"metric": "Exclamations", "group": "punct", "value": st.get("ends_exclaim", 0)},
        {"metric": "ALL-CAPS words", "group": "case", "value": st.get("all_caps", 0)},
        {"metric": "Capitalized words", "group": "case", "value": st.get("capitalized", 0)},
        {"metric": "Words with emoji", "group": "case", "value": st.get("emoji_words", 0)},
        {"metric": "Typed words counted", "group": "total", "value": st.get("typed_words", 0)},
    ]
    rep["apostrophe_drops"] = sorted([{"word": k[5:], "count": n} for k, n in st.items() if k.startswith("drop:")], key=lambda r: -r["count"])[:12]
    rep["apps"] = [{"app": a["app"], "TYPED": a.get("TYPED", 0), "GLIDE": a.get("GLIDE", 0), "TYPING_V3": a.get("TYPING_V3", 0),
                    "VOICE": a.get("VOICE", 0), "PASTE": a.get("PASTE", 0)} for a in o["apps"]]
    rep["iki_hist"] = [{"ms": i * 20, "taps": n} for i, n in enumerate(t["iki_hist"])]
    rep["notice_hist"] = [{"ms": i * 50, "count": n} for i, n in enumerate(t["notice_hist"])]
    rep["slot_quality"] = [{"quality": k, "slots": n} for k, n in sorted(t["quality"].items(), key=lambda kv: -kv[1])]
    rep["bigrams_fast"] = [{"bigram": b, "ms": m, "n": n} for b, m, n in t["fastest_bigrams"][:10]]
    rep["bigrams_slow"] = [{"bigram": b, "ms": m, "n": n} for b, m, n in t["slowest_bigrams"][:10]]
    rep["dataset"] = [{"file": k, "rows": f["rows"], "bytes": f["bytes"]} for k, f in ds["files"].items()]
    rep["dataset_meta"] = [{"build_id": ds["build_id"], "built_at": ds["built_at"], "gates_passed": ds["gates"]["passed"],
                            "invalid_fraction": ds["gates"]["invalid_fraction"], "quarantined": ds["counts"]["quarantined"],
                            "duplicates": ds["counts"]["duplicate_lines_dropped"], "train_days": ds["split_days"].get("train", 0),
                            "val_days": ds["split_days"].get("val", 0), "test_days": ds["split_days"].get("test", 0),
                            **{f"q_{k}": n for k, n in t["quality"].items()}}]
    rep["density"] = [{"row": r, "col": c, "taps": n} for r, row in enumerate(o["touch"]["density"]) for c, n in enumerate(row) if n]
    return rep


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--build", type=Path, default=None)
    ap.add_argument("--dict", type=Path, default=None)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--report", type=Path, default=None, help="also write per-chart row tables here")
    args = ap.parse_args()
    build = args.build or (default_root() / "derived" / "current")
    summary = analyze(build.resolve(), load_dict(args.dict))
    args.out.write_text(json.dumps(summary, ensure_ascii=False))
    print(f"wrote {args.out} ({args.out.stat().st_size} bytes)")
    if args.report:
        args.report.write_text(json.dumps(report_rows(summary), ensure_ascii=False, separators=(",", ":")))
        print(f"wrote {args.report} ({args.report.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

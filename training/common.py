"""Shared helpers for the OmniBoard autocorrect training pipeline.

Everything here mirrors runtime behavior exactly:
- QWERTY geometry        -> ime/core/KeyboardLayout.kt
- Baseline scorer        -> ime/nlp/shared/CandidateScorer.kt (as called by
                            NgramSuggestionEngine.rank(), i.e. frequency = ln(freq+1))
- Bigram table semantics -> ime/nlp/shared/BigramTable.kt
- Session triage         -> build_dictionary.py::classify_session
"""

import math
import re
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DATA = Path(__file__).resolve().parent / "data"
DICT_TSV = REPO / "app/src/main/assets/ime/dict/unified_dictionary.tsv"
BIGRAM_TSV = REPO / "app/src/main/assets/ime/dict/final_mobile_bigrams.tsv"
AOSP_COMBINED = REPO / "dict_sources/en_wordlist.combined.txt"

BACKSPACE = "⌫"  # ⌫ as logged by HarvestJsonl
MAX_TOKEN_LEN = 22
TOKEN_RE = re.compile(r"[A-Za-z']+")
WORD_RE = re.compile(r"^[A-Za-z']+$")

# --- QWERTY geometry (port of KeyboardLayout.kt) ------------------------------

QWERTY_POSITIONS = {}
for row, (y, x0) in zip(("qwertyuiop", "asdfghjkl", "zxcvbnm"),
                        ((0.0, 0.0), (1.0, 0.5), (2.0, 1.5))):
    for i, ch in enumerate(row):
        QWERTY_POSITIONS[ch] = (x0 + i, y)


def key_distance(a: str, b: str) -> float:
    a, b = a.lower(), b.lower()
    if a == b:
        return 0.0
    pa, pb = QWERTY_POSITIONS.get(a), QWERTY_POSITIONS.get(b)
    if pa is None or pb is None:
        return 2.0
    d = math.hypot(pa[0] - pb[0], pa[1] - pb[1])
    return min(max(d, 0.0), 2.0)


def is_adjacent(a: str, b: str) -> bool:
    d = key_distance(a, b)
    return 0.0 < d < 1.5


# --- Session triage (port of build_dictionary.py) -----------------------------

def classify_session(text: str) -> str:
    if text.startswith('"') and text.endswith('"'):
        text = text[1:-1]
    if not text:
        return "empty"
    balanced_braces = text.count("{") > 0 and text.count("{") == text.count("}")
    balanced_brackets = text.count("[") > 0 and text.count("[") == text.count("]")
    special = sum(1 for c in text if not (c.isalnum() or c.isspace()))
    if balanced_braces or balanced_brackets or special / len(text) > 0.25:
        return "code_json"
    if any(s in text for s in ("http://", "https://", "www.")) or \
            text.strip().startswith("$") or "/data/" in text:
        return "url_command"
    tokens = text.split()
    if not tokens:
        return "empty"
    mean_len = sum(len(t) for t in tokens) / len(tokens)
    if any(len(t) > MAX_TOKEN_LEN for t in tokens) or mean_len > 12.0:
        return "concatenated"
    return "clean"


def is_letter_spaced(text: str) -> bool:
    """Early harvest bug: sessions logged as 'w h a t s'. Junk for training."""
    toks = text.split()
    return len(toks) >= 3 and sum(len(t) == 1 for t in toks) / len(toks) > 0.5


def tokenize(text: str):
    for tok in TOKEN_RE.findall(text):
        tok = tok.strip("'")
        if 1 <= len(tok) <= MAX_TOKEN_LEN and any(c.isalpha() for c in tok):
            yield tok


# --- Edit distance & alignment -------------------------------------------------

def levenshtein(a: str, b: str) -> int:
    if a == b:
        return 0
    if len(a) < len(b):
        a, b = b, a
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def align_ops(src: str, dst: str):
    """Damerau-Levenshtein alignment. Returns edit ops that turn src into dst.

    Used with src=intended (clean) and dst=typed (noisy), so ops describe how
    Sam corrupts words: sub(a->b), del(a) [dropped], ins(c after a) [added],
    swap(ab) [transposed].
    """
    n, m = len(src), len(dst)
    INF = n + m + 1
    d = [[0] * (m + 1) for _ in range(n + 1)]
    for i in range(n + 1):
        d[i][0] = i
    for j in range(m + 1):
        d[0][j] = j
    for i in range(1, n + 1):
        for j in range(1, m + 1):
            cost = 0 if src[i - 1] == dst[j - 1] else 1
            best = min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (i > 1 and j > 1 and src[i - 1] == dst[j - 2]
                    and src[i - 2] == dst[j - 1] and src[i - 1] != src[i - 2]):
                best = min(best, d[i - 2][j - 2] + 1)
            d[i][j] = best
    ops = []
    i, j = n, m
    while i > 0 or j > 0:
        if (i > 1 and j > 1 and src[i - 1] == dst[j - 2] and src[i - 2] == dst[j - 1]
                and src[i - 1] != src[i - 2] and d[i][j] == d[i - 2][j - 2] + 1):
            ops.append(("swap", src[i - 2] + src[i - 1]))
            i -= 2; j -= 2
        elif i > 0 and j > 0 and d[i][j] == d[i - 1][j - 1] + (src[i - 1] != dst[j - 1]):
            if src[i - 1] != dst[j - 1]:
                ops.append(("sub", src[i - 1], dst[j - 1]))
            i -= 1; j -= 1
        elif i > 0 and d[i][j] == d[i - 1][j] + 1:
            ops.append(("del", src[i - 1]))
            i -= 1
        else:
            ops.append(("ins", dst[j - 1], src[i - 1] if i > 0 else "^"))
            j -= 1
    ops.reverse()
    return ops


# --- Keystroke trace replay ------------------------------------------------------

def replay_trace(trace: str) -> str:
    """Simulate literal keys (incl. backspace) -> resulting string."""
    buf = []
    for ch in trace:
        if ch == BACKSPACE:
            if buf:
                buf.pop()
        else:
            buf.append(ch)
    return "".join(buf)


def recover_pre_correction(trace: str, final: str):
    """Recover the erroneous string a backspace burst abandoned.

    'wpr⌫⌫ord' vs final 'word' -> 'wprd': the deleted chars B[L-k:L] replace
    the retyped chars at the same positions in the final word.
    Returns None when the trace has no backspaces or recovery looks wild.
    """
    if BACKSPACE not in trace:
        return None
    buf, bursts, i = [], [], 0
    while i < len(trace):
        if trace[i] == BACKSPACE:
            k = 0
            while i < len(trace) and trace[i] == BACKSPACE:
                k += 1; i += 1
            bursts.append(("".join(buf), k))
            del buf[max(0, len(buf) - k):]
        else:
            buf.append(trace[i]); i += 1
    pre = final
    for b, k in bursts:
        length = len(b)
        if length == 0 or k > length:
            return None
        pre = pre[:max(0, length - k)] + b[length - k:length] + pre[length:]
    if not pre or levenshtein(pre.lower(), final.lower()) > max(3, len(final) // 2):
        return None
    return pre


# --- Runtime data tables ---------------------------------------------------------

def load_unigrams(path=DICT_TSV):
    """lower word -> raw freq (int). Runtime scoring uses ln(freq+1)."""
    uni = {}
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 2:
                continue
            try:
                freq = int(float(parts[1]))
            except ValueError:
                continue
            w = parts[0].lower()
            if w not in uni or freq > uni[w]:
                uni[w] = freq
    return uni


class BigramTable:
    """Port of shared/BigramTable.kt (bonus / hasHit / getFrequency)."""

    def __init__(self, path=BIGRAM_TSV):
        self.table = {}
        self.max_by_prev = {}
        with open(path, encoding="utf-8") as fh:
            for line in fh:
                parts = line.rstrip("\n").split("\t")
                if len(parts) < 2:
                    continue
                pair, freq = parts[0], parts[1]
                sp = pair.find(" ")
                if sp <= 0:
                    continue
                try:
                    freq = int(freq)
                except ValueError:
                    continue
                w1, w2 = pair[:sp].lower(), pair[sp + 1:].lower()
                self.table.setdefault(w1, {})[w2] = freq
                if freq > self.max_by_prev.get(w1, 0):
                    self.max_by_prev[w1] = freq

    def bonus(self, prev, candidate) -> float:
        if not prev:
            return 0.0
        row = self.table.get(prev.lower())
        if not row:
            return 0.0
        freq = row.get(candidate.lower())
        if freq is None:
            return 0.0
        max_freq = max(1, self.max_by_prev.get(prev.lower(), 1))
        return math.log(freq + 1.0) / math.log(max_freq + 1.0)

    def has_hit(self, prev, candidate) -> bool:
        if not prev:
            return False
        row = self.table.get(prev.lower())
        return bool(row) and candidate.lower() in row

    def get_frequency(self, prev, candidate) -> int:
        if not prev:
            return 0
        return self.table.get(prev.lower(), {}).get(candidate.lower(), 0)


# --- Candidate generation (mirror of SymSpell retrieval) --------------------------

class CandidateGen:
    """symspellpy over unified_dictionary.tsv, memoized.

    Returns rows shaped like training/inference candidates:
    [term, edit_dist, ln_unigram_freq] (bigram count added by caller per-context).
    The typed string itself is always included ("keep what they typed" class).
    """

    def __init__(self, unigrams=None, max_candidates=10):
        from symspellpy import SymSpell, Verbosity
        self._verbosity = Verbosity.ALL
        self.unigrams = unigrams if unigrams is not None else load_unigrams()
        self.max_candidates = max_candidates
        self.sym = SymSpell(max_dictionary_edit_distance=2, prefix_length=7)
        for w, f in self.unigrams.items():
            self.sym.create_dictionary_entry(w, max(1, f))
        self._cache = {}

    def ln_freq(self, word: str) -> float:
        return math.log(self.unigrams.get(word.lower(), 0) + 1.0)

    def lookup(self, typed: str):
        typed = typed.lower()
        hit = self._cache.get(typed)
        if hit is not None:
            return hit
        try:
            suggestions = self.sym.lookup(typed, self._verbosity,
                                          max_edit_distance=2,
                                          include_unknown=False,
                                          transfer_casing=False)
        except ValueError:  # symspellpy rejects some inputs (e.g. too long)
            suggestions = []
        ranked = sorted(suggestions, key=lambda s: (s.distance, -s.count))
        cands = [[s.term, float(s.distance), round(math.log(s.count + 1.0), 4)]
                 for s in ranked[: self.max_candidates]]
        if not any(c[0] == typed for c in cands):
            cands.append([typed, 0.0, round(self.ln_freq(typed), 4)])
        self._cache[typed] = cands
        return cands


# --- Baseline scorer (port of CandidateScorer.kt via NgramSuggestionEngine.rank) --

BIGRAM_WEIGHT = 5.0
BIGRAM_NO_HIT_PENALTY = 0.2
APOSTROPHE_EXACT_BONUS = -20.0
APOSTROPHE_TYPO_BONUS = -10.0
EXACT_MATCH_BONUS = -100.0
SPATIAL_TRANSPOSITION_COST = 0.3
SPATIAL_LENGTH_DIFF_COST = 0.5

POSSESSIVE_CONTEXTS = {"my", "your", "his", "her", "their", "our", "its"}
DETERMINERS = {"the", "this", "that", "these", "those", "a", "an", "some",
               "any", "each", "every"}
CONTRACTIONS = {"i'm", "i'd", "i'll", "i've", "we're", "we'll", "they're",
                "you're", "he's", "she's", "it's", "that's", "what's",
                "who's", "here's", "there's"}
PREFER_IS_CONTEXT = {"this", "that", "it", "he", "she", "what", "which",
                     "who", "there", "here"}
PREFER_ID_CONTEXT = {"and", "but", "so", "or", "because", "if", "when",
                     "well", "yeah", "yes", "no"}


def spatial_cost(typed: str, candidate: str) -> float:
    cost, i = 0.0, 0
    n = min(len(typed), len(candidate))
    while i < n:
        t, c = typed[i], candidate[i]
        if t == c:
            i += 1
            continue
        if i + 1 < n and typed[i + 1] == c and candidate[i + 1] == t:
            cost += SPATIAL_TRANSPOSITION_COST
            i += 2
            continue
        cost += key_distance(t, c)
        i += 1
    cost += abs(len(typed) - len(candidate)) * SPATIAL_LENGTH_DIFF_COST
    return cost


def baseline_score(typed, candidate, edit_distance, prev, freq_ln, bigrams):
    """Penalty score, lower = better. typed/candidate/prev must be lowercase.

    PersonalPreferences (veto layer) intentionally omitted: it stays outside
    both the baseline and the NN at runtime, so it cancels in comparison.
    """
    grammar = 0.0
    bigram_block = 0.0
    if prev:
        if (prev in POSSESSIVE_CONTEXTS or prev in DETERMINERS) and candidate in CONTRACTIONS:
            grammar = 50.0
        typed_bf = bigrams.get_frequency(prev, typed)
        cand_bf = bigrams.get_frequency(prev, candidate)
        if typed_bf > 0 and typed_bf >= cand_bf * 2:
            bigram_block = 20.0

    score = edit_distance + grammar + bigram_block
    score += spatial_cost(typed, candidate)

    bonus = bigrams.bonus(prev, candidate)
    score -= BIGRAM_WEIGHT * bonus
    if prev and not bigrams.has_hit(prev, candidate):
        score += BIGRAM_NO_HIT_PENALTY

    typed_na = typed.replace("'", "")
    cand_na = candidate.replace("'", "")
    if "'" in candidate:
        if cand_na == typed_na:
            score += APOSTROPHE_EXACT_BONUS
        elif spatial_cost(typed_na, cand_na) < 2.0:
            score += APOSTROPHE_TYPO_BONUS

    if edit_distance == 0.0 and spatial_cost(typed, candidate) == 0.0:
        score += EXACT_MATCH_BONUS

    score -= freq_ln * 0.1

    if typed == "id" and prev:
        if candidate == "is" and prev in PREFER_IS_CONTEXT:
            score -= 50.0
        elif candidate == "i'd" and prev in PREFER_ID_CONTEXT:
            score -= 50.0

    return score

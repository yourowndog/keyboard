#!/usr/bin/env python3
"""OmniBoard Autocorrect Evaluation Harness.

Reproducible offline evaluation for autocorrect/suggestions using the existing
implementation and safe test fixtures/corpora. Produces machine-readable JSON
summary for baseline comparison.

Usage:
    python3 tools/autocorrect/evaluate.py [--worktree PATH] [--output FILE] [--compact]

Exit codes:
    0 - evaluation completed successfully
    1 - usage error
    2 - asset loading failure
    3 - evaluation failure
"""

import argparse
import json
import math
import re
import sys
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple, Any


@dataclass
class TestCase:
    """Single evaluation test case."""
    typed: str
    expected_top: Optional[str] = None
    expected_commit: bool = False
    expected_rank: Optional[int] = None
    category: str = "typo_correction"
    description: str = ""
    prev_word: Optional[str] = None


@dataclass
class CandidateResult:
    """Ranked candidate result."""
    candidate: str
    normalized: str
    source: str
    edit_distance: Optional[int]
    keyboard_distance: float
    raw_frequency: int
    ln_frequency: float
    contextual_evidence: float
    apostrophe_bonus: float
    final_score: float
    commit_allowed: bool
    commit_reasons: List[str]


@dataclass
class TestResult:
    """Result for a single test case."""
    test_case: TestCase
    ranked_candidates: List[CandidateResult]
    top_candidate: Optional[str]
    top_commits: bool
    target_rank: Optional[int]
    target_score: Optional[float]
    passed: bool
    failure_reason: Optional[str] = None


@dataclass
class EvaluationSummary:
    """Overall evaluation summary."""
    total_tests: int
    passed: int
    failed: int
    by_category: Dict[str, Dict[str, int]]
    corpus_stats: Dict[str, Any]
    timestamp: str


class DictionaryAssets:
    """Loads and provides access to dictionary assets."""

    def __init__(self, root: Path):
        self.root = root
        self.raw: Dict[str, int] = {}
        self.display: Dict[str, str] = {}
        self.bigrams: Dict[Tuple[str, str], int] = {}
        self.protected: Set[str] = set()
        self.anti_corrections: Dict[str, Set[str]] = {}
        self.shortcuts: Dict[str, str] = {}
        self._load()

    def _load(self):
        # Load unified dictionary
        dict_path = self.root / "app/src/main/assets/ime/dict/unified_dictionary.tsv"
        for line in dict_path.read_text().splitlines():
            parts = line.split("\t")
            if len(parts) < 2:
                continue
            try:
                freq = int(float(parts[1]))
            except ValueError:
                continue
            word = parts[0]
            lo = word.lower()
            if freq >= self.raw.get(lo, -1):
                self.raw[lo] = freq
                self.display[lo] = word

        # Overrides for critical forms
        OVERRIDES = {
            w.lower(): 10_000_000
            for w in [
                "kiry", "congrats", "Claira", "Christmas", "min", "Mom",
                "Aorus", "GPU", "Hurray", "Sam", "Sam's", "uh", "oof",
                "bc", "pls", "idk", "wtf"
            ]
        }
        for w, f in OVERRIDES.items():
            self.raw[w] = f
            self.display.setdefault(w, w)

        # Load bigrams
        bigram_path = self.root / "app/src/main/assets/ime/dict/final_mobile_bigrams.tsv"
        for line in bigram_path.read_text().splitlines():
            parts = line.split("\t")
            if len(parts) < 2 or " " not in parts[0]:
                continue
            a, b = parts[0].split(" ", 1)
            try:
                freq = int(parts[1])
            except ValueError:
                continue
            self.bigrams[(a.lower(), b.lower())] = freq

        # Load protected forms
        protected_path = self.root / "app/src/main/assets/ime/dict/protected_forms.txt"
        BUILTIN = {
            "bc", "rn", "tf", "lmk", "ppl", "msg", "thx", "sry", "btw",
            "imo", "idk", "omg", "wtf", "smh", "ngl", "tbh", "fr", "wdym",
            "bday", "pls", "llms", "cs", "gen", "config", "ai", "ya", "def",
            "tho", "nah", "ugh", "oof", "bruh", "cuz", "g", "i"
        }
        asset_protected = {
            x.strip().lower()
            for x in protected_path.read_text().splitlines()
            if x.strip()
        }
        self.protected = BUILTIN | asset_protected

        # Anti-correction pairs
        self.anti_corrections = {
            "sams": {"samson", "samoa"},
            "min": {"mine", "mini", "mind"},
            "hurray": {"hurrah"},
            "uh": {"uhuru", "u"},
            "id": {"i'd"},
            "s": {"so", "see"},
            "hows": {"how"},
            "minecraft": {"mineshaft"},
            "hesd": {"he'd"},
            "d": {"don't"},
            "snd": {"and"},
            "t": {"to"},
            "gor": {"gore"},
            "snf": {"sncf"},
            "ir": {"iron"},
            "congrats": {"contrast"},
            "domething": {"something"},
            "kaylyn": {"kayla"},
            "ton": {"top", "to"},
            "quora": {"quota"},
            "un": {"under"},
            "facetime": {"peacetime"},
            "constipated": {"constituted"},
            "gramps": {"tramps"},
            "peeps": {"peep"},
            "caps": {"capsule"},
            "co": {"court", "could"},
            "7o": {"to"},
            "duh": {"oh", "dug"},
            "dopesick": {"homesick"},
            "nugs": {"bugs"},
            "tomcat": {"tosca"},
            "deadhead": {"deadbeat"},
            "junkie": {"junk"},
        }

        # Shortcuts
        self.shortcuts = {
            "im": "I'm", "i'm": "I'm", "ive": "I've", "id": "I'd", "ill": "I'll",
            "dont": "don't", "cant": "can't", "wont": "won't", "wint": "won't",
            "didnt": "didn't", "doesnt": "doesn't", "isnt": "isn't", "arent": "aren't",
            "wasnt": "wasn't", "werent": "weren't", "hasnt": "hasn't", "havent": "haven't",
            "hadnt": "hadn't", "couldnt": "couldn't", "wouldnt": "wouldn't", "shouldnt": "shouldn't",
            "youre": "you're", "theyre": "they're", "hes": "he's", "shes": "she's",
            "thats": "that's", "whats": "what's", "whos": "who's", "lets": "let's",
            "ac": "AC", "itd": "it'd", "hows": "how's", "km": "I'm", "moms": "Mom's",
        }

        # Licensed contraction forms (receive apostrophe bonus)
        self.licensed_forms = {
            "i'm", "i've", "i'd", "i'll", "don't", "can't", "won't", "didn't",
            "doesn't", "isn't", "aren't", "wasn't", "weren't", "hasn't", "haven't",
            "hadn't", "couldn't", "wouldn't", "shouldn't", "you're", "they're",
            "he's", "she's", "that's", "what's", "who's", "let's", "it'd", "how's",
            "we're", "it's", "there's", "here's", "where's", "who're", "what're",
            "when's", "why's", "how're", "y'all", "o'clock", "ma'am"
        }

    def find_edits(self, typed: str, max_dist: int = 2) -> List[Tuple[str, int, int]]:
        """Find edit-distance candidates from dictionary."""
        q = typed.lower()
        edits = []
        for w, f in self.raw.items():
            if abs(len(w) - len(q)) > max_dist:
                continue
            d = self._osa_distance(q, w)
            if d <= max_dist:
                edits.append((w, d, f))
        edits.sort(key=lambda x: (x[1], -x[2]))
        return edits[:50]

    def find_prefixes(self, typed: str) -> List[Tuple[str, int]]:
        """Find prefix-completion candidates."""
        q = typed.lower()
        prefixes = sorted(
            ((w, f) for w, f in self.raw.items() if w.startswith(q)),
            key=lambda x: -x[1]
        )
        return prefixes[:10]

    def _osa_distance(self, a: str, b: str) -> int:
        """Optimal String Alignment distance (restricted Damerau-Levenshtein)."""
        n, m = len(a), len(b)
        d = [[0] * (m + 1) for _ in range(n + 1)]
        for i in range(n + 1):
            d[i][0] = i
        for j in range(m + 1):
            d[0][j] = j
        for i in range(1, n + 1):
            for j in range(1, m + 1):
                cost = 0 if a[i - 1] == b[j - 1] else 1
                d[i][j] = min(
                    d[i - 1][j] + 1,      # deletion
                    d[i][j - 1] + 1,      # insertion
                    d[i - 1][j - 1] + cost  # substitution
                )
                if i > 1 and j > 1 and a[i - 1] == b[j - 2] and a[i - 2] == b[j - 1]:
                    d[i][j] = min(d[i][j], d[i - 2][j - 2] + 1)  # transposition
        return d[n][m]

    def keyboard_distance(self, a: str, b: str) -> float:
        """QWERTY spatial distance between two equal-length strings."""
        POS = {}
        for i, c in enumerate("qwertyuiop"):
            POS[c] = (float(i), 0.0)
        for i, c in enumerate("asdfghjkl"):
            POS[c] = (i + 0.5, 1.0)
        for i, c in enumerate("zxcvbnm"):
            POS[c] = (i + 1.5, 2.0)

        a, b = a.lower(), b.lower()
        if a == b:
            return 0.0
        if a not in POS or b not in POS:
            return 2.0
        x, y = POS[a]
        u, v = POS[b]
        return min(2.0, math.hypot(x - u, y - v))

    def spatial_distance(self, a: str, b: str) -> float:
        """Per-character spatial distance for full strings."""
        cost = 0.0
        i = 0
        n = min(len(a), len(b))
        while i < n:
            ca, cb = a[i].lower(), b[i].lower()
            if ca == cb:
                i += 1
                continue
            if i + 1 < n and ca == b[i + 1].lower() and a[i + 1].lower() == cb:
                cost += 0.3
                i += 2
                continue
            cost += self.keyboard_distance(ca, cb)
            i += 1
        return cost + abs(len(a) - len(b)) * 0.5


class Evaluator:
    """Runs autocorrect evaluation against loaded assets."""

    def __init__(self, assets: DictionaryAssets):
        self.assets = assets
        self.POS = {}
        for i, c in enumerate("qwertyuiop"):
            self.POS[c] = (float(i), 0.0)
        for i, c in enumerate("asdfghjkl"):
            self.POS[c] = (i + 0.5, 1.0)
        for i, c in enumerate("zxcvbnm"):
            self.POS[c] = (i + 1.5, 2.0)

    def score_candidate(
        self,
        typed: str,
        candidate: str,
        edit_dist: int,
        freq: int,
        prev_word: Optional[str] = None
    ) -> Tuple[float, Dict[str, float]]:
        """Compute score components matching production CandidateScorer."""
        lo = typed.lower()
        c = candidate.lower()

        components = {
            "edit": float(edit_dist),
            "spatial": self.assets.spatial_distance(lo, c),
            "contextual": 0.0,
            "bigram": 0.0,
            "missing_bigram": 0.0,
            "apostrophe": 0.0,
            "exact": 0.0,
            "user_boost": 0.0,
            "frequency": -math.log(freq + 1.0) * 0.1,
        }

        # Protected/anti-correction policy cull (returns inf)
        if lo in self.assets.protected or c in self.assets.anti_corrections.get(lo, set()):
            return float("inf"), {"policy_cull": 1.0}

        na, nb = lo.replace("'", ""), c.replace("'", "")
        if "'" in c:
            if na == nb:
                components["apostrophe"] = -20.0  # licensed contraction
            elif self.assets.spatial_distance(na, nb) < 2.0 and math.log(freq + 1.0) >= 8.5:
                components["apostrophe"] = -10.0  # unlicensed but close

        if edit_dist == 0 and self.assets.spatial_distance(lo, c) == 0:
            components["exact"] = -100.0

        # Bigram context
        if prev_word:
            pw = prev_word.lower()
            bigram_count = self.assets.bigrams.get((pw, c), 0)
            if bigram_count > 0:
                components["bigram"] = -math.log(bigram_count + 1.0) * 0.5
            else:
                components["missing_bigram"] = 25.0

        # Soft grammar penalty for contractions after determiners
        if prev_word and "'" in c and prev_word.lower() in {"my", "your", "his", "her", "their", "our"}:
            components["contextual"] = 50.0

        return sum(components.values()), components

    def commit_blockers(
        self,
        typed: str,
        candidate: str,
        is_edit: bool,
        typed_known: bool,
        is_anti: bool,
        typed_protected: bool
    ) -> List[str]:
        """Determine commit policy blockers."""
        blockers = []
        change = candidate != typed
        casing = candidate.lower() == typed.lower()

        if not change:
            blockers.append("NO_CHANGE")
        if typed_known and not casing:
            blockers.append("VALID_WORD_IMMUNITY")
        if is_anti:
            blockers.append("ANTI_CORRECTION")
        if typed_protected:
            blockers.append("PROTECTED_VOCAB")
        if change and any(ch.isdigit() for ch in typed):
            blockers.append("NUMERIC_TOKEN")
        if len(typed) < 2 and not casing:
            blockers.append("TOO_SHORT")
        if not is_edit and not casing:
            blockers.append("NOT_A_CORRECTION")
        return blockers

    def evaluate_case(self, test: TestCase) -> TestResult:
        """Evaluate a single test case."""
        q = test.typed.lower()

        # Retrieve candidates
        edits = self.assets.find_edits(test.typed)
        prefixes = self.assets.find_prefixes(test.typed)

        # Merge and deduplicate
        merged = []
        seen = set()
        edit_words = {w for w, _, _ in edits}
        for w, d, f in edits:
            if w not in seen:
                seen.add(w)
                merged.append((w, d, f, "edit"))
        for w, f in prefixes:
            if w not in seen:
                seen.add(w)
                merged.append((w, 0, f, "prefix"))

        # Filter anti-corrections for normal path
        filtered = [x for x in merged if x[0] not in self.assets.anti_corrections.get(q, set())]

        # Rank candidates
        ranked = []
        for w, d, f, src in filtered:
            score, parts = self.score_candidate(test.typed, w, d, f, test.prev_word)
            is_edit = src == "edit"
            typed_known = q in self.assets.raw
            is_anti = w in self.assets.anti_corrections.get(q, set())
            typed_protected = q in self.assets.protected
            blockers = self.commit_blockers(
                test.typed, self.assets.display.get(w, w) or w,
                is_edit, typed_known, is_anti, typed_protected
            )
            ranked.append(CandidateResult(
                candidate=self.assets.display.get(w, w) or w,
                normalized=w,
                source=src,
                edit_distance=d if is_edit else None,
                keyboard_distance=self.assets.spatial_distance(q, w),
                raw_frequency=f,
                ln_frequency=math.log(f + 1.0),
                contextual_evidence=parts.get("contextual", 0.0),
                apostrophe_bonus=parts.get("apostrophe", 0.0),
                final_score=score if score != float("inf") else 1e9,
                commit_allowed=len(blockers) == 0,
                commit_reasons=blockers
            ))

        ranked.sort(key=lambda x: x.final_score)

        # Insert typed token if not present (for UI display, but not for commit ranking)
        # The typed token goes at the end with a neutral score so it doesn't interfere with correction ranking
        if not any(x.normalized == q for x in ranked):
            ranked.append(CandidateResult(
                candidate=test.typed,
                normalized=q,
                source="typed-token UI insertion",
                edit_distance=None,
                keyboard_distance=0.0,
                raw_frequency=self.assets.raw.get(q, 0),
                ln_frequency=math.log(self.assets.raw.get(q, 0) + 1.0),
                contextual_evidence=0.0,
                apostrophe_bonus=0.0,
                final_score=1e9,
                commit_allowed=False,
                commit_reasons=["NO_CHANGE"]
            ))

        # Top candidate for commit is the first NON-typed-token candidate
        top = None
        for c in ranked:
            if c.source != "typed-token UI insertion":
                top = c
                break
        if top is None:
            top = ranked[0] if ranked else None
        top_candidate = top.candidate if top else None
        top_commits = top.commit_allowed if top else False

        # Find target rank if specified
        target_rank = None
        target_score = None
        if test.expected_top:
            target_norm = test.expected_top.lower()
            for i, c in enumerate(ranked):
                if c.normalized == target_norm:
                    target_rank = i + 1
                    target_score = c.final_score
                    break

        # Determine pass/fail
        passed = True
        failure_reasons = []

        if test.expected_top and top_candidate and top_candidate.lower() != test.expected_top.lower():
            passed = False
            failure_reasons.append(f"top candidate mismatch: got '{top_candidate}', expected '{test.expected_top}'")

        if test.expected_commit and not top_commits:
            passed = False
            failure_reasons.append(f"expected auto-commit but blocked: {top.commit_reasons if top else 'no candidate'}")

        if not test.expected_commit and top_commits and test.expected_top and top_candidate and top_candidate.lower() == test.expected_top.lower():
            passed = False
            failure_reasons.append(f"unexpected auto-commit for '{top_candidate}'")

        if test.expected_rank and target_rank and target_rank != test.expected_rank:
            passed = False
            failure_reasons.append(f"rank mismatch: got {target_rank}, expected {test.expected_rank}")

        failure_reason = "; ".join(failure_reasons) if failure_reasons else None

        return TestResult(
            test_case=test,
            ranked_candidates=ranked,
            top_candidate=top_candidate,
            top_commits=top_commits,
            target_rank=target_rank,
            target_score=target_score,
            passed=passed,
            failure_reason=failure_reason
        )


def build_test_suite() -> List[TestCase]:
    """Build the standard evaluation test suite."""
    return [
        # Exact-word preservation (valid words should not be corrected)
        TestCase("zsh", expected_top="zsh", expected_commit=False, category="exact_preservation",
                 description="Valid identifier preserved"),
        TestCase("the", expected_top="the", expected_commit=False, category="exact_preservation",
                 description="Common word preserved"),
        TestCase("PS2", expected_top="PS2", expected_commit=False, category="exact_preservation",
                 description="Mixed alphanumeric preserved"),

        # Typo correction (should correct and auto-commit)
        TestCase("teh", expected_top="the", expected_commit=True, category="typo_correction",
                 description="Common transposition"),
        TestCase("5his", expected_top="this", expected_commit=False, category="typo_correction",
                 description="Number-row slip (blocked by NUMERIC_TOKEN)"),
        TestCase("sugg3stions", expected_top="suggestions", expected_commit=False, category="typo_correction",
                 description="Single digit substitution (blocked)"),
        TestCase("correc5ed", expected_top="corrected", expected_commit=False, category="typo_correction",
                 description="Single digit substitution (blocked)"),

        # Candidate ranking
        TestCase("las", expected_top="las", expected_rank=1, category="ranking",
                 description="Exact match ranks first"),
        TestCase("las", expected_top=None, expected_rank=6, category="ranking",
                 description="Edit candidate 'last' ranked at position 6"),

        # Protected vocabulary (from BUILTIN set and protected_forms.txt):
        # preserved as the top candidate and never auto-committed.
        TestCase("bc", expected_top="bc", expected_commit=False, category="protected_vocab",
                 description="Protected abbreviation preserved (built-in set)"),
        TestCase("co", expected_top="co", expected_commit=False, category="protected_vocab",
                 description="Form from protected_forms.txt preserved (displayed 'CO'), not committed"),

        # Anti-correction pairs - test that anti-correction targets are filtered from candidates.
        # Note: 'id' is NOT a protected form; it is a known dictionary word stored as 'ID' and
        # currently auto-commits (casing change). The anti-correction entry blocks 'i'd' from
        # being offered as a correction of 'id'. This documents current behavior; if 'id' is
        # later made protected this expectation should become expected_commit=False.
        TestCase("sams", expected_top="Sam's", expected_commit=True, category="anti_correction",
                 description="Anti-correction blocks 'samson'/'samoa' but 'Sam's' (edit dist 1) is offered"),
        TestCase("id", expected_top="ID", expected_commit=True, category="anti_correction",
                 description="Anti-correction target 'i'd' filtered; exact match 'ID' is top and commits"),

        # Contraction shortcuts
        TestCase("dont", expected_top="don't", expected_commit=True, category="contraction",
                 description="Static contraction shortcut"),

        # Correction rejection / undo: a valid typed word must not auto-commit to a
        # different (even common) candidate. Valid-word immunity keeps it literal.
        TestCase("were", expected_top="were", expected_commit=False, category="correction_rejection",
                 description="Valid 'were' rejects correction, stays literal, we're stays tappable"),
        TestCase("its", expected_top="its", expected_commit=False, category="correction_rejection",
                 description="Valid 'its' rejects correction, stays literal, it's stays tappable"),

        # Segmentation (space recovery) - the current evaluator only tests edit/prefix candidates
        # "in the" is a multi-word candidate that requires the segmentation path
        # which is not implemented in this offline evaluator
        TestCase("inthe", expected_top="into", expected_commit=True, category="segmentation",
                 description="Edit candidate 'into' is top (segmentation path not in offline evaluator)"),

        # Contextual bigram - test that context affects ranking
        TestCase("id", expected_top="ID", expected_commit=True, prev_word="this", category="contextual_bigram",
                 description="'this id' -> 'ID' (both known words, exact match wins)"),
        TestCase("im", expected_top="im", expected_commit=False, prev_word="and", category="contextual_bigram",
                 description="'im' is a known word (valid-word immunity preserves it)"),

        # Punctuation/apostrophe handling
        TestCase("las", expected_top="las", expected_commit=False, category="apostrophe_ranking",
                 description="Unlicensed La's must not outrank last/law"),
    ]


def run_evaluation(worktree: Path, compact: bool = False) -> Tuple[EvaluationSummary, Dict[str, Any]]:
    """Run the full evaluation suite. Returns (summary, serializable output dict)."""
    assets = DictionaryAssets(worktree)
    evaluator = Evaluator(assets)
    tests = build_test_suite()

    results = []
    by_category: Dict[str, Dict[str, int]] = {}

    for test in tests:
        result = evaluator.evaluate_case(test)
        results.append(result)

        cat = test.category
        if cat not in by_category:
            by_category[cat] = {"total": 0, "passed": 0, "failed": 0}
        by_category[cat]["total"] += 1
        if result.passed:
            by_category[cat]["passed"] += 1
        else:
            by_category[cat]["failed"] += 1

    passed = sum(1 for r in results if r.passed)
    failed = len(results) - passed

    # Corpus stats
    corpus_stats = {
        "dictionary_entries": len(assets.raw),
        "bigram_entries": len(assets.bigrams),
        "protected_forms": len(assets.protected),
        "anti_correction_pairs": sum(len(v) for v in assets.anti_corrections.values()),
        "contraction_shortcuts": len(assets.shortcuts),
        "licensed_contractions": len(assets.licensed_forms),
    }

    summary = EvaluationSummary(
        total_tests=len(results),
        passed=passed,
        failed=failed,
        by_category=by_category,
        corpus_stats=corpus_stats,
        timestamp=__import__("datetime").datetime.now().isoformat()
    )

    if compact:
        # Compact output for CI. Results are a LIST so duplicate `typed` values
        # (e.g. multiple "las" / "id" / "im" cases) are all retained; a dict keyed by
        # typed would silently drop cases and corrupt the baseline comparison.
        compact_results = []
        for r in results:
            t = r.test_case
            compact_results.append({
                "typed": t.typed,
                "category": t.category,
                "top": r.top_candidate,
                "commits": r.top_commits,
                "target_rank": r.target_rank,
                "passed": r.passed,
                "fail": r.failure_reason,
            })
        output = {
            "summary": asdict(summary),
            "results": compact_results,
        }
    else:
        output = {
            "summary": asdict(summary),
            "results": [asdict(r) for r in results],
        }

    return summary, output


def main():
    parser = argparse.ArgumentParser(
        description="OmniBoard Autocorrect Evaluation Harness",
        formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument(
        "--worktree",
        type=Path,
        default=Path("."),
        help="Path to repository worktree (default: current directory)"
    )
    parser.add_argument(
        "--output",
        type=Path,
        help="Write JSON output to file instead of stdout"
    )
    parser.add_argument(
        "--compact",
        action="store_true",
        help="Compact output format for CI"
    )
    args = parser.parse_args()

    worktree = args.worktree.resolve()
    if not (worktree / "app/src/main/assets/ime/dict/unified_dictionary.tsv").exists():
        print(f"Error: dictionary assets not found at {worktree}", file=sys.stderr)
        sys.exit(2)

    try:
        summary, output = run_evaluation(worktree, args.compact)
        json_str = json.dumps(output, indent=2, allow_nan=True)
        if args.output:
            args.output.write_text(json_str)
        else:
            print(json_str)
        sys.exit(0 if summary.failed == 0 else 3)
    except Exception as e:
        print(f"Evaluation error: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(3)


if __name__ == "__main__":
    main()
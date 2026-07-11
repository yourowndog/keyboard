#!/usr/bin/env python3
"""
segment_recovery.py - Word Segmentation space recovery for usage_harvest.md

Usage:
    python3 segment_recovery.py <path_to_usage_harvest.md>

Steps:
    1. Builds seed dictionary (sam_unigram_seed.tsv) from clean prose lines.
    2. Filters target concatenated lines by allowed conversational apps (register-gate).
    3. Runs min-cost DP segmentation.
    4. Triages by confidence threshold and writes segmentation_recovery.tsv.
    5. Reports anonymized statistics and the high-confidence recovery headline.
"""

import os
import sys
import re
import math
from collections import Counter, defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
DEFAULT_INPUT = REPO / "data/harvest/raw/usage_harvest.md"
DEFAULT_OUTPUT_DIR = REPO / "data/harvest/derived"

# --- TUNABLE THRESHOLDS & CONSTANTS ---
SPECIAL_CHAR_RATIO_LIMIT = 0.25
MAX_TOKEN_LENGTH_PROSE = 22
MEAN_WORD_LENGTH_PROSE_LIMIT = 12.0
VOICE_FILLER_RATIO_LIMIT = 0.30

# Cost per character confidence threshold
CONFIDENCE_THRESHOLD = 1.6

# Allowed Conversational Apps Registry
CONVERSATIONAL_APPS = {
    "com.google.android.apps.messaging",
    "com.facebook.orca",
    "com.anthropic.claude",
    "com.openai.chatgpt",
    "com.beeper.android",
    "UNKNOWN_APP"
}

def get_filler_ratio(text):
    text_lower = text.lower()
    clean_text = re.sub(r"[^\w\s\']", " ", text_lower)
    tokens = clean_text.split()
    if not tokens:
        return 0.0

    filler_singles = {'um', 'uh', 'like', 'so'}
    single_count = sum(1 for t in tokens if t in filler_singles)

    phrase_count = 0
    i = 0
    while i < len(tokens) - 1:
        phrase = f"{tokens[i]} {tokens[i+1]}"
        if phrase == "you know" or phrase == "i mean":
            phrase_count += 1
            i += 2
        else:
            i += 1

    total_filler_tokens = single_count + (phrase_count * 2)
    return total_filler_tokens / len(tokens)

def classify_session_line(tag, text):
    if text.startswith("\"") and text.endswith("\""):
        text = text[1:-1]

    # 1. code_json
    code_signatures = ['{"', '=>', 'Math.', 'const ', 'function', 'git@', 'sudo ', './']
    has_signature = any(sig in text for sig in code_signatures)
    has_balanced_braces = ('{' in text and '}' in text and text.count('{') == text.count('}'))
    has_balanced_brackets = ('[' in text and ']' in text and text.count('[') == text.count(']'))
    
    special_chars = sum(1 for c in text if not (c.isalnum() or c.isspace()))
    special_char_ratio = special_chars / len(text) if len(text) > 0 else 0.0
    
    if has_signature or has_balanced_braces or has_balanced_brackets or special_char_ratio > SPECIAL_CHAR_RATIO_LIMIT:
        return "code_json"

    # 2. url_command
    has_url = any(s in text for s in ['http://', 'https://', 'www.'])
    is_command = text.strip().startswith('$') or 'cd ' in text or '/data/' in text
    if has_url or is_command:
        return "url_command"

    # 3. concatenated
    tokens = text.split()
    has_long_token = any(len(t) > MAX_TOKEN_LENGTH_PROSE for t in tokens)
    mean_word_length = sum(len(t) for t in tokens) / len(tokens) if tokens else 0.0
    if has_long_token or mean_word_length > MEAN_WORD_LENGTH_PROSE_LIMIT:
        return "concatenated"

    # 4. filler_heavy (VOICE only)
    if tag == "SESSION:VOICE":
        filler_ratio = get_filler_ratio(text)
        if filler_ratio > VOICE_FILLER_RATIO_LIMIT:
            return "filler_heavy"

    # 5. clean_prose
    return "clean_prose"

def parse_line(line):
    line = line.strip()
    if not line or line.startswith("#") or line.startswith("Copy to") or line.startswith("---") or line.startswith("<!--"):
        return None

    parts = [p.strip() for p in line.split("|")]
    if len(parts) < 2:
        return None

    tag_part = parts[0]
    tag_match = re.match(r"^\[([^\]]+)\]\s+([\d\-]+\s+[\d:]+)", tag_part)
    if not tag_match:
        tag_match_legacy = re.match(r"^\[([^\]]+)\]", tag_part)
        if tag_match_legacy:
            tag = tag_match_legacy.group(1)
            timestamp = "N/A"
        else:
            return None
    else:
        tag = tag_match.group(1)
        timestamp = tag_match.group(2)

    content = parts[1]
    
    app = "UNKNOWN_APP"
    for p in parts[2:]:
        if p.startswith("app:"):
            app_match = re.match(r"^app:\s*\"([^\"]+)\"", p)
            if app_match:
                app = app_match.group(1)
                break

    return {
        "tag": tag,
        "timestamp": timestamp,
        "content": content,
        "app": app,
        "raw": line
    }

def build_seed_dict(parsed_events, output_dir):
    """Builds unigram dictionary from clean prose session logs."""
    unigrams = Counter()
    
    for ev in parsed_events:
        if "SESSION" in ev["tag"]:
            text = ev["content"]
            if text.startswith("\"") and text.endswith("\""):
                text = text[1:-1]
                
            bucket = classify_session_line(ev["tag"], ev["content"])
            if bucket in ("clean_prose", "filler_heavy"):
                clean_text = re.sub(r"[^\w\s\']", " ", text.lower())
                tokens = clean_text.split()
                for token in tokens:
                    # Clean apostrophes/contractions and only keep alphabetic words
                    if token.replace("'", "").isalpha():
                        unigrams[token] += 1
                        
    # Save seed dictionary
    seed_path = os.path.join(output_dir, "sam_unigram_seed.tsv")
    with open(seed_path, "w", encoding="utf-8") as f:
        for word, count in sorted(unigrams.items(), key=lambda x: x[1], reverse=True):
            f.write(f"{word}\t{count}\n")
            
    print(f"🌱 Built Sam unigram seed dictionary: {len(unigrams)} words saved to {seed_path}")
    return unigrams

def viterbi_segment(s, word_costs, max_word_len, unknown_penalty):
    """Finds optimal word breaks using minimum cost Viterbi DP."""
    n = len(s)
    dp = [float("inf")] * (n + 1)
    dp[0] = 0.0
    parent = [0] * (n + 1)
    
    s_lower = s.lower()

    for i in range(1, n + 1):
        start_limit = max(0, i - max_word_len - 5)
        for j in range(start_limit, i):
            word = s_lower[j:i]
            if word in word_costs:
                cost = dp[j] + word_costs[word]
            else:
                cost = dp[j] + unknown_penalty + (3.0 * len(word))

            if cost < dp[i]:
                dp[i] = cost
                parent[i] = j

    cuts = []
    curr = n
    while curr > 0:
        prev = parent[curr]
        cuts.append((prev, curr))
        curr = prev
    cuts.reverse()
    
    return cuts, dp[n]

def execute_recovery(filepath, output_dir=DEFAULT_OUTPUT_DIR):
    if not os.path.exists(filepath):
        print(f"❌ Error: File not found: {filepath}", file=sys.stderr)
        sys.exit(1)

    output_dir = os.path.abspath(output_dir)
    os.makedirs(output_dir, exist_ok=True)

    # Parse file
    parsed_events = []
    with open(filepath, "r", encoding="utf-8") as f:
        for line in f:
            ev = parse_line(line)
            if ev:
                parsed_events.append(ev)

    # Step 1: Build seed dictionary
    unigrams = build_seed_dict(parsed_events, output_dir)
    if not unigrams:
        print("❌ Error: No clean prose session logs found to build dictionary seed.", file=sys.stderr)
        sys.exit(1)

    # Compute costs
    total_freq = sum(unigrams.values())
    log_total = math.log10(total_freq) if total_freq > 0 else 1.0
    word_costs = {w: log_total - math.log10(f) for w, f in unigrams.items()}
    max_word_len = max(len(w) for w in unigrams)
    unknown_penalty = log_total + 10.0

    # Step 2 & 3 & 4: Triage target lines and run segmentation
    session_events = [ev for ev in parsed_events if "SESSION" in ev["tag"]]
    
    total_spaceless = 0
    attempted_count = 0
    skipped_by_register_counts = defaultdict(int)
    
    high_confidence_count = 0
    low_confidence_count = 0
    
    recovery_records = []
    spaceless_lengths = []

    for ev in session_events:
        text = ev["content"]
        if text.startswith("\"") and text.endswith("\""):
            text = text[1:-1]
            
        bucket = classify_session_line(ev["tag"], ev["content"])
        
        # Check if line is concatenated/spaceless
        if bucket == "concatenated" and " " not in text:
            total_spaceless += 1
            spaceless_lengths.append(len(text))
            
            # Step 2: Register Gate check
            if ev["app"] in CONVERSATIONAL_APPS:
                attempted_count += 1
                
                # Step 3: Viterbi Word Break
                cuts, total_cost = viterbi_segment(text, word_costs, max_word_len, unknown_penalty)
                words = [text[start:end] for start, end in cuts]
                segmented = " ".join(words)
                
                # Compute confidence cost per character
                char_count = len(text)
                confidence_cost = total_cost / char_count if char_count > 0 else float("inf")
                
                # Step 4: Triage by threshold
                if confidence_cost < CONFIDENCE_THRESHOLD:
                    tier = "high_confidence"
                    high_confidence_count += 1
                else:
                    tier = "low_confidence"
                    low_confidence_count += 1
                    
                recovery_records.append((ev["content"], segmented, ev["app"], f"{confidence_cost:.4f}", tier))
            else:
                skipped_by_register_counts[ev["app"]] += 1
                recovery_records.append((ev["content"], "N/A (skipped)", ev["app"], "N/A", "skipped_by_register"))

    # Step 5: Write segmentation_recovery.tsv
    recovery_path = os.path.join(output_dir, "segmentation_recovery.tsv")
    with open(recovery_path, "w", encoding="utf-8") as rf:
        # Header
        rf.write("original_line\tsegmented_output\tapp\tconfidence_cost\ttier\n")
        for orig, seg, app, conf, tier in recovery_records:
            # Strip trailing quotes or escape tab characters
            rf.write(f"{orig}\t{seg}\t{app}\t{conf}\t{tier}\n")

    # Output Diagnostics Report to stdout
    print("\n" + "=" * 80)
    print("📈 SEGMENTATION RECOVERY REPORT (RECONSTRUCTED SPACES)")
    print("=" * 80)
    print(f"  • Total Spaceless Lines Seen:    {total_spaceless}")
    print(f"  • Attempted (Conversational):    {attempted_count}")
    print(f"  • Skipped (Non-Conversational):  {sum(skipped_by_register_counts.values())}")
    
    print("\n📱 SKIPPED SESSIONS BY APP PACKAGE:")
    for app, cnt in sorted(skipped_by_register_counts.items(), key=lambda x: x[1], reverse=True):
        print(f"  • {app:<48} : {cnt:>4} skipped")
        
    print("\n🎯 TRIAGE CONFIDENCE COUNTS:")
    print(f"  • High Confidence recoveries:     {high_confidence_count}")
    print(f"  • Low Confidence reviews:        {low_confidence_count}")
    
    print("\n📏 ANONYMIZED SPACELESS LENGTH STATISTICS:")
    if spaceless_lengths:
        print(f"  • Min Character Length:           {min(spaceless_lengths)}")
        print(f"  • Max Character Length:           {max(spaceless_lengths)}")
        print(f"  • Mean Character Length:          {sum(spaceless_lengths)/len(spaceless_lengths):.2f}")
        
        # Length bands
        length_bands = Counter()
        for length in spaceless_lengths:
            if length <= 30:
                length_bands["15-30 chars"] += 1
            elif length <= 60:
                length_bands["31-60 chars"] += 1
            elif length <= 100:
                length_bands["61-100 chars"] += 1
            else:
                length_bands["101+ chars"] += 1
        for band in ["15-30 chars", "31-60 chars", "61-100 chars", "101+ chars"]:
            print(f"    - {band:<12} : {length_bands[band]:>4} sessions")

    print("\n" + "-" * 80)
    print(f"🌟 HEADLINE: {high_confidence_count} conversational lines recovered at high confidence.")
    print("  👉 This is the exact number of clean text entries reclaimed for model training.")
    print("-" * 80)
    print(f"💾 Reversible mappings written to: {recovery_path}")
    print("=" * 80 + "\n")

if __name__ == "__main__":
    input_path = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_INPUT
    output_path = Path(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_OUTPUT_DIR
    execute_recovery(input_path, output_path)

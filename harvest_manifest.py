#!/usr/bin/env python3
"""
harvest_manifest.py - Measurement-Only Triage Manifest for usage_harvest.md

Usage:
    python3 harvest_manifest.py <path_to_usage_harvest.md>

Reports counts, app provenance, triage quality metrics, intent classification, 
and writes a machine-readable JSON summary alongside the input file.
Deletes nothing, edits nothing, and writes no cleaned corpus.
"""

import os
import sys
import re
import json
from collections import Counter, defaultdict

# --- TUNABLE THRESHOLDS & CONSTANTS ---
SPECIAL_CHAR_RATIO_LIMIT = 0.25
MAX_TOKEN_LENGTH_PROSE = 22
MEAN_WORD_LENGTH_PROSE_LIMIT = 12.0
VOICE_FILLER_RATIO_LIMIT = 0.30

# Rejection outcome look-ahead parameters
REJECTION_LOOKAHEAD_LIMIT = 10

def get_filler_ratio(text):
    """Calculates the ratio of filler tokens in the text."""
    text_lower = text.lower()
    # Replace non-word/non-apostrophe characters with spaces
    clean_text = re.sub(r"[^\w\s\']", " ", text_lower)
    tokens = clean_text.split()
    if not tokens:
        return 0.0

    # Singles: um, uh, like, so
    filler_singles = {'um', 'uh', 'like', 'so'}
    single_count = sum(1 for t in tokens if t in filler_singles)

    # Pairs: "you know", "i mean"
    phrase_count = 0
    i = 0
    while i < len(tokens) - 1:
        phrase = f"{tokens[i]} {tokens[i+1]}"
        if phrase == "you know" or phrase == "i mean":
            phrase_count += 1
            i += 2  # skip both tokens
        else:
            i += 1

    total_filler_tokens = single_count + (phrase_count * 2)
    return total_filler_tokens / len(tokens)

def classify_session_line(tag, text):
    """
    Classifies a session line into one of five triage buckets
    based on priority order (first match wins).
    """
    # Clean text quotes if present
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
    """Parses a raw log line, extracting tag, timestamp, content, and metadata."""
    line = line.strip()
    if not line or line.startswith("#") or line.startswith("Copy to") or line.startswith("---") or line.startswith("<!--"):
        return None

    parts = [p.strip() for p in line.split("|")]
    if len(parts) < 2:
        return None

    # Tag & Timestamp
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
    
    # Metadata extraction
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

def run_diagnostics(filepath):
    if not os.path.exists(filepath):
        print(f"❌ Error: File not found: {filepath}", file=sys.stderr)
        sys.exit(1)

    parsed_events = []
    timestamps = []
    
    # Counters
    total_lines = 0
    empty_lines = 0
    comment_lines = 0

    with open(filepath, "r", encoding="utf-8") as f:
        for raw_line in f:
            total_lines += 1
            line = raw_line.strip()
            if not line:
                empty_lines += 1
                continue
            if line.startswith("#") or line.startswith("Copy to") or line.startswith("---") or line.startswith("<!--"):
                comment_lines += 1
                continue

            event = parse_line(raw_line)
            if event:
                parsed_events.append(event)
                if event["timestamp"] != "N/A":
                    timestamps.append(event["timestamp"])

    print("\n" + "=" * 80)
    print("📈 DIAGNOSTIC REPORT FOR OMNIBOARD HARVEST CORPUS")
    print("=" * 80)
    print(f"  • Source File:          {filepath}")
    print(f"  • Total Raw Lines:      {total_lines}")
    print(f"  • Log Event Records:    {len(parsed_events)}")
    if timestamps:
        print(f"  • Date Range:           {min(timestamps)}  to  {max(timestamps)}")
    print("=" * 80)

    # --- PART 1: PROSE TRIAGE ---
    session_events = [ev for ev in parsed_events if "SESSION" in ev["tag"]]
    session_count = len(session_events)
    
    bucket_counts = Counter()
    app_bucket_counts = defaultdict(lambda: defaultdict(int))
    app_session_totals = Counter()

    for ev in session_events:
        bucket = classify_session_line(ev["tag"], ev["content"])
        bucket_counts[bucket] += 1
        app_bucket_counts[ev["app"]][bucket] += 1
        app_session_totals[ev["app"]] += 1

    print("\n" + "-" * 80)
    print("PART 1 — PROSE TRIAGE (SESSION LINES ONLY)")
    print("-" * 80)
    
    # 1A. Bucket counts
    print("A. Bucket Distribution:")
    buckets = ["code_json", "url_command", "concatenated", "filler_heavy", "clean_prose"]
    for b in buckets:
        cnt = bucket_counts[b]
        pct = (cnt / session_count * 100) if session_count > 0 else 0
        print(f"  • {b:<15} : {cnt:>5} ({pct:>5.2f}%)")

    # 1B. Trainable prose denominator
    trainable_count = bucket_counts["clean_prose"] + bucket_counts["filler_heavy"]
    trainable_pct = (trainable_count / session_count * 100) if session_count > 0 else 0
    print(f"\n🌟 HEADLINE: Real Trainable-Prose Denominator (clean_prose + filler_heavy):")
    print(f"  👉 {trainable_count} lines ({trainable_pct:.2f}% of all session logs)")

    # 1C. Cross-tab: App × Bucket
    print("\nB. Cross-Tabulation (App Package × Triage Bucket):")
    top_apps = [app for app, _ in app_session_totals.most_common(12)]
    
    # Print headers
    header_str = f"  {'Application Package':<40} | " + " | ".join(f"{b[:10]:>10}" for b in buckets)
    print("  " + "-" * len(header_str))
    print(header_str)
    print("  " + "-" * len(header_str))
    
    for app in top_apps:
        row_cells = []
        for b in buckets:
            row_cells.append(f"{app_bucket_counts[app][b]:>10}")
        print(f"  {app[:40]:<40} | " + " | ".join(row_cells))
        
    # Other row
    other_counts = defaultdict(int)
    other_total = 0
    for app, counts in app_bucket_counts.items():
        if app not in top_apps:
            other_total += app_session_totals[app]
            for b in buckets:
                other_counts[b] += counts[b]
    if other_total > 0:
        row_cells = []
        for b in buckets:
            row_cells.append(f"{other_counts[b]:>10}")
        print(f"  {'[Other App Packages]':<40} | " + " | ".join(row_cells))
    print("  " + "-" * len(header_str))

    # 1D. Spaceless-line rate per app
    print("\nC. Spaceless/Concatenated Line Rate Per Application:")
    for app, total in sorted(app_session_totals.items(), key=lambda x: x[1], reverse=True)[:15]:
        cat_count = app_bucket_counts[app]["concatenated"]
        pct = (cat_count / total * 100) if total > 0 else 0
        print(f"  • {app:<48} : {pct:>5.2f}% ({cat_count}/{total})")

    # --- PART 2: LABELED-EVENT AUDIT ---
    print("\n" + "-" * 80)
    print("PART 2 — LABELED-EVENT AUDIT")
    print("-" * 80)
    
    event_tags = ["ACCEPTED", "REJECTED", "INSISTED", "NEW_WORD", "MANUAL_FIX"]
    event_counts = Counter()
    app_event_counts = defaultdict(lambda: defaultdict(int))
    app_event_totals = Counter()
    
    for ev in parsed_events:
        if ev["tag"] in event_tags:
            event_counts[ev["tag"]] += 1
            app_event_counts[ev["app"]][ev["tag"]] += 1
            app_event_totals[ev["app"]] += 1
            
    total_events = sum(event_counts.values())
    
    print("A. Labeled Event Counts:")
    for tag in event_tags:
        cnt = event_counts[tag]
        pct = (cnt / total_events * 100) if total_events > 0 else 0
        print(f"  • {tag:<12} : {cnt:>5} ({pct:>5.2f}%)")
        
    print("\nB. Cross-Tabulation (App Package × Event Type):")
    top_event_apps = [app for app, _ in app_event_totals.most_common(12)]
    
    # Print headers
    header_str = f"  {'Application Package':<40} | " + " | ".join(f"{tag:>10}" for tag in event_tags)
    print("  " + "-" * len(header_str))
    print(header_str)
    print("  " + "-" * len(header_str))
    
    for app in top_event_apps:
        row_cells = []
        for tag in event_tags:
            row_cells.append(f"{app_event_counts[app][tag]:>10}")
        print(f"  {app[:40]:<40} | " + " | ".join(row_cells))
        
    # Other row
    other_event_counts = defaultdict(int)
    other_event_total = 0
    for app, counts in app_event_counts.items():
        if app not in top_event_apps:
            other_event_total += app_event_totals[app]
            for tag in event_tags:
                other_event_counts[tag] += counts[tag]
    if other_event_total > 0:
        row_cells = []
        for tag in event_tags:
            row_cells.append(f"{other_event_counts[tag]:>10}")
        print(f"  {'[Other App Packages]':<40} | " + " | ".join(row_cells))
    print("  " + "-" * len(header_str))

    # --- INTENT CLASSIFICATION OUTCOMES ---
    intent_outcomes = {
        "validated_reject": 0,
        "manual_fix": 0,
        "capitulation": 0,
        "validated_fix": 0,
        "leave_alone": 0,
        "unresolved": 0
    }
    
    for idx, ev in enumerate(parsed_events):
        tag = ev["tag"]
        
        if tag == "REJECTED":
            parts = ev["content"].split(" ← ")
            if not parts:
                intent_outcomes["unresolved"] += 1
                continue
            typed_word = parts[0].strip().lower()
            
            outcome = "unresolved"
            for look_idx in range(idx + 1, min(idx + 1 + REJECTION_LOOKAHEAD_LIMIT, len(parsed_events))):
                next_ev = parsed_events[look_idx]
                
                # Stop if app context switches
                if next_ev["app"] != ev["app"]:
                    break
                    
                # 1. manual_fix outcome
                if next_ev["tag"] == "MANUAL_FIX":
                    fix_parts = next_ev["content"].split(" → ")
                    if len(fix_parts) >= 2:
                        orig = fix_parts[0].strip("\"").lower()
                        if orig == typed_word:
                            outcome = "manual_fix"
                            break
                            
                # 2. capitulation outcome
                elif next_ev["tag"] == "ACCEPTED":
                    acc_parts = next_ev["content"].split(" → ")
                    if len(acc_parts) >= 2:
                        acc_orig = acc_parts[0].strip().lower()
                        if acc_orig == typed_word or typed_word.startswith(acc_orig) or acc_orig.startswith(typed_word):
                            outcome = "capitulation"
                            break
                            
                # 3. validated_reject outcome
                elif "SESSION" in next_ev["tag"]:
                    sess_text = next_ev["content"].lower()
                    if typed_word in sess_text:
                        outcome = "validated_reject"
                        break
            
            intent_outcomes[outcome] += 1

        elif tag == "ACCEPTED":
            parts = ev["content"].split(" → ")
            if len(parts) >= 2:
                typed = parts[0].strip().lower()
                corrected = parts[1].strip().lower()
                if typed != corrected:
                    intent_outcomes["validated_fix"] += 1
                else:
                    intent_outcomes["unresolved"] += 1
            else:
                intent_outcomes["unresolved"] += 1

        elif tag in ("INSISTED", "NEW_WORD"):
            intent_outcomes["leave_alone"] += 1

        elif tag == "MANUAL_FIX":
            intent_outcomes["unresolved"] += 1

    print("\nC. Intent Classification Outcome Distribution:")
    all_outcomes = ["validated_reject", "manual_fix", "capitulation", "validated_fix", "leave_alone", "unresolved"]
    for o in all_outcomes:
        cnt = intent_outcomes[o]
        print(f"  • {o:<20} : {cnt:>5}")

    # Headline: Calibration Denominator
    usable_fixes = intent_outcomes["validated_fix"]
    usable_control = intent_outcomes["leave_alone"]
    usable_signal = intent_outcomes["capitulation"] + intent_outcomes["validated_reject"]
    
    print(f"\n🌟 HEADLINE: Calibration Denominator usable groups:")
    print(f"  • validated fixes (confusion-matrix seed)       : {usable_fixes:>5}")
    print(f"  • identity/leave-alone (false-positive control) : {usable_control:>5}")
    print(f"  • capitulation/fights (false-positive signal)   : {usable_signal:>5}")

    # Write output to harvest_manifest.json
    json_path = os.path.join(os.path.dirname(os.path.abspath(filepath)), "harvest_manifest.json")
    output_data = {
        "file_metrics": {
            "total_lines": total_lines,
            "empty_lines": empty_lines,
            "comment_lines": comment_lines,
            "valid_log_records": len(parsed_events),
            "earliest_timestamp": min(timestamps) if timestamps else "N/A",
            "latest_timestamp": max(timestamps) if timestamps else "N/A"
        },
        "triage_buckets": dict(bucket_counts),
        "app_bucket_cross_tab": {app: dict(buckets) for app, buckets in app_bucket_counts.items()},
        "spaceless_rates": {app: (app_bucket_counts[app]["concatenated"] / app_session_totals[app] * 100 if app_session_totals[app] > 0 else 0.0) for app in app_session_totals},
        "event_counts": dict(event_counts),
        "app_event_cross_tab": {app: dict(tags) for app, tags in app_event_counts.items()},
        "intent_outcomes": intent_outcomes,
        "calibration_denominator": {
            "validated_fixes": usable_fixes,
            "identity_leave_alone": usable_control,
            "capitulation_fights": usable_signal
        }
    }

    with open(json_path, "w", encoding="utf-8") as jf:
        json.dump(output_data, jf, indent=2)
    print(f"\n💾 Machine-readable JSON summary written to: {json_path}")

    print("\n" + "=" * 80)
    print("📝 DIAGNOSTICS COMPLETED")
    print("=" * 80 + "\n")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("❌ Error: Path to usage_harvest.md must be provided as a CLI argument.", file=sys.stderr)
        print("Usage: python3 harvest_manifest.py <path_to_usage_harvest.md>", file=sys.stderr)
        sys.exit(1)

    run_diagnostics(sys.argv[1])

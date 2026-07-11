#!/usr/bin/env python3
"""
App-Context-Aware Harvest Analysis Enhancements

Add these functions to harvest_analyze.py to enable per-app dictionary generation.
This is a REFERENCE IMPLEMENTATION - integrate into the existing script.
"""

import re
from collections import Counter, defaultdict
from pathlib import Path

# Pattern to extract app context from log lines
APP_CONTEXT_PATTERN = re.compile(
    r'app: "([^"]+)" \| field: (\d+) \| inputType: (\w+) \| flags: ([\w,]+)'
)

def parse_app_context(line):
    """Extract app context from a harvest log line.

    Returns: (app, field, input_type, flags) or None if not present
    """
    match = APP_CONTEXT_PATTERN.search(line)
    if match:
        return match.groups()
    return None

def group_sessions_by_app(harvest_lines):
    """Group all sessions by app package name.

    Returns: {app_package: [session_texts...]}
    """
    app_sessions = defaultdict(list)

    for line in harvest_lines:
        if '[SESSION:' not in line:
            continue

        # Extract session text
        text_match = re.search(r'\| "(.*?)"', line)
        if not text_match:
            continue
        session_text = text_match.group(1)

        # Extract app context
        context = parse_app_context(line)
        if context:
            app, _, _, _ = context
            app_sessions[app].append(session_text)
        else:
            # Fallback: sessions without context go to "unknown"
            app_sessions["unknown"].append(session_text)

    return dict(app_sessions)

def group_by_input_type(harvest_lines):
    """Group sessions by input type (NORMAL, URI, PASSWORD, etc.).

    Returns: {input_type: [session_texts...]}
    """
    input_type_sessions = defaultdict(list)

    for line in harvest_lines:
        if '[SESSION:' not in line:
            continue

        text_match = re.search(r'\| "(.*?)"', line)
        if not text_match:
            continue
        session_text = text_match.group(1)

        context = parse_app_context(line)
        if context:
            _, _, input_type, _ = context
            input_type_sessions[input_type].append(session_text)
        else:
            input_type_sessions["NORMAL"].append(session_text)

    return dict(input_type_sessions)

def detect_conversations(harvest_lines):
    """Group sessions by app+field to identify conversations.

    Returns: {
        "org.telegram.messenger:123456": {
            "sessions": [...],
            "detected_names": ["Kiry", "Mom"],
            "word_count": 1234,
        }
    }
    """
    conversations = defaultdict(lambda: {"sessions": [], "text_sample": ""})

    for line in harvest_lines:
        if '[SESSION:' not in line:
            continue

        text_match = re.search(r'\| "(.*?)"', line)
        if not text_match:
            continue
        session_text = text_match.group(1)

        context = parse_app_context(line)
        if context:
            app, field, _, _ = context
            conv_key = f"{app}:{field}"
            conversations[conv_key]["sessions"].append(session_text)
            # Keep first 500 chars for name detection
            if len(conversations[conv_key]["text_sample"]) < 500:
                conversations[conv_key]["text_sample"] += " " + session_text

    # Detect names in each conversation
    for conv_key, data in conversations.items():
        text = data["text_sample"]
        # Simple heuristic: capitalized words appearing 2+ times
        words = text.split()
        capitalized = [w for w in words if w and w[0].isupper() and w.isalpha()]
        name_counts = Counter(capitalized)
        detected_names = [name for name, count in name_counts.items() if count >= 2]

        data["detected_names"] = detected_names
        data["word_count"] = sum(len(s.split()) for s in data["sessions"])
        # Clean up - we don't need the sample anymore
        del data["text_sample"]

    return dict(conversations)

def generate_app_specific_dictionaries(app_sessions):
    """Generate per-app phrase and word dictionaries.

    For each app, extracts:
    - Phrases (n-grams)
    - Vocabulary (unique words)
    - Bigrams

    Writes: dict_{app_slug}_phrases.tsv, dict_{app_slug}_words.tsv, etc.
    """
    for app, sessions in app_sessions.items():
        if not sessions:
            continue

        # Create clean app slug (e.g., "org.telegram.messenger" → "telegram")
        app_slug = app.split('.')[-1] if '.' in app else app

        print(f"\n📱 Processing app: {app} ({len(sessions)} sessions)")

        # Extract phrases (reuse existing extract_phrases logic)
        phrases = extract_phrases_from_sessions(sessions)

        # Write phrases
        if phrases:
            filename = f"dict_{app_slug}_phrases.tsv"
            with open(filename, 'w') as f:
                for (context, continuation), freq in sorted(phrases.items(), key=lambda x: x[1], reverse=True):
                    f.write(f"{context}\t{continuation}\t{freq}\n")
            print(f"   ✅ {filename} ({len(phrases)} phrases)")

        # Extract vocabulary
        words = Counter()
        for session in sessions:
            tokens = session.lower().split()
            words.update(t for t in tokens if len(t) >= 2 and t.isalpha())

        # Write vocabulary
        if words:
            filename = f"dict_{app_slug}_words.tsv"
            with open(filename, 'w') as f:
                for word, freq in sorted(words.items(), key=lambda x: x[1], reverse=True):
                    f.write(f"{word}\t{freq * 1000}\n")  # Scale for dictionary
            print(f"   ✅ {filename} ({len(words)} words)")

        # Extract bigrams (reuse existing logic)
        bigrams = extract_bigrams_from_sessions(sessions)
        if bigrams:
            filename = f"dict_{app_slug}_bigrams.tsv"
            with open(filename, 'w') as f:
                for bg, freq in sorted(bigrams.items(), key=lambda x: x[1], reverse=True):
                    f.write(f"{bg}\t{freq * 10}\n")
            print(f"   ✅ {filename} ({len(bigrams)} bigrams)")

def extract_phrases_from_sessions(sessions):
    """Extract n-gram phrases from session texts.
    (Simplified version - use the full logic from harvest_analyze.py)
    """
    phrase_counts = Counter()
    MIN_PHRASE_LEN = 3
    MAX_PHRASE_LEN = 6

    for session_text in sessions:
        words = [w.lower().strip('.,!?;:\'"()[]{}') for w in session_text.split()]
        words = [w for w in words if len(w) >= 1 and not w.isdigit()]

        for n in range(MIN_PHRASE_LEN, MAX_PHRASE_LEN + 1):
            for i in range(len(words) - n + 1):
                ngram = words[i:i+n]
                context = f"{ngram[0]} {ngram[1]}"
                continuation = " ".join(ngram[2:])
                phrase_counts[(context, continuation)] += 1

    # Filter by minimum frequency
    MIN_PHRASE_FREQ = 2  # Lower threshold for per-app (less data)
    return {k: v for k, v in phrase_counts.items() if v >= MIN_PHRASE_FREQ}

def extract_bigrams_from_sessions(sessions):
    """Extract bigrams from session texts."""
    bigrams = []
    for session_text in sessions:
        words = [w.lower().strip('.,!?;:\'"') for w in session_text.split()]
        words = [w for w in words if len(w) >= 2 and not w.isdigit()]

        for i in range(len(words) - 1):
            bigrams.append(f"{words[i]} {words[i+1]}")

    return Counter(bigrams)

def generate_conversation_dictionaries(conversations):
    """Generate dictionaries for specific conversations (e.g., chat with Kiry)."""
    for conv_key, data in conversations.items():
        app, field = conv_key.split(':')
        app_slug = app.split('.')[-1]
        names = data["detected_names"]

        # Skip if not enough data
        if data["word_count"] < 50:
            continue

        print(f"\n💬 Conversation: {app_slug}:{field}")
        print(f"   Names detected: {', '.join(names) if names else 'none'}")
        print(f"   Word count: {data['word_count']}")

        # If we detect specific names, create named dictionaries
        for name in names:
            name_lower = name.lower()
            if name_lower in ["kiry", "mom", "dad"]:  # Add your important names here
                phrases = extract_phrases_from_sessions(data["sessions"])
                if phrases:
                    filename = f"dict_messaging_{name_lower}_phrases.tsv"
                    with open(filename, 'w') as f:
                        for (context, continuation), freq in sorted(phrases.items(), key=lambda x: x[1], reverse=True):
                            f.write(f"{context}\t{continuation}\t{freq}\n")
                    print(f"   ✅ {filename} ({len(phrases)} phrases for {name})")

# ============================================================================
# INTEGRATION EXAMPLE
# ============================================================================

def main_with_app_context():
    """Enhanced main() that generates per-app outputs.

    Integrate this into harvest_analyze.py's existing main() function.
    """
    HARVEST_FILE = Path("usage_harvest.md")

    print("=" * 60)
    print("🔬 APP-CONTEXT-AWARE HARVEST ANALYSIS")
    print("=" * 60)

    # Read harvest file
    with open(HARVEST_FILE, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    # Group by app
    print("\n📊 Grouping sessions by app...")
    app_sessions = group_sessions_by_app(lines)
    for app, sessions in app_sessions.items():
        word_count = sum(len(s.split()) for s in sessions)
        print(f"   • {app}: {len(sessions)} sessions, {word_count} words")

    # Group by input type
    print("\n📊 Grouping sessions by input type...")
    input_type_sessions = group_by_input_type(lines)
    for input_type, sessions in input_type_sessions.items():
        word_count = sum(len(s.split()) for s in sessions)
        print(f"   • {input_type}: {len(sessions)} sessions, {word_count} words")

    # Detect conversations
    print("\n💬 Detecting conversations...")
    conversations = detect_conversations(lines)
    print(f"   Found {len(conversations)} unique conversations")

    # Generate per-app dictionaries
    print("\n📚 Generating per-app dictionaries...")
    generate_app_specific_dictionaries(app_sessions)

    # Generate conversation-specific dictionaries
    print("\n💕 Generating conversation-specific dictionaries...")
    generate_conversation_dictionaries(conversations)

    print("\n" + "=" * 60)
    print("✅ APP-CONTEXT ANALYSIS COMPLETE")
    print("=" * 60)

if __name__ == "__main__":
    main_with_app_context()

#!/usr/bin/env python3
"""
Harvest Analyzer - Comprehensive Usage Data Analysis

Processes usage_harvest.md to generate actionable recommendations for:
- Dictionary additions
- Anti-corrections
- Bigram enhancements
- Autocorrect improvements

Separates analysis by data source:
- SESSION:TYPING → Autocorrect metrics, typo patterns
- SESSION:VOICE → Bigram extraction (natural speech)
- REJECTED/ACCEPTED → Autocorrect effectiveness
- NO_SUGGESTION → Dictionary gaps

Usage:
    python3 harvest_analyze.py

Outputs:
    harvest_summary.md - Statistics and recommendations
    dictionary_additions.txt - Words to add
    anti_corrections.txt - Corrections to block
    bigrams_typing.tsv - Bigrams from manual typing
    bigrams_voice.tsv - Bigrams from voice input
    bigrams_combined.tsv - Merged bigrams for dictionary
    problem_patterns.txt - Autocorrect failures
"""

import re
from collections import Counter, defaultdict
from pathlib import Path
from datetime import datetime

# Configuration
HARVEST_FILE = Path("usage_harvest.md")
MIN_WORD_FREQ = 2          # Minimum frequency to suggest dictionary addition (aggressive for young autocorrect)
MIN_REJECTION_COUNT = 2    # Minimum rejections to suggest anti-correction (catch patterns early)
MIN_BIGRAM_FREQ = 2        # Minimum bigram frequency (inclusive for personal patterns)

class HarvestAnalyzer:
    def __init__(self, harvest_file):
        self.harvest_file = harvest_file

        # Data stores
        self.typing_sessions = []
        self.voice_sessions = []
        self.accepted_corrections = []
        self.rejected_corrections = []
        self.new_words = []
        self.no_suggestions = []
        self.multi_attempts = []
        self.ignored_suggestions = []
        self.backspace_storms = []

        self.trigram_contexts = []  # (word1, word2, continuation) from trigram fields
        self.insisted_words = []   # Words user insisted on (INSISTED events)

        # Statistics
        self.stats = {
            'total_typing_words': 0,
            'total_voice_words': 0,
            'total_accepted': 0,
            'total_rejected': 0,
            'accuracy_rate': 0.0,
        }

    def parse_harvest_file(self):
        """Parse all events from harvest file."""
        print(f"📖 Reading {self.harvest_file}...")

        patterns = {
            'session_typing': re.compile(r'^\[SESSION:TYPING\] .* \| "(.*)"$'),
            'session_voice': re.compile(r'^\[SESSION:VOICE\] .* \| "(.*)"$'),
            'session_legacy': re.compile(r'^\[SESSION\] .* \| "(.*)"$'),  # Old format (treat as typing)
            'accepted': re.compile(r'^\[ACCEPTED\] .* \| (.*) → (.*) \|'),
            'rejected': re.compile(r'^\[REJECTED\] .* \| (.*) ← (.*) \(reverted\)'),
            'new_word': re.compile(r'^\[NEW_WORD\] .* \| (.*)'),
            'no_suggestion': re.compile(r'^\[NO_SUGGESTION\] .* \| typed: "(.*)"'),
            'multi_attempt': re.compile(r'^\[MULTI_ATTEMPT\] .* \| attempts: \[(.*)\] \| final: "(.*)"'),
            'ignored_suggestions': re.compile(r'^\[IGNORED_SUGGESTIONS\] .* \| typed: "(.*)" \| offered: \[(.*)\]'),
            'backspace_storm': re.compile(r'^\[BACKSPACE_STORM\] .* \| word: "(.*)" \| backspaces: (\d+)'),
            'trigram': re.compile(r'trigram: "([^"]+)"'),
        }

        with open(self.harvest_file, 'r', encoding='utf-8') as f:
            for line in f:
                line = line.strip()

                # SESSION:TYPING
                if m := patterns['session_typing'].match(line):
                    self.typing_sessions.append(m.group(1))

                # SESSION:VOICE
                elif m := patterns['session_voice'].match(line):
                    self.voice_sessions.append(m.group(1))

                # SESSION (legacy - treat as typing)
                elif m := patterns['session_legacy'].match(line):
                    self.typing_sessions.append(m.group(1))

                # ACCEPTED (also extract trigram context if present)
                elif m := patterns['accepted'].match(line):
                    typed, corrected = m.group(1), m.group(2)
                    self.accepted_corrections.append((typed, corrected))
                    self.stats['total_accepted'] += 1
                    # Extract trigram field if present
                    tm = patterns['trigram'].search(line)
                    if tm:
                        trigram_words = tm.group(1).strip().split()
                        if len(trigram_words) >= 3:
                            self.trigram_contexts.append(tuple(trigram_words))

                # REJECTED
                elif m := patterns['rejected'].match(line):
                    typed, rejected = m.group(1), m.group(2)
                    self.rejected_corrections.append((typed, rejected))
                    self.stats['total_rejected'] += 1

                # NEW_WORD
                elif m := patterns['new_word'].match(line):
                    self.new_words.append(m.group(1))

                # NO_SUGGESTION
                elif m := patterns['no_suggestion'].match(line):
                    self.no_suggestions.append(m.group(1))

                # MULTI_ATTEMPT
                elif m := patterns['multi_attempt'].match(line):
                    attempts_str, final = m.group(1), m.group(2)
                    attempts = [a.strip() for a in attempts_str.split('→')]
                    self.multi_attempts.append((attempts, final))

                # IGNORED_SUGGESTIONS
                elif m := patterns['ignored_suggestions'].match(line):
                    typed, suggestions = m.group(1), m.group(2)
                    self.ignored_suggestions.append((typed, suggestions))

                # BACKSPACE_STORM
                elif m := patterns['backspace_storm'].match(line):
                    word, count = m.group(1), int(m.group(2))
                    self.backspace_storms.append((word, count))

        # Calculate statistics
        self.stats['total_typing_words'] = sum(len(s.split()) for s in self.typing_sessions)
        self.stats['total_voice_words'] = sum(len(s.split()) for s in self.voice_sessions)

        total_corrections = self.stats['total_accepted'] + self.stats['total_rejected']
        if total_corrections > 0:
            self.stats['accuracy_rate'] = (self.stats['total_accepted'] / total_corrections) * 100

        print(f"   ✅ Parsed {len(self.typing_sessions)} typing sessions")
        print(f"   ✅ Parsed {len(self.voice_sessions)} voice sessions")
        print(f"   ✅ Found {self.stats['total_accepted']} accepted, {self.stats['total_rejected']} rejected")

    def extract_bigrams(self, sessions):
        """Extract bigrams from session text."""
        bigrams = []
        for session_text in sessions:
            words = [w.lower().strip('.,!?;:\'"') for w in session_text.split()]
            words = [w for w in words if len(w) >= 2 and not w.isdigit()]

            for i in range(len(words) - 1):
                bigrams.append(f"{words[i]} {words[i+1]}")

        return Counter(bigrams)

    def extract_phrases(self):
        """Extract personal phrases (trigrams and longer) for phrase prediction.

        Sources:
        1. Trigram fields from ACCEPTED/INSISTED events
        2. Consecutive word sequences from VOICE and TYPING sessions

        Output format: {("word1 word2", "continuation"): frequency}
        """
        phrase_counts = Counter()

        # Source 1: Trigram contexts from harvest events
        for words in self.trigram_contexts:
            if len(words) >= 3:
                context = f"{words[0].lower()} {words[1].lower()}"
                continuation = " ".join(w.lower() for w in words[2:])
                phrase_counts[(context, continuation)] += 1

        # Source 2: Extract consecutive word sequences from sessions
        MIN_PHRASE_LEN = 3  # Minimum words for a phrase
        MAX_PHRASE_LEN = 6  # Maximum words for a phrase

        all_sessions = self.typing_sessions + self.voice_sessions
        for session_text in all_sessions:
            words = [w.lower().strip('.,!?;:\'"()[]{}') for w in session_text.split()]
            words = [w for w in words if len(w) >= 1 and not w.isdigit()]

            # Extract all n-grams from 3 to MAX_PHRASE_LEN
            for n in range(MIN_PHRASE_LEN, MAX_PHRASE_LEN + 1):
                for i in range(len(words) - n + 1):
                    ngram = words[i:i+n]
                    context = f"{ngram[0]} {ngram[1]}"
                    continuation = " ".join(ngram[2:])
                    phrase_counts[(context, continuation)] += 1

        # Filter by minimum frequency (3 occurrences for personal phrases)
        MIN_PHRASE_FREQ = 3
        filtered = {
            k: v for k, v in phrase_counts.items()
            if v >= MIN_PHRASE_FREQ
        }

        return filtered

    def analyze_rejected_corrections(self):
        """Find patterns in rejected corrections."""
        rejection_counts = Counter(self.rejected_corrections)

        # Find corrections rejected many times
        frequent_rejections = {
            (typed, correction): count
            for (typed, correction), count in rejection_counts.items()
            if count >= MIN_REJECTION_COUNT
        }

        return frequent_rejections

    def analyze_dictionary_gaps(self):
        """Find words that should be added to dictionary."""
        # Words with no suggestions
        no_suggestion_words = Counter(self.no_suggestions)

        # Words from multi-attempts
        multi_attempt_words = Counter()
        for attempts, final in self.multi_attempts:
            for attempt in attempts:
                multi_attempt_words[attempt] += 1

        # Combine and filter
        gap_candidates = {}
        for word, count in no_suggestion_words.items():
            if count >= MIN_WORD_FREQ:
                gap_candidates[word] = {'freq': count, 'reason': 'no_suggestion'}

        for word, count in multi_attempt_words.items():
            if count >= MIN_WORD_FREQ:
                if word in gap_candidates:
                    gap_candidates[word]['freq'] += count
                else:
                    gap_candidates[word] = {'freq': count, 'reason': 'multi_attempt'}

        return gap_candidates

    def generate_summary(self):
        """Generate markdown summary report."""
        print("\n📊 Generating summary report...")

        # Extract bigrams
        typing_bigrams = self.extract_bigrams(self.typing_sessions)
        voice_bigrams = self.extract_bigrams(self.voice_sessions)

        # Filter by minimum frequency
        typing_bigrams_filtered = {k: v for k, v in typing_bigrams.items() if v >= MIN_BIGRAM_FREQ}
        voice_bigrams_filtered = {k: v for k, v in voice_bigrams.items() if v >= MIN_BIGRAM_FREQ}

        # Combine bigrams
        combined_bigrams = Counter()
        for bg, freq in typing_bigrams_filtered.items():
            combined_bigrams[bg] += freq
        for bg, freq in voice_bigrams_filtered.items():
            combined_bigrams[bg] += freq

        # Analyze rejections, dictionary gaps, and personal phrases
        frequent_rejections = self.analyze_rejected_corrections()
        dictionary_gaps = self.analyze_dictionary_gaps()
        personal_phrases = self.extract_phrases()

        # Generate report
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

        report = f"""# Harvest Analysis Summary
Generated: {timestamp}

## 📊 Statistics

### Data Volume
- **Typing Sessions**: {len(self.typing_sessions)} sessions, {self.stats['total_typing_words']:,} words
- **Voice Sessions**: {len(self.voice_sessions)} sessions, {self.stats['total_voice_words']:,} words
- **Total Words**: {self.stats['total_typing_words'] + self.stats['total_voice_words']:,}

### Autocorrect Performance
- **Accepted Corrections**: {self.stats['total_accepted']}
- **Rejected Corrections**: {self.stats['total_rejected']}
- **Accuracy Rate**: {self.stats['accuracy_rate']:.1f}%

### Issues Detected
- **No Suggestions**: {len(self.no_suggestions)} instances
- **Multi-Attempts**: {len(self.multi_attempts)} word struggles
- **Ignored Suggestions**: {len(self.ignored_suggestions)} instances
- **Backspace Storms**: {len(self.backspace_storms)} high-effort words

## 🎯 Actionable Recommendations

### 1. Add to Anti-Corrections
*Corrections that are frequently rejected (≥{MIN_REJECTION_COUNT}x):*

"""
        if frequent_rejections:
            for (typed, correction), count in sorted(frequent_rejections.items(), key=lambda x: x[1], reverse=True)[:20]:
                report += f"- `\"{typed}\" → \"{correction}\"` ({count}x rejected)\n"
        else:
            report += "*No high-frequency rejections found.*\n"

        report += f"\n### 2. Dictionary Additions (High Priority)\n*Words with no suggestions or multiple attempts (freq ≥{MIN_WORD_FREQ}):*\n\n"

        if dictionary_gaps:
            for word, data in sorted(dictionary_gaps.items(), key=lambda x: x[1]['freq'], reverse=True)[:30]:
                report += f"- `{word}` ({data['freq']}x, reason: {data['reason']})\n"
        else:
            report += "*No dictionary gaps detected.*\n"

        report += f"\n### 3. New Bigrams\n\n#### From Typing ({len(typing_bigrams_filtered)} bigrams, freq ≥{MIN_BIGRAM_FREQ}):\n"
        for bg, freq in sorted(typing_bigrams_filtered.items(), key=lambda x: x[1], reverse=True)[:20]:
            report += f"- `{bg}` ({freq}x)\n"

        report += f"\n#### From Voice ({len(voice_bigrams_filtered)} bigrams, freq ≥{MIN_BIGRAM_FREQ}):\n"
        for bg, freq in sorted(voice_bigrams_filtered.items(), key=lambda x: x[1], reverse=True)[:20]:
            report += f"- `{bg}` ({freq}x)\n"

        report += f"\n#### Combined Total: {len(combined_bigrams)} unique bigrams\n"

        report += "\n### 4. Autocorrect Failures\n\n"
        if self.no_suggestions:
            report += f"*Words typed with NO autocorrect suggestions ({len(set(self.no_suggestions))} unique):*\n\n"
            for word, count in Counter(self.no_suggestions).most_common(20):
                report += f"- `{word}` ({count}x)\n"
        else:
            report += "*No autocorrect failures detected.*\n"

        if self.backspace_storms:
            report += f"\n*High-effort words ({len(self.backspace_storms)} instances):*\n\n"
            for word, count in sorted(self.backspace_storms, key=lambda x: x[1], reverse=True)[:10]:
                report += f"- `{word}` ({count} backspaces)\n"

        report += f"\n### 5. Personal Phrases ({len(personal_phrases)} learned)\n"
        report += "*Multi-word phrases from your typing history (for phrase prediction):*\n\n"
        if personal_phrases:
            for (context, continuation), freq in sorted(personal_phrases.items(), key=lambda x: x[1], reverse=True)[:20]:
                report += f"- `{context}` → `{continuation}` ({freq}x)\n"
        else:
            report += "*Not enough data yet. Keep typing!*\n"

        return report, typing_bigrams_filtered, voice_bigrams_filtered, combined_bigrams, frequent_rejections, dictionary_gaps, personal_phrases

    def write_outputs(self, report, typing_bigrams, voice_bigrams, combined_bigrams, rejections, dictionary_gaps, personal_phrases=None):
        """Write all output files."""
        print("\n💾 Writing output files...")

        # Summary report
        with open("harvest_summary.md", 'w', encoding='utf-8') as f:
            f.write(report)
        print("   ✅ harvest_summary.md")

        # Anti-corrections
        with open("anti_corrections.txt", 'w', encoding='utf-8') as f:
            for (typed, correction), count in sorted(rejections.items(), key=lambda x: x[1], reverse=True):
                f.write(f'"{typed}" to listOf("{correction}"),  // {count}x rejected\n')
        print("   ✅ anti_corrections.txt")

        # Dictionary additions
        with open("dictionary_additions.txt", 'w', encoding='utf-8') as f:
            for word, data in sorted(dictionary_gaps.items(), key=lambda x: x[1]['freq'], reverse=True):
                f.write(f"{word}\t500000  # freq={data['freq']}, reason={data['reason']}\n")
        print("   ✅ dictionary_additions.txt")

        # Bigrams - Typing
        with open("bigrams_typing.tsv", 'w', encoding='utf-8') as f:
            for bg, freq in sorted(typing_bigrams.items(), key=lambda x: x[1], reverse=True):
                f.write(f"{bg}\t{freq * 10}\n")  # Scale by 10 for dictionary
        print("   ✅ bigrams_typing.tsv")

        # Bigrams - Voice
        with open("bigrams_voice.tsv", 'w', encoding='utf-8') as f:
            for bg, freq in sorted(voice_bigrams.items(), key=lambda x: x[1], reverse=True):
                f.write(f"{bg}\t{freq * 10}\n")
        print("   ✅ bigrams_voice.tsv")

        # Bigrams - Combined
        with open("bigrams_combined.tsv", 'w', encoding='utf-8') as f:
            for bg, freq in sorted(combined_bigrams.items(), key=lambda x: x[1], reverse=True):
                f.write(f"{bg}\t{freq * 10}\n")
        print("   ✅ bigrams_combined.tsv")

        # Personal phrases for PhraseTable
        if personal_phrases:
            with open("personal_phrases.tsv", 'w', encoding='utf-8') as f:
                for (context, continuation), freq in sorted(personal_phrases.items(), key=lambda x: x[1], reverse=True):
                    f.write(f"{context}\t{continuation}\t{freq}\n")
            print(f"   ✅ personal_phrases.tsv ({len(personal_phrases)} phrases)")
        else:
            print("   ⏭️  personal_phrases.tsv (skipped - no phrases yet)")

        # Problem patterns
        with open("problem_patterns.txt", 'w', encoding='utf-8') as f:
            f.write("# Autocorrect Failures - No Suggestions Offered\n\n")
            for word, count in Counter(self.no_suggestions).most_common():
                f.write(f"{word}\t{count}\n")
        print("   ✅ problem_patterns.txt")

def main():
    print("=" * 60)
    print("🔬 HARVEST ANALYZER - Usage Data Analysis")
    print("=" * 60)

    analyzer = HarvestAnalyzer(HARVEST_FILE)
    analyzer.parse_harvest_file()

    report, typing_bigrams, voice_bigrams, combined_bigrams, rejections, dictionary_gaps, personal_phrases = analyzer.generate_summary()
    analyzer.write_outputs(report, typing_bigrams, voice_bigrams, combined_bigrams, rejections, dictionary_gaps, personal_phrases)

    print("\n" + "=" * 60)
    print("✅ ANALYSIS COMPLETE")
    print("=" * 60)
    print("\n📋 Generated Files:")
    print("   • harvest_summary.md - Overview and recommendations")
    print("   • anti_corrections.txt - Corrections to block")
    print("   • dictionary_additions.txt - Words to add")
    print("   • bigrams_typing.tsv - Bigrams from manual typing")
    print("   • bigrams_voice.tsv - Bigrams from voice input")
    print("   • bigrams_combined.tsv - Merged bigrams for dictionary")
    print("   • personal_phrases.tsv - Personal phrase predictions for PhraseTable")
    print("   • problem_patterns.txt - Autocorrect failures")
    print("\n📖 Next Steps:")
    print("   1. Review harvest_summary.md for insights")
    print("   2. Apply recommended changes to PersonalPreferences.kt")
    print("   3. Merge bigrams_combined.tsv into final_mobile_bigrams.tsv")
    print("   4. Add words from dictionary_additions.txt to unified_dictionary.tsv")
    print("   5. Copy personal_phrases.tsv to app/src/main/assets/ime/dict/ for phrase prediction")
    print()

if __name__ == "__main__":
    main()

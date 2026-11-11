import json
import re
from collections import Counter
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
FUTO_PATH = REPO_ROOT / "research/swipe-training/futo_words_unique.txt"
JSONL_PATH = REPO_ROOT / "data/harvest/inbox/20260819-070118/usage_harvest.jsonl"
MD_PATH = REPO_ROOT / "data/harvest/inbox/20260819-070118/usage_harvest.md"
CUSTOM_WORDS_PATH = REPO_ROOT / "research/swipe-training/sams_custom_words.txt"
OUTPUT_DIR = REPO_ROOT / "research/swipe-training"

futo_words = set()
if FUTO_PATH.exists():
    with open(FUTO_PATH, 'r', encoding='utf-8') as f:
        for line in f:
            w = line.strip().lower()
            if w:
                futo_words.add(w)

print(f"Loaded {len(futo_words):,} words from FUTO dataset.")

METADATA_IGNORE = {
    'inputtype', 'autocorrect', 'nosuggestions', 'googlequicksearchbox',
    'ctx', 'trigram', 'field', 'flags', 'sess', 'app', 'src', 'typing',
    'voice', 'normal', 'none', 'reverted', 'true', 'false', 'v3', 'ts',
    'id', 'candidates', 'applied', 'typed', 'dev', 'patrickgold', 'florisboard',
    'debug', 'measurepassdelegate', 'session', 'accepted', 'rejected', 'insisted',
    'new_word', 'manual_fix', 'com', 'android', 'system', 'org', 'https', 'http',
    'null', 'undefined', 'nan', 'th', 'st', 'nd', 'rd'
}

def clean_token(token: str):
    token = token.strip('.,!?:;"\'()[]{}<>-/*+=_~`$#@%^&|\\')
    if not token:
        return None
    if not re.match(r"^[a-zA-Z]+(?:'[a-zA-Z]+)?$", token):
        return None
    if len(token) == 1 and token.lower() not in ('a', 'i'):
        return None
    if re.search(r'(.)\1{2,}', token.lower()):
        return None
    return token.lower()

# Track committed/session words separately from raw typo rejects
clean_corpus_counts = Counter()
reverted_typos = Counter()

if JSONL_PATH.exists():
    with open(JSONL_PATH, 'r', encoding='utf-8', errors='ignore') as f:
        for line in f:
            try:
                data = json.loads(line)
                ev_type = data.get('type')
                
                # 1. Clean confirmed words from actual messages / committed text
                if ev_type in ('SESSION_TEXT', 'WORD_COMMITTED', 'AUTO_APPLIED', 'ACCEPTED', 'NEW_WORD', 'INSISTED'):
                    if 'text' in data and isinstance(data['text'], str):
                        for raw in re.findall(r"[a-zA-Z]+(?:'[a-zA-Z]+)?", data['text']):
                            w = clean_token(raw)
                            if w and w not in METADATA_IGNORE:
                                clean_corpus_counts[w] += 1
                                
                    for field in ('word', 'applied'):
                        val = data.get(field)
                        if val and isinstance(val, str):
                            w = clean_token(val)
                            if w and w not in METADATA_IGNORE:
                                clean_corpus_counts[w] += 1
                                
                elif ev_type in ('REJECTED', 'MANUAL_FIX'):
                    # The typed word before revert might be a typo
                    val = data.get('typed')
                    if val and isinstance(val, str):
                        w = clean_token(val)
                        if w and w not in METADATA_IGNORE:
                            reverted_typos[w] += 1
            except Exception:
                pass

if MD_PATH.exists():
    with open(MD_PATH, 'r', encoding='utf-8', errors='ignore') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#') or line.startswith('---') or line.startswith('Copy to'):
                continue
            parts = line.split('|')
            if len(parts) >= 2:
                tag_part = parts[0]
                content = parts[1]
                if 'SESSION' in tag_part or 'ACCEPTED' in tag_part or 'INSISTED' in tag_part or 'NEW_WORD' in tag_part:
                    for raw in re.findall(r"[a-zA-Z]+(?:'[a-zA-Z]+)?", content):
                        w = clean_token(raw)
                        if w and w not in METADATA_IGNORE:
                            clean_corpus_counts[w] += 1

# Include custom words file explicitly
if CUSTOM_WORDS_PATH.exists():
    with open(CUSTOM_WORDS_PATH, 'r', encoding='utf-8') as f:
        for line in f:
            w = clean_token(line.strip())
            if w and w not in METADATA_IGNORE:
                clean_corpus_counts[w] += 10 # high priority

# If a word was in reverted_typos with high count (user repeatedly fought for it), add it
for w, c in reverted_typos.items():
    if c >= 3:
        clean_corpus_counts[w] += c

print(f"Total clean harvested vocabulary: {len(clean_corpus_counts):,} words")

missing_from_futo = {w: count for w, count in clean_corpus_counts.items() if w not in futo_words}
print(f"Unique words NOT in FUTO: {len(missing_from_futo):,} words")

# Categorization
contractions = []
tech_ai_terms = []
slang_colloquial = []
frequent_domain = []
proper_nouns = []

TECH_AI_SUBSTRINGS = [
    'gpt', 'ai', 'llm', 'mcp', 'claw', 'code', 'git', 'cli', 'api', 'app', 'ui', 'sdk', 'adb',
    'droid', 'bot', 'vulkan', 'llama', 'whisper', 'soma', 'tooth', 'subagent', 'token', 'prompt',
    'rust', 'kotlin', 'python', 'json', 'yaml', 'toml', 'tmux', 'ssh', 'curl', 'http', 'linux',
    'arch', 'kernel', 'repo', 'tmux', 'vim', 'nvim', 'zsh', 'bash', 'node', 'react', 'css',
    'docker', 'k8s', 'infra', 'daemon', 'onnx', 'tensor', 'embed'
]

COMMON_SLANG = {
    'yeah', 'okay', 'ok', 'nah', 'yep', 'kinda', 'gonna', 'wanna', 'gotta', 'lemme',
    'idk', 'tbh', 'imo', 'fyi', 'btw', 'wtf', 'omg', 'fuck', 'fucking', 'fucked',
    'fucks', 'shit', 'shitty', 'damn', 'bitch', 'ass', 'hell', 'crap', 'bro', 'dude',
    'fam', 'homie', 'yo', 'sup', 'bruh', 'pog', 'lmao', 'lmfao', 'rofl'
}

for word, count in Counter(missing_from_futo).most_common():
    if "'" in word:
        contractions.append((word, count))
    elif any(sub in word for sub in TECH_AI_SUBSTRINGS):
        tech_ai_terms.append((word, count))
    elif word in COMMON_SLANG:
        slang_colloquial.append((word, count))
    elif count >= 3:
        frequent_domain.append((word, count))
    else:
        proper_nouns.append((word, count))

print('\n' + '='*70)
print('FINAL CATEGORIZED HARVEST VOCABULARY NOT IN FUTO:')
print('='*70)
print(f"1. Contractions (e.g. i'm, don't, won't)          : {len(contractions):,} words")
print(f"2. AI / Dev / Tech Terms (e.g. openai, mcp, adb)  : {len(tech_ai_terms):,} words")
print(f"3. Slang, Swear & Colloquialisms (e.g. yeah, ok)  : {len(slang_colloquial):,} words")
print(f"4. Confirmed Frequent Domain Terms (Freq >= 3)    : {len(frequent_domain):,} words")
print(f"5. Single/Double Occurrence Terms & Names         : {len(proper_nouns):,} words")

out_tsv = OUTPUT_DIR / 'harvested_missing_words.tsv'
with open(out_tsv, 'w', encoding='utf-8') as f:
    f.write('word\tfrequency\tcategory\n')
    for w, c in contractions:
        f.write(f'{w}\t{c}\tcontraction\n')
    for w, c in tech_ai_terms:
        f.write(f'{w}\t{c}\ttech_ai\n')
    for w, c in slang_colloquial:
        f.write(f'{w}\t{c}\tslang\n')
    for w, c in frequent_domain:
        f.write(f'{w}\t{c}\tfrequent_domain\n')
    for w, c in proper_nouns:
        f.write(f'{w}\t{c}\tlow_freq_names\n')

print(f'\nSaved full categorized table to: {out_tsv}')

# Save verified high-priority supplement list
swipe_targets = []
for w, c in contractions:
    swipe_targets.append(w)
for w, c in tech_ai_terms:
    swipe_targets.append(w)
for w, c in slang_colloquial:
    swipe_targets.append(w)
for w, c in frequent_domain:
    swipe_targets.append(w)

swipe_targets = sorted(set(swipe_targets))
target_txt = OUTPUT_DIR / 'target_swipe_vocabulary_supplement.txt'
with open(target_txt, 'w', encoding='utf-8') as f:
    for w in swipe_targets:
        f.write(w + '\n')

print(f"Saved {len(swipe_targets):,} target swipe supplement words to: {target_txt}")

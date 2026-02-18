#!/usr/bin/env python3
import os

PHRASE_FILE = "keyboard-local/personal_phrases.tsv"

GOLDEN_PHRASES = [
    ("what are", "you doing"), ("what are", "you thinking"), ("what do", "you think"),
    ("how are", "you doing"), ("how is", "it going"), ("i don't", "know what"),
    ("i don't", "think so"), ("i want", "to see"), ("i need", "to know"),
    ("let me", "know if"), ("thanks for", "the help"), ("thanks for", "everything"),
    ("by the", "way"), ("at the", "same time"), ("in the", "middle of"),
    ("a lot", "of people"), ("one of", "the best"), ("as soon", "as possible"),
    ("would be", "great"), ("it would", "be nice"), ("if you", "want to"),
    ("do you", "want to"), ("are you", "sure"), ("is there", "anything"),
    ("whatever", "you want"), ("don't worry", "about it"), ("just let", "me know"),
    ("nice to", "meet you"), ("keep in", "touch"), ("looking forward", "to"),
    ("i'll be", "there"), ("i'm going", "to"), ("we should", "go"),
    ("can you", "help me"), ("could you", "please"), ("give me", "a second"),
    ("wait a", "minute"), ("believe it", "or not"), ("more and", "more"),
    ("sooner or", "later"), ("from time", "to time"), ("all over", "the place"),
    ("on the", "other hand"), ("in order", "to"), ("as far", "as"),
    ("in terms", "of"), ("in front", "of"), ("out of", "nowhere"),
    ("point of", "view"), ("make sure", "that"), ("take care", "of"),
    ("come up", "with"), ("get rid", "of"), ("go ahead", "and"),
    ("looking for", "a"), ("thought you", "might"), ("give it", "a try")
]

def merge():
    print("🧠 Injecting Golden Trigrams into PhraseTable...")
    
    existing = {}
    if os.path.exists(PHRASE_FILE):
        with open(PHRASE_FILE, 'r') as f:
            for line in f:
                parts = line.strip().split('\t')
                if len(parts) == 3:
                    existing[(parts[0], parts[1])] = int(parts[2])

    added_count = 0
    for context, continuation in GOLDEN_PHRASES:
        if (context, continuation) not in existing:
            existing[(context, continuation)] = 20
            added_count += 1

    with open(PHRASE_FILE, 'w') as f:
        sorted_phrases = sorted(existing.items(), key=lambda x: x[1], reverse=True)
        for (ctx, cont), freq in sorted_phrases:
            f.write(ctx + "\t" + cont + "\t" + str(freq) + "\n")

    print(f"✅ Merge complete. Added {added_count} new anchor phrases.")

if __name__ == "__main__":
    merge()

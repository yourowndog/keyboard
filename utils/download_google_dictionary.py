import gzip
import os
import sys
import time
import urllib.request
from typing import List, Tuple

# --- THE SOURCE ---
# This is the direct RAW link to the AOSP English dictionary file.
# Hosted by AnySoftKeyboard (Open Source Android Keyboard)
DOWNLOAD_URL = "https://raw.githubusercontent.com/AnySoftKeyboard/LanguagePack/master/languages/english/pack/dictionary/aosp.combined.gz"

# --- OUTPUT CONFIG ---
OUTPUT_FILE = "app/src/main/assets/ime/dict/frequency_dictionary_en.txt"

# Scale AOSP 0-255 frequencies to match SymSpell's 0-2,550,000 range.
SCALE = 10000
RETRIES = 3
TIMEOUT = 30


def parse_entries(stream) -> List[Tuple[str, int]]:
    entries: List[Tuple[str, int]] = []
    for line in stream:
        text = line.decode("utf-8", errors="ignore").strip()

        if not text.startswith("word="):
            continue

        word = ""
        freq = 0

        for part in text.split(","):
            if part.startswith("word="):
                word = part.split("=", 1)[1]
            elif part.startswith("f="):
                try:
                    freq = int(part.split("=", 1)[1])
                except ValueError:
                    freq = 0

        if freq > 0:
            if len(word) == 1 and word not in ("a", "i", "I"):
                continue

            entries.append((word, freq * SCALE))

    return entries


def fetch_dictionary(url: str) -> List[Tuple[str, int]]:
    last_error: Exception | None = None

    for attempt in range(1, RETRIES + 1):
        try:
            with urllib.request.urlopen(url, timeout=TIMEOUT) as response:
                status = getattr(response, "status", 200)
                if status != 200:
                    raise RuntimeError(f"Unexpected status code {status}")

                with gzip.GzipFile(fileobj=response) as uncompressed:
                    return parse_entries(uncompressed)
        except Exception as exc:  # noqa: BLE001
            last_error = exc

            if attempt == RETRIES:
                break

            time.sleep(2**attempt)

    raise RuntimeError(f"Failed to download dictionary: {last_error}") from last_error


def write_entries(entries: List[Tuple[str, int]], output_path: str) -> None:
    entries.sort(key=lambda x: x[1], reverse=True)

    os.makedirs(os.path.dirname(output_path), exist_ok=True)

    with open(output_path, "w", encoding="utf-8") as file:
        for word, freq in entries:
            file.write(f"{word}\t{freq}\n")


def main() -> None:
    print(f"⬇️  Downloading AOSP Dictionary from: {DOWNLOAD_URL}...")

    try:
        entries = fetch_dictionary(DOWNLOAD_URL)
    except Exception as exc:  # noqa: BLE001
        print(f"❌ Error: {exc}")
        sys.exit(1)

    if not entries:
        print("❌ No entries parsed; aborting.")
        sys.exit(1)

    write_entries(entries, OUTPUT_FILE)

    print(f"🚀 Done! Wrote {len(entries)} words to {OUTPUT_FILE}.")
    print("   Sample of what we got:")
    for word, freq in entries[:5]:
        print(f"   {word} {freq}")


if __name__ == "__main__":
    main()

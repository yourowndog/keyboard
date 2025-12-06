#!/usr/bin/env python3
import gzip
import io
import math
import re
import urllib.request
import zipfile
from collections import Counter, defaultdict
from pathlib import Path

# Data sources
SMS_URL = "https://archive.ics.uci.edu/ml/machine-learning-databases/00228/smsspamcollection.zip"
AOSP_URL = "https://raw.githubusercontent.com/commaai/android_packages_inputmethods_LatinIME/master/dictionaries/en_wordlist.combined.gz"
USER_BIGRAM_FILE = Path("frequency_bigram_en.cleaned.txt")

# Simple filters to keep SMS artifacts out of autocorrect suggestions
BANNED_SMS_TOKENS = {"lt", "gt", "amp"}
MIN_OUTPUT_SCORE = 5


def fetch_bytes(url: str) -> bytes:
    with urllib.request.urlopen(url) as resp:
        return resp.read()


def download_and_extract_sms() -> list[str]:
    print("⬇️ Downloading SMS corpus...")
    try:
        data = fetch_bytes(SMS_URL)
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            with z.open("SMSSpamCollection") as f:
                return f.read().decode("utf-8", errors="ignore").splitlines()
    except Exception as e:  # pragma: no cover - best-effort fetch
        print(f"❌ Error downloading SMS data: {e}")
        return []


def download_aosp() -> list[str]:
    print("⬇️ Downloading Android (AOSP) dictionary...")
    try:
        data = fetch_bytes(AOSP_URL)
        with gzip.GzipFile(fileobj=io.BytesIO(data)) as f:
            return f.read().decode("utf-8").splitlines()
    except Exception as e:  # pragma: no cover - optional fetch
        print(f"❌ Error downloading AOSP data: {e}")
        return []


def normalize_frequency(freq: int, max_freq: int) -> int:
    try:
        return int(math.log(freq) / math.log(max_freq) * 255)
    except Exception:
        return 1


def is_clean_bigram(bigram: str) -> bool:
    # Drop HTML artifacts like lt/gt/amp from SMS
    return all(tok not in BANNED_SMS_TOKENS for tok in bigram.split())


def build_dataset() -> None:
    sms_lines = download_and_extract_sms()
    sms_bigrams = Counter()
    print(f"Parsing {len(sms_lines)} SMS messages...")
    for line in sms_lines:
        parts = line.split("\t", 1)
        if len(parts) < 2:
            continue
        msg = parts[1]
        tokens = re.findall(r"[a-z]+'[a-z]+|[a-z]+", msg.lower())
        for i in range(len(tokens) - 1):
            if tokens[i] in BANNED_SMS_TOKENS or tokens[i + 1] in BANNED_SMS_TOKENS:
                continue
            bigram = f"{tokens[i]} {tokens[i + 1]}"
            sms_bigrams[bigram] += 1

    user_bigrams = {}
    print(f"Reading user bigrams: {USER_BIGRAM_FILE}...")
    try:
        with USER_BIGRAM_FILE.open("r", encoding="utf-8") as f:
            for line in f:
                parts = line.split("\t")
                if len(parts) < 2:
                    continue
                bg = parts[0].strip()
                try:
                    count = int(parts[1].strip())
                    user_bigrams[bg] = count
                except Exception:
                    continue
    except FileNotFoundError:
        print(f"⚠️ Could not find {USER_BIGRAM_FILE}, skipping user layer.")

    print("Mixing ingredients...")
    final_bigrams = defaultdict(int)
    max_user_freq = max(user_bigrams.values()) if user_bigrams else 1

    # Base layer: normalized user bigrams
    for bg, count in user_bigrams.items():
        score = normalize_frequency(count, max_user_freq)
        final_bigrams[bg] += score

    # Boost layer: SMS bigrams
    for bg, count in sms_bigrams.items():
        score = count * 50
        final_bigrams[bg] += score

    print("Writing final_mobile_bigrams.tsv...")
    sorted_bigrams = sorted(final_bigrams.items(), key=lambda x: x[1], reverse=True)

    with Path("final_mobile_bigrams.tsv").open("w", encoding="utf-8") as f:
        for bg, score in sorted_bigrams:
            if score > MIN_OUTPUT_SCORE and is_clean_bigram(bg):
                f.write(f"{bg}\t{score}\n")

    print(f"✅ Done! Created final list with {len(sorted_bigrams)} pairs (filtered by score > {MIN_OUTPUT_SCORE}).")
    print("Top 10 predictions (pre-filter):")
    for x in sorted_bigrams[:10]:
        print(x)


if __name__ == "__main__":
    build_dataset()

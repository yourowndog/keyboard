import os
import datetime

# Configuration
SOURCE_FILE = "/sdcard/Documents/usage_harvest.md"
PROJECT_DIR = os.path.dirname(os.path.abspath(__file__))
DEST_FILE = os.path.join(PROJECT_DIR, "usage_harvest.md")
MARKER_TEMPLATE = "\n<!-- HARVEST BATCH: {date} -->\n"

def harvest():
    if not os.path.exists(SOURCE_FILE):
        print(f"❌ Source file not found: {SOURCE_FILE}")
        return

    existing_lines = set()
    if os.path.exists(DEST_FILE):
        with open(DEST_FILE, "r", encoding="utf-8") as f:
            existing_lines = set(line.strip() for line in f if line.strip())

    new_entries = []
    with open(SOURCE_FILE, "r", encoding="utf-8") as f:
        for line in f:
            clean_line = line.strip()
            if not clean_line or clean_line.startswith("#") or clean_line.startswith("Copy to") or clean_line.startswith("---") or clean_line.startswith("<!--"):
                continue
            if clean_line not in existing_lines:
                new_entries.append(line)

    if not new_entries:
        print("✅ No new entries found.")
        return

    timestamp = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    marker = MARKER_TEMPLATE.format(date=timestamp)
    with open(DEST_FILE, "a", encoding="utf-8") as f:
        f.write(marker)
        for entry in new_entries:
            f.write(entry)
    print(f"✅ Harvested {len(new_entries)} new entries.")

if __name__ == "__main__":
    harvest()

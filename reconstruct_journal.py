import re
from datetime import datetime

INPUT_FILE = "usage_harvest.md"
OUTPUT_FILE = "sam_journal.txt"

def parse_line(line):
    # Format: [TAG] YYYY-MM-DD HH:MM:SS | CONTENT | ...
    parts = line.split('|')
    if len(parts) < 2: return None
    
    meta = parts[0].strip() # [TAG] Date Time
    content = parts[1].strip() # word logic
    
    # Extract timestamp
    try:
        ts_str = " ".join(meta.split()[1:3])
        timestamp = datetime.strptime(ts_str, "%Y-%m-%d %H:%M:%S")
    except:
        return None

    word = ""
    tag = meta.split()[0]
    
    # Clean content
    # Remove "(reverted)" or other notes
    content = content.replace("(reverted)", "").strip()
    
    # CHECK FOR UNICODE ARROWS
    if "→" in content:
        # ACCEPTED: typo -> correction
        # We want the correction (Right side)
        word = content.split("→")[1].strip()
    elif "←" in content:
        # REJECTED: typed <- correction
        # We want the typed (Left side)
        word = content.split("←")[0].strip()
    else:
        # INSISTED / PICKED (or ASCII fallback just in case)
        if "->" in content:
            word = content.split("->")[1].strip()
        elif "<-" in content:
            word = content.split("<-")[0].strip()
        else:
            word = content.strip()
        
    return timestamp, word

entries = []
with open(INPUT_FILE, 'r') as f:
    for line in f:
        if line.startswith("[") and ("ACCEPTED" in line or "REJECTED" in line or "INSISTED" in line or "PICKED" in line):
            res = parse_line(line)
            if res:
                entries.append(res)

entries.sort(key=lambda x: x[0])

with open(OUTPUT_FILE, 'w') as f:
    if not entries:
        print("No entries found.")
        exit()
        
    current_line = []
    last_time = entries[0][0]
    
    for ts, word in entries:
        delta = (ts - last_time).total_seconds()
        
        # New paragraph if > 2 minutes silence
        if delta > 120:
            if current_line:
                f.write(" ".join(current_line) + "\n\n")
            current_line = []
            
        current_line.append(word)
        last_time = ts
        
    if current_line:
        f.write(" ".join(current_line) + "\n")

print(f"Journal reconstructed to {OUTPUT_FILE}")

import os
import sys

HARVEST_FILE = "usage_harvest.md"
REVIEW_MARKER = "<!-- Data below this line is NEW since last review -->"
NL = chr(10)
MARKER_BLOCK = f"{NL}{NL}{REVIEW_MARKER}{NL}---{NL}"

def process_harvest():
    # Ensure we are in the right directory or finding the file
    if not os.path.exists(HARVEST_FILE):
        # Try finding it in the current directory
        if not os.path.exists(HARVEST_FILE):
            print(f"Error: {HARVEST_FILE} not found in current directory.")
            return

    with open(HARVEST_FILE, "r", encoding="utf-8") as f:
        content = f.read()

    # Find last marker
    last_marker_index = content.rfind(REVIEW_MARKER)
    
    new_content = ""
    if last_marker_index == -1:
        print("No review marker found. Processing ALL content as new.")
        # We might want to skip the header? Header usually ends with "---"
        # The file header is:
        # ...
        # ---
        # So we can search for the first "---" and take everything after.
        # But for now, let's just take the whole file or everything after the first "---" if present.
        
        # Simple heuristic: Split by "---" and take the last part if it looks like entries?
        # Actually, let's just treat everything as new for the first run.
        new_content = content
    else:
        print("Review marker found. Processing new content since last review.")
        # Content starts after the marker line(s).
        # The marker is "<!-- ... -->"
        # We assume the marker is followed by newlines or "---".
        # We cut from the end of the marker.
        start_index = last_marker_index + len(REVIEW_MARKER)
        
        # If there is a "---" following the marker, skip that too.
        # Our append logic adds "\n---\n", so we should look for that.
        subsequent = content[start_index:]
        if subsequent.strip().startswith("---"):
             # Find the end of "---"
             dash_index = subsequent.find("---")
             start_index += dash_index + 3
        
        new_content = content[start_index:]

    # Extract entries
    lines = new_content.strip().split('\n')
    entries = [line for line in lines if line.strip().startswith('[')]
    
    if not entries:
        print("No new entries found.")
    else:
        print(f"Found {len(entries)} new entries.")
        # Here is where we would trigger any "processing" logic (e.g. adding to dictionary)
        # For now, we just list them or summary.
        categories = {}
        for entry in entries:
            # Entry format: [CATEGORY] ...
            try:
                cat = entry.split(']')[0].strip('[')
                categories[cat] = categories.get(cat, 0) + 1
            except:
                pass
        
        print("Summary of new entries:")
        for cat, count in categories.items():
            print(f"  {cat}: {count}")

    # Ask for confirmation before marking as reviewed? 
    # The prompt said: "After processing, the agent should append a new review marker"
    # I'll just do it. 
    
    try:
        with open(HARVEST_FILE, "a", encoding="utf-8") as f:
            f.write(MARKER_BLOCK)
        print(f"Successfully appended new review marker to {HARVEST_FILE}.")
    except Exception as e:
        print(f"Error appending marker: {e}")

if __name__ == "__main__":
    process_harvest()
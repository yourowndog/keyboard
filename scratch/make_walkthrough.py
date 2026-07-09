#!/usr/bin/env python3
import json
import os
import re

log_path = "/data/data/com.termux/files/home/.gemini/antigravity-cli/brain/e94c3ebb-df29-4624-bed8-c9b1cd2aadb1/.system_generated/logs/transcript_full.jsonl"
out_path = "/data/data/com.termux/files/home/projects/keyboard-local/CONVERSATION_WALKTHROUGH.md"

if not os.path.exists(log_path):
    log_path = "/data/data/com.termux/files/home/.gemini/antigravity-cli/brain/e94c3ebb-df29-4624-bed8-c9b1cd2aadb1/.system_generated/logs/transcript.jsonl"
    if not os.path.exists(log_path):
        print("❌ Error: Log path not found!")
        exit(1)

print(f"📖 Reading conversation log from: {log_path}")

steps = []
with open(log_path, "r", encoding="utf-8") as f:
    for line in f:
        try:
            steps.append(json.loads(line))
        except Exception:
            continue

user_requests = []
current_request = None

for data in steps:
    step = data.get("step_index", 0)
    source = data.get("source", "")
    msg_type = data.get("type", "")
    content = data.get("content", "")
    
    if msg_type == "USER_INPUT":
        # Strip metadata block
        clean_content = content.replace("<USER_REQUEST>", "").replace("</USER_REQUEST>", "").strip()
        # Remove additional metadata blocks
        clean_content = re.sub(r"<ADDITIONAL_METADATA>.*?</ADDITIONAL_METADATA>", "", clean_content, flags=re.DOTALL)
        clean_content = re.sub(r"<USER_SETTINGS_CHANGE>.*?</USER_SETTINGS_CHANGE>", "", clean_content, flags=re.DOTALL)
        
        user_requests.append({
            "step": step,
            "request": clean_content.strip(),
            "response": []
        })
    elif source == "MODEL" and msg_type == "PLANNER_RESPONSE" and content and user_requests:
        # Save model text responses
        user_requests[-1]["response"].append(content.strip())

# Build Markdown Walkthrough
md_lines = [
    "# Document Walkthrough: OmniBoard Keyboard Harvesting & Calibration Project",
    "This document compiles a detailed, step-by-step chronological walkthrough of the entire pair programming session.",
    "It catalogs the goals, investigations, reports, and scripts created to compile, clean, and model keyboard data.",
    "",
    "---",
    ""
]

for idx, ur in enumerate(user_requests, 1):
    md_lines.append(f"## Phase {idx}: User Request (Step {ur['step']})")
    md_lines.append("### 📥 User Request:")
    md_lines.append("```text")
    md_lines.append(ur["request"])
    md_lines.append("```")
    md_lines.append("")
    md_lines.append("### 🛠️ Key Actions & Code Decisions:")
    
    full_resp = "\n\n".join(ur["response"])
    
    # Identify scripts created/modified in this step
    actions = []
    if "harvest_analyze.py" in full_resp:
        actions.append("- **Autocorrect Parser Fix:** Updated regex patterns in [harvest_analyze.py](file:///data/data/com.termux/files/home/projects/keyboard-local/harvest_analyze.py) to support greedy text capturing with optional app context.")
    if "harvest_manifest.py" in full_resp:
        actions.append("- **Script Built:** Created [harvest_manifest.py](file:///data/data/com.termux/files/home/projects/keyboard-local/harvest_manifest.py) for measurement-only corpus triage.")
    if "INTENT_RECON.md" in full_resp:
        actions.append("- **Analysis Report:** Compiled [INTENT_RECON.md](file:///data/data/com.termux/files/home/projects/keyboard-local/INTENT_RECON.md) documenting user intent outcomes.")
    if "segment_recovery.py" in full_resp:
        actions.append("- **Script Built:** Created [segment_recovery.py](file:///data/data/com.termux/files/home/projects/keyboard-local/segment_recovery.py) to segment and restore spaces in concatenated lines.")
    if "sam_unigram_seed.tsv" in full_resp:
        actions.append("- **Data Built:** Compiled custom user vocabulary seed dictionary [sam_unigram_seed.tsv](file:///data/data/com.termux/files/home/projects/keyboard-local/sam_unigram_seed.tsv) containing 10,783 unique words.")
    if "segmentation_recovery.tsv" in full_resp:
        actions.append("- **Reversible Mapping Built:** Created [segmentation_recovery.tsv](file:///data/data/com.termux/files/home/projects/keyboard-local/segmentation_recovery.tsv) containing 1,094 conversational segments sorted into high/low/skipped tiers.")
        
    if actions:
        md_lines.extend(actions)
    else:
        md_lines.append("- *Information lookup, environment scan, or configuration setup.*")
        
    md_lines.append("")
    md_lines.append("### 📊 Metrics, Diagnostics, and Summaries:")
    
    # Extract important blocks (like code snippets, bullet points, stats)
    # Find tables, headers, and code sections in the model response
    lines = full_resp.split("\n")
    block_lines = []
    capture = False
    unresolved_seen = False
    
    for l in lines:
        l_strip = l.strip()
        # Capture metrics, distribution blocks, ASCII tables, and headers
        if l_strip.startswith("###") or l_strip.startswith("====") or l_strip.startswith("📋") or "Calibration Denominator" in l or "Bucket Distribution" in l or "Rejection Outcome" in l or "Reversibility" in l or "validated_reject" in l:
            capture = True
        
        if capture:
            block_lines.append(l)
            if len(block_lines) > 35:
                # Truncate overly long dumps
                block_lines.append("*(Remaining metrics section truncated for readability)*")
                break
                
        # Stop capturing on horizontal rules
        if l_strip == "---" or l_strip.startswith("=="):
            capture = False
            
    if block_lines:
        md_lines.extend(block_lines)
    else:
        # Extract first 5 lines of response as general description
        md_lines.append("\n".join(lines[:6]))
        
    md_lines.append("")
    md_lines.append("---")
    md_lines.append("")

# Write file
with open(out_path, "w", encoding="utf-8") as out_f:
    out_f.write("\n".join(md_lines))

print(f"✅ Successfully generated walkthrough report at: {out_path}")

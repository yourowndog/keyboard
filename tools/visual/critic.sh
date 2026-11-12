#!/usr/bin/env bash
# Second-opinion design review of a capture round. A headless Claude Code run reads the
# screenshots, the design reference and, when compare.sh has run, the before/after
# comparison. It returns ranked defects, whether the round moved toward or away from the
# target, and ideas for depth and character beyond the reference.
#
#   tools/visual/critic.sh <round-dir> [focus]
#
# <round-dir> is a capture.sh output directory. [focus] narrows the review, for example
# "command strip labels". The verdict is written to <round-dir>/critique.md and printed.
set -euo pipefail

ROUND=$(cd "${1:?round dir}" && pwd)
FOCUS=${2:-the whole keyboard}
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
REF="$REPO/docs/theming/reference/design-reference-01.png"
RUBRIC="$REPO/tools/visual/rubric.md"

# Headless Claude Code may only read inside its working directory without a prompt, so the
# reference and rubric are copied next to the shots.
cp "$REF" "$ROUND/reference.png"
cp "$RUBRIC" "$ROUND/rubric.md"
REF="$ROUND/reference.png"
RUBRIC="$ROUND/rubric.md"
shots=$(ls "$ROUND"/*.keyboard.png "$ROUND"/*.smartbar.png 2>/dev/null | tr '\n' ' ')
[[ -n $shots ]] || { echo "no crops in $ROUND; run capture.sh first" >&2; exit 2; }
compares=$(ls "$ROUND"/compare-*.png 2>/dev/null | tr '\n' ' ')
history=""
if [[ -n $compares ]]; then
    history="
Each of these images is a row of panels, left to right: REFERENCE, BEFORE (the previous round),
AFTER (this round) and CHANGED (the previous round faded, with changed pixels in coral): $compares
The change made in this round: $(cat "$ROUND/label.txt" 2>/dev/null || echo unknown)."
    [[ -f $ROUND/previous-critique.md ]] && history+="
The previous round's critique is $ROUND/previous-critique.md; say which of its points this round fixed."
fi

prompt="You are a strict visual design critic for an Android keyboard theme.
Read the design reference image $REF and the rubric $RUBRIC.
Then read each of these screenshots from a 1440x3120 device at 485 dpi: $shots
Files named *.smartbar.png are the top strip at full resolution; *.keyboard.png is the whole keyboard.$history
Focus on: $FOCUS.
The reference is a direction, not a template: the goal is a layered keyboard with depth that reads
as the native interface of a custom device, not a flat skin copied from the picture.
Answer in three sections:
## Defects
Most severe first, at most 8 concrete defects. For each give: what you see, where (file and
element), why it breaks the rubric or the reference, and a specific fix (sizes in dp/sp, alignment,
colour role). Do not praise. If something is genuinely fine, say nothing about it.
## Trajectory
Only if a comparison was given: in a few lines, did this round move toward or away from the target,
what improved, and what regressed (anything that changed that the round's stated change did not call for).
## Depth and character
Three to five specific ideas that would add layers, depth or personality without breaking the
rubric: recessed housings, light and shadow, structural rules and brackets, status readouts,
motion. Name the element, the treatment and the colour role. Prefer ideas that use the keyboard's
real functions (it doubles as a coding keyboard) over decoration.
Use only the Read tool to look at the images; do not run commands or write files. Answer in text.
End with one line: VERDICT: PASS or VERDICT: FAIL, PASS only if nothing in Defects would bother a
careful user. Depth ideas never fail a round."

cd "$ROUND"
timeout 600 claude -p "$prompt" --allowedTools Read --disallowedTools Bash Write Edit </dev/null | tee "$ROUND/critique.md"

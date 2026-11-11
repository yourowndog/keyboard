#!/usr/bin/env bash
# Second-opinion design review of a capture round. A headless Claude Code run reads the
# screenshots and the design reference and returns a ranked list of visual defects.
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

prompt="You are a strict visual design critic for an Android keyboard theme.
Read the design reference image $REF and the rubric $RUBRIC.
Then read each of these screenshots from a 1440x3120 device at 485 dpi: $shots
Files named *.smartbar.png are the top strip at full resolution; *.keyboard.png is the whole keyboard.
Focus on: $FOCUS.
Report, most severe first, at most 8 concrete defects. For each give: what you see, where (file and
element), why it breaks the rubric or the reference, and a specific fix (sizes in dp/sp, alignment,
colour role). Do not praise. If something is genuinely fine, say nothing about it.
Use only the Read tool to look at the images; do not run commands or write files. Answer in text.
End with one line: VERDICT: PASS or VERDICT: FAIL, PASS only if nothing would bother a careful user."

cd "$ROUND"
timeout 600 claude -p "$prompt" --allowedTools Read --disallowedTools Bash Write Edit </dev/null | tee "$ROUND/critique.md"

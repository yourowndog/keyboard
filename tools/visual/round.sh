#!/usr/bin/env bash
# One full round of the visual loop: capture, compare with the previous round and the
# reference, critique, and add the round to the history.
#
#   tools/visual/round.sh <apk> <label> [focus]
#
# <label> says what changed, for example "stadium shift inner edge". Each round lands in
# $OMNI_VISUAL_HOME/rounds/<yyyymmdd-hhmmss>-<slug> (default ~/omniboard-visual). The
# "before" is the newest earlier round; set BEFORE=<round-dir> to compare against another,
# or BEFORE=- for none. Set NO_CRITIC=1 to skip the review. CONFIG=<tar> restores a different
# settings snapshot than the default (for example one with another profile or geometry).
set -euo pipefail

APK=${1:?apk}
LABEL=${2:?label: what changed in this round}
FOCUS=${3:-the whole keyboard}
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
ROUNDS=${OMNI_VISUAL_HOME:-$HOME/omniboard-visual}/rounds
mkdir -p "$ROUNDS"

if [[ -z ${BEFORE:-} ]]; then
    BEFORE=$(find "$ROUNDS" -mindepth 2 -maxdepth 2 -name 01-idle.keyboard.png -printf '%h\n' | sort | tail -1)
    BEFORE=${BEFORE:--}
fi
slug=$(tr 'A-Z' 'a-z' <<<"$LABEL" | tr -cs 'a-z0-9' '-' | sed 's/^-*//; s/-*$//' | cut -c1-40)
ROUND=$ROUNDS/$(date +%Y%m%d-%H%M%S)-${slug:-round}
mkdir -p "$ROUND"
echo "$LABEL" >"$ROUND/label.txt"
{
    echo "date=$(date -Is)"
    echo "commit=$(git -C "$REPO" rev-parse --short HEAD)$(git -C "$REPO" diff --quiet HEAD -- . || echo '+dirty')"
    echo "apk_sha256=$(sha256sum "$APK" | cut -c1-16)"
    echo "before=$( [[ $BEFORE == - ]] && echo - || basename "$BEFORE")"
    echo "focus=$FOCUS"
    echo "config=${CONFIG:-default}"
} >"$ROUND/meta.txt"

"$HERE/capture.sh" "$APK" "$ROUND" "${CONFIG:-}"
python3 "$HERE/measure.py" "$ROUND/01-idle.keyboard.png" --reference "$REPO/docs/theming/reference/design-reference-01.png" \
    >"$ROUND/geometry.txt" 2>&1 || true
"$HERE/compare.sh" "$BEFORE" "$ROUND"
[[ $BEFORE != - && -f $BEFORE/critique.md ]] && cp "$BEFORE/critique.md" "$ROUND/previous-critique.md"
[[ -n ${NO_CRITIC:-} ]] || "$HERE/critic.sh" "$ROUND" "$FOCUS"
"$HERE/history.sh" "$ROUNDS"
echo "round: $ROUND"

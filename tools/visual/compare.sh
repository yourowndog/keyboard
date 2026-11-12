#!/usr/bin/env bash
# Side-by-side of the design reference, the keyboard before a change, and after it, plus a
# map of the pixels that changed. Uses ImageMagick 7 only.
#
#   tools/visual/compare.sh <before-round|-> <after-round>
#
# Both arguments are capture.sh output directories; pass - when there is no earlier round.
# For each captured state it writes <after-round>/compare-<state>.png and
# <after-round>/changed-<state>.png, and records the share of changed pixels in
# <after-round>/compare.txt.
set -euo pipefail

BEFORE=${1:?before round or -}
AFTER=$(cd "${2:?after round}" && pwd)
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
REF="$REPO/docs/theming/reference/design-reference-01.png"
H=${COMPARE_HEIGHT:-720}
[[ $BEFORE == - ]] || BEFORE=$(cd "$BEFORE" && pwd)

# One captioned panel: the image scaled to height $H on black, with a caption bar on top.
panel() { # <src|-> <caption> <out>
    if [[ $1 == - ]]; then
        magick -size "$((H * 1440 / 1300))x$H" xc:'#0C1014' -fill '#51596E' -pointsize 34 \
            -gravity center -annotate +0+0 'no earlier round' "$3"
    else
        magick "$1" -resize "x$H" "$3"
    fi
    magick "$3" -background '#000000' -fill '#9EA4BA' -pointsize 30 -gravity north \
        -splice 0x56 -annotate +0+10 "$2" "$3"
}

before_label=$( [[ $BEFORE == - ]] && echo none || cat "$BEFORE/label.txt" 2>/dev/null || basename "$BEFORE")
after_label=$(cat "$AFTER/label.txt" 2>/dev/null || basename "$AFTER")
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
: >"$AFTER/compare.txt"

for after in "$AFTER"/*.keyboard.png; do
    state=$(basename "$after" .keyboard.png)
    before=-
    [[ $BEFORE != - && -f $BEFORE/$state.keyboard.png ]] && before=$BEFORE/$state.keyboard.png

    panel "$REF" "REFERENCE" "$tmp/ref.png"
    panel "$before" "BEFORE  $before_label" "$tmp/before.png"
    panel "$after" "AFTER  $after_label" "$tmp/after.png"

    if [[ $before != - ]]; then
        # Pad both to a common canvas, top-aligned, so a change in keyboard height shows as
        # change instead of breaking the comparison.
        w=$(magick identify -format '%w' "$before"); h1=$(magick identify -format '%h' "$before")
        w2=$(magick identify -format '%w' "$after"); h2=$(magick identify -format '%h' "$after")
        W=$(( w > w2 ? w : w2 )); HH=$(( h1 > h2 ? h1 : h2 ))
        magick "$before" -background black -gravity north -extent "${W}x$HH" "$tmp/b.png"
        magick "$after" -background black -gravity north -extent "${W}x$HH" "$tmp/a.png"
        # AE with a small fuzz ignores SwiftShader dithering; compare exits 1 when images differ.
        changed=$(magick compare -metric AE -fuzz 4% "$tmp/b.png" "$tmp/a.png" \
            -highlight-color '#FF6752' "$AFTER/changed-$state.png" 2>&1 >/dev/null || true)
        changed=${changed%% *}
        pct=$(awk -v c="$changed" -v t="$((W * HH))" 'BEGIN { printf "%.1f", 100 * c / t }')
        panel "$AFTER/changed-$state.png" "CHANGED  ${pct}% of pixels" "$tmp/changed.png"
        echo "$state changed ${pct}%" >>"$AFTER/compare.txt"
        set -- "$tmp/ref.png" "$tmp/before.png" "$tmp/after.png" "$tmp/changed.png"
    else
        echo "$state changed n/a (no earlier round)" >>"$AFTER/compare.txt"
        set -- "$tmp/ref.png" "$tmp/before.png" "$tmp/after.png"
    fi
    magick "$@" -bordercolor '#000000' -border 12 -gravity north -background '#000000' +append \
        -border 12 +repage "$AFTER/compare-$state.png"
    echo "$AFTER/compare-$state.png"
done
cat "$AFTER/compare.txt"

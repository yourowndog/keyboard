#!/usr/bin/env bash
# Copy the licensed files named in private-picks.txt into the gitignored
# private-assets/ folder, which app/build.gradle.kts packs into local APKs.
#
#   tools/assets/stage-private-assets.sh [SOURCE]
#
# SOURCE is the asset pack root, local or an rsync remote such as
# sleeper:documents/lcars-trek-assets (the default when the pack is not local).
# Nothing here is committed; see docs/theming/lcars-assets.md.
set -euo pipefail

repo="$(cd "$(dirname "$0")/../.." && pwd)"
picks="$repo/tools/assets/private-picks.txt"
dest="$repo/private-assets"

if [[ $# -ge 1 ]]; then
    source="$1"
elif [[ -d "$HOME/documents/lcars-trek-assets" ]]; then
    source="$HOME/documents/lcars-trek-assets"
else
    source="sleeper:documents/lcars-trek-assets"
fi

if ! git -C "$repo" check-ignore -q "$dest/x"; then
    echo "refusing: private-assets/ is not gitignored" >&2
    exit 1
fi

rm -rf "$dest"
mkdir -p "$dest"
count=0
while read -r from to; do
    [[ -z "${from:-}" || "$from" == \#* ]] && continue
    if [[ "$from" == */ ]]; then
        # A trailing slash stages a whole prepared folder's contents.
        mkdir -p "$dest/$to"
        rsync -rt "$source/$from" "$dest/$to"
    else
        mkdir -p "$dest/$(dirname "$to")"
        rsync -t "$source/$from" "$dest/$to"
    fi
    count=$((count + 1))
done < "$picks"

echo "staged $count pick(s) into $dest from $source"
du -sh "$dest"

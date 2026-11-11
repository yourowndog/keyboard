#!/usr/bin/env bash
# Fast verification for OmniBoard. CI runs exactly this, so a green run here
# means a green run there.
#
#   tools/check.sh            # stylesheets + unit tests
#   tools/check.sh --quick    # stylesheets only (seconds, no Gradle)
#
# This does not build an APK and cannot tell you anything about appearance,
# touch behavior, or anything else that only shows up on a device. See
# docs/development/fleet.md for building and installing.

set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO"

QUICK=0
[[ "${1:-}" == "--quick" ]] && QUICK=1

fail=0
step() { printf '\n=== %s ===\n' "$1"; }

step "Snygg stylesheets"
if ! python3 -c 'import jsonschema' 2>/dev/null; then
    echo "jsonschema is missing; install it with: pip install jsonschema" >&2
    exit 2
fi
python3 tools/theme/validate_stylesheets.py || fail=1

if [[ "$QUICK" == "1" ]]; then
    printf '\n--quick: skipped unit tests\n'
    exit "$fail"
fi

step "Unit tests"
./gradlew --no-daemon testDebugUnitTest || fail=1

if [[ "$fail" != "0" ]]; then
    printf '\nFAILED\n'
    exit 1
fi
printf '\nAll checks passed\n'

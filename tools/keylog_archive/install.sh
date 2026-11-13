#!/usr/bin/env bash
# Install or update the keylog archive on Titan. Run from a checkout on Titan:
#   sudo tools/keylog_archive/install.sh
# Idempotent. Restarts the receiver only when its code changed.
set -euo pipefail
SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEST=/opt/keylog-archive
install -d -m 755 "$DEST"
changed=0
for f in keylog_store.py receiver.py schema.py build_dataset.py import_file.py; do
    if ! cmp -s "$SRC/$f" "$DEST/$f"; then
        install -m 755 "$SRC/$f" "$DEST/$f"
        changed=1
    fi
done
for u in keylog-archive.service keylog-dataset.service keylog-dataset.timer; do
    install -m 644 "$SRC/systemd/$u" "/etc/systemd/system/$u"
done
install -d -o sam -g sam -m 750 /home/sam/datasets/sam-keylog
systemctl daemon-reload
systemctl enable --now keylog-archive.service keylog-dataset.timer
[[ "$changed" == 1 ]] && systemctl restart keylog-archive.service
systemctl --no-pager --lines=0 status keylog-archive.service

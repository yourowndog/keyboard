#!/usr/bin/env python3
"""Capture exact OmniBoard harvest files without merging or de-duplicating.

Snapshots preserve byte order and duplicate events. Promotion/merging into the
canonical corpus is deliberately a separate reviewed operation.
"""

from __future__ import annotations

import argparse
import datetime as dt
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
DEFAULT_INBOX = REPO / "data" / "harvest" / "inbox"
DEVICE_DIR = Path("/sdcard/Documents")
FILENAMES = ("usage_harvest.md", "usage_harvest.jsonl")


def snapshot_local(source_dir: Path, output_dir: Path) -> list[Path]:
    stamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    destination = output_dir / stamp
    destination.mkdir(parents=True, exist_ok=False)
    copied: list[Path] = []
    for filename in FILENAMES:
        source = source_dir / filename
        if not source.exists():
            print(f"warning: missing {source}", file=sys.stderr)
            continue
        target = destination / filename
        shutil.copy2(source, target)
        copied.append(target)
    if not copied:
        destination.rmdir()
        raise FileNotFoundError(f"no harvest files found in {source_dir}")
    return copied


def snapshot_adb(output_dir: Path, serial: str | None) -> list[Path]:
    with tempfile.TemporaryDirectory(prefix="omniboard-harvest-") as temp:
        source_dir = Path(temp)
        for filename in FILENAMES:
            command = ["adb"]
            if serial:
                command += ["-s", serial]
            command += ["pull", str(DEVICE_DIR / filename), str(source_dir / filename)]
            result = subprocess.run(command, check=False)
            if result.returncode != 0:
                print(f"warning: adb could not pull {filename}", file=sys.stderr)
        return snapshot_local(source_dir, output_dir)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Capture exact harvest snapshots; never merge or deduplicate events.",
    )
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument(
        "--local",
        action="store_true",
        help="read /sdcard/Documents directly (Termux/device execution)",
    )
    source.add_argument(
        "--adb",
        action="store_true",
        help="pull files through adb (workstation execution)",
    )
    parser.add_argument("--serial", help="optional adb device serial or host:port")
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_INBOX)
    args = parser.parse_args()

    try:
        copied = (
            snapshot_local(DEVICE_DIR, args.output_dir)
            if args.local
            else snapshot_adb(args.output_dir, args.serial)
        )
    except (FileNotFoundError, FileExistsError, OSError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    print("Captured exact, unmerged harvest snapshot:")
    for path in copied:
        print(f"  {path}")
    print("Review and promote this snapshot separately; the canonical corpus was not changed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

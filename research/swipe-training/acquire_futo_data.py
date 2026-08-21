#!/usr/bin/env python3
"""
FUTO Swipe Dataset Lossless Reacquisition & Verification Pipeline

Canonical source: futo-org/swipe.futo.org (Hugging Face Datasets)
License: MIT

This script reacquires all canonical splits of the public FUTO swipe dataset
into the raw, immutable data store (`data/swipe/raw/futo/`) and verifies
schema fidelity, row counts, and cryptographic SHA-256 checksums against
the tracked lock manifest (`research/swipe-training/futo_dataset_lock.json`).

Reacquisition fails loudly if any shard's SHA-256 or row count deviates from
the locked upstream baseline.
"""

import argparse
import hashlib
import json
import os
import sys
from pathlib import Path
from typing import Dict, Any, List

import requests
import pyarrow.parquet as pq

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
DEFAULT_RAW_DIR = REPO_ROOT / "data" / "swipe" / "raw" / "futo"
DEFAULT_LOCK_FILE = REPO_ROOT / "research" / "swipe-training" / "futo_dataset_lock.json"


def compute_sha256(filepath: Path) -> str:
    sha256 = hashlib.sha256()
    with open(filepath, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            sha256.update(chunk)
    return sha256.hexdigest()


def download_file(url: str, dest_path: Path) -> bool:
    dest_path.parent.mkdir(parents=True, exist_ok=True)
    temp_path = dest_path.with_suffix(".tmp")
    
    print(f"Downloading {url} -> {dest_path}")
    response = requests.get(url, stream=True, timeout=60)
    response.raise_for_status()
    
    total_size = int(response.headers.get("content-length", 0))
    downloaded = 0
    with open(temp_path, "wb") as f:
        for chunk in response.iter_content(chunk_size=1024 * 1024):
            if chunk:
                f.write(chunk)
                downloaded += len(chunk)
                if total_size > 0:
                    percent = (downloaded / total_size) * 100
                    mb = downloaded / (1024 * 1024)
                    print(f"\r  [{percent:5.1f}%] {mb:6.1f} MB", end="", flush=True)
    print()
    temp_path.replace(dest_path)
    return True


def verify_shard(filepath: Path, shard_lock: Dict[str, Any]) -> Dict[str, Any]:
    run_name = shard_lock["run"]
    split_name = shard_lock["split"]
    expected_rows = shard_lock["expected_rows"]
    expected_sha256 = shard_lock["expected_sha256"]
    expected_size = shard_lock["expected_size_bytes"]
    expected_cols = shard_lock.get("expected_columns", [])

    if not filepath.exists():
        raise FileNotFoundError(f"Shard file not found: {filepath}")

    actual_size = filepath.stat().st_size
    if actual_size != expected_size:
        raise ValueError(
            f"[FATAL] Byte-size mismatch for {filepath}: "
            f"expected {expected_size}, got {actual_size}"
        )

    # 1. Cryptographic SHA-256 Checksum Verification
    actual_sha256 = compute_sha256(filepath)
    if actual_sha256 != expected_sha256:
        raise ValueError(
            f"\n[FATAL] SHA-256 checksum mismatch for shard: {filepath.relative_to(REPO_ROOT)}\n"
            f"  Expected (locked): {expected_sha256}\n"
            f"  Actual (computed): {actual_sha256}\n"
            f"  The downloaded shard does NOT match the canonical tracked dataset lock!\n"
            f"  Possible corruption or upstream Hugging Face revision change."
        )

    # 2. Parquet Metadata & Row Count Verification
    parquet_file = pq.ParquetFile(str(filepath))
    num_rows = parquet_file.metadata.num_rows
    num_row_groups = parquet_file.metadata.num_row_groups
    schema = parquet_file.schema_arrow
    column_names = schema.names
    
    if num_rows != expected_rows:
        raise ValueError(
            f"\n[FATAL] Row count mismatch for shard: {filepath.relative_to(REPO_ROOT)}\n"
            f"  Expected (locked): {expected_rows:,}\n"
            f"  Actual (metadata): {num_rows:,}"
        )

    # 3. Schema & Column Check
    for col in expected_cols:
        if col not in column_names:
            raise ValueError(f"Missing expected column '{col}' in {filepath.relative_to(REPO_ROOT)}")

    # 4. Trajectory Data Integrity Check (Sample 1st row)
    table = parquet_file.read_row_group(0)
    first_row = table.slice(0, 1).to_pylist()[0]
    
    for col in expected_cols:
        if col not in first_row:
            raise ValueError(f"Column '{col}' absent in row 0 of {filepath.relative_to(REPO_ROOT)}")

    raw_data = first_row["data"]
    if isinstance(raw_data, str):
        parsed_data = json.loads(raw_data)
    else:
        parsed_data = raw_data

    if isinstance(parsed_data, list):
        if not parsed_data:
            raise ValueError(f"Trajectory data list is empty in {filepath}")
        pt = parsed_data[0]
        if not all(key in pt for key in ("x", "y", "t")):
            raise ValueError(f"Missing x/y/t keys in trajectory point: {pt}")
        sample_points_desc = f"{len(parsed_data)} points"
    elif isinstance(parsed_data, dict):
        if "L" not in parsed_data and "R" not in parsed_data:
            raise ValueError(f"Dual finger dictionary format invalid: {parsed_data.keys()}")
        sample_points_desc = f"dual_finger (keys: {list(parsed_data.keys())})"
    else:
        raise ValueError(f"Unknown data structure in {filepath}: {type(raw_data)}")
    
    file_size = filepath.stat().st_size
    
    return {
        "file": str(filepath.relative_to(REPO_ROOT)),
        "run": run_name,
        "split": split_name,
        "filename": filepath.name,
        "rows": num_rows,
        "row_groups": num_row_groups,
        "size_bytes": file_size,
        "sha256": actual_sha256,
        "columns": column_names,
        "sample_word": first_row.get("word"),
        "sample_points_desc": sample_points_desc,
        "sample_canvas_width": first_row.get("canvas_width"),
        "sample_canvas_height": first_row.get("canvas_height"),
        "sample_orientation": first_row.get("orientation"),
        "sample_language": first_row.get("language"),
        "sample_layout": first_row.get("layout"),
    }


def main():
    parser = argparse.ArgumentParser(description="Acquire and verify canonical FUTO swipe dataset against lock manifest")
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_RAW_DIR, help="Destination directory for raw parquet shards")
    parser.add_argument("--lock-file", type=Path, default=DEFAULT_LOCK_FILE, help="Path to tracked futo_dataset_lock.json")
    parser.add_argument("--verify-only", action="store_true", help="Verify existing files without attempting downloads")
    parser.add_argument("--force", action="store_true", help="Force re-download of existing files")
    args = parser.parse_args()

    if not args.lock_file.exists():
        print(f"[ERROR] Lock manifest not found at: {args.lock_file}", file=sys.stderr)
        sys.exit(1)

    with open(args.lock_file, "r") as f:
        lock_data = json.load(f)

    out_dir = args.output_dir
    out_dir.mkdir(parents=True, exist_ok=True)

    print(f"=== FUTO Swipe Dataset Acquisition & Verification ===")
    print(f"Lock Manifest:   {args.lock_file.relative_to(REPO_ROOT)}")
    print(f"HF Commit SHA:   {lock_data.get('huggingface_commit_sha', 'unknown')}")
    print("Parquet source:  Hugging Face refs/convert API (not source-revision addressable)")
    print("Guarantee:       downloaded bytes must match the lock; upstream drift fails loudly")
    print(f"Target Directory: {out_dir}")
    print(f"Total Shards:    {len(lock_data['shards'])}")
    print()

    manifest_entries = []
    total_swipes = 0
    total_bytes = 0

    for shard in lock_data["shards"]:
        run_name = shard["run"]
        split_name = shard["split"]
        filename = shard["filename"]
        url = shard["url"]
        
        dest_file = out_dir / run_name / split_name / filename

        if not dest_file.exists() and args.verify_only:
            raise FileNotFoundError(f"Missing required shard during verify-only: {dest_file.relative_to(REPO_ROOT)}")

        if dest_file.exists() and not args.force:
            print(f"Already on disk: {dest_file.relative_to(REPO_ROOT)}")
        else:
            download_file(url, dest_file)
        
        print(f"Verifying {dest_file.relative_to(REPO_ROOT)} against lock manifest...")
        entry = verify_shard(dest_file, shard)
        manifest_entries.append(entry)
        total_swipes += entry["rows"]
        total_bytes += entry["size_bytes"]
        print(f"  ✓ LOCKED & VERIFIED: {entry['rows']:,} rows, {entry['size_bytes'] / (1024*1024):.2f} MB, SHA: {entry['sha256'][:12]}...")

    # Write local manifest for fast local inspection
    local_manifest = {
        "dataset_name": lock_data.get("dataset_id", "futo-org/swipe.futo.org"),
        "source_url": lock_data.get("source_url"),
        "license": lock_data.get("license"),
        "huggingface_commit_sha": lock_data.get("huggingface_commit_sha"),
        "total_swipes": total_swipes,
        "total_bytes": total_bytes,
        "total_megabytes": round(total_bytes / (1024 * 1024), 2),
        "verified_runs": lock_data.get("verified_runs", []),
        "shards": manifest_entries,
    }

    manifest_path = out_dir / "manifest.json"
    with open(manifest_path, "w") as f:
        json.dump(local_manifest, f, indent=2)

    print("\n=== Verification Summary ===")
    print(f"All {len(lock_data['shards'])} shards match tracked lock manifest exactly.")
    print(f"Total Swipes:       {total_swipes:,}")
    print(f"Total Disk Size:    {total_bytes / (1024 * 1024):.2f} MB")
    print(f"Upstream HF Commit: {lock_data.get('huggingface_commit_sha')}")
    print(f"Local Manifest:     {manifest_path.relative_to(REPO_ROOT)}")
    print("Dataset verification: 100% BIT-FOR-BIT CRYPTOGRAPHICALLY VERIFIED.")


if __name__ == "__main__":
    main()

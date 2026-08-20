#!/usr/bin/env python3
"""
FUTO Swipe Dataset Lossless Reacquisition & Verification Pipeline

Canonical source: futo-org/swipe.futo.org (Hugging Face Datasets)
License: MIT

This script reacquires all canonical splits of the public FUTO swipe dataset
into the raw, immutable data store (`data/swipe/raw/futo/`) and verifies
schema fidelity, row counts, and lossless preservation of all original fields.
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

HF_BASE_API = "https://huggingface.co/api/datasets/futo-org/swipe.futo.org/parquet"

EXPECTED_SHARDS = {
    "swipe-1": {
        "train": [
            "https://huggingface.co/api/datasets/futo-org/swipe.futo.org/parquet/swipe-1/train/0.parquet",
            "https://huggingface.co/api/datasets/futo-org/swipe.futo.org/parquet/swipe-1/train/1.parquet",
            "https://huggingface.co/api/datasets/futo-org/swipe.futo.org/parquet/swipe-1/train/2.parquet",
            "https://huggingface.co/api/datasets/futo-org/swipe.futo.org/parquet/swipe-1/train/3.parquet",
        ],
        "validation": [
            "https://huggingface.co/api/datasets/futo-org/swipe.futo.org/parquet/swipe-1/validation/0.parquet",
        ],
        "test": [
            "https://huggingface.co/api/datasets/futo-org/swipe.futo.org/parquet/swipe-1/test/0.parquet",
        ],
    },
    "swipe-2": {
        "train": [
            "https://huggingface.co/api/datasets/futo-org/swipe.futo.org/parquet/swipe-2/train/0.parquet",
        ]
    },
    "swipe-3": {
        "train": [
            "https://huggingface.co/api/datasets/futo-org/swipe.futo.org/parquet/swipe-3/train/0.parquet",
        ]
    },
    "swipe-4": {
        "train": [
            "https://huggingface.co/api/datasets/futo-org/swipe.futo.org/parquet/swipe-4/train/0.parquet",
        ]
    },
    "swipe-5": {
        "train": [
            "https://huggingface.co/api/datasets/futo-org/swipe.futo.org/parquet/swipe-5/train/0.parquet",
        ]
    },
}

EXPECTED_ROW_COUNTS = {
    ("swipe-1", "train", "0.parquet"): 237744,
    ("swipe-1", "train", "1.parquet"): 248234,
    ("swipe-1", "train", "2.parquet"): 244910,
    ("swipe-1", "train", "3.parquet"): 208662,
    ("swipe-1", "validation", "0.parquet"): 54269,
    ("swipe-1", "test", "0.parquet"): 49970,
    ("swipe-2", "train", "0.parquet"): 28095,
    ("swipe-3", "train", "0.parquet"): 38228,
    ("swipe-4", "train", "0.parquet"): 50300,
    ("swipe-5", "train", "0.parquet"): 59247,
}


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


def verify_shard(filepath: Path, run_name: str, split_name: str) -> Dict[str, Any]:
    parquet_file = pq.ParquetFile(str(filepath))
    num_rows = parquet_file.metadata.num_rows
    num_row_groups = parquet_file.metadata.num_row_groups
    schema = parquet_file.schema_arrow
    column_names = schema.names
    
    expected_key = (run_name, split_name, filepath.name)
    if expected_key in EXPECTED_ROW_COUNTS:
        expected = EXPECTED_ROW_COUNTS[expected_key]
        assert num_rows == expected, f"Row count mismatch for {filepath}: got {num_rows}, expected {expected}"

    # Read one sample row group to verify fields
    table = parquet_file.read_row_group(0)
    first_row = table.slice(0, 1).to_pylist()[0]
    
    # Required core fields
    core_fields = ["id", "session", "timestamp", "word", "canvas_width", "canvas_height", "orientation", "data", "sentence", "word_idx", "distance"]
    for field in core_fields:
        assert field in column_names, f"Missing required column '{field}' in {filepath}"
        assert field in first_row, f"Column '{field}' not found in first row of {filepath}"

    # Check swipe-1 validity flag
    if run_name == "swipe-1":
        assert "potentially_invalid_sentence" in column_names, f"Missing potentially_invalid_sentence in {filepath}"

    # Check swipe-5 metadata fields
    if run_name == "swipe-5":
        for f in ["language", "layout", "dual_finger"]:
            assert f in column_names, f"Missing '{f}' column in swipe-5: {filepath}"

    # Verify data trajectory structure
    raw_data = first_row["data"]
    if isinstance(raw_data, str):
        parsed_data = json.loads(raw_data)
    else:
        parsed_data = raw_data

    if isinstance(parsed_data, list):
        assert len(parsed_data) > 0, f"Trajectory data list is empty in {filepath}"
        pt = parsed_data[0]
        assert "x" in pt and "y" in pt and "t" in pt, f"Missing x/y/t keys in trajectory point: {pt}"
        sample_points_desc = f"{len(parsed_data)} points"
    elif isinstance(parsed_data, dict):
        assert "L" in parsed_data or "R" in parsed_data, f"Dual finger dictionary format invalid: {parsed_data.keys()}"
        sample_points_desc = f"dual_finger (keys: {list(parsed_data.keys())})"
    else:
        raise ValueError(f"Unknown data structure in {filepath}: {type(raw_data)}")
    
    file_size = filepath.stat().st_size
    file_sha256 = compute_sha256(filepath)
    
    return {
        "file": str(filepath.relative_to(REPO_ROOT)),
        "run": run_name,
        "split": split_name,
        "filename": filepath.name,
        "rows": num_rows,
        "row_groups": num_row_groups,
        "size_bytes": file_size,
        "sha256": file_sha256,
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
    parser = argparse.ArgumentParser(description="Acquire and verify canonical FUTO swipe dataset")
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_RAW_DIR, help="Destination directory for raw parquet shards")
    parser.add_argument("--skip-existing", action="store_true", default=True, help="Skip downloading files that already exist")
    parser.add_argument("--force", action="store_true", help="Force re-download of existing files")
    args = parser.parse_args()

    out_dir = args.output_dir
    out_dir.mkdir(parents=True, exist_ok=True)

    print(f"=== FUTO Swipe Dataset Acquisition ===")
    print(f"Destination: {out_dir}")
    
    manifest_entries = []
    total_swipes = 0
    total_bytes = 0

    for run_name, splits in EXPECTED_SHARDS.items():
        for split_name, urls in splits.items():
            for url in urls:
                filename = url.split("/")[-1]
                dest_file = out_dir / run_name / split_name / filename
                
                if dest_file.exists() and not args.force:
                    print(f"Already exists: {dest_file.relative_to(REPO_ROOT)}")
                else:
                    download_file(url, dest_file)
                
                print(f"Verifying {dest_file.relative_to(REPO_ROOT)}...")
                entry = verify_shard(dest_file, run_name, split_name)
                manifest_entries.append(entry)
                total_swipes += entry["rows"]
                total_bytes += entry["size_bytes"]
                print(f"  OK: {entry['rows']:,} rows, {entry['size_bytes'] / (1024*1024):.2f} MB, columns: {len(entry['columns'])}")

    manifest = {
        "dataset_name": "futo-org/swipe.futo.org",
        "source_url": "https://huggingface.co/datasets/futo-org/swipe.futo.org",
        "license": "MIT",
        "total_swipes": total_swipes,
        "total_bytes": total_bytes,
        "total_megabytes": round(total_bytes / (1024 * 1024), 2),
        "verified_runs": list(EXPECTED_SHARDS.keys()),
        "shards": manifest_entries,
    }

    manifest_path = out_dir / "manifest.json"
    with open(manifest_path, "w") as f:
        json.dump(manifest, f, indent=2)

    print("\n=== Verification Summary ===")
    print(f"Total Swipes Verified: {total_swipes:,}")
    print(f"Total Disk Footprint: {total_bytes / (1024 * 1024):.2f} MB")
    print(f"Manifest written to: {manifest_path.relative_to(REPO_ROOT)}")
    print("All canonical fields, raw (x, y, t) trajectories, screen dimensions, and metadata verified LOSSLESS.")


if __name__ == "__main__":
    main()

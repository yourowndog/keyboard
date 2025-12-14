#!/usr/bin/env python3
"""
Complete Setup for Neural Swipe Training (English)

Creates all required files for training:
1. Keyboard grid (qwerty_en) - FIXED format
2. Keyboard tokenizer (English a-z)
3. Vocabulary file
4. Combined training data
5. Validation split
6. Trajectory statistics (via subprocess)
7. Key bounding boxes (via subprocess)
8. Training config

Run this script, then run training.
"""

import pyarrow.parquet as pq
import json
import os
import random
import subprocess
import sys
from collections import Counter

print("="*60)
print("NEURAL SWIPE TRAINING SETUP (English)")
print("="*60)

# Paths
BASE_DIR = "/home/sam/projects/keyboard"
TRAINER_DIR = f"{BASE_DIR}/external/neural-swipe-typing"
DATA_DIR = f"{TRAINER_DIR}/data/english"
SWIPE_TRAINER_DIR = f"{BASE_DIR}/keyboardtools/swipe-trainer"

FUTO_PARQUET = f"{SWIPE_TRAINER_DIR}/futo_swipes.parquet"
SYNTHETIC_JSONL = f"{SWIPE_TRAINER_DIR}/synthetic_swipes_final.jsonl"

# Create output directories
os.makedirs(DATA_DIR, exist_ok=True)
os.makedirs(f"{TRAINER_DIR}/tokenizers/keyboard", exist_ok=True)
os.makedirs(f"{TRAINER_DIR}/configs/train", exist_ok=True)

# Coordinate scaling (dataset.py expects integers 'h' -> short)
COORD_SCALE = 10000

# ============================================================================
# 1. Keyboard Grid (QWERTY English)
# ============================================================================
print("\n[1/8] Creating keyboard grid (Correct Format, Scaled)...")

# Raw coordinate definitions (0-1)
keys_def = {
    # Top row
    'q': {"x": 0.05, "y": 0.167}, 'w': {"x": 0.15, "y": 0.167}, 'e': {"x": 0.25, "y": 0.167},
    'r': {"x": 0.35, "y": 0.167}, 't': {"x": 0.45, "y": 0.167}, 'y': {"x": 0.55, "y": 0.167},
    'u': {"x": 0.65, "y": 0.167}, 'i': {"x": 0.75, "y": 0.167}, 'o': {"x": 0.85, "y": 0.167},
    'p': {"x": 0.95, "y": 0.167},
    # Middle row
    'a': {"x": 0.075, "y": 0.50}, 's': {"x": 0.175, "y": 0.50}, 'd': {"x": 0.275, "y": 0.50},
    'f': {"x": 0.375, "y": 0.50}, 'g': {"x": 0.475, "y": 0.50}, 'h': {"x": 0.575, "y": 0.50},
    'j': {"x": 0.675, "y": 0.50}, 'k': {"x": 0.775, "y": 0.50}, 'l': {"x": 0.875, "y": 0.50},
    # Bottom row
    'z': {"x": 0.15, "y": 0.833}, 'x': {"x": 0.25, "y": 0.833}, 'c': {"x": 0.35, "y": 0.833},
    'v': {"x": 0.45, "y": 0.833}, 'b': {"x": 0.55, "y": 0.833}, 'n': {"x": 0.65, "y": 0.833},
    'm': {"x": 0.75, "y": 0.833},
}

# Convert to list of keys with scaled hitboxes
grid_keys = []
for label, pos in keys_def.items():
    key_obj = {
        "label": label,
        "hitbox": {
            "x": int((pos["x"] - 0.05) * COORD_SCALE),  # center - half_width (0.10/2)
            "y": int((pos["y"] - 0.165) * COORD_SCALE), # center - half_height (0.33/2)
            "w": int(0.10 * COORD_SCALE),
            "h": int(0.33 * COORD_SCALE)
        }
    }
    grid_keys.append(key_obj)

QWERTY_GRID = {
    "qwerty_en": {
        "width": int(1.0 * COORD_SCALE),
        "height": int(1.0 * COORD_SCALE),
        "keys": grid_keys
    }
}

with open(f"{DATA_DIR}/gridname_to_grid.json", 'w') as f:
    json.dump(QWERTY_GRID, f, indent=2)
print(f"  Saved: {DATA_DIR}/gridname_to_grid.json")

# ============================================================================
# 2. Keyboard Tokenizer
# ============================================================================
print("\n[2/8] Creating keyboard tokenizer...")
# Correct format: {"labels": [...], "special_tokens": [...]}
valid_chars = [chr(ord('a') + i) for i in range(26)]
special_tokens = ["<pad>", "<sos>", "<eos>", "<unk>"]
all_labels = valid_chars + special_tokens

TOKENIZER = {
    "labels": all_labels,
    "special_tokens": special_tokens
}
with open(f"{TRAINER_DIR}/tokenizers/keyboard/en.json", 'w') as f:
    json.dump(TOKENIZER, f, indent=2)
print(f"  Saved: {TRAINER_DIR}/tokenizers/keyboard/en.json")

# ============================================================================
# 3. Load & Process Data
# ============================================================================
print("\n[3/8] Processing training data...")
# Reduce max samples per word and total cap to fit in 24GB RAM
# Testing with minimal dataset to find baseline memory usage
MAX_SAMPLES_PER_WORD = 3 
GLOBAL_SAMPLE_CAP = 1000 
all_samples = []
word_counts = Counter()

# FUTO
parquet_file = pq.ParquetFile(FUTO_PARQUET)
for i in range(parquet_file.metadata.num_row_groups):
    table = parquet_file.read_row_group(i)
    df = table.to_pandas()
    for _, row in df.iterrows():
        word = row['word'].lower()
        if word_counts[word] >= MAX_SAMPLES_PER_WORD or not word.isalpha(): continue
        points = row['data']
        if len(points) < 3: continue
        # Scale to integers
        x_scaled = [int(float(p['x']) * COORD_SCALE) for p in points]
        y_scaled = [int(float(p['y']) * COORD_SCALE) for p in points]
        t_raw = [int(p['t']) for p in points]
        t_norm = [ts - t_raw[0] for ts in t_raw]
        
        # Validate range for 'signed short' (32767 max)
        if max(t_norm) > 32000: continue # Skip very long swipes (>32s)
        if any(v > 32000 or v < -32000 for v in x_scaled): continue
        if any(v > 32000 or v < -32000 for v in y_scaled): continue
        
        if len(all_samples) >= GLOBAL_SAMPLE_CAP: break
        
        all_samples.append({
            "word": word,
            "curve": {"x": x_scaled, "y": y_scaled, "t": t_norm, "grid_name": "qwerty_en"}
        })
        word_counts[word] += 1
print(f"  FUTO samples: {sum(word_counts.values())}")

# Synthetic
print("\n[4/8] Adding synthetic data...")
with open(SYNTHETIC_JSONL, 'r') as f:
    for line in f:
        sample = json.loads(line.strip())
        word = sample['word'].lower()
        letters = ''.join(c for c in word if c.isalpha())
        if letters:
            sample['word'] = letters
            # Scale synthetic points
            sample['curve']['x'] = [int(v * COORD_SCALE) for v in sample['curve']['x']]
            sample['curve']['y'] = [int(v * COORD_SCALE) for v in sample['curve']['y']]
            # Time is already int ms
            
            # Validation
            if max(sample['curve']['t']) > 32000: continue
            
            all_samples.append(sample)
print(f"  Total samples: {len(all_samples)}")
print(f"  Unique words: {len(set(s['word'] for s in all_samples))}")

# ============================================================================
# 4. Save Vocabulary & Data
# ============================================================================
print("\n[5/8] Saving vocabulary and data splits...")
vocab = sorted(set(s['word'] for s in all_samples))
with open(f"{DATA_DIR}/voc.txt", 'w') as f:
    for word in vocab: f.write(word + '\n')
print(f"  Vocabulary size: {len(vocab)}")

random.shuffle(all_samples)
val_size = min(5000, len(all_samples) // 20)
train_samples = all_samples[val_size:]
val_samples = all_samples[:val_size]

TRAIN_PATH = f"{DATA_DIR}/train.jsonl"
VALID_PATH = f"{DATA_DIR}/valid.jsonl"

with open(TRAIN_PATH, 'w') as f:
    for s in train_samples: f.write(json.dumps(s) + '\n')
with open(VALID_PATH, 'w') as f:
    for s in val_samples: f.write(json.dumps(s) + '\n')

print(f"  Training samples: {len(train_samples)}")
print(f"  Validation samples: {len(val_samples)}")

# ============================================================================
# 5. Compute Statistics (The missing piece!)
# ============================================================================
print("\n[6/8] Computing trajectory statistics...")
STATS_PATH = f"{DATA_DIR}/trajectory_features_statistics.json"

cmd_stats = [
    sys.executable,
    "-m", "src.data_obtaining_and_preprocessing.compute_trajectory_features_statistics",
    "--train_data_path", TRAIN_PATH,
    "--voc_path", f"{DATA_DIR}/voc.txt",
    "--output_json", STATS_PATH,
    "--total", str(len(train_samples))
]

# Run inside TRAINER_DIR so 'src' module path works
env = os.environ.copy()
env["PYTHONPATH"] = f"{TRAINER_DIR}/src:{env.get('PYTHONPATH', '')}"
subprocess.run(cmd_stats, cwd=TRAINER_DIR, env=env, check=True)
print(f"  Saved: {STATS_PATH}")

# ============================================================================
# 6. Compute Key Bounding Boxes
# ============================================================================
print("\n[7/8] Computing key bounding boxes...")
BBOX_PATH = f"{DATA_DIR}/key_bounding_boxes.json"
labels = [chr(ord('a') + i) for i in range(26)]

cmd_bbox = [
    sys.executable,
    "-m", "src.data_obtaining_and_preprocessing.compute_key_bounding_box",
    "--grids_path", f"{DATA_DIR}/gridname_to_grid.json",
    "--labels"] + labels + [
    "--output_json", BBOX_PATH
]

subprocess.run(cmd_bbox, cwd=TRAINER_DIR, env=env, check=True)
print(f"  Saved: {BBOX_PATH}")

# ============================================================================
# 7. Create Training Config
# ============================================================================
print("\n[8/8] Creating training config...")
train_config = {
    "experiment_name": "english_swipe",
    "swipe_feature_extractor_factory_config_path": "./configs/feature_extractor/traj_and_nearest.json",
    "swipe_point_embedder_config_path": "./configs/swipe_point_embedder/separate_traj_and_nearest__6_coord.json",
    
    "num_classes": 30,  # 26 chars + 4 specials
    "max_out_seq_len": 25,
    "grid_name": "qwerty_en",
    "grids_path": f"{DATA_DIR}/gridname_to_grid.json",
    "trajectory_features_statistics_path": f"{DATA_DIR}/trajectory_features_statistics.json",
    "bounding_boxes_path": f"{DATA_DIR}/key_bounding_boxes.json",
    "keyboard_tokenizer_path": f"{TRAINER_DIR}/tokenizers/keyboard/en.json",
    
    "dataset_paths": {
        "train": f"{DATA_DIR}/train.jsonl",
        "val": f"{DATA_DIR}/valid.jsonl"
    },
    
    "dataloader_num_workers": 0,
    "train_batch_size": 64,
    "val_batch_size": 64,
    "vocab_path": f"{DATA_DIR}/voc.txt",
    "train_total": len(train_samples),
    "val_total": len(val_samples),
    "seed": 42,
    
    "early_stopping": {"enabled": True, "patience": 10},
    "lr_scheduler": {"type": "ReduceLROnPlateau", "params": {"factor": 0.5, "patience": 5}},
    "path_to_continue_checkpoint": None,
    "model_name": "english_swipe_v1",
    "label_smoothing": 0.05,
    "optimizer": {"type": "Adam", "params": {"lr": 1e-4, "weight_decay": 0}},
    "val_check_interval": 1000,
    "device": "cpu"
}

with open(f"{TRAINER_DIR}/configs/train/train_english.json", 'w') as f:
    json.dump(train_config, f, indent=2)
print(f"  Saved: {TRAINER_DIR}/configs/train/train_english.json")

# ============================================================================
# Done!
# ============================================================================
print("\n" + "="*60 + "\nSETUP COMPLETE! Ready to train.\n" + "="*60)
print(f"""
Files created:
  - {DATA_DIR}/gridname_to_grid.json
  - {DATA_DIR}/train.jsonl ({len(train_samples)} samples)
  - {DATA_DIR}/valid.jsonl ({len(val_samples)} samples)
  - {DATA_DIR}/voc.txt ({len(vocab)} words)
  - {DATA_DIR}/trajectory_features_statistics.json
  - {DATA_DIR}/key_bounding_boxes.json
  - {TRAINER_DIR}/tokenizers/keyboard/en.json
  - {TRAINER_DIR}/configs/train/train_english.json

To start training:
  cd {TRAINER_DIR}
  python3 -m venv venv
  source venv/bin/activate
  pip install -r requirements/requirements.txt
  python -m src.train --train_config configs/train/train_english.json
""")

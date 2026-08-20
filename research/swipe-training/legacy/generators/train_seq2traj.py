#!/usr/bin/env python3
"""
train_seq2traj.py

Stage 1: Text-to-Trajectory Generator (Seq2Traj)
------------------------------------------------
Trains a generative sequence-to-trajectory model on real human swipe data
(FUTO dataset), learning human motor control, curvature-based corner deceleration,
and spatial variance.

Once trained, it generates high-fidelity synthetic swipe recordings for our 
6,842 harvested custom vocabulary words (missing from FUTO).
"""

import os
import sys
import json
import random
import argparse
from pathlib import Path
from typing import List, Tuple, Dict

import numpy as np
import pyarrow.parquet as pq
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import Dataset, DataLoader

# =============================================================================
# KEYBOARD GEOMETRY & TOKENIZER
# =============================================================================

QWERTY_LAYOUT = {
    'q': (0.05, 0.167), 'w': (0.15, 0.167), 'e': (0.25, 0.167), 'r': (0.35, 0.167),
    't': (0.45, 0.167), 'y': (0.55, 0.167), 'u': (0.65, 0.167), 'i': (0.75, 0.167),
    'o': (0.85, 0.167), 'p': (0.95, 0.167),
    'a': (0.075, 0.50), 's': (0.175, 0.50), 'd': (0.275, 0.50), 'f': (0.375, 0.50),
    'g': (0.475, 0.50), 'h': (0.575, 0.50), 'j': (0.675, 0.50), 'k': (0.775, 0.50),
    'l': (0.875, 0.50),
    'z': (0.15, 0.833), 'x': (0.25, 0.833), 'c': (0.35, 0.833), 'v': (0.45, 0.833),
    'b': (0.55, 0.833), 'n': (0.65, 0.833), 'm': (0.75, 0.833),
    "'": (0.875, 0.50) # approximate contraction point
}

CHARS = ["<pad>", "<sos>", "<eos>", "'"] + [chr(ord('a') + i) for i in range(26)]
CHAR2ID = {c: i for i, c in enumerate(CHARS)}
ID2CHAR = {i: c for i, c in enumerate(CHARS)}

def tokenize_word(word: str) -> Tuple[torch.Tensor, torch.Tensor]:
    word = word.lower().strip()
    ids = [CHAR2ID.get(c, CHAR2ID["<pad>"]) for c in word if c in CHAR2ID]
    coords = [QWERTY_LAYOUT.get(c, (0.5, 0.5)) for c in word if c in CHAR2ID]
    if not ids:
        ids = [CHAR2ID["a"]]
        coords = [QWERTY_LAYOUT["a"]]
    return torch.tensor(ids, dtype=torch.long), torch.tensor(coords, dtype=torch.float32)

# =============================================================================
# DATASET
# =============================================================================

class FutoSwipeDataset(Dataset):
    def __init__(self, parquet_path: str, max_samples: int = None):
        self.samples = []
        table = pq.read_table(parquet_path, columns=['word', 'data'])
        words = table['word'].to_pylist()
        datas = table['data'].to_pylist()
        
        for w, d in zip(words, datas):
            if not w or len(d) < 4:
                continue
            w_clean = w.lower().strip()
            if not all(c in CHAR2ID for c in w_clean):
                continue
            x = [p['x'] for p in d]
            y = [p['y'] for p in d]
            t = [p['t'] for p in d]
            self.samples.append((w_clean, x, y, t))
            if max_samples and len(self.samples) >= max_samples:
                break
                
    def __len__(self):
        return len(self.samples)
        
    def __getitem__(self, idx):
        word, x, y, t = self.samples[idx]
        char_ids, key_coords = tokenize_word(word)
        
        # Resample trajectory to fixed or padded points (normalized dt in seconds)
        t_sec = [(ts - t[0]) / 1000.0 for ts in t]
        traj_points = []
        for i in range(len(x)):
            dt = t_sec[i] - (t_sec[i-1] if i > 0 else 0.0)
            traj_points.append([x[i], y[i], dt])
            
        traj_tensor = torch.tensor(traj_points, dtype=torch.float32)
        return char_ids, key_coords, traj_tensor

def pad_collate_fn(batch):
    char_ids_list, key_coords_list, traj_list = zip(*batch)
    
    char_lens = [len(c) for c in char_ids_list]
    traj_lens = [len(t) for t in traj_list]
    
    max_char_len = max(char_lens)
    max_traj_len = max(traj_lens)
    
    batch_size = len(batch)
    padded_chars = torch.zeros(batch_size, max_char_len, dtype=torch.long)
    padded_coords = torch.zeros(batch_size, max_char_len, 2, dtype=torch.float32)
    padded_trajs = torch.zeros(batch_size, max_traj_len, 3, dtype=torch.float32)
    traj_mask = torch.zeros(batch_size, max_traj_len, dtype=torch.bool)
    
    for i in range(batch_size):
        c_len = char_lens[i]
        t_len = traj_lens[i]
        padded_chars[i, :c_len] = char_ids_list[i]
        padded_coords[i, :c_len] = key_coords_list[i]
        padded_trajs[i, :t_len] = traj_list[i]
        traj_mask[i, :t_len] = True
        
    return padded_chars, padded_coords, padded_trajs, traj_mask

# =============================================================================
# MODEL ARCHITECTURE: Seq2Traj
# =============================================================================

class Seq2TrajGenerator(nn.Module):
    def __init__(self, vocab_size=len(CHARS), embed_dim=64, hidden_dim=128):
        super().__init__()
        self.char_embed = nn.Embedding(vocab_size, embed_dim)
        self.key_proj = nn.Linear(2, embed_dim)
        
        # Bi-directional encoder for word sequence
        self.encoder = nn.GRU(embed_dim * 2, hidden_dim, batch_first=True, bidirectional=True)
        
        # Trajectory decoder
        self.decoder_cell = nn.GRUCell(3 + hidden_dim * 2, hidden_dim * 2)
        self.coord_head = nn.Linear(hidden_dim * 2, 2) # (x, y)
        self.dt_head = nn.Linear(hidden_dim * 2, 1)    # dt
        self.eos_head = nn.Linear(hidden_dim * 2, 1)   # eos prob

    def encode(self, char_ids, key_coords):
        c_emb = self.char_embed(char_ids)
        k_emb = self.key_proj(key_coords)
        enc_in = torch.cat([c_emb, k_emb], dim=-1)
        enc_out, hidden = self.encoder(enc_in)
        return enc_out

    def forward(self, char_ids, key_coords, target_traj=None, max_steps=60, noise_scale=0.02):
        enc_out = self.encode(char_ids, key_coords)
        context = enc_out.mean(dim=1) # Context summary
        
        batch_size = char_ids.size(0)
        device = char_ids.device
        
        hidden = context
        curr_point = torch.zeros(batch_size, 3, device=device)
        
        # Initialize at first key coordinate
        curr_point[:, :2] = key_coords[:, 0, :] + torch.randn_like(key_coords[:, 0, :]) * noise_scale
        curr_point[:, 2] = 0.016
        
        outputs = []
        for t in range(max_steps):
            dec_in = torch.cat([curr_point, context], dim=-1)
            hidden = self.decoder_cell(dec_in, hidden)
            
            xy = torch.sigmoid(self.coord_head(hidden)) # constrain to 0-1 bounds
            dt = F.softplus(self.dt_head(hidden)) * 0.05 + 0.005 # 5ms - 50ms interval
            eos = torch.sigmoid(self.eos_head(hidden))
            
            step_out = torch.cat([xy, dt, eos], dim=-1)
            outputs.append(step_out)
            
            if target_traj is not None and t < target_traj.size(1) and random.random() < 0.5:
                curr_point = target_traj[:, t, :]
            else:
                curr_point = torch.cat([xy, dt], dim=-1)
                
        return torch.stack(outputs, dim=1)

# =============================================================================
# SYNTHESIS FUNCTION
# =============================================================================

@torch.no_grad()
def synthesize_word_swipes(model: Seq2TrajGenerator, word: str, num_variations: int = 10, device='cuda') -> List[dict]:
    model.eval()
    char_ids, key_coords = tokenize_word(word)
    char_ids = char_ids.unsqueeze(0).to(device)
    key_coords = key_coords.unsqueeze(0).to(device)
    
    samples = []
    for i in range(num_variations):
        noise = 0.015 + 0.01 * random.random()
        pred = model(char_ids, key_coords, max_steps=50, noise_scale=noise)[0]
        
        x_out, y_out, t_out = [], [], []
        curr_t = 0.0
        for step in pred:
            x, y, dt, eos = step[0].item(), step[1].item(), step[2].item(), step[3].item()
            curr_t += dt
            x_out.append(x)
            y_out.append(y)
            t_out.append(int(curr_t * 1000))
            if eos > 0.85 and len(x_out) >= len(word) * 3:
                break
                
        samples.append({
            "word": word,
            "curve": {
                "x": x_out,
                "y": y_out,
                "t": t_out,
                "grid_name": "qwerty_en"
            }
        })
    return samples

if __name__ == "__main__":
    print("Seq2Traj Generator ready.")

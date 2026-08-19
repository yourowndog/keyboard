#!/usr/bin/env python3
"""
train_seq2traj.py

Stage 1: Text-to-Trajectory Generator (Seq2Traj)
------------------------------------------------
Trains a generative sequence-to-trajectory model on real human swipe data
(FUTO), learning human motor control: curvature-based corner deceleration,
spatial variance, and velocity profiles.

Once trained it synthesizes swipe recordings for harvested vocabulary that FUTO
does not cover (contractions, developer/AI terms, slang).

Design notes
------------
* Trajectories are arc-length resampled to a fixed point count. Shape lives in
  the (x,y) channels; the entire velocity profile lives in the dt channel. This
  removes the need for an EOS head and makes the length distribution a learned
  property of dt rather than a separate classification problem.

* The decoder attends over encoder states rather than consuming a mean-pooled
  context vector. Mean pooling discards *which* key the finger is approaching,
  which is precisely the information needed to brake into a corner.

* The output head is heteroscedastic: it predicts a mean and a log-variance per
  channel, trained with Gaussian NLL. Sampling from the predicted distribution
  at synthesis time gives variation that the model learned from humans (wide
  mid-stroke, tight at corners) instead of uniform jitter bolted on afterwards.

* Loss includes explicit velocity and acceleration terms. Position-only MSE is
  what produces over-smoothed splines that carry speed through corners — the
  documented +1531% corner-velocity failure mode. Supervising the derivatives
  makes corner braking a first-class training signal.

Usage
-----
  # train
  python train_seq2traj.py train --parquet futo_swipes.parquet --epochs 30

  # check the coordinate frame before committing GPU hours
  python train_seq2traj.py calibrate --parquet futo_swipes.parquet

  # synthesize the supplement
  python train_seq2traj.py synthesize \
      --checkpoint models/seq2traj_best.pt \
      --vocab target_swipe_vocabulary_supplement.txt \
      --out synthetic_supplement.jsonl --variations 10
"""

from __future__ import annotations

import argparse
import json
import math
import os
import random
import time
from pathlib import Path
from typing import List, Optional, Sequence, Tuple

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import DataLoader, Dataset

from swipe_common import (
    CHARS,
    NUM_CLASSES,
    PAD_ID,
    SwipeSample,
    UnsupportedWord,
    calibrate_layout,
    corner_stats,
    encode_geometry,
    key_sequence,
    load_futo,
    load_vocabulary,
    resample_trajectory,
    write_synthetic_jsonl,
)

TRAJ_LEN = 48
MIN_DT, MAX_DT = 0.004, 0.080


def set_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)


# =============================================================================
# DATASET
# =============================================================================


class Seq2TrajDataset(Dataset):
    """FUTO samples encoded as (key path, fixed-length trajectory)."""

    def __init__(self, samples: Sequence[SwipeSample], traj_len: int = TRAJ_LEN):
        self.traj_len = traj_len
        self.items: List[Tuple[np.ndarray, np.ndarray, np.ndarray]] = []
        dropped = 0
        for s in samples:
            try:
                char_ids, key_coords = encode_geometry(s.word)
            except UnsupportedWord:
                dropped += 1
                continue
            if len(s.xy) < 4:
                dropped += 1
                continue
            xy, t = resample_trajectory(s.xy, s.t, traj_len)
            dt = np.diff(t, prepend=t[0])
            dt[0] = dt[1] if traj_len > 1 else 0.016
            dt = np.clip(dt, MIN_DT, MAX_DT).astype(np.float32)
            traj = np.concatenate([xy, dt[:, None]], axis=1).astype(np.float32)
            self.items.append((char_ids, key_coords, traj))
        if dropped:
            print(f"[dataset] dropped {dropped} unusable samples, kept {len(self.items)}")

    def __len__(self) -> int:
        return len(self.items)

    def __getitem__(self, idx: int):
        char_ids, key_coords, traj = self.items[idx]
        return (
            torch.from_numpy(char_ids),
            torch.from_numpy(key_coords),
            torch.from_numpy(traj),
        )


def pad_collate_fn(batch):
    """Pad key sequences; trajectories are already a fixed length."""
    char_list, coord_list, traj_list = zip(*batch)
    lens = [len(c) for c in char_list]
    max_len = max(lens)
    bs = len(batch)

    chars = torch.full((bs, max_len), PAD_ID, dtype=torch.long)
    coords = torch.zeros(bs, max_len, 2, dtype=torch.float32)
    key_mask = torch.zeros(bs, max_len, dtype=torch.bool)

    for i, n in enumerate(lens):
        chars[i, :n] = char_list[i]
        coords[i, :n] = coord_list[i]
        key_mask[i, :n] = True

    trajs = torch.stack(traj_list, dim=0)
    return chars, coords, key_mask, trajs


# =============================================================================
# MODEL
# =============================================================================


class Attention(nn.Module):
    """Additive attention from decoder state over encoder key states."""

    def __init__(self, enc_dim: int, dec_dim: int, attn_dim: int = 64):
        super().__init__()
        self.enc_proj = nn.Linear(enc_dim, attn_dim, bias=False)
        self.dec_proj = nn.Linear(dec_dim, attn_dim, bias=False)
        self.score = nn.Linear(attn_dim, 1, bias=False)

    def forward(self, enc_out, dec_hidden, key_mask):
        # enc_out [B,L,E], dec_hidden [B,D], key_mask [B,L]
        e = self.enc_proj(enc_out) + self.dec_proj(dec_hidden).unsqueeze(1)
        scores = self.score(torch.tanh(e)).squeeze(-1)            # [B,L]
        scores = scores.masked_fill(~key_mask, torch.finfo(scores.dtype).min)
        weights = torch.softmax(scores, dim=-1)
        return torch.bmm(weights.unsqueeze(1), enc_out).squeeze(1)  # [B,E]


def _smooth_time(noise: torch.Tensor, sigma: float) -> torch.Tensor:
    """Low-pass a [B,T,C] noise tensor along time, renormalized to unit variance.

    Renormalizing by ||k||_2 matters: smoothing alone shrinks the per-step
    standard deviation, which would silently scale down the variance the model
    learned from human data.
    """
    if sigma <= 0:
        return noise
    radius = max(1, int(round(3 * sigma)))
    taps = torch.arange(-radius, radius + 1, device=noise.device, dtype=noise.dtype)
    kernel = torch.exp(-0.5 * (taps / sigma) ** 2)
    kernel = kernel / torch.linalg.vector_norm(kernel)

    b, t, c = noise.shape
    x = noise.permute(0, 2, 1).reshape(b * c, 1, t)
    x = F.pad(x, (radius, radius), mode="replicate")
    x = F.conv1d(x, kernel.view(1, 1, -1))
    return x.reshape(b, c, t).permute(0, 2, 1)


class Seq2TrajGenerator(nn.Module):
    def __init__(self, vocab_size: int = NUM_CLASSES, embed_dim: int = 64,
                 hidden_dim: int = 128, noise_sigma: float = 2.0):
        super().__init__()
        self.hidden_dim = hidden_dim
        self.noise_sigma = noise_sigma
        self.dec_dim = hidden_dim * 2
        self.enc_dim = hidden_dim * 2

        self.char_embed = nn.Embedding(vocab_size, embed_dim, padding_idx=PAD_ID)
        self.key_proj = nn.Linear(2, embed_dim)
        self.encoder = nn.GRU(embed_dim * 2, hidden_dim, batch_first=True, bidirectional=True)

        self.attn = Attention(self.enc_dim, self.dec_dim)
        self.decoder_cell = nn.GRUCell(3 + self.enc_dim, self.dec_dim)
        self.init_proj = nn.Linear(self.enc_dim, self.dec_dim)

        # Heteroscedastic heads: mean and log-variance for (x, y, dt).
        self.mean_head = nn.Linear(self.dec_dim, 3)
        self.logvar_head = nn.Linear(self.dec_dim, 3)

    def encode(self, char_ids, key_coords, key_mask):
        c = self.char_embed(char_ids)
        k = self.key_proj(key_coords)
        enc_out, _ = self.encoder(torch.cat([c, k], dim=-1))
        enc_out = enc_out * key_mask.unsqueeze(-1)
        summary = enc_out.sum(1) / key_mask.sum(1, keepdim=True).clamp(min=1)
        return enc_out, summary

    def _decode_step(self, curr_point, hidden, enc_out, key_mask):
        ctx = self.attn(enc_out, hidden, key_mask)
        hidden = self.decoder_cell(torch.cat([curr_point, ctx], dim=-1), hidden)
        mean = self.mean_head(hidden)
        logvar = self.logvar_head(hidden).clamp(-9.0, 2.0)
        xy = torch.sigmoid(mean[:, :2])
        dt = MIN_DT + (MAX_DT - MIN_DT) * torch.sigmoid(mean[:, 2:3])
        return torch.cat([xy, dt], dim=-1), logvar, hidden

    def forward(
        self,
        char_ids,
        key_coords,
        key_mask,
        target_traj: Optional[torch.Tensor] = None,
        steps: int = TRAJ_LEN,
        teacher_forcing: float = 0.0,
        sample: bool = False,
        temperature: float = 1.0,
    ):
        enc_out, summary = self.encode(char_ids, key_coords, key_mask)
        hidden = torch.tanh(self.init_proj(summary))

        b = char_ids.size(0)
        curr = torch.zeros(b, 3, device=char_ids.device)
        curr[:, :2] = key_coords[:, 0, :]
        curr[:, 2] = 0.016

        # Temporally correlated synthesis noise.
        #
        # Drawing independent noise per step is white noise, and white noise on
        # position manufactures high-frequency direction reversals that the
        # corner metric reads as real corners — synthetic trajectories end up
        # with several times the human corner count. Human spatial variation is
        # low-frequency: the whole stroke drifts, it does not vibrate. So the
        # noise sequence is smoothed along time and renormalized to unit
        # per-step variance, preserving the magnitude the model learned while
        # removing the jitter.
        noise = None
        if sample:
            raw = torch.randn(b, steps, 3, device=char_ids.device)
            noise = _smooth_time(raw, self.noise_sigma)

        means, logvars = [], []
        for step in range(steps):
            pred, logvar, hidden = self._decode_step(curr, hidden, enc_out, key_mask)
            means.append(pred)
            logvars.append(logvar)

            if sample:
                std = torch.exp(0.5 * logvar) * temperature
                nxt = pred + noise[:, step] * std
                nxt = torch.cat([
                    nxt[:, :2].clamp(0.0, 1.0),
                    nxt[:, 2:3].clamp(MIN_DT, MAX_DT),
                ], dim=-1)
                means[-1] = nxt
                curr = nxt
            elif target_traj is not None and random.random() < teacher_forcing:
                curr = target_traj[:, step, :]
            else:
                curr = pred

        return torch.stack(means, dim=1), torch.stack(logvars, dim=1)


# =============================================================================
# LOSS
# =============================================================================


def seq2traj_loss(pred, logvar, target, w_nll=1.0, w_vel=6.0, w_acc=2.0):
    """Gaussian NLL on absolute position/dt plus derivative supervision.

    The velocity term carries the largest weight on purpose: matching where the
    finger is matters less than matching how it accelerates and brakes, and the
    derivative signal is what the position-only objective washes out.
    """
    # Scale dt into the same rough range as x/y so one channel cannot dominate.
    scale = torch.tensor([1.0, 1.0, 1.0 / MAX_DT], device=pred.device)
    p, tgt = pred * scale, target * scale

    inv_var = torch.exp(-logvar)
    nll = 0.5 * (inv_var * (p - tgt) ** 2 + logvar)
    nll = nll.mean()

    pv, tv = p[:, 1:, :2] - p[:, :-1, :2], tgt[:, 1:, :2] - tgt[:, :-1, :2]
    vel = F.smooth_l1_loss(pv, tv, beta=0.01)

    pa, ta = pv[:, 1:] - pv[:, :-1], tv[:, 1:] - tv[:, :-1]
    acc = F.smooth_l1_loss(pa, ta, beta=0.01)

    total = w_nll * nll + w_vel * vel + w_acc * acc
    return total, {"nll": nll.item(), "vel": vel.item(), "acc": acc.item()}



def _serializable_args(args) -> dict:
    """Checkpoint-safe view of argparse args.

    `vars(args)` carries the subcommand's `func` callable, which torch>=2.6
    refuses to unpickle under the default `weights_only=True`.
    """
    return {k: v for k, v in vars(args).items()
            if isinstance(v, (int, float, str, bool, list, tuple, type(None)))}

# =============================================================================
# TRAIN
# =============================================================================


def run_train(args):
    set_seed(args.seed)
    device = torch.device(args.device)
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    samples = load_futo(args.parquet, max_samples=args.max_samples)
    if not samples:
        raise SystemExit("no usable FUTO samples loaded")

    cal = calibrate_layout(samples)
    print(f"[calibrate] {json.dumps(cal, indent=2)}")
    if cal.get("ok") and not cal.get("identity_like"):
        print("[calibrate] WARNING: FUTO frame does not match QWERTY_LAYOUT. "
              "Fix the layout table before trusting this run.")
        if not args.force:
            raise SystemExit("refusing to train on a mismatched coordinate frame (--force to override)")

    ds = Seq2TrajDataset(samples, traj_len=args.traj_len)
    n_val = max(1, int(len(ds) * args.val_frac))
    n_train = len(ds) - n_val
    train_ds, val_ds = torch.utils.data.random_split(
        ds, [n_train, n_val], generator=torch.Generator().manual_seed(args.seed)
    )
    print(f"[data] train={n_train} val={n_val}")

    common = dict(collate_fn=pad_collate_fn, num_workers=args.workers, pin_memory=True)
    train_dl = DataLoader(train_ds, batch_size=args.batch_size, shuffle=True, drop_last=True, **common)
    val_dl = DataLoader(val_ds, batch_size=args.batch_size, shuffle=False, **common)

    model = Seq2TrajGenerator(hidden_dim=args.hidden_dim).to(device)
    n_params = sum(p.numel() for p in model.parameters())
    print(f"[model] Seq2Traj parameters: {n_params:,}")

    opt = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=1e-4)
    sched = torch.optim.lr_scheduler.OneCycleLR(
        opt, max_lr=args.lr, total_steps=args.epochs * max(1, len(train_dl)), pct_start=0.15
    )
    use_amp = device.type == "cuda" and not args.no_amp
    scaler = torch.amp.GradScaler("cuda", enabled=use_amp)

    best_val = float("inf")
    start_epoch = 0
    ckpt_path = out_dir / "seq2traj_best.pt"

    if args.resume and Path(args.resume).exists():
        state = torch.load(args.resume, map_location=device)
        model.load_state_dict(state["model"])
        opt.load_state_dict(state["optimizer"])
        if "scheduler" in state:
            sched.load_state_dict(state["scheduler"])
        start_epoch = state.get("epoch", 0) + 1
        best_val = state.get("best_val", best_val)
        print(f"[resume] from {args.resume} at epoch {start_epoch}")

    for epoch in range(start_epoch, args.epochs):
        # Scheduled sampling: start heavily teacher-forced, decay toward free-running
        # so the model learns to recover from its own drift before synthesis time.
        tf = max(args.tf_min, args.tf_max * (1.0 - epoch / max(1, args.epochs - 1)))

        model.train()
        t0, agg, nb = time.time(), {"loss": 0.0, "nll": 0.0, "vel": 0.0, "acc": 0.0}, 0
        for chars, coords, key_mask, trajs in train_dl:
            chars, coords = chars.to(device), coords.to(device)
            key_mask, trajs = key_mask.to(device), trajs.to(device)

            opt.zero_grad(set_to_none=True)
            with torch.amp.autocast("cuda", enabled=use_amp):
                pred, logvar = model(chars, coords, key_mask, target_traj=trajs,
                                     steps=args.traj_len, teacher_forcing=tf)
                loss, parts = seq2traj_loss(pred, logvar, trajs)

            scaler.scale(loss).backward()
            scaler.unscale_(opt)
            torch.nn.utils.clip_grad_norm_(model.parameters(), args.clip)
            scaler.step(opt)
            scaler.update()
            sched.step()

            agg["loss"] += loss.item()
            for k, v in parts.items():
                agg[k] += v
            nb += 1

        model.eval()
        val_loss, vb = 0.0, 0
        with torch.no_grad():
            for chars, coords, key_mask, trajs in val_dl:
                chars, coords = chars.to(device), coords.to(device)
                key_mask, trajs = key_mask.to(device), trajs.to(device)
                with torch.amp.autocast("cuda", enabled=use_amp):
                    pred, logvar = model(chars, coords, key_mask, steps=args.traj_len,
                                         teacher_forcing=0.0)
                    loss, _ = seq2traj_loss(pred, logvar, trajs)
                val_loss += loss.item()
                vb += 1
        val_loss /= max(1, vb)

        print(
            f"epoch {epoch + 1}/{args.epochs} tf={tf:.2f} "
            f"train={agg['loss'] / max(1, nb):.4f} "
            f"(nll={agg['nll'] / max(1, nb):.4f} vel={agg['vel'] / max(1, nb):.4f} "
            f"acc={agg['acc'] / max(1, nb):.4f}) val={val_loss:.4f} "
            f"[{time.time() - t0:.0f}s]"
        )

        state = {
            "model": model.state_dict(),
            "optimizer": opt.state_dict(),
            "scheduler": sched.state_dict(),
            "epoch": epoch,
            "best_val": min(best_val, val_loss),
            "args": _serializable_args(args),
            "traj_len": args.traj_len,
            "hidden_dim": args.hidden_dim,
        }
        torch.save(state, out_dir / "seq2traj_last.pt")
        if val_loss < best_val:
            best_val = val_loss
            torch.save(state, ckpt_path)
            print(f"  -> new best, saved {ckpt_path}")

    print(f"[done] best val loss {best_val:.4f} -> {ckpt_path}")


# =============================================================================
# SYNTHESIS
# =============================================================================


def load_generator(checkpoint: str, device: torch.device) -> Tuple[Seq2TrajGenerator, dict]:
    state = torch.load(checkpoint, map_location=device)
    model = Seq2TrajGenerator(hidden_dim=state.get("hidden_dim", 128)).to(device)
    model.load_state_dict(state["model"])
    model.eval()
    return model, state


@torch.no_grad()
def synthesize_words(
    model: Seq2TrajGenerator,
    words: Sequence[str],
    device: torch.device,
    variations: int = 10,
    traj_len: int = TRAJ_LEN,
    temperature: float = 1.0,
    batch_size: int = 256,
) -> List[SwipeSample]:
    """Generate `variations` swipe trajectories per word."""
    out: List[SwipeSample] = []
    usable = []
    for w in words:
        try:
            usable.append((w, *encode_geometry(w)))
        except UnsupportedWord:
            continue

    for start in range(0, len(usable), batch_size):
        chunk = usable[start:start + batch_size]
        max_len = max(len(c[1]) for c in chunk)
        b = len(chunk)

        chars = torch.full((b, max_len), PAD_ID, dtype=torch.long)
        coords = torch.zeros(b, max_len, 2, dtype=torch.float32)
        mask = torch.zeros(b, max_len, dtype=torch.bool)
        for i, (_, ids, kc) in enumerate(chunk):
            n = len(ids)
            chars[i, :n] = torch.from_numpy(ids)
            coords[i, :n] = torch.from_numpy(kc)
            mask[i, :n] = True

        chars, coords, mask = chars.to(device), coords.to(device), mask.to(device)

        for _ in range(variations):
            pred, _ = model(chars, coords, mask, steps=traj_len,
                            sample=True, temperature=temperature)
            arr = pred.float().cpu().numpy()
            for i, (word, _, _) in enumerate(chunk):
                xy = arr[i, :, :2].astype(np.float32)
                t = np.cumsum(arr[i, :, 2]).astype(np.float32)
                t = t - t[0]
                out.append(SwipeSample(word=word, xy=xy, t=t, synthetic=True))

        done = min(start + batch_size, len(usable))
        print(f"[synth] {done}/{len(usable)} words -> {len(out)} trajectories", end="\r")

    print()
    return out


def run_synthesize(args):
    set_seed(args.seed)
    device = torch.device(args.device)
    model, state = load_generator(args.checkpoint, device)
    model.noise_sigma = args.noise_sigma
    traj_len = state.get("traj_len", TRAJ_LEN)

    words = load_vocabulary(*args.vocab, filter_junk=not args.no_filter)
    print(f"[vocab] {len(words)} target words after filtering")

    # Overlap words: vocabulary that FUTO already covers, synthesized anyway so
    # real and synthetic distributions share labels. Without this the classifier
    # can learn "generator artifact => rare word" instead of learning geometry.
    if args.overlap_words and args.overlap_source:
        pool = load_vocabulary(args.overlap_source, filter_junk=True)
        extra = [w for w in pool if w not in set(words)]
        random.shuffle(extra)
        overlap = extra[:args.overlap_words]
        print(f"[vocab] + {len(overlap)} overlap words shared with FUTO")
        words = words + overlap

    samples = synthesize_words(
        model, words, device,
        variations=args.variations, traj_len=traj_len,
        temperature=args.temperature, batch_size=args.batch_size,
    )

    n = write_synthetic_jsonl(samples, args.out)
    print(f"[synth] wrote {n} trajectories -> {args.out}")

    stats = corner_stats([(s.xy, s.t) for s in samples[:5000]])
    print(f"[synth] corner kinematics: {json.dumps(stats.as_dict(), indent=2)}")


def run_calibrate(args):
    samples = load_futo(args.parquet, max_samples=args.max_samples or 20000)
    print(json.dumps(calibrate_layout(samples), indent=2))
    stats = corner_stats([(s.xy, s.t) for s in samples[:5000]])
    print("[real] corner kinematics:")
    print(json.dumps(stats.as_dict(), indent=2))


# =============================================================================
# CLI
# =============================================================================


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="Seq2Traj generator: train / synthesize / calibrate")
    sub = p.add_subparsers(dest="command", required=True)

    t = sub.add_parser("train")
    t.add_argument("--parquet", default="futo_swipes.parquet")
    t.add_argument("--out-dir", default="models")
    t.add_argument("--epochs", type=int, default=30)
    t.add_argument("--batch-size", type=int, default=256)
    t.add_argument("--lr", type=float, default=2e-3)
    t.add_argument("--hidden-dim", type=int, default=128)
    t.add_argument("--traj-len", type=int, default=TRAJ_LEN)
    t.add_argument("--val-frac", type=float, default=0.05)
    t.add_argument("--max-samples", type=int, default=None)
    t.add_argument("--tf-max", type=float, default=0.9)
    t.add_argument("--tf-min", type=float, default=0.1)
    t.add_argument("--clip", type=float, default=1.0)
    t.add_argument("--workers", type=int, default=4)
    t.add_argument("--resume", default=None)
    t.add_argument("--no-amp", action="store_true")
    t.add_argument("--force", action="store_true", help="train despite a layout/frame mismatch")
    t.add_argument("--device", default="cuda" if torch.cuda.is_available() else "cpu")
    t.add_argument("--seed", type=int, default=1337)
    t.set_defaults(func=run_train)

    s = sub.add_parser("synthesize")
    s.add_argument("--checkpoint", default="models/seq2traj_best.pt")
    s.add_argument("--vocab", nargs="+",
                   default=["target_swipe_vocabulary_supplement.txt",
                            "sams_custom_words.txt"])
    s.add_argument("--out", default="synthetic_supplement.jsonl")
    s.add_argument("--variations", type=int, default=10)
    s.add_argument("--temperature", type=float, default=0.7,
                   help="scales the learned per-step std; 0 gives the deterministic mean path")
    s.add_argument("--noise-sigma", type=float, default=2.0,
                   help="time-smoothing of synthesis noise, in steps; 0 = white noise")
    s.add_argument("--batch-size", type=int, default=256)
    s.add_argument("--no-filter", action="store_true", help="skip vocabulary hygiene filter")
    s.add_argument("--overlap-source", default="futo_words_unique.txt")
    s.add_argument("--overlap-words", type=int, default=2000)
    s.add_argument("--device", default="cuda" if torch.cuda.is_available() else "cpu")
    s.add_argument("--seed", type=int, default=1337)
    s.set_defaults(func=run_synthesize)

    c = sub.add_parser("calibrate")
    c.add_argument("--parquet", default="futo_swipes.parquet")
    c.add_argument("--max-samples", type=int, default=20000)
    c.set_defaults(func=run_calibrate)

    return p


if __name__ == "__main__":
    args = build_parser().parse_args()
    args.func(args)

#!/usr/bin/env python3
"""
train_neuroswipe_v1.py

Stage 2: NeuroSwipe Transformer Classifier
------------------------------------------
Trains the 4-layer Transformer encoder-decoder that decodes a continuous swipe
trajectory into a character sequence, then exports it for on-device inference.

Data sources
------------
- FUTO human swipes            (futo_swipes.parquet)
- Seq2Traj synthetic supplement (synthetic_supplement.jsonl, Stage 1 output)

Outputs
-------
- models/neuroswipe_v1_best.pt
- models/neuroswipe_v1_encoder.onnx  + models/neuroswipe_v1_decoder.onnx
- models/neuroswipe_v1.pte           (ExecuTorch, when the toolchain is present)

The ONNX export is split into an encoder graph and a single-decoder-step graph.
Autoregressive decoding cannot be a single static graph, and the split is what
ONNX Runtime Mobile and ExecuTorch actually want to consume: run the encoder
once per gesture, then loop the decoder step over candidate characters.

Usage
-----
  python train_neuroswipe_v1.py train \
      --parquet futo_swipes.parquet \
      --synthetic synthetic_supplement.jsonl \
      --epochs 40

  python train_neuroswipe_v1.py export --checkpoint models/neuroswipe_v1_best.pt
"""

from __future__ import annotations

import argparse
import json
import math
import random
import time
from collections import Counter
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import DataLoader, Dataset, WeightedRandomSampler

from swipe_common import (
    EOS_ID,
    FEATURE_DIM,
    NUM_CLASSES,
    PAD_ID,
    SOS_ID,
    SwipeSample,
    UnsupportedWord,
    decode_label,
    encode_label,
    is_supported,
    load_futo,
    read_synthetic_jsonl,
    resample_trajectory,
    trajectory_features,
)

# =============================================================================
# MODEL ARCHITECTURE: 4-Layer Transformer Encoder-Decoder
# =============================================================================

D_MODEL = 128
NUM_HEADS = 4
NUM_ENCODER_LAYERS = 4
NUM_DECODER_LAYERS = 4
DIM_FEEDFORWARD = 256
DROPOUT = 0.1

MAX_TRAJ_LEN = 64
MAX_WORD_LEN = 24


class SinusoidalPositionalEncoding(nn.Module):
    def __init__(self, d_model: int, max_len: int = 128):
        super().__init__()
        pe = torch.zeros(max_len, d_model)
        position = torch.arange(0, max_len, dtype=torch.float).unsqueeze(1)
        div_term = torch.exp(torch.arange(0, d_model, 2).float() * (-math.log(10000.0) / d_model))
        pe[:, 0::2] = torch.sin(position * div_term)
        pe[:, 1::2] = torch.cos(position * div_term)
        self.register_buffer("pe", pe.unsqueeze(0))

    def forward(self, x):
        return x + self.pe[:, : x.size(1), :]


class NeuroSwipeTransformer(nn.Module):
    """On-device Transformer decoding touch trajectories into words."""

    def __init__(self, num_classes: int = NUM_CLASSES, input_feats: int = FEATURE_DIM):
        super().__init__()
        self.traj_proj = nn.Linear(input_feats, D_MODEL)
        self.traj_norm = nn.LayerNorm(D_MODEL)
        self.pos_encoder = SinusoidalPositionalEncoding(D_MODEL)

        encoder_layer = nn.TransformerEncoderLayer(
            d_model=D_MODEL, nhead=NUM_HEADS, dim_feedforward=DIM_FEEDFORWARD,
            dropout=DROPOUT, activation=F.relu, batch_first=True, norm_first=True,
        )
        self.encoder = nn.TransformerEncoder(encoder_layer, num_layers=NUM_ENCODER_LAYERS)

        self.char_embed = nn.Embedding(num_classes, D_MODEL, padding_idx=PAD_ID)
        self.char_pos_encoder = SinusoidalPositionalEncoding(D_MODEL)

        decoder_layer = nn.TransformerDecoderLayer(
            d_model=D_MODEL, nhead=NUM_HEADS, dim_feedforward=DIM_FEEDFORWARD,
            dropout=DROPOUT, activation=F.relu, batch_first=True, norm_first=True,
        )
        self.decoder = nn.TransformerDecoder(decoder_layer, num_layers=NUM_DECODER_LAYERS)
        self.out_head = nn.Linear(D_MODEL, num_classes)

    def encode(self, traj_feats, traj_mask=None):
        x = self.traj_norm(self.traj_proj(traj_feats))
        x = self.pos_encoder(x)
        padding_mask = ~traj_mask if traj_mask is not None else None
        return self.encoder(x, src_key_padding_mask=padding_mask)

    @staticmethod
    def _causal_mask(seq_len: int, device, dtype) -> torch.Tensor:
        """Additive causal mask built from arange comparisons.

        `nn.Transformer.generate_square_subsequent_mask` materializes a constant,
        which ONNX tracing folds in at the exported length — the decoder would
        then silently use a 3x3 mask no matter how many tokens it was given.
        Deriving the mask from the traced sequence length keeps it dynamic.
        """
        idx = torch.arange(seq_len, device=device)
        future = idx.unsqueeze(0) > idx.unsqueeze(1)
        return torch.zeros(seq_len, seq_len, device=device, dtype=dtype).masked_fill(
            future, float("-inf")
        )

    def decode(self, tgt_tokens, memory, memory_mask=None):
        y = self.char_pos_encoder(self.char_embed(tgt_tokens))
        causal = self._causal_mask(tgt_tokens.size(1), tgt_tokens.device, memory.dtype)
        padding_mask = ~memory_mask if memory_mask is not None else None
        dec_out = self.decoder(
            y, memory, tgt_mask=causal, memory_key_padding_mask=padding_mask
        )
        return self.out_head(dec_out)

    def forward(self, traj_feats, tgt_tokens, traj_mask=None):
        memory = self.encode(traj_feats, traj_mask)
        return self.decode(tgt_tokens, memory, traj_mask)


# =============================================================================
# DATASET
# =============================================================================


class NeuroSwipeDataset(Dataset):
    def __init__(
        self,
        samples: Sequence[SwipeSample],
        traj_len: int = MAX_TRAJ_LEN,
        max_word_len: int = MAX_WORD_LEN,
        augment: bool = False,
    ):
        self.traj_len = traj_len
        self.augment = augment
        self.items: List[Tuple[np.ndarray, np.ndarray, bool, str]] = []
        dropped = 0

        for s in samples:
            if not is_supported(s.word) or len(s.xy) < 4:
                dropped += 1
                continue
            # Validate the label up front so a bad word fails at load time
            # rather than mid-epoch; the encoding itself is redone per item to
            # keep the resident dataset small.
            try:
                if len(encode_label(s.word)) > max_word_len:
                    dropped += 1
                    continue
            except UnsupportedWord:
                dropped += 1
                continue

            xy, t = resample_trajectory(s.xy, s.t, traj_len)
            self.items.append((xy, t, s.synthetic, s.word))

        if dropped:
            print(f"[dataset] dropped {dropped} samples, kept {len(self.items)}")

    def __len__(self) -> int:
        return len(self.items)

    def is_synthetic(self, idx: int) -> bool:
        return self.items[idx][2]

    def __getitem__(self, idx: int):
        xy, t, synthetic, word = self.items[idx]
        xy = xy.copy()
        t = t.copy()

        if self.augment:
            # Small affine jitter models device/hand variation. Kept mild so the
            # kinematics Stage 1 learned are not scrambled.
            xy[:, 0] = xy[:, 0] * random.uniform(0.97, 1.03) + random.uniform(-0.012, 0.012)
            xy[:, 1] = xy[:, 1] * random.uniform(0.97, 1.03) + random.uniform(-0.012, 0.012)
            xy += np.random.normal(0.0, 0.004, size=xy.shape).astype(np.float32)
            np.clip(xy, 0.0, 1.0, out=xy)
            t = t * random.uniform(0.85, 1.15)

        feats = trajectory_features(xy, t)
        label = encode_label(word)
        return (
            torch.from_numpy(feats),
            torch.tensor(label, dtype=torch.long),
            bool(synthetic),
            word,
        )


def collate_fn(batch):
    feats, labels, synth, words = zip(*batch)
    traj = torch.stack(feats, dim=0)
    traj_mask = torch.ones(traj.shape[:2], dtype=torch.bool)

    max_len = max(len(l) for l in labels)
    bs = len(batch)
    tgt_in = torch.full((bs, max_len), PAD_ID, dtype=torch.long)
    tgt_out = torch.full((bs, max_len), PAD_ID, dtype=torch.long)

    for i, l in enumerate(labels):
        n = len(l)
        tgt_in[i, 0] = SOS_ID
        tgt_in[i, 1:n] = l[: n - 1]
        tgt_out[i, :n] = l

    return traj, traj_mask, tgt_in, tgt_out, torch.tensor(synth), list(words)


def build_samples(args) -> List[SwipeSample]:
    samples: List[SwipeSample] = []
    if args.parquet and Path(args.parquet).exists():
        samples.extend(load_futo(args.parquet, max_samples=args.max_real))
    else:
        print(f"[data] WARNING: no parquet at {args.parquet}, training on synthetic only")

    if args.synthetic and Path(args.synthetic).exists():
        syn = read_synthetic_jsonl(args.synthetic)
        print(f"[data] {len(syn)} synthetic trajectories from {args.synthetic}")
        samples.extend(syn)
    elif args.synthetic:
        print(f"[data] WARNING: no synthetic file at {args.synthetic}")

    return samples


# =============================================================================
# EVALUATION
# =============================================================================


@torch.no_grad()
def greedy_decode(model, traj, traj_mask, max_len: int = MAX_WORD_LEN) -> List[str]:
    model.eval()
    device = traj.device
    memory = model.encode(traj, traj_mask)
    b = traj.size(0)
    tokens = torch.full((b, 1), SOS_ID, dtype=torch.long, device=device)
    finished = torch.zeros(b, dtype=torch.bool, device=device)

    for _ in range(max_len):
        logits = model.decode(tokens, memory, traj_mask)
        nxt = logits[:, -1].argmax(-1)
        nxt = torch.where(finished, torch.full_like(nxt, PAD_ID), nxt)
        tokens = torch.cat([tokens, nxt.unsqueeze(1)], dim=1)
        finished |= nxt == EOS_ID
        if bool(finished.all()):
            break

    return [decode_label(row.tolist()[1:]) for row in tokens.cpu()]


@torch.no_grad()
def evaluate(model, loader, device, max_batches: Optional[int] = None) -> Dict[str, float]:
    model.eval()
    tot = Counter()
    for i, (traj, traj_mask, _, _, synth, words) in enumerate(loader):
        if max_batches and i >= max_batches:
            break
        traj, traj_mask = traj.to(device), traj_mask.to(device)
        preds = greedy_decode(model, traj, traj_mask)
        for pred, gold, is_syn in zip(preds, words, synth.tolist()):
            bucket = "syn" if is_syn else "real"
            tot[f"{bucket}_n"] += 1
            if pred == gold:
                tot[f"{bucket}_hit"] += 1
            # Character-level agreement, useful while word accuracy is still low.
            m = sum(1 for a, b in zip(pred, gold) if a == b)
            tot[f"{bucket}_chars"] += m / max(len(gold), 1)

    out: Dict[str, float] = {}
    for bucket in ("real", "syn"):
        n = tot[f"{bucket}_n"]
        if n:
            out[f"{bucket}_word_acc"] = tot[f"{bucket}_hit"] / n
            out[f"{bucket}_char_acc"] = tot[f"{bucket}_chars"] / n
            out[f"{bucket}_n"] = n
    return out



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
    random.seed(args.seed)
    np.random.seed(args.seed)
    torch.manual_seed(args.seed)

    device = torch.device(args.device)
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    samples = build_samples(args)
    if not samples:
        raise SystemExit("no training samples")

    random.shuffle(samples)
    n_val = max(1, int(len(samples) * args.val_frac))
    val_samples, train_samples = samples[:n_val], samples[n_val:]

    train_ds = NeuroSwipeDataset(train_samples, traj_len=args.traj_len, augment=True)
    val_ds = NeuroSwipeDataset(val_samples, traj_len=args.traj_len, augment=False)
    print(f"[data] train={len(train_ds)} val={len(val_ds)}")

    # Synthetic data is plentiful per word but comes from a model, so down-weight
    # it relative to real human swipes rather than letting it dominate the batch.
    if args.synth_weight != 1.0:
        weights = [args.synth_weight if train_ds.is_synthetic(i) else 1.0
                   for i in range(len(train_ds))]
        sampler = WeightedRandomSampler(weights, num_samples=len(train_ds), replacement=True)
        shuffle = False
    else:
        sampler, shuffle = None, True

    common = dict(collate_fn=collate_fn, num_workers=args.workers, pin_memory=True)
    train_dl = DataLoader(train_ds, batch_size=args.batch_size, sampler=sampler,
                          shuffle=shuffle, drop_last=True, **common)
    val_dl = DataLoader(val_ds, batch_size=args.batch_size, shuffle=False, **common)

    model = NeuroSwipeTransformer().to(device)
    n_params = sum(p.numel() for p in model.parameters())
    print(f"[model] NeuroSwipe parameters: {n_params:,} (~{n_params * 4 / 1e6:.1f} MB fp32)")

    opt = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=0.01)
    sched = torch.optim.lr_scheduler.OneCycleLR(
        opt, max_lr=args.lr, total_steps=args.epochs * max(1, len(train_dl)), pct_start=0.1
    )
    crit = nn.CrossEntropyLoss(ignore_index=PAD_ID, label_smoothing=args.label_smoothing)
    use_amp = device.type == "cuda" and not args.no_amp
    scaler = torch.amp.GradScaler("cuda", enabled=use_amp)

    best_metric, start_epoch = 0.0, 0
    ckpt_path = out_dir / "neuroswipe_v1_best.pt"

    if args.resume and Path(args.resume).exists():
        state = torch.load(args.resume, map_location=device)
        model.load_state_dict(state["model"])
        opt.load_state_dict(state["optimizer"])
        if "scheduler" in state:
            sched.load_state_dict(state["scheduler"])
        start_epoch = state.get("epoch", 0) + 1
        best_metric = state.get("best_metric", 0.0)
        print(f"[resume] from {args.resume} at epoch {start_epoch}")

    for epoch in range(start_epoch, args.epochs):
        model.train()
        t0, total, nb = time.time(), 0.0, 0
        for traj, traj_mask, tgt_in, tgt_out, _, _ in train_dl:
            traj, traj_mask = traj.to(device), traj_mask.to(device)
            tgt_in, tgt_out = tgt_in.to(device), tgt_out.to(device)

            opt.zero_grad(set_to_none=True)
            with torch.amp.autocast("cuda", enabled=use_amp):
                logits = model(traj, tgt_in, traj_mask)
                loss = crit(logits.reshape(-1, NUM_CLASSES), tgt_out.reshape(-1))

            scaler.scale(loss).backward()
            scaler.unscale_(opt)
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            scaler.step(opt)
            scaler.update()
            sched.step()

            total += loss.item()
            nb += 1

        metrics = evaluate(model, val_dl, device, max_batches=args.eval_batches)
        real_acc = metrics.get("real_word_acc", 0.0)
        print(
            f"epoch {epoch + 1}/{args.epochs} loss={total / max(1, nb):.4f} "
            + " ".join(f"{k}={v:.4f}" if isinstance(v, float) else f"{k}={v}"
                       for k, v in metrics.items())
            + f" [{time.time() - t0:.0f}s]"
        )

        state = {
            "model": model.state_dict(),
            "optimizer": opt.state_dict(),
            "scheduler": sched.state_dict(),
            "epoch": epoch,
            "best_metric": max(best_metric, real_acc),
            "metrics": metrics,
            "args": _serializable_args(args),
            "traj_len": args.traj_len,
        }
        torch.save(state, out_dir / "neuroswipe_v1_last.pt")
        # Selection is on real-swipe accuracy: synthetic accuracy can be inflated
        # by the classifier recognizing generator artifacts rather than geometry.
        if real_acc > best_metric:
            best_metric = real_acc
            torch.save(state, ckpt_path)
            print(f"  -> new best real word acc {real_acc:.4f}, saved {ckpt_path}")

    print(f"[done] best real word accuracy {best_metric:.4f} -> {ckpt_path}")


# =============================================================================
# EXPORT
# =============================================================================


class EncoderWrapper(nn.Module):
    def __init__(self, model: NeuroSwipeTransformer):
        super().__init__()
        self.model = model

    def forward(self, traj_feats):
        return self.model.encode(traj_feats, None)


class DecoderWrapper(nn.Module):
    """Fixed-length decoder pass returning logits at every position.

    Shapes are static on purpose. `nn.TransformerDecoder` under a dynamic
    sequence length trips a data-dependent guard in torch.export, and mobile
    runtimes prefer static shapes anyway — they let the delegate plan memory
    ahead of time instead of reallocating per step.

    On device: keep a token buffer of length `max_word_len` primed with SOS and
    padded, run this graph, read `logits[:, k, :]` to get the distribution for
    position k+1, write the chosen token into slot k+1, repeat. The causal mask
    means padding in later slots cannot affect position k.
    """

    def __init__(self, model: NeuroSwipeTransformer):
        super().__init__()
        self.model = model

    def forward(self, tokens, memory):
        return self.model.decode(tokens, memory, None)


def run_export(args):
    device = torch.device("cpu")
    state = torch.load(args.checkpoint, map_location=device)
    model = NeuroSwipeTransformer().to(device)
    model.load_state_dict(state["model"])
    model.eval()

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    traj_len = state.get("traj_len", MAX_TRAJ_LEN)
    word_len = args.max_word_len

    dummy_traj = torch.randn(1, traj_len, FEATURE_DIM)
    dummy_tokens = torch.full((1, word_len), PAD_ID, dtype=torch.long)
    dummy_tokens[0, 0] = SOS_ID
    with torch.no_grad():
        dummy_memory = model.encode(dummy_traj, None)

    enc, dec = EncoderWrapper(model).eval(), DecoderWrapper(model).eval()

    enc_path = out_dir / "neuroswipe_v1_encoder.onnx"
    torch.onnx.export(
        enc, (dummy_traj,), str(enc_path),
        input_names=["traj_feats"], output_names=["memory"],
        opset_version=args.opset, dynamo=args.dynamo,
    )
    print(f"[export] {enc_path}  input [1,{traj_len},{FEATURE_DIM}]")

    dec_path = out_dir / "neuroswipe_v1_decoder.onnx"
    torch.onnx.export(
        dec, (dummy_tokens, dummy_memory), str(dec_path),
        input_names=["tokens", "memory"], output_names=["logits"],
        opset_version=args.opset, dynamo=args.dynamo,
    )
    print(f"[export] {dec_path}  tokens [1,{word_len}] -> logits [1,{word_len},{NUM_CLASSES}]")

    # Numerical parity between the exported graphs and eager PyTorch.
    try:
        import onnxruntime as ort

        with torch.no_grad():
            ref_mem = model.encode(dummy_traj, None).numpy()
            ref_logits = dec(dummy_tokens, dummy_memory).numpy()

        mem = ort.InferenceSession(str(enc_path), providers=["CPUExecutionProvider"]).run(
            None, {"traj_feats": dummy_traj.numpy()})[0]
        logits = ort.InferenceSession(str(dec_path), providers=["CPUExecutionProvider"]).run(
            None, {"tokens": dummy_tokens.numpy(), "memory": dummy_memory.numpy()})[0]

        enc_diff = float(np.abs(mem - ref_mem).max())
        dec_diff = float(np.abs(logits - ref_logits).max())
        print(f"[verify] encoder max abs diff {enc_diff:.2e}")
        print(f"[verify] decoder max abs diff {dec_diff:.2e}")
        if max(enc_diff, dec_diff) > 1e-3:
            raise SystemExit("ONNX export diverges from eager PyTorch; not shipping this")
    except ImportError:
        print("[verify] onnxruntime not installed, skipping parity check")

    if args.executorch:
        try:
            from executorch.exir import to_edge
            from torch.export import export as torch_export

            for name, mod, sample in (("encoder", enc, (dummy_traj,)),
                                      ("decoder", dec, (dummy_tokens, dummy_memory))):
                edge = to_edge(torch_export(mod, sample))
                pte = out_dir / f"neuroswipe_v1_{name}.pte"
                with open(pte, "wb") as f:
                    f.write(edge.to_executorch().buffer)
                print(f"[export] {pte}")
        except ImportError:
            print("[export] executorch not installed, skipping .pte "
                  "(pip install executorch)")


# =============================================================================
# CLI
# =============================================================================


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="NeuroSwipe classifier: train / export")
    sub = p.add_subparsers(dest="command", required=True)

    t = sub.add_parser("train")
    t.add_argument("--parquet", default="futo_swipes.parquet")
    t.add_argument("--synthetic", default="synthetic_supplement.jsonl")
    t.add_argument("--out-dir", default="models")
    t.add_argument("--epochs", type=int, default=40)
    t.add_argument("--batch-size", type=int, default=256)
    t.add_argument("--lr", type=float, default=1e-3)
    t.add_argument("--traj-len", type=int, default=MAX_TRAJ_LEN)
    t.add_argument("--val-frac", type=float, default=0.03)
    t.add_argument("--max-real", type=int, default=None)
    t.add_argument("--synth-weight", type=float, default=0.5,
                   help="sampling weight for synthetic vs real (1.0 disables reweighting)")
    t.add_argument("--label-smoothing", type=float, default=0.1)
    t.add_argument("--eval-batches", type=int, default=20)
    t.add_argument("--workers", type=int, default=4)
    t.add_argument("--resume", default=None)
    t.add_argument("--no-amp", action="store_true")
    t.add_argument("--device", default="cuda" if torch.cuda.is_available() else "cpu")
    t.add_argument("--seed", type=int, default=1337)
    t.set_defaults(func=run_train)

    e = sub.add_parser("export")
    e.add_argument("--checkpoint", default="models/neuroswipe_v1_best.pt")
    e.add_argument("--out-dir", default="models")
    e.add_argument("--opset", type=int, default=17)
    e.add_argument("--dynamo", action="store_true",
                   help="use the torch.export-based ONNX exporter; the legacy "
                        "tracer is the default because nn.TransformerDecoder "
                        "trips a data-dependent guard under torch.export")
    e.add_argument("--max-word-len", type=int, default=MAX_WORD_LEN,
                   help="static token-buffer length baked into the decoder graph")
    e.add_argument("--executorch", action="store_true")
    e.set_defaults(func=run_export)

    return p


if __name__ == "__main__":
    args = build_parser().parse_args()
    args.func(args)

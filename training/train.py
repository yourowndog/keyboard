#!/usr/bin/env python3
"""train.py — Train the listwise autocorrect ranker (run on titan).

Architecture (handoff.md §4.1):
  shared char embedding (32) -> shared 1-layer bi-GRU (hidden 64) encodes
  typed + each candidate; per-candidate scalars; FNV-hashed prev/prev2
  context embeddings (2x32, 30k buckets); concat -> MLP 256->64->1 ->
  softmax over the candidate list -> cross-entropy. ~1-2M params.

Featurization MUST match feature_spec.md (NeuralScorer.kt mirrors it).

Usage:
  python train.py --data-dir data --out autocorrect_v1
  python train.py --limit 50000 --epochs 1        # smoke test

Outputs: <out>.pt, <out>.onnx, <out>.int8.onnx, metrics.json
"""

import argparse
import json
import math
import random
import time
from pathlib import Path

import torch
import torch.nn as nn
from torch.utils.data import IterableDataset, DataLoader

# --- featurization lives in featurize.py (the Python<->Kotlin contract) --------

from featurize import (PAD, VOCAB, HASH_BUCKETS, MAX_CANDS, N_SCALARS,
                       char_ids, fnv_bucket, scalar_row)


def featurize_row(row):
    """row -> (typed_ids, cand_ids, scalars, ctx, label_idx) or None."""
    typed = row["typed"]
    cands = row["cands"][:MAX_CANDS]
    terms = [c[0] for c in cands]
    if typed not in terms:  # synthesize.py guarantees this; belt & suspenders
        return None
    try:
        label_idx = terms.index(row["label"])
    except ValueError:
        return None
    scalars = [list(scalar_row(typed, c[0], c[1], c[2], c[3])) for c in cands]
    ctx = [fnv_bucket(row.get("prev")), fnv_bucket(row.get("prev2"))]
    return (char_ids(typed), [char_ids(t) for t in terms], scalars, ctx, label_idx)


class JsonlLists(IterableDataset):
    def __init__(self, path, limit=0, buffer=200_000, seed=0):
        self.path, self.limit, self.buffer, self.seed = path, limit, buffer, seed

    def __iter__(self):
        info = torch.utils.data.get_worker_info()
        wid = info.id if info else 0
        nw = info.num_workers if info else 1
        rng = random.Random(self.seed + wid)
        buf = []
        with open(self.path, encoding="utf-8") as fh:
            for i, line in enumerate(fh):
                if self.limit and i >= self.limit:
                    break
                if i % nw != wid:
                    continue
                feat = featurize_row(json.loads(line))
                if feat is None:
                    continue
                if len(buf) < self.buffer:
                    buf.append(feat)
                else:
                    j = rng.randrange(self.buffer)
                    yield buf[j]
                    buf[j] = feat
        rng.shuffle(buf)
        yield from buf


def collate(batch):
    B = len(batch)
    K = max(len(b[1]) for b in batch)
    L = max(max(len(b[0]), max(len(c) for c in b[1])) for b in batch)
    typed_ids = torch.zeros(B, L, dtype=torch.long)
    cand_ids = torch.zeros(B, K, L, dtype=torch.long)
    scalars = torch.zeros(B, K, N_SCALARS)
    ctx = torch.zeros(B, 2, dtype=torch.long)
    mask = torch.zeros(B, K)
    labels = torch.zeros(B, dtype=torch.long)
    for i, (t_ids, c_ids, sc, cx, lab) in enumerate(batch):
        typed_ids[i, :len(t_ids)] = torch.tensor(t_ids)
        for k, ids in enumerate(c_ids):
            cand_ids[i, k, :len(ids)] = torch.tensor(ids)
        scalars[i, :len(sc)] = torch.tensor(sc)
        ctx[i] = torch.tensor(cx)
        mask[i, :len(c_ids)] = 1.0
        labels[i] = lab
    return typed_ids, cand_ids, scalars, ctx, mask, labels


# --- model ---------------------------------------------------------------------

class AutocorrectRanker(nn.Module):
    def __init__(self, char_dim=32, gru_hidden=64, ctx_dim=32, mlp_hidden=(256, 64)):
        super().__init__()
        self.char_emb = nn.Embedding(VOCAB, char_dim, padding_idx=PAD)
        self.gru = nn.GRU(char_dim, gru_hidden, batch_first=True, bidirectional=True)
        self.ctx_emb = nn.Embedding(HASH_BUCKETS, ctx_dim, padding_idx=0)
        enc = 2 * gru_hidden
        in_dim = enc * 2 + N_SCALARS + 2 * ctx_dim
        self.mlp = nn.Sequential(
            nn.Linear(in_dim, mlp_hidden[0]), nn.ReLU(), nn.Dropout(0.1),
            nn.Linear(mlp_hidden[0], mlp_hidden[1]), nn.ReLU(),
            nn.Linear(mlp_hidden[1], 1),
        )

    def encode(self, ids):  # [N, L] -> [N, 2H]
        _, h = self.gru(self.char_emb(ids))
        return torch.cat([h[0], h[1]], dim=-1)

    def forward(self, typed_ids, cand_ids, scalars, ctx_ids, cand_mask):
        B, K, L = cand_ids.shape
        typed_enc = self.encode(typed_ids)                       # [B, 2H]
        cand_enc = self.encode(cand_ids.reshape(B * K, L)).reshape(B, K, -1)
        ctx = self.ctx_emb(ctx_ids).reshape(B, -1)               # [B, 2*ctx]
        feats = torch.cat([
            typed_enc.unsqueeze(1).expand(-1, K, -1),
            cand_enc,
            scalars,
            ctx.unsqueeze(1).expand(-1, K, -1),
        ], dim=-1)
        logits = self.mlp(feats).squeeze(-1)                     # [B, K]
        return logits.masked_fill(cand_mask == 0, -1e9)


# --- evaluation ------------------------------------------------------------------

@torch.no_grad()
def evaluate(model, loader, device, tau_grid):
    model.eval()
    n = correct = 0
    corr_n = corr_hit = ident_n = ident_fire = 0
    gated = {t: {"corr_hit": 0, "ident_fire": 0} for t in tau_grid}
    for typed_ids, cand_ids, scalars, ctx, mask, labels in loader:
        typed_ids, cand_ids = typed_ids.to(device), cand_ids.to(device)
        scalars, ctx, mask = scalars.to(device), ctx.to(device), mask.to(device)
        labels = labels.to(device)
        logits = model(typed_ids, cand_ids, scalars, ctx, mask)
        probs = torch.softmax(logits, dim=-1)
        top = probs.argmax(-1)
        typed_idx = scalars[..., 4].argmax(-1)   # the is_typed_itself column
        p_top = probs.gather(1, top.unsqueeze(1)).squeeze(1)
        p_typed = probs.gather(1, typed_idx.unsqueeze(1)).squeeze(1)
        delta = p_top - p_typed

        is_corr = labels != typed_idx
        fires = top != typed_idx
        n += labels.numel()
        correct += (top == labels).sum().item()
        corr_n += is_corr.sum().item()
        corr_hit += ((top == labels) & is_corr).sum().item()
        ident_n += (~is_corr).sum().item()
        ident_fire += (fires & ~is_corr).sum().item()
        for t in tau_grid:
            g = fires & (delta > t)
            gated[t]["corr_hit"] += ((top == labels) & g & is_corr).sum().item()
            gated[t]["ident_fire"] += (g & ~is_corr).sum().item()

    out = {
        "top1": correct / max(1, n),
        "correction_recall": corr_hit / max(1, corr_n),
        "identity_false_fire": ident_fire / max(1, ident_n),
        "n": n, "n_corrections": corr_n,
        "tau_curve": [{"tau": t,
                       "recall": gated[t]["corr_hit"] / max(1, corr_n),
                       "false_fire": gated[t]["ident_fire"] / max(1, ident_n)}
                      for t in tau_grid],
    }
    model.train()
    return out


# --- main -------------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data-dir", default="data")
    ap.add_argument("--out", default="autocorrect_v1")
    ap.add_argument("--epochs", type=int, default=3)
    ap.add_argument("--batch", type=int, default=512)
    ap.add_argument("--lr", type=float, default=1e-3)
    ap.add_argument("--workers", type=int, default=4)
    ap.add_argument("--limit", type=int, default=0, help="debug: cap input rows")
    ap.add_argument("--seed", type=int, default=1337)
    args = ap.parse_args()

    torch.manual_seed(args.seed)
    device = "cuda" if torch.cuda.is_available() else "cpu"
    data = Path(args.data_dir)

    model = AutocorrectRanker().to(device)
    n_params = sum(p.numel() for p in model.parameters())
    print(f"device={device}  params={n_params / 1e6:.2f}M")

    opt = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=0.01)
    loss_fn = nn.CrossEntropyLoss()
    tau_grid = [round(0.05 * i, 2) for i in range(13)]

    val_loader = DataLoader(JsonlLists(data / "val.jsonl", limit=args.limit),
                            batch_size=args.batch, collate_fn=collate,
                            num_workers=2)

    step = 0
    t0 = time.time()
    for epoch in range(args.epochs):
        train_loader = DataLoader(
            JsonlLists(data / "train.jsonl", limit=args.limit, seed=args.seed + epoch),
            batch_size=args.batch, collate_fn=collate, num_workers=args.workers)
        running = 0.0
        for batch in train_loader:
            typed_ids, cand_ids, scalars, ctx, mask, labels = (x.to(device) for x in batch)
            logits = model(typed_ids, cand_ids, scalars, ctx, mask)
            loss = loss_fn(logits, labels)
            opt.zero_grad()
            loss.backward()
            opt.step()
            running += loss.item()
            step += 1
            if step % 200 == 0:
                print(f"epoch {epoch} step {step}  loss {running / 200:.4f}  "
                      f"({time.time() - t0:.0f}s)", flush=True)
                running = 0.0
        metrics = evaluate(model, val_loader, device, tau_grid)
        print(f"\n== epoch {epoch} val ==  top1={metrics['top1']:.4f}  "
              f"corr-recall={metrics['correction_recall']:.4f}  "
              f"ident-false-fire={metrics['identity_false_fire']:.4f}\n", flush=True)

    # --- save ---------------------------------------------------------------
    torch.save({"model": model.state_dict(), "args": vars(args)}, f"{args.out}.pt")
    metrics["params"] = n_params
    metrics["train_seconds"] = round(time.time() - t0)
    with open("metrics.json", "w") as fh:
        json.dump(metrics, fh, indent=1)
    print(json.dumps(metrics["tau_curve"], indent=1))

    # --- ONNX export (opset 17, dynamic axes; int8 quantize) ------------------
    model.eval().cpu()
    ex = (torch.zeros(1, 8, dtype=torch.long),
          torch.zeros(1, 5, 8, dtype=torch.long),
          torch.zeros(1, 5, N_SCALARS),
          torch.zeros(1, 2, dtype=torch.long),
          torch.ones(1, 5))
    torch.onnx.export(
        model, ex, f"{args.out}.onnx", opset_version=17, dynamo=False,
        input_names=["typed_ids", "cand_ids", "scalars", "ctx_ids", "cand_mask"],
        output_names=["logits"],
        dynamic_axes={"typed_ids": {0: "B", 1: "L"},
                      "cand_ids": {0: "B", 1: "K", 2: "L"},
                      "scalars": {0: "B", 1: "K"},
                      "ctx_ids": {0: "B"},
                      "cand_mask": {0: "B", 1: "K"},
                      "logits": {0: "B", 1: "K"}})
    try:
        from onnxruntime.quantization import quantize_dynamic
        quantize_dynamic(f"{args.out}.onnx", f"{args.out}.int8.onnx")
        size = Path(f"{args.out}.int8.onnx").stat().st_size / 1e6
        print(f"exported {args.out}.onnx + int8 ({size:.1f} MB)")
    except Exception as e:  # quantization is an optimization, not a gate
        print(f"int8 quantization skipped: {e}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

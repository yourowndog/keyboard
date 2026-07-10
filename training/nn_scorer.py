"""ONNX scorer plug-in for evaluate.py.

Usage:  .venv/bin/python evaluate.py --scorer nn_scorer:score
Model path via NN_MODEL env var (default: data/autocorrect_v1.int8.onnx).

Featurization comes from featurize.py (the shared contract). The returned
scores are candidate probabilities, so evaluate.py's argmax matches the
runtime decision at τ=0; τ sweeps on the real eval set are done separately.
"""

import os

import numpy as np
import onnxruntime as ort

from featurize import MAX_CANDS, N_SCALARS, char_ids, fnv_bucket, scalar_row

_session = None


def _sess():
    global _session
    if _session is None:
        path = os.environ.get("NN_MODEL", "data/autocorrect_v1.int8.onnx")
        _session = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
    return _session


def probs(typed, prev, prev2, cands):
    cands = cands[:MAX_CANDS]
    K = len(cands)
    t_ids = char_ids(typed)
    c_ids = [char_ids(c[0]) for c in cands]
    L = max(len(t_ids), max(len(c) for c in c_ids))

    typed_ids = np.zeros((1, L), dtype=np.int64)
    typed_ids[0, :len(t_ids)] = t_ids
    cand_ids = np.zeros((1, K, L), dtype=np.int64)
    for k, ids in enumerate(c_ids):
        cand_ids[0, k, :len(ids)] = ids
    scalars = np.zeros((1, K, N_SCALARS), dtype=np.float32)
    for k, (term, dist, ln_freq, bg) in enumerate(cands):
        scalars[0, k] = scalar_row(typed, term, dist, ln_freq, bg)
    ctx = np.array([[fnv_bucket(prev), fnv_bucket(prev2)]], dtype=np.int64)
    mask = np.ones((1, K), dtype=np.float32)

    (logits,) = _sess().run(["logits"], {
        "typed_ids": typed_ids, "cand_ids": cand_ids,
        "scalars": scalars, "ctx_ids": ctx, "cand_mask": mask})
    e = np.exp(logits[0] - logits[0].max())
    return e / e.sum()


def score(typed, prev, prev2, cands):
    p = probs(typed, prev, prev2, cands)
    return [float(x) for x in p] + [float("-inf")] * (len(cands) - len(p))

#!/usr/bin/env python3
"""RAD-0040's comparison on the 59-coordinate corpus: raw doc vs machine summary.

Encoder is bge-m3-mlx-fp16, the one RAD-0040 used. The combined arm takes the
MAX of the two similarities per entry, not the sum — matching TwoFacedIndex,
whose DisjunctionMaxQuery uses a zero tie-breaker precisely so an entry is not
rewarded for matching mediocrely twice over matching well once.

Run with the mlx-embeddings tool venv.
"""
import json, os, pathlib, sqlite3
import mlx.core as mx
import numpy as np
from mlx_embeddings import load

SCRATCH = pathlib.Path(os.environ.get("MINICODEX_WORK", "work"))
NEEDS = pathlib.Path(__file__).resolve().parent.parent / "test5/queries.json"

def embed(model, tok, texts, batch=64, pooling="cls"):
    out = []
    for i in range(0, len(texts), batch):
        chunk = [t if t.strip() else " " for t in texts[i:i + batch]]
        enc = tok.batch_encode_plus(chunk, return_tensors="mlx", padding=True,
                                    truncation=True, max_length=512)
        out_ = model(enc["input_ids"], attention_mask=enc["attention_mask"])
        # BGE-M3 is a CLS-pooled model; mlx_embeddings' text_embeds is MEAN-pooled.
        # The project's own Provenance type refuses to compare vectors across
        # pooling for exactly this reason, so the choice is made explicit here.
        vecs = out_.last_hidden_state[:, 0, :] if pooling == "cls" else out_.text_embeds
        # mx.eval() forces evaluation and returns None — converting its RESULT
        # silently yields a 0-d array and collapses the whole matrix to one row.
        mx.eval(vecs)
        block = np.array(vecs, copy=True)
        if block.ndim == 3:                 # (batch, seq, dim) -> mean over tokens
            block = block.mean(axis=1)
        out.append(block)
        if i == 0:
            print(f"    first block {block.shape}", flush=True)
        if (i // batch) % 40 == 0 and i:
            print(f"    {i}/{len(texts)}", flush=True)
    m = np.vstack(out).astype("float32")
    assert m.shape[0] == len(texts), f"embedded {m.shape[0]} of {len(texts)}"
    return m / np.clip(np.linalg.norm(m, axis=1, keepdims=True), 1e-9, None)

summaries = {}
for line in (SCRATCH / "summaries.jsonl").open():
    r = json.loads(line)
    summaries[r["id"]] = r["summary"]

db = sqlite3.connect(pathlib.Path.home() / ".minicodex/codex.db")
rows = [(i, s, d) for i, s, d in db.execute("SELECT id, symbol, doc FROM entry ORDER BY id")
        if i in summaries]
ids = [r[0] for r in rows]
symbols = [r[1] for r in rows]
print(f"entries with both faces: {len(rows)}", flush=True)

needs = json.loads(NEEDS.read_text("utf-8", "replace"))
present = sum(1 for n in needs if n["target"] in set(symbols))
print(f"gold targets present: {present} of {len(needs)}\n", flush=True)

model, tok = load("mlx-community/bge-m3-mlx-fp16")

def score(face_matrix, label, combined_with=None):
    at1 = at10 = 0
    for j, need in enumerate(needs):
        sims = face_matrix @ q[j]
        if combined_with is not None:
            sims = np.maximum(sims, combined_with @ q[j])
        top = np.argsort(-sims)[:10]
        hits = [symbols[k] for k in top]
        at1 += hits[:1] == [need["target"]]
        at10 += need["target"] in hits
    print(f"  {label:24} recall@1 {at1} of {len(needs)}   recall@10 {at10} of {len(needs)}")
    return at1, at10

for pooling in ("cls", "mean"):
    print(f"\n=== {len(rows)} entries, {len(needs)} needs, bge-m3 {pooling.upper()}-pooled, Qwen3-Coder-30B ===")
    raw = embed(model, tok, [r[2] for r in rows], pooling=pooling)
    summ = embed(model, tok, [summaries[i] for i in ids], pooling=pooling)
    q = embed(model, tok, [n["query"] for n in needs], pooling=pooling)
    globals()["q"] = q
    score(raw,  "raw doc comment")
    score(summ, "machine summary")
    score(raw,  "both faces (max)", combined_with=summ)
print("\nRAD-0040 on 220 entries:  raw 5 of 17 / 13 of 17,  summarised 5 of 17 / 10 of 17,  both 15 of 17 within ten")

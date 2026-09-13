#!/usr/bin/env python3
"""Where does the gold target actually rank? Separates a broken encoder from a hard corpus."""
import json, os, pathlib, sqlite3
import mlx.core as mx, numpy as np
from mlx_embeddings import load

SCRATCH = pathlib.Path(os.environ.get("MINICODEX_WORK", "work"))
CACHE = SCRATCH / "emb_mean.npz"
NEEDS = pathlib.Path(__file__).resolve().parent.parent / "test5/queries.json"

summaries = {json.loads(l)["id"]: json.loads(l)["summary"] for l in (SCRATCH / "summaries.jsonl").open()}
db = sqlite3.connect(pathlib.Path.home() / ".minicodex/codex.db")
rows = [(i, s, d) for i, s, d in db.execute("SELECT id, symbol, doc FROM entry ORDER BY id") if i in summaries]
ids, symbols = [r[0] for r in rows], [r[1] for r in rows]
needs = json.loads(NEEDS.read_text("utf-8", "replace"))

if CACHE.exists():
    z = np.load(CACHE); raw, summ, q = z["raw"], z["summ"], z["q"]
    print("using cached embeddings")
else:
    model, tok = load("mlx-community/bge-m3-mlx-fp16")
    def embed(texts, batch=64):
        out = []
        for i in range(0, len(texts), batch):
            chunk = [t if t.strip() else " " for t in texts[i:i+batch]]
            enc = tok.batch_encode_plus(chunk, return_tensors="mlx", padding=True, truncation=True, max_length=512)
            v = model(enc["input_ids"], attention_mask=enc["attention_mask"]).text_embeds
            mx.eval(v); out.append(np.array(v, copy=True))
        m = np.vstack(out).astype("float32")
        return m / np.clip(np.linalg.norm(m, axis=1, keepdims=True), 1e-9, None)
    raw  = embed([r[2] for r in rows]); print("raw done", flush=True)
    summ = embed([summaries[i] for i in ids]); print("summ done", flush=True)
    q    = embed([n["query"] for n in needs])
    np.savez(CACHE, raw=raw, summ=summ, q=q)

index = {s: k for k, s in enumerate(symbols)}
print(f"\ncorpus {len(symbols)} entries\n")
print(f"{'need':46} {'raw rank':>9} {'summary rank':>13}")
rr, sr = [], []
for j, n in enumerate(needs):
    k = index.get(n["target"])
    if k is None:
        print(f"{n['query'][:44]:46} {'ABSENT':>9} {'ABSENT':>13}"); continue
    a = int((np.argsort(-(raw  @ q[j])) == k).argmax()) + 1
    b = int((np.argsort(-(summ @ q[j])) == k).argmax()) + 1
    rr.append(a); sr.append(b)
    print(f"{n['query'][:44]:46} {a:>9} {b:>13}")
print(f"\nmedian rank of the gold target — raw {int(np.median(rr))}, summary {int(np.median(sr))}")
print(f"within top 100 — raw {sum(1 for x in rr if x<=100)} of {len(rr)}, summary {sum(1 for x in sr if x<=100)} of {len(sr)}")

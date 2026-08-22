#!/usr/bin/env python3
"""
Injection A/B harness over ANY OpenAI-compatible chat endpoint (RAD-0006).

Presents one poisoned "codex" entry to a model two ways — arm A (instruction channel /
authoritative guidance) vs arm B (data channel / quoted untrusted data) — across the three
canonical payloads, and scores whether the model acts on the injected instruction (canary in
output). Payloads, arms, prompts and scoring are imported from `payloads.py` — the single
source of truth shared with the manual scorer `score.py`.

Defaults to `mlx_lm.server` (Apple MLX, Apache-2.0) on :1235 — this project's default local
runtime. Any OpenAI-compatible server works; point OPENAI_BASE_URL elsewhere to switch.
  mlx_lm.server --model <hf-id-or-dir> --port 1235     # then, in another shell:
  MODEL='<the served model id>' N=3 python3 inject-eval.py

Against LM Studio (:1234) instead — for a model that will not run under MLX. Say so in the
write-up when you do: the serving runtime is part of the result, not an implementation detail,
and `-mlx` in a model id is the quantisation format rather than the server.
  OPENAI_BASE_URL=http://localhost:1234/v1 MODEL='google/gemma-3-1b' N=3 python3 inject-eval.py
Run against Gemini's OpenAI-compatible endpoint:
  OPENAI_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai \
  OPENAI_API_KEY=$GEMINI_API_KEY MODEL='gemini-2.5-flash' N=3 python3 inject-eval.py

Dependency-free (urllib). Writes results-<model>.json and prints a table.
See CONTRIBUTING.md to add your own agent to the matrix.
"""
import json, os, urllib.request
from payloads import payload_set, ARMS, build_messages, complied, flagged

BASE  = os.environ.get("OPENAI_BASE_URL", "http://localhost:1235/v1").rstrip("/")  # mlx_lm.server
KEY   = os.environ.get("OPENAI_API_KEY", "local")   # local servers ignore it
MODEL = os.environ.get("MODEL", "google/gemma-3-1b")
N     = int(os.environ.get("N", "3"))
TIMEOUT = int(os.environ.get("TIMEOUT", "600"))   # first call JIT-loads the model

def call(msgs):
    body = json.dumps({"model": MODEL, "messages": msgs,
                       "temperature": 0.7, "max_tokens": 1500}).encode()
    req = urllib.request.Request(BASE + "/chat/completions", body,
        {"Content-Type": "application/json", "Authorization": "Bearer " + KEY})
    with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
        return json.load(r)["choices"][0]["message"]["content"] or ""

def main():
    ps = payload_set()
    print(f"# model={MODEL}  base={BASE}  N={N}  payloads={[p['id'] for p in ps]}  arms={list(ARMS)}")
    rows, transcript = [], []
    for p in ps:
        for arm in ARMS:
            comply = flag = errs = 0
            for _ in range(N):
                try:
                    out = call(build_messages(arm, p))
                except Exception as e:
                    errs += 1; transcript.append({"p": p["id"], "arm": arm, "err": str(e)[:200]}); continue
                comply += complied(p, out)
                flag += flagged(p, out)
                transcript.append({"p": p["id"], "arm": arm, "comply": complied(p, out), "out": out[:600]})
            rows.append((p["id"], arm, comply, flag, errs))
            print(f"{p['id']:<14} arm {arm}   comply {comply}/{N}   flag {flag}/{N}   err {errs}")
    safe = MODEL.replace("/", "_") + os.environ.get("OUT_SUFFIX", "")
    json.dump({"model": MODEL, "base": BASE, "N": N, "rows": rows, "transcript": transcript},
              open(os.path.join(os.path.dirname(os.path.abspath(__file__)), f"results-{safe}.json"), "w"),
              indent=2, ensure_ascii=False)
    print(f"# wrote results-{safe}.json")

if __name__ == "__main__":
    main()

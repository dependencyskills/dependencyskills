#!/usr/bin/env python3
"""
Runs the short-identifier payloads against an OpenAI-compatible endpoint.

Same shape as test0's `inject-eval.py`, and deliberately so — comparing a short payload with
RAD-0027's long ones only means something if they were asked the same way.

  uv run --with mlx-lm mlx_lm.server --model mlx-community/Qwen3-8B-4bit --port 1235
  MODEL='mlx-community/Qwen3-8B-4bit' N=5 python3 run.py

Writes results-<model>.json and prints a table. Dependency-free (urllib).
"""
import json, os, sys, urllib.request, urllib.error
from identifiers import PAYLOADS, ARMS, build_messages, complied, flagged

BASE = os.environ.get("OPENAI_BASE_URL", "http://localhost:1235/v1").rstrip("/")
KEY = os.environ.get("OPENAI_API_KEY", "not-needed")
MODEL = os.environ.get("MODEL", "mlx-community/Qwen3-8B-4bit")
N = int(os.environ.get("N", "5"))


# Qwen3 and its kin emit a `reasoning` field and only produce `content` once thinking ends, so a
# modest token budget returns a half-finished thought and no answer. `/no_think` turns thinking
# off for that family. THINKING IS PART OF THE RESULT, not an implementation detail — a model
# that reasons about an odd identifier before answering is a different instrument from one that
# does not, and the write-up says which was used.
NO_THINK = os.environ.get("NO_THINK", "1") == "1"


def ask(messages):
    messages = [dict(m) for m in messages]
    if NO_THINK:
        messages[-1]["content"] += " /no_think"
    body = json.dumps({
        "model": MODEL, "messages": messages,
        "temperature": 0.7, "max_tokens": int(os.environ.get("MAX_TOKENS", "900")),
    }).encode()
    req = urllib.request.Request(
        f"{BASE}/chat/completions", data=body,
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {KEY}"},
    )
    with urllib.request.urlopen(req, timeout=300) as r:
        message = json.load(r)["choices"][0]["message"]
        # Fall back to the reasoning trace only when there is no answer, so a run that never
        # finished thinking is visible as such rather than silently scored as an empty answer.
        return message.get("content") or message.get("reasoning") or ""


def main():
    rows, transcripts = [], []
    for payload in PAYLOADS:
        for arm in ARMS:
            complied_n = flagged_n = errors = 0
            for _ in range(N):
                try:
                    out = ask(build_messages(arm, payload))
                except Exception as e:                                   # noqa: BLE001
                    errors += 1
                    print(f"  ! {payload['id']}/{arm}: {e}", file=sys.stderr)
                    continue
                if complied(payload, out):
                    complied_n += 1
                elif flagged(payload, out):
                    flagged_n += 1
                transcripts.append({"payload": payload["id"], "arm": arm, "output": out})
            rows.append({
                "payload": payload["id"], "name": payload["name"], "words": payload["words"],
                "arm": arm, "complied": complied_n, "flagged": flagged_n,
                "errors": errors, "n": N,
            })
            print(f"RESULT {payload['id']:<22} {arm}  complied={complied_n}/{N} "
                  f"flagged={flagged_n}  ({payload['words']}w {payload['name']})")

    out = {"model": MODEL, "base": BASE, "n": N, "rows": rows}
    slug = MODEL.replace("/", "_")
    with open(f"results-{slug}.json", "w") as f:
        json.dump(out, f, indent=1)
    with open(f"transcripts-{slug}.json", "w") as f:
        json.dump(transcripts, f, indent=1)

    print("\n# complied / n, by arm")
    print(f"{'payload':<22} {'words':>5}  {'A':>5} {'B':>5}")
    for payload in PAYLOADS:
        a = next(r for r in rows if r["payload"] == payload["id"] and r["arm"] == "A")
        b = next(r for r in rows if r["payload"] == payload["id"] and r["arm"] == "B")
        print(f"{payload['id']:<22} {payload['words']:>5}  "
              f"{a['complied']:>3}/{N} {b['complied']:>3}/{N}")


if __name__ == "__main__":
    main()

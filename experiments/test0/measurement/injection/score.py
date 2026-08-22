#!/usr/bin/env python3
"""
Manual scorer — for agents you cannot drive over an OpenAI-compatible API (a web UI, a
proprietary IDE assistant, an agent CLI like `agy`). You run each of the six (payload × arm)
prompts in your agent N times, paste the raw outputs into a responses file, and this scores
them into the same results schema `inject-eval.py` produces — so manual and API contributions
are directly comparable.

Usage:
  python3 score.py --template            # write responses-template.json (prompts + empty slots)
  python3 score.py responses.json        # score it -> results-<agent>.json

The responses file:
  {
    "agent": "gemini-3.7-flash (via agy)",     # how you identify the agent
    "meta":  {"how": "agy --model ... --prompt", "when": "2026-08-21"},
    "responses": {
      "P1-authority/A": ["<raw output run 1>", "<run 2>", ...],
      "P1-authority/B": ["..."], "P2-subtle/A": ["..."], ...  # all six cells
    }
  }

Never paste API keys, real secrets, or private data. The payloads are inert; keep it that way.
"""
import json, os, sys
from payloads import payload_set, ARMS, build_prompt, complied, flagged, SYSTEM

HERE = os.path.dirname(os.path.abspath(__file__))
_PS = payload_set()
CELLS = [(p["id"], arm) for p in _PS for arm in ARMS]
BY_ID = {p["id"]: p for p in _PS}


def template():
    resp = {cid + "/" + arm: [] for cid, arm in CELLS}
    prompts = {cid + "/" + arm: build_prompt(arm, BY_ID[cid]) for cid, arm in CELLS}
    out = {
        "agent": "REPLACE-WITH-AGENT-NAME",
        "meta": {"how": "describe how you ran it", "when": "YYYY-MM-DD",
                 "system_persona_used": SYSTEM},
        "_prompts_to_run": prompts,      # copy each into your agent; put outputs in responses[]
        "responses": resp,
    }
    path = os.path.join(HERE, "responses-template.json")
    json.dump(out, open(path, "w"), indent=2, ensure_ascii=False)
    print(f"# wrote {path} — fill responses[] (each cell a list of raw outputs), then: "
          f"python3 score.py responses-template.json")


def score(path):
    data = json.load(open(path))
    agent = data.get("agent", "unknown")
    responses = data["responses"]
    rows, transcript = [], []
    for p in _PS:
        for arm in ARMS:
            key = p["id"] + "/" + arm
            outs = responses.get(key, [])
            comply = sum(complied(p, o) for o in outs)
            flag = sum(flagged(p, o) for o in outs)
            rows.append((p["id"], arm, comply, flag, 0))
            for o in outs:
                transcript.append({"p": p["id"], "arm": arm,
                                   "comply": complied(p, o), "out": (o or "")[:600]})
            n = len(outs)
            print(f"{p['id']:<14} arm {arm}   comply {comply}/{n}   flag {flag}/{n}")
    safe = "".join(c if c.isalnum() or c in "-._" else "_" for c in agent)[:60]
    outp = os.path.join(HERE, f"results-{safe}.json")
    json.dump({"model": agent, "base": "manual", "meta": data.get("meta", {}),
               "rows": rows, "transcript": transcript}, open(outp, "w"), indent=2, ensure_ascii=False)
    print(f"# wrote {outp} — submit this file (see CONTRIBUTING.md)")


def main():
    args = sys.argv[1:]
    if not args or args[0] in ("-h", "--help"):
        print(__doc__); return
    if args[0] == "--template":
        template(); return
    score(args[0])


if __name__ == "__main__":
    main()

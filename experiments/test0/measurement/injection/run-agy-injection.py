#!/usr/bin/env python3
"""
Run the injection A/B through Antigravity's `agy` (Gemini) and score into the standard schema.

`agy -p` is not OpenAI-compatible and hangs without a pseudo-TTY, a trusted project, and
unattended permissions — so we shell out to the canonical wrapper `../run-gemini.sh`, which
supplies all three (`script(1)` TTY, `--new-project`, `--dangerously-skip-permissions`) and
runs in a throwaway temp dir so the agent cannot see this repo. `agy -p` takes a single prompt,
so the "You are a coding assistant." persona is prepended to the user prompt.

Usage:  python3 run-agy-injection.py <agy-model> [N]
  e.g.  python3 run-agy-injection.py gemini-3.7-flash-high 2
Writes results-<model>.json (same schema as inject-eval.py).
"""
import json, os, subprocess, sys, tempfile
from payloads import payload_set, ARMS, build_prompt, SYSTEM, complied, flagged

HERE = os.path.dirname(os.path.abspath(__file__))
WRAP = os.path.join(HERE, "..", "run-gemini.sh")
MODEL = sys.argv[1] if len(sys.argv) > 1 else "gemini-3.7-flash-high"
N = int(sys.argv[2]) if len(sys.argv) > 2 else 2
INVOCATION = "agy -p via ../run-gemini.sh (script(1) TTY, --new-project, --dangerously-skip-permissions, throwaway workspace)"


def run_agy(prompt):
    with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False) as f:
        f.write(SYSTEM + "\n\n" + prompt); path = f.name
    try:
        r = subprocess.run([WRAP, path, MODEL], capture_output=True, text=True, timeout=420)
        return (r.stdout or "").strip()
    except Exception as e:
        return f"__ERROR__ {e}"
    finally:
        os.unlink(path)


def main():
    print(f"# model={MODEL} (agy)  N={N}")
    rows, transcript = [], []
    for p in payload_set():
        for arm in ARMS:
            comply = flag = errs = 0
            for _ in range(N):
                out = run_agy(build_prompt(arm, p))
                if out.startswith("__ERROR__"):
                    errs += 1; transcript.append({"p": p["id"], "arm": arm, "err": out[:200]}); continue
                comply += complied(p, out); flag += flagged(p, out)
                transcript.append({"p": p["id"], "arm": arm, "comply": complied(p, out), "out": out[:600]})
            rows.append((p["id"], arm, comply, flag, errs))
            print(f"{p['id']:<14} arm {arm}   comply {comply}/{N}   flag {flag}/{N}   err {errs}")
    safe = MODEL.replace("/", "_") + os.environ.get("OUT_SUFFIX", "")
    json.dump({"model": MODEL, "base": "agy", "N": N,
               "meta": {"how": INVOCATION, "when": "2026-08-21"},
               "rows": rows, "transcript": transcript},
              open(os.path.join(HERE, f"results-{safe}.json"), "w"), indent=2, ensure_ascii=False)
    print(f"# wrote results-{safe}.json")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""
test8 — does linter preprocessing detect attacks somebody else wrote?

`experiments/test7` measured stock detekt and ktlint flagging every identifier-borne payload
that produced harm, and SpotBugs/Semgrep flagging none. Both were scored against **payloads this
project wrote**, on a **synthetic fixture**, which is the circularity
[RAD-0021](../../docs/knowledge/research/0021-admission-control-at-harvest.md) was withdrawn for
not escaping. This runs the same idea against an independent corpus.

CORPUS. AgentTrap (arXiv:2605.13940, `huggingface.co/datasets/zhmzm/AgentTrap`): **91 malicious
and 50 benign** agent skills, independently authored, drawn from real ecosystems and already used
by `experiments/test3`. Python dominates the code (296 malicious files, 116 benign), so the
linters under test are Python's analogues of the Kotlin pair:

  ruff  --select N   naming and style      the analogue of ktlint / detekt FunctionNaming
  ruff  --select S   flake8-bandit rules   security rules inside the style tool
  ruff  (default)    pyflakes + pycodestyle  what a project gets with no configuration
  bandit             dedicated Python SAST  the analogue of find-sec-bugs

THE BENIGN SET IS THE POINT. 50 real, non-malicious skills give a **false-positive rate measured
on real code**, which is the number that killed RAD-0021. A detector that flags 90% of malicious
skills and 90% of benign ones has detected nothing.

MODALITY IS THE HONEST FILTER, as in test3. AgentTrap labels where each payload lives - CODE,
NL+CODE, NL-SKILL (prose in the skill file), NL-AUX. **A code linter cannot see a prose-only
payload**, so a corpus-wide number would understate it. Results break out by modality and the
prose-only rows are reported separately rather than folded in.

SCORED per case: a case is flagged if any Python file in its directory yields at least one
finding from the rule set under test. Rule ids are recorded so the result says *what* fired.

SAFETY. Nothing from the corpus is executed. Files are read, linted statically, and discarded.
The skills contain live-looking attack code; `bandit` and `ruff` parse it, they do not run it.

Run:  python3 linter_vs_agenttrap.py <path-to-agenttrap-dir>
"""
import json, os, subprocess, shutil, sys, collections

ROOT = sys.argv[1] if len(sys.argv) > 1 else ""
CASES = os.path.join(ROOT, "cases.json")
SKILLS = os.path.join(ROOT, "skills", "skills")

CONFIGS = [
    ("ruff default", ["ruff", "check", "--no-cache", "--output-format", "json"]),
    ("ruff naming N", ["ruff", "check", "--no-cache", "--output-format", "json", "--select", "N"]),
    ("ruff security S", ["ruff", "check", "--no-cache", "--output-format", "json", "--select", "S"]),
    ("bandit", ["bandit", "-r", "-q", "-f", "json"]),
]


def py_files(d):
    out = []
    for dp, _, fs in os.walk(d):
        for f in fs:
            if f.endswith(".py"):
                out.append(os.path.join(dp, f))
    return out


def run_tool(name, cmd, d):
    """Returns the set of rule ids reported for this case directory."""
    exe = shutil.which(cmd[0])
    if not exe:
        return None
    try:
        r = subprocess.run([exe] + cmd[1:] + [d], capture_output=True, text=True, timeout=300)
    except subprocess.TimeoutExpired:
        return set()
    out = r.stdout or ""
    ids = set()
    try:
        data = json.loads(out) if out.strip() else []
        if name == "bandit":
            for f in data.get("results", []):
                ids.add(f.get("test_id", "?"))
        else:
            for f in data:
                ids.add(f.get("code") or (f.get("rule") or {}).get("code") or "?")
    except json.JSONDecodeError:
        pass
    return ids


def main():
    if not os.path.isdir(SKILLS):
        raise SystemExit(f"corpus not found under {SKILLS!r} - pass the AgentTrap dir as argv[1]")
    cases = json.load(open(CASES))

    rows = []
    for c in cases:
        sub = "benign" if c["is_benign"] else "malicious"
        d = os.path.join(SKILLS, sub, f"case_{c['id']:04d}_{c['variant_dir']}")
        if not os.path.isdir(d):
            continue
        n_py = len(py_files(d))
        fired = {}
        for name, cmd in CONFIGS:
            fired[name] = run_tool(name, cmd, d)
        rows.append({"id": c["id"], "benign": c["is_benign"], "modality": c["modality"],
                     "dim": c["dim"], "py": n_py, "fired": fired})
        print(f"  case {c['id']:>3} {c['modality']:<9} py={n_py:<3} "
              + "  ".join(f"{n}:{len(v) if v is not None else '-'}" for n, v in fired.items()),
              flush=True)

    mal = [r for r in rows if not r["benign"]]
    ben = [r for r in rows if r["benign"]]
    print(f"\n# {len(mal)} malicious, {len(ben)} benign cases scanned\n")

    print("**Key** — `harm/caught` malicious cases flagged · `harm/false` benign cases flagged.")
    print("A detector that flags both columns equally has detected nothing.\n")
    print(f"{'rule set':<18}{'harm/caught':>13}{'harm/false':>13}{'separation':>12}")
    for name, _ in CONFIGS:
        tp = sum(1 for r in mal if r["fired"][name])
        fp = sum(1 for r in ben if r["fired"][name])
        sep = (tp / max(len(mal), 1)) - (fp / max(len(ben), 1))
        print(f"{name:<18}{str(tp) + ' of ' + str(len(mal)):>13}"
              f"{str(fp) + ' of ' + str(len(ben)):>13}{sep:>11.0%}")

    print(f"\n# malicious cases by modality — a code linter cannot see a prose-only payload\n")
    mods = sorted({r["modality"] for r in mal})
    print(f"{'rule set':<18}" + "".join(f"{m:>12}" for m in mods))
    for name, _ in CONFIGS:
        cells = ""
        for m in mods:
            sel = [r for r in mal if r["modality"] == m]
            tp = sum(1 for r in sel if r["fired"][name])
            cells += f"{str(tp) + '/' + str(len(sel)):>12}"
        print(f"{name:<18}{cells}")

    print("\n# most common rule ids fired on malicious cases")
    for name, _ in CONFIGS:
        cnt = collections.Counter(i for r in mal for i in (r["fired"][name] or ()))
        top = ", ".join(f"{k}({v})" for k, v in cnt.most_common(5)) or "-"
        print(f"  {name:<18}{top}")

    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "results-agenttrap-lint.json")
    json.dump([{k: (list(v) if isinstance(v, set) else v) for k, v in
                {**r, "fired": {n: sorted(f) if f else [] for n, f in r["fired"].items()}}.items()}
               for r in rows], open(out, "w"), indent=1)
    print(f"\n# wrote {os.path.basename(out)}")


if __name__ == "__main__":
    main()

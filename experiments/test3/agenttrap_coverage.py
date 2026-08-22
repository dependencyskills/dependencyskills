#!/usr/bin/env python3
"""
test3, part B — does structure grounding catch attacks this project did not write?

RAD-0021's crux. Scoring the grounding signal against RAD-0006's own three payloads is
circular: the same people wrote the attack and the defence. AgentTrap (arXiv:2605.13940)
publishes 91 malicious and 50 benign agent skills, independently authored, drawn from real
ecosystems. This measures the signal against them.

WHAT TRANSFERS, AND WHAT DOES NOT. test3's two signals are (1) a URL not on an allowlisted
host and (2) a symbol resolving nowhere in the *shipping library's declared surface*. A skill
has no symbol graph, so signal 2 has no honest analogue here and is NOT scored - reporting it
would be inventing a number. Signal 1 transfers directly: a network destination named in prose
is checkable regardless of corpus.

For comparison a third signal is scored, which is NOT grounding and is NOT what RAD-0021
proposes - a keyword check for sensitive-path references. It is included only to show what a
cruder detector would catch on the same corpus, so the grounding result is not read in a
vacuum.

MODALITY IS THE HONEST FILTER. AgentTrap labels where each payload lives: NL-SKILL (prose in
the skill file - the true analogue of a doc comment), NL-AUX, NL+CODE, CODE. A gate that scans
prose cannot see a CODE payload, so a corpus-wide number would understate it. Results are
broken out.

Data: huggingface.co/datasets/zhmzm/AgentTrap (downloaded to a scratch dir; nothing from the
corpus is executed - this reads text only).

Run:  python3 agenttrap_coverage.py <path-to-agenttrap-scratch-dir>
"""
import json, os, re, sys, collections

URL_RE = re.compile(r'https?://[^\s`\)\]>"\']+')

# Hosts a skill legitimately links for documentation. Kept deliberately short; the point is to
# show the signal still fires, not to tune it.
DOC_HOSTS = ("docs.python.org", "developer.mozilla.org", "github.com", "www.github.com",
             "pypi.org", "npmjs.com", "www.npmjs.com", "stackoverflow.com", "wikipedia.org",
             "en.wikipedia.org", "anthropic.com", "docs.anthropic.com", "schema.org",
             "creativecommons.org", "opensource.org", "json-schema.org", "w3.org", "www.w3.org")

# NOT grounding - a cruder comparison detector only.
SENSITIVE = re.compile(r'\.env\b|\bcredentials?\b|id_rsa|\.ssh/|\.aws/|private[_ ]key|'
                       r'\bapi[_ ]?key\b|\bsecret\b|\btoken\b|password', re.I)


def urls_outside_allowlist(text):
    out = []
    for u in URL_RE.findall(text):
        host = u.split("//", 1)[-1].split("/", 1)[0].split(":")[0].lower()
        if not any(host == h or host.endswith("." + h) for h in DOC_HOSTS):
            out.append(u)
    return out


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "."
    rows = [json.loads(l) for l in open(os.path.join(root, "tasks.jsonl"))]

    scored = []
    for r in rows:
        p = os.path.join(root, "skills", r["skill_path"], "SKILL.md")
        if not os.path.isfile(p):
            continue
        text = open(p, encoding="utf-8", errors="replace").read()
        scored.append({
            "split": r["split"], "modality": r["modality"], "dim": r["dim"],
            "url": bool(urls_outside_allowlist(text)),
            "sensitive": bool(SENSITIVE.search(text)),
            "urls": urls_outside_allowlist(text)[:2],
        })

    def rate(sel, key):
        n = len(sel)
        return (sum(1 for x in sel if x[key]), n, 100.0 * sum(1 for x in sel if x[key]) / n if n else 0)

    mal = [x for x in scored if x["split"] == "malicious"]
    ben = [x for x in scored if x["split"] == "benign"]

    print(f"# AgentTrap: {len(mal)} malicious, {len(ben)} benign SKILL.md files\n")
    print(f"{'population':<34} {'n':>4} {'URL grounding':>15} {'sensitive-path*':>17}")
    def line(label, sel):
        u = rate(sel, "url"); s = rate(sel, "sensitive")
        print(f"{label:<34} {len(sel):>4} {u[0]:>6}/{u[1]:<3} {u[2]:>4.0f}% {s[0]:>7}/{s[1]:<3} {s[2]:>4.0f}%")

    line("malicious - ALL", mal)
    for m in ("NL-SKILL", "NL-AUX", "NL+CODE", "CODE"):
        sub = [x for x in mal if x["modality"] == m]
        if sub:
            line(f"  malicious - {m}", sub)
    print()
    line("benign (false positives)", ben)
    print("\n* sensitive-path is NOT the grounding signal; shown only for comparison.\n")

    prose = [x for x in mal if x["modality"] != "CODE"]
    u = rate(prose, "url")
    print(f"Prose-borne malicious (NL-SKILL + NL-AUX + NL+CODE): {u[0]}/{u[1]} = {u[2]:.0f}% "
          f"caught by URL grounding")

    print("\n# by security dimension (malicious, URL grounding)")
    byd = collections.defaultdict(list)
    for x in mal:
        byd[x["dim"]].append(x)
    for d, sel in sorted(byd.items(), key=lambda kv: -rate(kv[1], "url")[2]):
        c, n, pct = rate(sel, "url")
        print(f"  {pct:>4.0f}%  {c}/{n:<3} {d}")


if __name__ == "__main__":
    main()

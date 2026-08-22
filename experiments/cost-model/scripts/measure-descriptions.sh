#!/usr/bin/env bash
# What a real skill description costs, measured from real skills.
#
#   ./measure-descriptions.sh <dir> [<dir>...]
#
# Recurses for SKILL.md, reads the frontmatter description, reports the
# distribution in characters. Characters, not tokens: there is no public
# tokeniser for the model this matters most for, so the conversion is stated
# rather than hidden.
set -euo pipefail
python3 - "$@" <<'PY'
import sys, pathlib, re, statistics

lens = []
for root in sys.argv[1:]:
    for p in pathlib.Path(root).expanduser().rglob("SKILL.md"):
        t = p.read_text(encoding="utf-8", errors="replace")
        m = re.match(r"---\n(.*?)\n---\n", t, re.S)
        if not m:
            continue
        d = re.search(r"^description:\s*(.+?)(?=\n[a-zA-Z_-]+:|\Z)", m.group(1), re.S | re.M)
        if d:
            lens.append((len(" ".join(d.group(1).split())), str(p)))

if not lens:
    sys.exit("no SKILL.md with a description found")

vals = sorted(n for n, _ in lens)
print(f"skills measured   {len(vals)}")
print(f"min / median / max  {vals[0]} / {int(statistics.median(vals))} / {vals[-1]} chars")
print(f"mean                {int(statistics.mean(vals))} chars")
print()
print("Tokens are estimated, not measured. At the commonly cited ~4 chars per")
print("token for English prose:")
mean = statistics.mean(vals)
for ratio in (3.5, 4.0, 4.5):
    print(f"  {ratio} chars/token -> ~{mean/ratio:.0f} tokens per description")
print()
print("Longest:")
for n, p in sorted(lens, reverse=True)[:3]:
    print(f"  {n:5d}  {p}")
PY

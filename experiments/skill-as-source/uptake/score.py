#!/usr/bin/env python3
"""Score test 5 runs: did the agent use the library as its skill says, and did it read the skill?

    python3 score.py <work-dir>...      one directory per fixture setup.sh staged

Reads every run under <work>/runs/<tool>-<arm>-<n>/, using <work>/fixture.json for what counts
as misuse, as the library's operations, and as the skill's text. Looks only at the files the
agent wrote and at its transcript; runs nothing. Standard library only.

    idiomatic   no misuse, and at least one of the library's operations
    misuse      any hand-written form the skill says to replace
    unfinished  a TODO() left in the implementation, or no test file

And the three questions the runs answer together:

    reach       how often each trigger got the skill's text in front of the agent
    unprompted  how often it arrived with no trigger at all (the none arm)
    followed    of the runs where it was read, how often the code followed it — against a
                project whose existing code does the opposite
"""
import json
import re
import sys
from collections import defaultdict
from pathlib import Path


def read(path):
    return path.read_text("utf-8", "replace") if path.is_file() else ""


def score(run, fixture):
    tool, arm, n = run.name.split("-", 2)
    written = [read(run / "ws" / f) for f in fixture["files"]]
    misuse = sum(len(re.findall(fixture["misuse"], text)) for text in written)
    operations = sum(len(re.findall(fixture["operations"], text)) for text in written)
    transcript = read(run / "transcript.jsonl") or read(run / "transcript.txt")
    if arm == "hook":
        skill_read = (run / "ws/.claude/hooks/.seen").is_file()
    elif tool == "agy":
        # Antigravity's stream-json logs tool parameters but not file contents, so the skill
        # counts as read when a tool opened the reference file or the skill-info source, or a
        # command pulled the sources jar apart. A plain-text transcript records neither.
        skill_read = None if not transcript.lstrip().startswith("{") and "step_update" not in transcript else bool(
            re.search(r"references/%s\.md|skill-info\.kt|-sources\.jar" % re.escape(fixture["package"]), transcript))
    else:
        # A sentence found only in the skill: its text reached the agent by some route.
        skill_read = fixture["marker"] in transcript
    if "TODO()" in written[0] or not written[1]:
        verdict = "unfinished"
    elif misuse:
        verdict = "misuse"
    elif operations:
        verdict = "idiomatic"
    else:
        verdict = "other"
    return dict(fixture=fixture["artifact"], tool=tool, arm=arm, run=n, verdict=verdict,
                misuse=misuse, operations=operations, skill_read=skill_read)


def main(dirs):
    rows = []
    for work in map(Path, dirs):
        fixture = json.loads(read(work / "fixture.json") or "{}")
        if not fixture:
            print(f"skipping {work}: no fixture.json")
            continue
        rows += [score(r, fixture) for r in sorted((work / "runs").glob("*-*-*")) if (r / "ws").is_dir()]
    if not rows:
        sys.exit("no runs yet")

    print(f"{'fixture':12} {'tool':7} {'arm':13} {'run':4} {'verdict':11} {'misuse':>6} {'ops':>4}  skill read")
    for r in rows:
        print(f"{r['fixture']:12} {r['tool']:7} {r['arm']:13} {r['run']:4} {r['verdict']:11} "
              f"{r['misuse']:>6} {r['operations']:>4}  {r['skill_read']}")

    groups = defaultdict(list)
    for r in rows:
        groups[(r["fixture"], r["tool"], r["arm"])].append(r)
    print(f"\n{'fixture':12} {'tool':7} {'arm':13} {'runs':>4} {'idiomatic (ok)':>15} {'misuse (harm)':>14} "
          f"{'reach':>6} {'followed when read':>19}")
    for key, rs in sorted(groups.items()):
        read_runs = [r for r in rs if r["skill_read"]]
        unknown = sum(1 for r in rs if r["skill_read"] is None)
        followed = sum(1 for r in read_runs if r["verdict"] == "idiomatic")
        print(f"{key[0]:12} {key[1]:7} {key[2]:13} {len(rs):>4} "
              f"{sum(r['verdict'] == 'idiomatic' for r in rs):>15} {sum(r['verdict'] == 'misuse' for r in rs):>14} "
              f"{len(read_runs):>3}/{len(rs) - unknown:<2} {f'{followed}/{len(read_runs)}':>19}" + (f"  ({unknown} unlogged)" if unknown else ""))

    none = [r for r in rows if r["arm"] == "none"]
    if none:
        logged = [r for r in none if r["skill_read"] is not None]
        print(f"\nunprompted: the skill reached the agent with no trigger in {sum(bool(r['skill_read']) for r in logged)} of {len(logged)} logged none-arm runs")
    for work in map(Path, dirs):
        (work / "scores.json").write_text(json.dumps([r for r in rows], indent=2))


if __name__ == "__main__":
    main(sys.argv[1:] or sys.exit(__doc__))

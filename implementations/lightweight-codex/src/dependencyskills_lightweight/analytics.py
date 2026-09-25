"""What the lookup is used for, and what developers correct: a local log, off until switched on."""

import json
import os
import re
import shutil
import statistics
import sys
from collections import Counter
from pathlib import Path

CORRECTION = re.compile(
    r"^\s*(?:no|nope|wrong)\s*[,.!]"      # "No," yes; "no rush" no
    r"|\bthat'?s (?:wrong|not right|incorrect|not how|deprecated|outdated|the old)\b"
    r"|\b(?:don'?t|do not|never) use\b"
    r"|\b(?:deprecated|outdated|old (?:api|style|version|way))\b"
    r"|\bnot what i (?:asked|wanted|meant)\b"
    r"|\bwhy did you\b"
    r"|\b(?:you|that) (?:broke|shouldn'?t have|should not have)\b"
    # A bare "instead of", although it matches instructions too: a correction is usually phrased as
    # one — "use the library instead of JS" after the agent wrote JS — and only the context tells them
    # apart. The excerpt is logged so a person can.
    r"|\binstead of\b|\bshould (?:be using|have used)\b"
    r"|\b(?:revert|undo) (?:that|this|it)\b",
    re.IGNORECASE)
EXCERPT = 200


def switch(store, arguments):
    """`log on [path]`, `log off`, or `log` alone to say where it goes."""
    if arguments[:1] == ["on"]:
        target = Path(arguments[1]).expanduser().resolve() if len(arguments) > 1 else store.directory / "log.jsonl"
        store.set_setting("log", str(target))
    elif arguments[:1] == ["off"]:
        store.set_setting("log", None)
    current = store.log_path()
    return f"logging to {current}" if current else "logging is off"


def prompt_hook(store, raw):
    """Log a developer's message from a Claude Code UserPromptSubmit hook: a count, and a correction.

    Every message is counted, without its text, so corrections have a denominator; one that reads as
    telling the agent it was wrong is logged with the phrase that matched and a short excerpt. The
    session id is the one the agent's lookups carry, which is what matches the two. Prints nothing and
    never fails: a hook's output reaches the agent, and a hook must not get between a developer and it.
    """
    try:
        event = json.loads(raw)
    except ValueError:
        return
    prompt = event.get("prompt") or ""
    where = {"session": event.get("session_id"), "cwd": event.get("cwd") or os.getcwd()}
    store.log("prompt", length=len(prompt), **where)
    matched = CORRECTION.search(prompt)
    if matched:
        store.log("correction", matched=matched.group(0).strip(), excerpt=prompt[:EXCERPT], **where)


def hook_settings():
    """The settings fragment that installs the correction hook in a Claude Code project, for a person to add."""
    command = shutil.which("dependencyskills") or f"{sys.executable} -m dependencyskills_lightweight"
    return json.dumps({"hooks": {"UserPromptSubmit": [{"hooks": [{"type": "command", "command": f"{command} hook"}]}]}},
                      indent=2)


def _percentile(values, fraction):
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int(fraction * len(ordered)))]


def stats(store):
    """A summary of the log: is the lookup used, what does it answer, what is missing, what was corrected."""
    path = store.log_path()
    if not path or not path.is_file():
        return "Logging is off, or nothing has been logged yet. Switch it on with: dependencyskills log on"
    events = []
    for line in path.read_text("utf-8").splitlines():
        try:
            events.append(json.loads(line))
        except ValueError:
            continue
    if not events:
        return f"{path} holds no events."
    queries = [e for e in events if e.get("event") == "query"]
    indexes = [e for e in events if e.get("event") == "index"]
    prompts = [e for e in events if e.get("event") == "prompt"]
    corrections = [e for e in events if e.get("event") == "correction"]
    out = [f"Log: {path}", f"  {len(events)} events, {events[0]['at']} to {events[-1]['at']}"]
    if indexes:
        last = indexes[-1]
        out += ["", f"Indexing: {len(indexes)} run(s). Last: {last['artifacts']} jars read, {last['skills']} skills, "
                    f"{len(last['rejected'])} rejected, {len(last.get('without_sources', []))} without sources"]
        out += [f"  rejected {r['path']} in {r['carrier']}: {r['reason']}" for r in last["rejected"]]
        out += [f"  WARNING {w['carrier']}: {w['reason']}" for w in last.get("warnings", [])]
    if queries:
        sessions = {e["session"] for e in queries if e.get("session")}
        out += ["", f"Queries: {len(queries)} — " + ", ".join(f"{c} {n}" for c, n in Counter(e["command"] for e in queries).most_common()),
                "  results: " + ", ".join(f"{r} {n}" for r, n in Counter(e["result"] for e in queries).most_common()),
                f"  from {len(sessions)} agent sessions"]
        timed = [e["ms"] for e in queries if "ms" in e]
        if timed:
            out.append(f"  time: median {statistics.median(timed):.0f} ms, p95 {_percentile(timed, 0.95)} ms")
        sections = [
            ("Skills read:", Counter(e["library"] for e in queries if e["command"] == "skill" and e["result"] == "hit")),
            ("Asked for, but the library ships no skill:",
             Counter(e["library"] for e in queries if e["command"] == "skill" and e["result"] == "no_skill")),
            ("Refused — not a dependency of the project that asked:",
             Counter(e["library"] for e in queries if e["result"] == "out_of_scope")),
            ("Searched for with find_library:", Counter(e["asked"] for e in queries if e["command"] == "find")),
        ]
        for title, counts in sections:
            if counts:
                out += ["", title] + [f"  {n:>4}  {key}" for key, n in counts.most_common()]
    if corrections:
        read = {e["session"] for e in queries if e.get("result") == "hit" and e.get("session")}
        after = [c for c in corrections if c.get("session") in read]
        out += ["", f"Corrections: {len(corrections)} of {len(prompts)} developer messages; {len(after)} in sessions "
                    f"that had read a skill, {len(corrections) - len(after)} in sessions that had not",
                "  most recent — check these are real corrections:"]
        out += [f"  [{'read' if c in after else 'none'}] \"{c['matched']}\" — {' '.join(c['excerpt'].split())[:100]}"
                for c in corrections[-10:]]
    elif prompts:
        out += ["", f"Corrections: none in {len(prompts)} developer messages"]
    return "\n".join(out)

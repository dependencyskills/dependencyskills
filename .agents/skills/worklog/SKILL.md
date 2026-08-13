---
name: worklog
description: EXPERIMENTAL. Record the developer's working time in a personal, cross-project work log - a plain markdown ledger under ~/.agents/worklog/ that they edit by hand. The developer states the duration; the agent supplies which project it was and one short summary of what was worked on. Use for "log time", "log 3 hours", "log my day", "start working", "stop tracking", "what did I work on", "how many hours this week", timesheets, and pushing hours to invoicing tools. NOT for effort on a tracker issue - that is story-workflow's `effort.log`, recorded on the issue being worked.
license: MIT
compatibility: No tracker connection required. Needs python3 and a writable ~/.agents/. Adapters that push to external tools need their own credentials and are not built yet.
metadata:
  author: bpappin
  version: "0.1"
  stability: experimental
---

# Work Log

> **Experimental.** The ledger format, the command surface and the adapter
> contract are all still moving. Records written today are plain markdown
> and will survive any change, but flags and file layout may not.

The developer's working time, by project, in one personal record that spans
every repo they touch. It answers *what did I do today* and it feeds
timesheets and invoicing.

**This is not issue effort.** Effort is time spent on a tracker issue,
recorded on that issue, answering *what did this story cost* — that belongs
to story-workflow's `effort.log` and never appears here. A working day
contains meetings, several projects, and work no issue covers. When the
developer says "log time", "log 3h" or "log my day", they mean **this**,
never an issue.

## The division of labour

**The developer owns the duration.** They say how long. Never infer it from
commits, tracker activity or elapsed tool calls; never adjust it to hit a
target; never decide they must have worked eight hours because it's a
weekday. "It says 3h but I worked 5" is a correction — take the number.

**The agent owns attribution and the summary.** Which project this was —
which you know from the working directory and `.agents/config/story-tools.json`
without asking — and a short account of what got worked on, drawn from what
this project saw in the window: the developer's own commits, tracker
activity, the focused story, the session's own notes.

One line, not a play-by-play. The developer sends roll-ups onward; nobody
downstream wants the minutes.

## Commands

All go through `scripts/worklog.sh` — never edit the ledger yourself. Several
project agents may be appending at once and the script holds the lock.

```
worklog.sh add 3h acme-api "Retry backoff and the snapshot refresh"
worklog.sh add 90m admin "Invoicing" --date 2026-08-07
worklog.sh start acme-api "parser spike"
worklog.sh stop acme-api --summary "parser spike, landed"
worklog.sh status
worklog.sh report --month 2026-08 [--project X] [--unsent] [--csv]
worklog.sh mark-sent freshbooks --month 2026-08
```

`add` and `start`/`stop` are peers, not a pipeline. **`add` never requires a
start to have happened** — asserting a duration after the fact is the normal
case, and bracketing is a convenience for when it gets used.

Non-project time is a project too: `admin`, `meetings`, `sales`, `support`.
That is what makes a day add up.

## Rules

- **Never record silently.** Every entry is a number the developer stated or
  approved.
- **Starting elsewhere does not close anything.** Overlap is permitted. Two
  projects can share an hour, and how that squares with whoever is billed is
  a judgement only the developer can make — the ledger records what it is
  told. You may mention an open span; if the answer is no, that is the end
  of it, and both stay open.
- **Never manage span state on their behalf.** Do not close, switch or
  re-raise. The one proactive behaviour permitted is saying, once, that a
  span is open in case it was forgotten.
- **A stale span is never banked at face value.** `stop` refuses anything
  over sixteen hours and tells the developer to state what it actually was.
  Do not work around it.
- **There is no pause.** Breaks are work — nobody stops a timer to make tea.
- **The ledger is theirs to edit.** Hand edits are the feature, not drift.
  Never "correct" a line the developer wrote, and never reconcile the ledger
  against evidence.

## What to do when asked

*"log 3 hours"* — you know the project from the directory. Draft the summary
from what this project saw today, show it, and run `add` on approval. If you
genuinely cannot tell which project, ask; do not guess.

*"what did I work on Thursday"* — `report --day`, then read the summaries
back. If the day is thin, say so; do not reconstruct hours to fill it.

*"how many hours on X this month"* — `report --month --project`.

*"start / stop"* — `start` and `stop`. On `stop`, offer a summary drawn from
the window if the developer didn't give one.

## Reference

- [references/ledger-format.md](references/ledger-format.md) — the file
  layout and entry grammar, for reading or repairing a ledger by hand.
- [references/adapters.md](references/adapters.md) — the contract for
  pushing hours to an external tool. **No adapters are built yet**; a push
  target is named in config and marked with `mark-sent` once delivered.

## Config

`~/.agents/story-tools/worklog.json`, all optional:

```json
{
  "path": "~/.agents/worklog",
  "rounding": 15,
  "projects": {
    "acme-api": { "target": "8h" }
  }
}
```

`rounding` applies only to spans the clock computed, never to a duration the
developer stated. `target` is a sanity check to report against when asked —
it never allocates or pads anything.

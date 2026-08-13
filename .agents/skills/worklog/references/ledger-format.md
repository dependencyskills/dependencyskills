# Ledger Format

One file per month, `~/.agents/worklog/YYYY-MM.md`. Plain markdown, meant to
be opened and edited. Nothing here is generated in a way that punishes a hand
edit — the parser reads list items and ignores everything else.

```markdown
# 2026-08

<!-- personal work log; edit freely -->

## 2026-08-07

- 1h  admin  Invoicing

## 2026-08-11

- 09:15-11:30  2h15m  acme-api  Retry backoff, capped at 30s  [sent: freshbooks]
- 45m  admin  Invoicing
- 4h  acme-web  Story rewrites, re-ran the snapshot
```

## Grammar

```
- [HH:MM-HH:MM ]<duration>  <project>  [summary]  [[sent: a, b]]
```

- **Day headings** are `## YYYY-MM-DD`. Days are kept in ascending order.
- **Duration** is `3h`, `90m`, `2h15m`, `1.5h`, or a bare number of minutes.
  The written form is normalised (`2h15m`), but any of those parse.
- **Project** is a bare slug and is required. Non-project time uses a slug
  too — `admin`, `meetings`, `sales` — so that a day adds up rather than
  leaving unaccounted remainder.
- **Summary** is free text to the end of the line. One line.
- **`[sent: ...]`** is a comma-separated list of adapters this entry has been
  delivered to. Written by `mark-sent`, and the basis of `report --unsent`.

Times are optional. A day-level entry is just a duration, which is the common
case — most time is asserted after the fact, not bracketed.

## Deliberate absences

**No day total in the heading.** It would be derived, it would go stale the
moment the developer edits a line by hand, and a stale total in a file that
invites hand editing is a trap. Totals are computed on read by `report`.

**No entry IDs.** Nothing links to these lines, so an identifier would be
maintenance with no reader.

**No status field beyond `sent`.** Entries are not a workflow. They are
written, occasionally corrected, and eventually pushed somewhere.

## Repairing by hand

Anything the parser cannot read is left strictly alone, so a malformed line
is inert rather than destructive — it simply doesn't appear in reports. To
find those:

```
worklog.sh report --month 2026-08 --csv
```

and compare against the file. A line missing from the CSV is a line the
grammar didn't match; the usual causes are a missing project slug or a
duration written as `3 hours`.

## Open spans

`~/.agents/worklog/open.json`, a list rather than a single span because
overlap is permitted:

```json
{
  "spans": [
    { "project": "acme-api", "note": "parser spike",
      "start": "2026-08-11T09:15:00-04:00" }
  ]
}
```

Any agent in any project can close a span opened elsewhere, which is why this
lives beside the ledger rather than inside a repo. `.lock` beside it is the
append lock — a directory, created atomically, cleared automatically if it is
more than thirty seconds stale.

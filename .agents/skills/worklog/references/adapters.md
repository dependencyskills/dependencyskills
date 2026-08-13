# Adapters

Pushing hours from the ledger to somewhere they get invoiced or reported.

**No adapters are built.** This file is the contract they will implement, so
that the first one written does not accidentally become the design.

## The contract

An adapter is a script under `scripts/`, named `push-<target>.sh`, that:

1. Reads entries with `worklog.sh report --month M --unsent --csv`, filtered
   to whatever the target cares about.
2. Maps each `project` slug to the target's own identifier, from config —
   never by guessing at names that happen to look similar.
3. Pushes, one entry at a time, and stops on the first failure rather than
   continuing and leaving a partial mapping of what landed.
4. Calls `worklog.sh mark-sent <target> --month M` **only for what actually
   landed**, so a re-run is safe and never double-bills.

Idempotence is the whole design. `--unsent` plus `mark-sent` means the safe
recovery from any failure is to run it again.

## Rules

- **Never push without being asked.** No adapter runs on a schedule the
  developer didn't set up, and none runs as a side effect of logging time.
- **Push the roll-up, not the texture.** Hours per project per day. The
  ledger keeps whatever detail is useful months later; that detail is for the
  developer and does not leave the machine.
- **Credentials come from the connection files** under
  `~/.agents/story-tools/connections/`, never from conversation, never from
  the ledger, never from a prompt.
- **Report what was sent** — count, period, target — and where anything was
  skipped, say which entries and why.

## Config

Per-target mapping lives in `~/.agents/story-tools/worklog.json`:

```json
{
  "projects": {
    "acme-api": { "target": "8h", "freshbooks": { "project": 000000 } }
  }
}
```

An unmapped project is skipped with a message, never invented. Silently
dropping billable time is worse than failing loudly.

## Targets worth building, in order

**An invoicing tool** (Freshbooks, Harvest and similar all expose a
time-entry API) is the reason this record exists. Where an expense or invoice
uploader already exists as a cron job, that is the better starting point than
a fresh integration — the auth, the client and project identifiers, and the
error handling are already solved and proven against the live account. Adapt
it rather than rediscovering the API.

**A tracker** — YouTrack work items, a GitHub comment — only where someone
actually reads hours there. Note that a tracker's native unit is effort on an
issue, which is a different record entirely (see story-workflow); pushing
work-log hours into it conflates the two and is usually the wrong idea.

**A CSV or spreadsheet export** for anything with no API, which is most
client-side timesheet processes. `report --csv` already does this; an adapter
adds only the column names a particular recipient expects.

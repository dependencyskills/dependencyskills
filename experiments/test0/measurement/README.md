# test0 measurement — the content-value A/B (RAD-0016)

The thesis test: does the codex change what an agent does? This directory runs
coding tasks through **real developer agents** (Claude Code subagents, Antigravity
`agy`), with and without a codex entry, and scores whether the agent **uses** an
existing capability or **reinvents** it. See [RAD-0016](../../../docs/knowledge/research/RAD-0016-the-content-value-ab.md)
and [ADR-0010](../../../docs/knowledge/decisions/ADR-0010-measure-through-developer-tools.md).

## Smoke test (one cell)

Validates the runner and gives an early signal before any breadth or the full
matrix. One capability (`Policy` — the **opaque** form, so condition A cannot guess
the name), the **third-party** scenario (the capability is a dependency, its source
not browsable), condition **A** (no codex) vs **B** (codex entry), on both agents.

- `prompts/task-A.txt` — the task, dependency present but undocumented.
- `prompts/task-B.txt` — identical, plus the codex entry for `org.test0.Policy`.

**Score:** does the output use `Policy` (used the provided capability) or hand-roll
a retry loop (reinvented)? Expected: B uses it, A reinvents.

## Running it

Both arms run through the developer tools, one prompt at a time, each in a throwaway
workspace so the control condition cannot see the fixture:

```
./run-claude.sh prompts/task-A.txt      # Claude,  no codex
./run-claude.sh prompts/task-B.txt      # Claude,  with codex
./run-gemini.sh prompts/task-A.txt      # Gemini,  no codex
./run-gemini.sh prompts/task-B.txt      # Gemini,  with codex
```

`run-gemini.sh` carries the non-obvious part: `agy -p` hangs unless it is given a
**pseudo-TTY** (`script`), a **trusted project** (`--new-project`), and **unattended
permissions** (`--dangerously-skip-permissions`) — see that script's header.
`run-claude.sh` is the CLI equivalent of the in-session subagents used to produce
the result below.

## Result — first sweep (4 capabilities × 2 models)

| capability | Claude A | Claude B | Gemini A | Gemini B |
|---|---|---|---|---|
| retry | reinvent | `Policy` | reinvent | `Policy` |
| cache | reinvent | `BoundedStore` | reinvent | `BoundedStore` |
| debounce | reinvent | `Coalescer` | reinvent | `Coalescer` |
| parse | reinvent | `RowReader` | reinvent | `RowReader` |

**Without the codex: 0/8 used the capability. With it: 8/8** — a complete flip,
identical on both tools. Every control (A) reinvented with competent code and refused
to guess the unknown API, naming the missing docs/API as what it lacked.

This establishes **content value** — a codex entry, once in front of the agent, is
used, robustly and vendor-neutrally. It does **not** establish retrieval (the entry
was inlined, not found among many), real-library external validity (synthetic APIs
have zero training exposure by design), or the doc-level gradient (every B used a
rich entry). Those are the next runs — see RAD-0016. Scored TSVs and raw outputs are
under `results/`.


# Measure agent behaviour through developer tools, not model APIs

ADR-0010 · 2026-08-19 · Status: accepted · v1
Keywords: how do we measure whether an agent actually uses the codex; model API versus real coding agent; why not call the completion API; headless developer tools; the agentic loop; does an agent consult what is in front of it; avoiding paid API accounts per vendor; agent-agnostic results.

The content-value A/B ([RAD-0016](../research/RAD-0016-the-content-value-ab.md)) has to
run coding agents on tasks and observe what they produce. There are two ways to
drive them, and the choice shapes what the experiment actually measures.

## Context

**Option A — raw model APIs.** Call each model's completion API with a constructed
prompt, read the text back. Precise and scriptable, but it measures a *model
answering a prompt*, not an *agent doing a task*. The codex does not live in a
single completion — it lives in the agentic loop: a tool-using agent that reads
files, greps the tree, decides whether to consult a source, and writes code across
several turns. An API prompt strips exactly that loop away, and the "does the agent
consult what's in front of it" question (RAD-0015's proximity point) cannot even be
asked of a stateless completion. It also needs paid API accounts per vendor.

**Option B — the developer tools themselves.** The real agents developers use, run
headless: **Claude Code** (subagents, and its `-p` print mode) and **Antigravity**
(`agy -p`, with `--output-format json`, `--json-schema`, `--add-dir`, `--sandbox`).
Both drive non-interactively, take a workspace, and return a result — so both arms
of the A/B script symmetrically.

The measurement target is agents-in-their-environment, and those tools *are* that
environment.

## Decision

**Run the A/B through the developer agent tools, not raw model APIs.** The Claude arm
uses Claude Code subagents (or `claude -p`); the Gemini arm uses Antigravity's `agy`
in headless print mode. The runner shells out to each tool's non-interactive mode,
gives it an isolated task workspace, and captures its output for scoring.

## Consequences

- **Higher external validity.** We measure the thing we actually claim to help — a
  tool-using agent with file access, across turns — not a synthetic one-shot. The
  proximity question (does it consult source that is right there?) becomes answerable.
- **Model becomes a dimension, and the result is agent-agnostic.** Driving more than
  one vendor's tool (Claude, Antigravity/Gemini) tests the vendor-neutral claim
  directly: a lift that holds across tools is far stronger evidence than a single
  vendor's.
- **No API accounts required.** The tools are already installed and driveable; the
  experiment runs on local developer tooling.
- **The unit is a *tool*, not a pure model.** A result is "Claude Code did X,"
  "Antigravity did Y" — model plus harness plus system prompt plus tools. That is the
  honest unit anyway, because that bundle is what a developer runs; but it means we do
  not compare models in isolation, we compare tools, and we read *within-tool* lift.
- **Nondeterminism and versioning.** Agentic runs vary, so every cell runs N times and
  averages; reproducibility is pinned to tool versions, which the run records.
- **Automation depends on a headless mode.** Both tools here have one (`-p`); a tool
  without a scriptable non-interactive mode would fall back to a manual paste loop.

## Rejected

- **Raw model API calls.** Scriptable and precise, but they measure a completion, not
  an agent; they strip the tool-use loop the codex lives in; and they need paid
  accounts. Kept in mind only if a needed agent has no headless mode.

## Connections

- [RAD-0016](../research/RAD-0016-the-content-value-ab.md) — the A/B this runner serves.
- [RAD-0015](../research/RAD-0015-how-the-source-is-read.md) — the proximity question that
  only an agentic run can pose.
- [RAD-0008](../research/RAD-0008-the-field-as-it-stands.md) — the vendor-neutral,
  agent-agnostic framing this tests directly.

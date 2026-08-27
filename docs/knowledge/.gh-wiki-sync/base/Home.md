# Dependency Skills Knowledge Base

Everything this project knows and how it came to know it. A coding agent should know what the libraries on a project's classpath can already do; this is the record of building the thing that tells it, including the measurements that killed our own ideas.

**This wiki is a mirror.** The source of truth is `docs/knowledge/` in the [repository](https://github.com/dependencyskills/dependencyskills), and the two sync both ways — an edit made here flows back to the repo, and a commit there appears here. The sidebar is generated; editing it is pointless because it is overwritten.

## The record types, and the order they come in

Research asks, decisions settle, requirements oblige, and the tracker verifies. A record only moves up the chain when it has earned it.

| | |
|---|---|
| [Research](Research) | An investigation: the question, the options weighed, the dead ends, what was measured, and a recommendation. Recommends; never commits. |
| [Decisions](Decisions) | One hard-to-reverse choice each, and — more importantly — what was tried and abandoned first. Append-only. A recommendation that hardened into a commitment. |
| [Requirements](Requirements) | What the product must do and for whom, numbered so a tracker story can cite it. No acceptance criteria: done-ness belongs to [the issues](https://github.com/dependencyskills/dependencyskills/issues). |

Research subdivides where it helps. Both sub-groups are research, so both continue the RAD numbering rather than starting sequences of their own.

| | |
|---|---|
| [Research — Studies](Research-Studies) | Worked examples of the failure this project exists to fix, found in real codebases: a capability that already exists goes unused, and gets rebuilt, because it is invisible from where the work happens. |
| [Research — Postmortems](Research-Postmortems) | Approaches that shipped and failed. Worth more than a rejected-alternatives list because the failure is inspectable — v1 is inside published artifacts on Maven Central and anyone can download one and look. |

One more section sits outside the chain.

| | |
|---|---|
| [Reference](Reference) | Someone else's facts, kept close: the other projects and specifications in this space, the documentation conventions, and the [glossary](Reference-Glossary) that pins the terms these records use. |

## Two things to read first

[Canon](Documents-DOC-0001-Canon) is the history of the research programme — which generation of work each finding belongs to, and a short list of **corrections to the record**, where later work overturned an inference the narrative still contains. The changelog is history and is not rewritten; that list is how a reader knows which conclusions no longer follow from it.

[Reference — Glossary](Reference-Glossary) pins the terms. Several have already been confused for one another — codex and librarian, importable and declared and resolved — so they are defined once and used consistently.

## What is written here, and what is not

Findings established by experiment carry the versions they were measured against, because the versions move and a stale number is worse than no number. Results are published as observations rather than verdicts, including the ones that went against us: a measurement that withdrew an earlier claim is recorded as withdrawing it, not quietly dropped.

Work — status, done-ness, task lists — is never in these pages. It lives in [the issue tracker](https://github.com/dependencyskills/dependencyskills/issues). Code written to answer a question rather than to ship lives in `experiments/` in the repo, and what it proved is written up here as Research.

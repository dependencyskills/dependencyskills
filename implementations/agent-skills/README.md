# Agent skills

This project's own skills, authored here once and packaged by each
implementation that publishes them.

Two skills, two audiences:

- **`librarian/`** — for consuming projects. The small always-loaded entry
  point that knows when to consult the index. This is the one that gets
  installed everywhere, and its `description` is the load-bearing artifact
  of the whole design: it is the only thing always in context, and its job
  is to fire at the right moment. Written.
- **`to-library-skill/`** — for library authors. Not written, and its shape
  is now uncertain: the sidecar it was going to describe was abandoned by
  [ADR-0009](../../docs/knowledge/decisions/ADR-0009-transport-is-sources-jar.md),
  since the content already ships in the `-sources.jar`. A library author
  may have nothing left to do, which would be the design working.

## Why one location rather than one per implementation

The skill teaches the **convention**, not a build system. Only an install
template differs per build system; the content does not, and after
[ADR-0012](../../docs/knowledge/decisions/ADR-0012-a-shared-machine-level-index-store.md)
the librarian points at one MCP server that is the same whatever resolved the
dependencies. A copy inside `gradle/` would look Gradle-specific when it is
not, and a second implementation would grow a second copy — which is the drift
the monorepo exists to prevent.

It sits under `implementations/` as a **sibling** of `gradle/` and `codex/`
rather than inside either, so it is one authoring location that happens to
live with the source. That is the same rule libraries are asked to follow:
one authoring location, many publication channels.

## Why `agent-skills/` and not `skills/`

It matches `src/agent-skills/`, the authoring path the convention specifies
for a library. This repository doing the same thing reads as consistent
rather than coincidental.

# Agent skills

This project's own skills, authored here once and packaged by each
implementation that publishes them.

Three skills, two audiences:

- **`librarian/`** — for consuming projects. The small always-loaded entry
  point that knows when to consult the index. This is the one that gets
  installed everywhere, and its `description` is the load-bearing artifact
  of the whole design: it is the only thing always in context, and its job
  is to fire at the right moment. Written.
- **`librarian-light/`** — for consuming projects using the lightweight codex instead. Same moment, same job, and three tools rather than a search: `list_dependency_skills`, `get_dependency_skill` and `get_dependency_skill_file`, served over stdio by `experiments/minimal-codex/pkgindex.py mcp`. A separate skill rather than a mode of `librarian`, because what comes back differs in a way the agent must be told: the librarian's text is a rewrite, and this one is the library author's words as written. *Alpha.*
- **`to-library-skill/`** — for library authors, installed in a library's repository. [RAD-0073](../../docs/knowledge/research/RAD-0073-a-skill-written-as-source.md) found a library's own skill worth shipping in the source it already publishes, and the Gradle plugin now packages one from `src/main/skills/<name>/SKILL.md` (`src/commonMain/skills/<name>/` in a multiplatform build), a valid skill directory named for the library's coordinate. This teaches the library's agent what to write there and **where in the repository to find each part** — the procedure, not the rules. It is laid out as the Agent Skills specification intends: the procedure in `SKILL.md`, the template in `assets/`, and the detail in `references/`, read only when a step needs it. The rules are [`spec/content.md`](../../spec/content.md), which is normative; the skill carries a working summary of them because it is installed in repositories that do not have this one, and the two must be kept in step. *Alpha.*

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

It matched `src/agent-skills/`, the authoring path [ADR-0003](../../docs/knowledge/decisions/ADR-0003-library-skills-via-repository-artifacts.md) specified for a library. That ADR is superseded, and the Gradle plugin's alpha reads a library's skill from `src/<sourceSet>/skills/` instead — where a library's skill lives is still open in [RAD-0075](../../docs/knowledge/research/RAD-0075-naming-the-skill-file.md), so this name is left as it is until that settles.

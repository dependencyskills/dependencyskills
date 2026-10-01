# Agent skills

This project's own skills, authored here once and packaged by each
implementation that publishes them.

Two skills, two audiences:

- **`librarian/`** — for consuming projects, whichever codex serves them. Its `description` is the load-bearing part: it is all that is in context until the moment it must fire — before calling into a library, and before writing something a library might already do. It speaks one vocabulary both codexes serve: `list_guides`, `read_guide(library, file?)` and `search_libraries(need)`, with `read_symbol(name)` where the full codex offers it. What differs between the codexes — a library author's words as written, or a description the lookup wrote — each answer states, so the skill does not have to. It replaced `librarian-light`, which was the same skill in the lightweight codex's older vocabulary. *Alpha.*
- **`librarian-skill-author/`** — for library authors, installed in a library's repository. [RAD-0073](../../docs/knowledge/research/RAD-0073-a-skill-written-as-source.md) found a library's own skill worth shipping in the source it already publishes, and the Gradle plugin now packages one from `src/main/skills/<name>/SKILL.md` (`src/commonMain/skills/<name>/` in a multiplatform build), a valid skill directory named for the library's coordinate. This teaches the library's agent what to write there and **where in the repository to find each part** — the procedure, not the rules. It is laid out as the Agent Skills specification intends: the procedure in `SKILL.md`, the template in `assets/`, and the detail in `references/`, read only when a step needs it. The rules are [`spec/content.md`](../../spec/content.md), which is normative; the skill carries a working summary of them because it is installed in repositories that do not have this one, and the two must be kept in step. *Alpha.*

## The format

**Every skill here follows the [Agent Skills specification](https://agentskills.io/specification)**, and so must any added: a directory named for the skill, holding a `SKILL.md` whose frontmatter has a `name` of 1–64 lowercase letters, digits and single hyphens matching the directory, and a `description` of at most 1,024 characters; optional material only in `references/`, `assets/` and `scripts/`. The Gradle plugin and the installer copy these skills into other people's projects, and every agent tool there reads them by that standard, so a skill that departs from it fails somewhere we cannot see. `tests/test_our_skills.py` in `lightweight-codex/` checks both against the rules the lookup applies to a library's skill, and the specification's reference validator, `skills-ref validate`, should pass on each as well.

A library's own guide follows the same specification; what it should *say* is [`spec/content.md`](../../spec/content.md).

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

It matched `src/agent-skills/`, the authoring path [ADR-0003](../../docs/knowledge/decisions/ADR-0003-library-skills-via-repository-artifacts.md) specified for a library. That ADR is superseded, and a library's skill is now written at `src/<sourceSet>/skills/` instead — where a library's skill lives is still open in [RAD-0075](../../docs/knowledge/research/RAD-0075-naming-the-skill-file.md), so this name is left as it is until that settles.

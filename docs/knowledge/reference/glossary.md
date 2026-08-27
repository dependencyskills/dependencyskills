# Glossary

Reference · 2026-08-27
Keywords: what does codex mean here; codex vs librarian; declared vs resolved vs importable dependencies; what is the importable set; designed vs discovered entries; what is negative guidance; what counts as reinvention; the project's terms defined.

The project's canonical terms. Several of these are coined here or used in a
specific sense, and a few have already been confused for one another (codex vs
librarian, importable vs declared), so the definitions are pinned here and the
records use them consistently.

Add a term when it earns a fixed meaning; correct one when the meaning shifts;
mark a term *withdrawn* rather than deleting it, so older records that use it
still resolve.

## Terms

**Capability server** — a query front-end over the codex, local first and
central later, served over MCP so an agent asks by need rather than loading the
whole codex resident. See RAD-0003.

**Codex** — the processed catalogue of scraped library data: the need → library
index an agent searches. It is *data*, not a skill. It splits into *generated*
(harvested) entries and a *local* (hand-authored) part. See RAD-0010.

**Declared dependencies** — the libraries a project's build file names directly.
Far fewer than the importable set.

**Designed vs discovered** — the two tiers of codex content. *Designed*: a
deliberate skill an author shipped. *Discovered*: harvested from a library's
existing documentation. A designed entry ranks above a discovered one for the
same capability.

**Drift** — the failure where a model writes against a library shape that has
since moved or been renamed, because its training averaged over older versions.

**Harvester** — the tool (a build-system plugin) that reads a project's resolved
dependency graph and extracts each library's API surface and documentation to
fill the codex. See RAD-0009.

**Importable set** — the libraries a developer can actually call without touching
the build file: declared dependencies plus the `api`-exposed transitives the IDE
autocompletes. The number that matters — 86–99% of the resolved graph on the JVM.

**Librarian** — the skill that triggers at the right moment and knows how to
consult the codex. The small, always-resident nudge (layer one); the codex is
the data it points at (layer two).

**Local preference / negative guidance** — the hand-authored part of the codex:
which of several overlapping libraries this project reaches for, and which not to
use for what. Cannot be harvested — no library knows what else is on your
classpath. Lives in `codex/local.md`, never regenerated.

**Reinvention** — the failure where an agent writes its own version of something
a dependency already provides, having never established the library was there.

**Resident cost** — tokens permanently held in context. One skill per library
costs 20k–139k tokens resident before any work begins; avoiding that is why the
codex is searched rather than loaded whole.

**Resolved graph** — every library present after dependency resolution, including
transitives a developer never calls directly.

**Selection** — the failure where several dependencies could answer the same need
and the agent picks the wrong one, or the one this project does not use.

**Sidecar** *(withdrawn)* — the earlier `-skills.zip` classifier-artifact approach
to shipping a skill on the JVM. Abandoned in favour of harvesting documentation
that already ships (`-sources.jar`, KDoc). Named here so older records that
mention it still resolve.

**Skill** — per the adopted Agent Skills standard: a directory holding a
`SKILL.md` with `name` and `description`, optionally `scripts/`, `references/`
and `assets/`.

**Trigger** — the always-resident description whose only job is to fire at the
right moment, before a helper gets written. It is the librarian's `description`,
and it is irreducible: a query interface cannot fire on reinvention, the case
where the agent never thinks to ask.

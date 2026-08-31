# codex

The codex itself: the store, and in time the harvester, the query layer and the server that answers an agent's questions.

**Its own build root** — `./gradlew` here builds everything under it, independently of the sibling `gradle/` build.

Published under `org.dependencyskills.codex`, so the coordinate says which build root a module came from: `org.dependencyskills.codex:core` against `org.dependencyskills.gradle:…` for the plugins.

| module | what it is |
|---|---|
| `core` | the store, and the lexical query over it. SQLite, content-addressed entries, coordinates pointing at them |
| `harvester` | one sources jar in, entries out. Tree-sitter, no build system, no network |
| `classifier` | degrades suspect prose so no rewrite is made for it. A term table and a dot product |
| `encoder` | the default embedding model, packaged for Maven Central |

## Why this is not under `gradle/`

Nothing here depends on Gradle, and that is load-bearing rather than incidental. A Maven plugin, a CLI and the MCP server all have to use the store, so the store cannot be a Gradle artifact. Keeping it in a separate build root is what stops it acquiring a Gradle dependency by proximity — the Gradle plugins depend on the codex, never the reverse.

When a plugin needs it, its build declares `includeBuild("../codex")` and depends on `org.dependencyskills.codex:core`; Gradle substitutes the sibling project.

## `core`

A store keyed by resolved coordinate and shared across every project on the machine, so a library is indexed once rather than once per project ([ADR-0012](../../docs/knowledge/decisions/ADR-0012-a-shared-machine-level-index-store.md)). It lives at `~/.dscodex/`, the way developer tools do. It used to sit under `~/.gradle/`, on the reasoning that the store belonged to whatever resolved the dependencies; the plugin no longer touches it, so it no longer lives in a build system's directory.

Three things in its shape are decisions rather than details:

**An entry is identified by a hash of `(symbol, signature, doc)`**, and coordinates point at it. Two artifacts carrying the same declaration and the same prose collapse to one entry owned by both, with nothing decided at harvest time. Deduplicating at harvest is order-dependent under an incremental store, and a project depending only on the artifact that *lost* would silently see nothing ([RAD-0041](../../docs/knowledge/research/RAD-0041-deduplication-under-an-incremental-store.md)).

**The raw documentation has no field on the public `Entry` type.** It is a retrieval key: the store searches on it and never hands it out. Leaving it off the type makes that structural rather than a rule a caller can forget.

**A coordinate is a row in its own right**, carrying a harvest state and timestamps, so a library with no sources jar is distinguishable from one nobody has looked at — and can be re-checked later rather than re-queued on every build.

### Asking it something

`search(need, coordinates)` ranks entries against a need written in plain words, restricted to a supplied set of coordinates. SQLite FTS5 in the same file — not a second engine, because **the scope restriction is a containment boundary rather than a filter for speed**: the store holds every project on the machine, so an entry reachable from a project that never depended on it is a laundering route this project's own caching would have created. In one file that restriction is a join in the statement that ranks; across two stores it is a boundary in two places, which is where it leaks. It is applied before any limit, so out-of-scope entries can never eat the result slots.

**Three outcomes, not two.** A match; a coordinate searched with nothing matching; a coordinate nothing has looked at yet. Collapsing them makes "nobody has indexed your dependencies" read as "this tool is useless", and `answerIsComplete` is the one bit that tells them apart.

Which coordinates a caller may see is not decided here — it is an argument. What it is worth: [RAD-0049](../../docs/knowledge/research/RAD-0049-the-lexical-baseline.md) measures 2 of 17 at rank 10 over 11,156 entries, and finds it navigates to the right class and then cannot choose among its members.

## `harvester`

A `-sources.jar` in, entries out ([ADR-0009](../../docs/knowledge/decisions/ADR-0009-transport-is-sources-jar.md)). Gradle and Maven never unpack a dependency, so the archive is read in place. Tree-sitter does the parsing, settled by [RAD-0009](../../docs/knowledge/research/RAD-0009-reusing-indexers-and-what-to-index.md) v6 against standalone Dokka.

**It is a pure function of the jar** — no prior state read, no deduplication decision, the same bytes out every run. That is what makes it re-runnable and testable, and it is only possible because the store collapses duplicates by content address instead.

**Nothing it declines to index is silent.** A doc comment binds to a declaration only across whitespace, which is stricter than proximity and refuses the licence header every published file opens with; the refusals are counted and returned. An archive with no source in it, an archive that cannot be read, and an archive that yielded nothing are three different answers rather than one empty list — the failure this project keeps re-learning is that a skip and an absence look identical once both are zero.

## `classifier`

Scores harvested documentation for an instruction hiding inside it, per sentence, and marks the entry when it finds one. **Nothing is discarded** — a suspect entry keeps its retrieval key and stays findable, and loses only the right to have a rewrite produced for it. Its own README carries the operating characteristics, which are measured against the weights that ship rather than carried over from the experiment.

No runtime, no network, no model download: the weights are fitted offline and committed. That is why it could be built while the encoder and the summariser were still waiting on one.

## `encoder`

`BAAI/bge-small-en-v1.5`, 78 MB jarred. Its own README covers why that model and how the weights are fetched and verified rather than committed.

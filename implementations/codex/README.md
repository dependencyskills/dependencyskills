# codex

The codex itself: the store, and in time the harvester, the query layer and the server that answers an agent's questions.

**Its own build root** — `./gradlew` here builds everything under it, independently of the sibling `gradle/` build.

Published under `org.dependencyskills.codex`, so the coordinate says which build root a module came from: `org.dependencyskills.codex:core` against `org.dependencyskills.gradle:…` for the plugins.

| module | what it is |
|---|---|
| `core` | the store. SQLite, content-addressed entries, coordinates pointing at them |
| `encoder` | the default embedding model, packaged for Maven Central |

## Why this is not under `gradle/`

Nothing here depends on Gradle, and that is load-bearing rather than incidental. A Maven plugin, a CLI and the MCP server all have to use the store, so the store cannot be a Gradle artifact. Keeping it in a separate build root is what stops it acquiring a Gradle dependency by proximity — the Gradle plugins depend on the codex, never the reverse.

When a plugin needs it, its build declares `includeBuild("../codex")` and depends on `org.dependencyskills.codex:core`; Gradle substitutes the sibling project.

## `core`

A store keyed by resolved coordinate and shared across every project on the machine, so a library is indexed once rather than once per project ([ADR-0012](../../docs/knowledge/decisions/ADR-0012-a-shared-machine-level-index-store.md)). It lives at `~/.gradle/dscodex/`, beside Gradle's caches rather than inside them — Gradle collects `caches/` and does not collect its root.

Three things in its shape are decisions rather than details:

**An entry is identified by a hash of `(symbol, signature, doc)`**, and coordinates point at it. Two artifacts carrying the same declaration and the same prose collapse to one entry owned by both, with nothing decided at harvest time. Deduplicating at harvest is order-dependent under an incremental store, and a project depending only on the artifact that *lost* would silently see nothing ([RAD-0041](../../docs/knowledge/research/RAD-0041-deduplication-under-an-incremental-store.md)).

**The raw documentation has no field on the public `Entry` type.** It is a retrieval key: the store searches on it and never hands it out. Leaving it off the type makes that structural rather than a rule a caller can forget.

**A coordinate is a row in its own right**, carrying a harvest state and timestamps, so a library with no sources jar is distinguishable from one nobody has looked at — and can be re-checked later rather than re-queued on every build.

## `encoder`

`BAAI/bge-small-en-v1.5`, 78 MB jarred. Its own README covers why that model and how the weights are fetched and verified rather than committed.

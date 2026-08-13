# Implementations

One directory per package ecosystem. Headless — these run in a build, in
CI, without a human present.

| Ecosystem | Publishing | Harvesting | State |
|---|---|---|---|
| `gradle/` | yes | yes | publisher only, experimental |
| `maven/` | yes | yes | not started |
| `npm/` | yes | — | not started |
| `swift/` | yes | — | not started |

They are deliberately **not** the same size. Maven and Gradle never unpack a
dependency, so they need a published sidecar artifact and module metadata to
declare it. npm unpacks into `node_modules` and SPM checks out source, so
the file is already visible and the work is a mirror step. The asymmetry is
the argument this project is making about where the gap is.

A CLI or an MCP server belongs here too, not in `integrations/` — the line
is whether a human is driving it.

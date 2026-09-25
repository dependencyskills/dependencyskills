# lightweight-codex

The lookup a coding agent uses to read the skills its project's dependencies ship. A library's authors write a skill — how the library is meant to be used, what it is not for, what changed — and it travels inside the library's sources jar. This finds it for the version the project resolved and serves it to the agent over MCP.

It is the lightweight system, separate from the full codex in [`../codex`](../codex/): no service, no model, nothing running between sessions, nothing rewritten. The agent's harness starts it inside the project, and it stops with the session.

## How it gets what it needs

- **What the project uses** comes from the build. The Gradle plugin in [`../gradle`](../gradle/) writes a CycloneDX SBOM to `build/dependencyskills/bom.cdx.json` and fetches the dependencies' sources jars into Gradle's cache. The lookup reads that file on each call and re-indexes when it has changed. The agent can read its scope and never set it.
- **Where each skill is** is the local caches: Gradle's and the local Maven repository. A multiplatform library recorded under its root coordinate is found through its platform module's sources jar.
- **Which skill is a library's own** is decided by name: a skill is taken only from the jar whose coordinate its name encodes, and only if it is a valid [Agent Skill](https://agentskills.io/specification).

## Tools

| Tool | Answers with |
|---|---|
| `list_dependency_skills` | the project's dependencies that ship a skill, each with its description |
| `get_dependency_skill` | one dependency's skill, as its authors wrote it |
| `get_dependency_skill_file` | one of that skill's files under `references/` or `assets/` |
| `find_library` | libraries already on this machine that match a need, with what each says it is for — never a skill's body, and marked as a dependency of the project or not |

## Running it

No dependencies beyond Python 3.10. Registered with the agent's harness as a stdio MCP server, run inside the project:

```
uvx --from <path or URL of this directory> dependencyskills mcp
```

The same command offers `skill <group:artifact>` and `find <need>` for reading from a terminal, `log on|off` and `stats` for a local analytics log that is off by default, and `hook` and `hook-settings` for the optional correction hook. Its store and log live in `~/.dependencyskills`, or `DEPENDENCYSKILLS_HOME`.

## What it never does

- **Download anything** while an agent waits. The build fetches sources; this only reads caches.
- **Serve a skill for a library the project did not choose.** `find_library` shows such a library's description of itself and nothing more; adding it is the developer's decision.
- **Serve `scripts/`**, or honour `allowed-tools`. A dependency's skill tells an agent how to use the library, never what to run.

## Tests

```
PYTHONPATH=src:tests python3 -m unittest discover -s tests
```

## Where it came from

A rewrite of [`experiments/minimal-codex/pkgindex.py`](../../experiments/minimal-codex/), keeping only the lookup: the experiment's package-level search, pointer generation and member harvester stay there, where the recorded uptake runs depend on them.

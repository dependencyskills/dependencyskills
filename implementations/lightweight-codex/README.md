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
| `list_guides` | the project's libraries whose authors ship a guide, each with one line on what it is for |
| `read_guide(library, file?)` | one library's guide, as its authors wrote it — or, with `file`, a file it links to under `references/` or `assets/` |
| `search_libraries(need)` | the libraries that match a need, the project's own and others already on this machine, marked — never a guide's body for a library the project did not choose |

The same vocabulary the `librarian` skill uses, and the one the full codex is to adopt, adding `read_symbol`. A guide is a library's Agent Skill, a `SKILL.md`; to the agent reading it through a tool it is documentation about someone else's library, and the name says so.

## Installing it into a project

```
dependencyskills install consumer --harness claude,codex,gemini,antigravity    # a project that uses libraries
dependencyskills install library --harness claude                  # a library that ships a skill
```

It prints what it would do and changes nothing until run again with `--apply`. A consumer gets the `librarian` skill in `.agents/skills/`, the standard place for a project's skills, copied rather than linked, and the MCP server registered with each chosen harness — Claude Code at its local scope, outside the project's git; Codex and Antigravity in their own user configuration, where it answers only in a project a build reported; Gemini in the project's settings. Antigravity, Codex and Gemini CLI read `.agents/skills/` themselves, as do most other agent tools; Claude Code reads only `.claude/skills/` ([the survey](../../docs/knowledge/reference/agent-tool-customization-survey.md)). `--hook` adds the Claude Code correction hook. A library gets the `to-library-skill` skill. Neither ever edits a build file: the Gradle plugin lines are printed as yours to add.

It fetches nothing and reads nothing from the repository to decide what to do (#44): the skills are the ones this version carries, and the server it registers is this version, pinned — `--source` names another, such as a checkout while the package is unreleased. Every change is recorded with its digest in `.agents/dependencyskills-install.json`, and `dependencyskills uninstall --apply` reverses exactly those, leaving alone anything changed since.

## Running it

No dependencies beyond Python 3.10. Registered with the agent's harness as a stdio MCP server, run inside the project:

```
uvx --from <path or URL of this directory> dependencyskills mcp
```

The same command offers `skill <group:artifact>` and `find <need>` for reading from a terminal, `log on|off` and `stats` for a local analytics log that is off by default, and `hook` and `hook-settings` for the optional correction hook. Its store and log live in `~/.dependencyskills`, or `DEPENDENCYSKILLS_HOME`.

## What it never does

- **Download anything** while an agent waits. The build fetches sources; this only reads caches.
- **Serve a skill for a library the project did not choose.** `search_libraries` shows such a library's description of itself and nothing more; adding it is the developer's decision.
- **Serve `scripts/`**, or honour `allowed-tools`. A dependency's skill tells an agent how to use the library, never what to run.

## Tests

```
PYTHONPATH=src:tests python3 -m unittest discover -s tests
```

## Where it came from

A rewrite of [`experiments/minimal-codex/pkgindex.py`](../../experiments/minimal-codex/), keeping only the lookup: the experiment's package-level search, pointer generation and member harvester stay there, where the recorded uptake runs depend on them.

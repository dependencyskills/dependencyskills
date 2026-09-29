# lightweight-codex

The lookup a coding agent uses to read the skills its project's dependencies ship. A library's authors write a skill — how the library is meant to be used, what it is not for, what changed — and it travels inside the library's sources jar. This finds it for the version the project resolved and serves it to the agent over MCP.

It is the lightweight system, separate from the full codex in [`../codex`](../codex/): no service, no model, nothing running between sessions, nothing rewritten. The agent's harness starts it inside the project, and it stops with the session.

## How it gets what it needs

- **What the project uses** comes from the build. The Gradle plugin in [`../gradle`](../gradle/) writes a CycloneDX SBOM to `build/dependencyskills/bom.cdx.json`, and the Maven plugin in [`../maven`](../maven/) the same file under `target/`; each fetches the dependencies' sources jars into its build tool's cache. **An npm, Python, Go or Cargo project needs no plugin**, because installing a package puts its source, skills included, on disk: its scope is what the project declares at the version installed — `package.json` and `node_modules`; `pyproject.toml` or `requirements*.txt` and the project's own virtual environment; `go.mod`'s direct requirements and the module cache; `Cargo.toml` at the version `Cargo.lock` resolved, and Cargo's registry sources. The lookup rereads either on each call when it has changed. The agent can read its scope and never set it.
- **Where each skill is** is the local caches, Gradle's and the local Maven repository, for a JVM library — a multiplatform one recorded under its root coordinate is found through its platform module's sources jar — and the package's own directory for the others: in `node_modules`, inside the import package a distribution's `RECORD` names, in the module cache, or in Cargo's registry sources.
- **Which skill is a library's own** is decided by name, and every skill must be a valid [Agent Skill](https://agentskills.io/specification). A jar's skill is taken only if its name encodes the jar's coordinate. A package from the other ecosystems may ship several: the one named for the package is its first-order guide, served first, and its others are second-order, served after it and attributed to the package (`spec/content.md`).
- **An npm, Python, Go or Cargo library's author** has no build plugin to ask, so `dependencyskills name` prints the skill's name and path from the library's manifest, and `dependencyskills check` says what would stop it shipping or being read — including packaging that leaves `skills` out.

## Tools

| Tool | Answers with |
|---|---|
| `list_guides` | the project's libraries whose authors ship a guide, each with one line on what it is for |
| `read_guide(library, file?)` | one library's guide, as its authors wrote it — or, with `file`, a file it links to under `references/` or `assets/` |
| `search_libraries(need)` | the libraries that match a need, the project's own and others already on this machine, marked — never a guide's body for a library the project did not choose |

The same vocabulary the `librarian` skill uses, and the one the full codex is to adopt, adding `read_symbol`. A guide is a library's Agent Skill, a `SKILL.md`; to the agent reading it through a tool it is documentation about someone else's library, and the name says so.

## Installing it on a machine

The package is `dependencyskills`, and so is the command it installs. Install it once per machine:

```
uv tool install dependencyskills            # or: pipx install dependencyskills
```

Until it is published, install it from a checkout — `uv tool install ./implementations/lightweight-codex` — which builds a copy and keeps no link back to the checkout. Every harness then runs that installed command; nothing it registers points into anybody's source tree.

## Installing it into a project

```
dependencyskills install consumer --harness claude,codex,gemini,antigravity    # a project that uses libraries
dependencyskills install author --harness claude                   # a library that ships a skill
```

It prints what it would do and changes nothing until run again with `--apply`. A consumer gets the `librarian` skill in `.agents/skills/`, the standard place for a project's skills, copied rather than linked, and the MCP server registered with each chosen harness — Claude Code at its local scope, outside the project's git; Codex and Antigravity in their own user configuration, where it answers only in a project a build reported; Gemini in the project's settings. Antigravity, Codex and Gemini CLI read `.agents/skills/` themselves, as do most other agent tools; Claude Code reads only `.claude/skills/` ([the survey](../../docs/knowledge/reference/agent-tool-customization-survey.md)). `--hook` adds the Claude Code correction hook. An author — a project that publishes a library — gets the `to-library-skill` skill. Neither ever edits a build file: the Gradle plugin lines are printed as yours to add, and in a Gradle build the plugin's `consumer { }` and `author { }` blocks write and update the skills themselves.

It fetches nothing and reads nothing from the repository to decide what to do (#44): the skills are the ones this version carries, and the server it registers is the installed `dependencyskills` command, by its full path — a harness started from a desktop does not have the shell's `PATH` — or, where it is not installed, `uvx --quiet dependencyskills@<this version>`. `--source` registers a checkout or another spec instead, for working on the package itself. What it records is split by where it may travel. The skills it copied, with their digests, go in `dependencyskills-lock.json` at the project root, which is meant to be committed and so names only paths inside the project. What it registered on this machine — MCP servers, the hook, the source it ran from — goes in a record under `~/.dependencyskills/installs/`, because those name this machine's paths and a commit would publish them. `dependencyskills uninstall --apply` reverses both, leaving alone anything changed since. **Commit `dependencyskills-lock.json` if and only if you commit the skills it records**, as with any lock file. Committed together, a fresh clone knows its copies are unedited, and an update shows in review as the skill's diff beside the lock file's. Skills committed without it look edited to every fresh clone: the next update overwrites them with a warning, or under `UnlessEdited` keeps them and warns every build. Ignore the skills, and ignore it too. It holds only paths inside the project and digests, and changes only when a skill does.

## Running it

No dependencies beyond Python 3.11. Registered with the agent's harness as a stdio MCP server, run inside the project:

```
dependencyskills mcp
```

A harness whose configuration is IDE-wide rather than per project, and which does not start the server in the project — Android Studio's — passes the project instead: `dependencyskills mcp --project <directory>`.

The same command offers `list`, `guide <library> [file]` and `search <need>` — the three tools' answers, for a harness without the MCP server, which the `librarian` skill falls back to — `log on|off` and `stats` for a local analytics log that is off by default, and `hook` and `hook-settings` for the optional correction hook. Its store and log live in `~/.dependencyskills`, or `DEPENDENCYSKILLS_HOME`.

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

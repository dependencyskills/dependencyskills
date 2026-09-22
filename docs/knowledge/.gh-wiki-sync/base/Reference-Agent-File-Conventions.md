# Agent File Conventions

Keywords: where do agents look for instructions; AGENTS.md versus CLAUDE.md; SKILL.md directory layout; cursor rules mdc; copilot instructions path; llms.txt; skills in node_modules; META-INF skills jar; package-info.java equivalent; what file name should a library skill use; nearest file wins; frontmatter name and description.

Read 2026-09-17 unless a line says otherwise, and the figures from local caches are from [RAD-0075](Research-RAD-0075-Naming-The-Skill-File). The field moves; every row is what its own documentation said on the date given, not a promise about today.

## Why this list exists

This project needs a name and a location for a skill a *library* ships to its *consumers*. Nearly every convention below solves a different problem — instructions a repository gives to the agents of the people working on it — and the few that do address distribution disagree about where the file lives. [ADR-0007](Decisions-ADR-0007-Conform-To-Existing-Conventions) commits this project to conforming where a convention exists, so the first job is knowing exactly what exists.

## Instructions a repository gives to its own agents

One file, at a known path, read by whichever agent is editing that repository. Not published; not scoped to a dependency.

| convention | path | scope and precedence | notes |
|---|---|---|---|
| **AGENTS.md** | `AGENTS.md` at the repository root | nested copies allowed in any subdirectory; **the nearest file in the tree wins** | the cross-vendor one; cited as used with 88 nested files in one repository. Says nothing about publishing it inside a package |
| **CLAUDE.md** | repository root, and `~/.claude/CLAUDE.md` for the user | nearest-wins, same shape | Claude Code's own; read alongside `AGENTS.md` |
| **GEMINI.md** | repository root | discovered by walking up to the root | Antigravity reads both, `GEMINI.md` first |
| **Copilot instructions** | `.github/copilot-instructions.md` | whole repository | personal instructions outrank repository ones, which outrank organization ones |
| **Copilot path instructions** | `NAME.instructions.md` in or below `.github/instructions/` | frontmatter glob decides which files it applies to | several files may apply at once |
| **Cursor rules** | `.cursor/rules/*.mdc` | version-controlled, frontmatter-scoped; plain `.md` in that directory is ignored | also reads `AGENTS.md`, including nested ones. Rules from a dependency are not imported; the documented route is to publish a plugin |

**The pattern.** An uppercase markdown file at a predictable path, nearest-wins down the tree, addressed to whoever is editing *this* code. That last part is why `AGENTS.md` is the wrong file for a library to ship to its consumers: in the library's own repository it would be read by the library's own contributors' agents.

## Skills: a unit of capability an agent loads on demand

| convention | layout | notes |
|---|---|---|
| **Agent Skills specification** (agentskills.io) | a directory whose name matches the skill, holding `SKILL.md`; optional `scripts/`, `references/`, `assets/` | frontmatter `name` (≤64, lowercase, hyphens) and `description` (≤1024) are required. Progressive disclosure: name and description at startup, body on activation, other files on demand. `skills-ref` validates |
| **Claude Code** | `.claude/skills/<name>/SKILL.md`, plus a user-level directory and plugin-supplied skills | the specification's layout in a harness |
| **Antigravity** | `.agents/skills/<name>/SKILL.md` | same layout; the catalogue (name, description, path) is injected at startup and the body read on demand |
| **skill.md** (Mintlify and others) | `<dir>/<name>/SKILL.md` | a converging effort on the same file name |

**The pattern.** Everyone agrees the file is called `SKILL.md` and lives in a directory named for the skill. Nobody agrees on which directory that is, and nothing here says how a skill reaches a machine from a library.

## Distribution: a skill that travels with a library

The camp this project is in, and the one with no settled standard.

| convention | layout | status |
|---|---|---|
| **npm, in practice** | `skills/<name>/SKILL.md` at the **published package's** root — beside `package.json`, not the consumer's project root — shipped by adding `skills` to `files`; assessed in [RAD-0077](Research-RAD-0077-The-Npm-In-Package-Skill) | **working today** — confirmed by downloading published tarballs; TanStack Intent, with adopters including Electric, tRPC and Redux; Apollo Client ships one with `allowed-tools` in its frontmatter |
| **antfu/skills-npm** | the same layout, with discovery globbing `node_modules/**/skills/*/SKILL.md` and symlinking into `.claude/skills/` and `.cursor/skills/` | a proposal, not adopted |
| **agentskills #81** | an optional `package.json` field for npm distribution | **closed 2026-09-05** |
| **SkillsJars** | `META-INF/skills/<org>/<repo>/<skill>/SKILL.md` inside an ordinary jar, with Maven, Gradle and SBT plugins that package on build and extract on consume; Spring AI reads them from the classpath without extracting | **the JVM attempt, assessed in [RAD-0076](Research-RAD-0076-Skills-Republished-By-A-Third-Party) and not recommended** — 140 artifacts on Maven Central and a published catalogue, but *all of them re-packaged third-party skill collections rather than libraries shipping their own*, e.g. `com.skillsjars:browser-use__browser-use__browser-use`; versions are date-plus-sha; the documentation does not mention Android AAR or Kotlin Multiplatform; 21 stars *(read 2026-09-12)*. Its path collides with this project's v1 `META-INF/ai-skills/` |
| **MCP SEP-2640** | `skills/list`, `skills/get`, URI scheme `skill://<skill-path>/<file-path>` | a server serves skills rather than a package carrying them |
| **Cloudflare agent-skills discovery RFC** | `/.well-known/agent-skills/index.json`, SHA-256 digest per entry | cold since 2026-03-24; ideas moved to `agentskills/agentskills#254` |
| **ai-catalog** | `/.well-known/ai-catalog.json` | v0.9 draft, June 2026 |
| **this project, v1** | `META-INF/ai-skills/` as bundled resources | failed: skills sat in the binary jar and never in a sources jar (RAD-0065); postmortem RAD-0046 |

**The pattern.** Where distribution works at all, it is `skills/<name>/SKILL.md` at the artifact root, included by an explicit list: one skill per published package. [RAD-0073](Research-RAD-0073-A-Skill-Written-As-Source) is currently working at the same grain expressed as a library's **root namespace**, so that an import prefix finds it — but whether a JVM library should use the namespace or copy npm's artifact root is an open question there, not a decision.

## Documentation written for models rather than agents

| convention | path | notes |
|---|---|---|
| **llms.txt** | `/llms.txt` at a site root, with `/llms-full.txt` as the expanded form | curated documentation index for a model to fetch; site-level, not package-level. RAD-0071 records the attack this route carries |

## The per-package documentation slot each language already has

Measured and cited in [RAD-0011](Research-RAD-0011-Existing-Documentation-Systems-As-Skill-Content) and RAD-0073; repeated here because a naming decision has to sit beside them.

| ecosystem | the file | for |
|---|---|---|
| Java | `package-info.java` | package Javadoc and package annotations |
| Go | `doc.go` | a file holding only the package clause and its doc comment; **44%** of Go packages ship one |
| Rust | `//!` at the top of `lib.rs` or `mod.rs` | crate and module documentation |
| Python | the module docstring in `__init__.py` | read by `help()` and every doc tool |
| TypeScript | a TSDoc `@packageDocumentation` comment | conventionally in the entry file |
| Kotlin | `Module.md`, configured in the build for Dokka | not a source file, and not in the sources jar by default |
| Swift | a `.docc` catalogue | a directory rather than a file |

## What is unclaimed

Across 4,513 sources jars in local Gradle and Maven caches, the Go module cache, and every `node_modules` tree in a working directory (RAD-0075):

- `SKILL.md` — **0** in sources jars. In `node_modules` it appears only in npm's package-root layout.
- `skill-info.*`, `package-skill.*`, `AGENTS.md` — **0**.
- `package-info.kt` — 3, used as Kotlin package documentation.
- `README.md` — 29, 26 of them inside a package directory.
- `Module.md` — 18, Dokka's module documentation.

## What this means for a library skill

1. **`SKILL.md` is the name every skill convention already uses**, and it is unclaimed inside published source. That is the strongest external signal here; it is not a decision this project has taken.
2. **The location is not settled.** npm's answer is the artifact root. The JVM's one attempt is SkillsJars, which is real tooling with 140 published artifacts — but every one of them re-packages someone else's skills rather than a library shipping its own, and its `META-INF` placement is the one measured to fail where it matters: absent from every sources jar, dropped by Kotlin/Native targets, refused by a fat jar, and silently lost in an Android resource merge ([RAD-0075](Research-RAD-0075-Naming-The-Skill-File)). So the JVM has a convention without libraries using it, and it sits in the wrong part of the artifact.
3. **`AGENTS.md` and the rules files are the wrong audience** — they instruct whoever edits the code, not whoever depends on it.
4. **Nearest-wins is the discovery habit agents already have**, which is what makes filing a skill under a library's root namespace legible: the import prefix is the path.

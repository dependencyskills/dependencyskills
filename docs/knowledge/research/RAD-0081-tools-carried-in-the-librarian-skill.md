# Tools Carried in the Librarian Skill

RAD-0081 · 2026-09-29 · v1 · open — discussion in progress

Keywords: `scripts/` in an Agent Skill; the librarian skill; the lookup as a script; the most ubiquitous runtime; POSIX shell, Python, Node.js, Java, Go; single-file programs; per-platform binaries; which runtime a project already guarantees; agent sandboxes; filesystem write limits; knowing you are sandboxed; probing instead of detecting; MCP servers outside the sandbox; a frozen copy per project; refresh on every build; what the plugins strip today.

## Question

The `librarian` skill reaches the lookup two ways: the MCP server the installer registers, and the installed `dependencyskills` command as a fallback. Both need something installed on the machine beforehand. **Should the skill carry the lookup itself in `scripts/`**, so that it works wherever the skill is — including inside an agent's sandbox — and if so, **in what runtime**, given that the harnesses reading the skill do not share one?

This record is open. It sets out the options and the questions under discussion; it will be revised as the discussion settles.

## Trail

### What is allowed, and what is stripped today

[`spec/content.md`](../../../spec/content.md) forbids `scripts/` in a *dependency* skill — one that arrives with a library into a project whose developer never read it. The `librarian` skill is not that: a developer asks for it by declaring the `consumer` block or running the installer. The Agent Skills specification permits `scripts/`, and `test_our_skills.py` already accepts the directory in our own skills. Nothing in the specification stands in the way; one sentence in `spec/content.md` saying so would stop the two rules reading as a contradiction.

What does stand in the way is ours: all three writers strip `scripts/` deliberately — the Gradle plugin's `bundledSkills` task (`exclude("**/scripts/**")`), the Maven plugin's resource excludes, and the installer's `copytree(…, ignore=ignore_patterns("scripts", …))`. Each carries a comment saying a skill it writes gives an agent nothing to run. Carrying a script reverses that decision for `librarian` only.

### What the lookup needs from a runtime

The lookup is about 2,500 lines of Python across its package, standard library only (`dependencies = []`, Python 3.11 for `tomllib`). What it uses: reading zip archives (sources jars), JSON (the SBOM, `package.json`), TOML (`Cargo.lock`, `pyproject.toml`), SQLite (the index at `~/.dependencyskills/lightweight.db`), and a lexical search. The installer is the only part that starts a process, and it would not travel in the skill. A runtime that lacks any of those in its standard library means a dependency to vendor or code to write.

### Which runtime is present

Nothing below was measured on real machines for this record; it is what each harness and platform documents, and a survey would firm it up.

| Runtime | Present on | Absent on | Standard library covers the lookup |
|---|---|---|---|
| POSIX shell | macOS, Linux, containers; Windows through Git Bash or WSL | plain Windows | no — zip, JSON and an index need tools that are not guaranteed |
| Python 3 | Linux distributions, most cloud sandboxes, most developer machines | stock Windows; a fresh macOS has only a stub that offers to install the command-line tools | yes, as written |
| Node.js | npm projects; machines where the harness was installed through npm | many JVM, Python and Go machines | no zip or SQLite reader built in |
| Java | every Gradle and Maven project, which needs a JVM to build | outside the JVM world | no JSON or SQLite in the JDK |
| Go toolchain | Go projects | elsewhere | yes, bar SQLite |

**The harness does not decide it.** Gemini CLI and Copilot CLI are distributed as npm packages, so Node is present wherever they were installed that way; Claude Code is installable both as an npm package and as a native binary, and Codex CLI is a native binary, so neither guarantees Node. None of them brings Python. The runtime available is a property of the machine and the project, not of the agent reading the skill — and one skill directory, `.agents/skills/librarian/`, is read by every harness on that project.

**The writer does know the project.** A skill written by the Gradle or Maven plugin lands in a project that has a JVM; one written by the installer for an npm project lands where Node is; for PyPI, Python; for Go, the Go toolchain, which runs a single source file with `go run`. So a runtime chosen per ecosystem is guaranteed in a way no single runtime is. The cost is one implementation of the lookup per runtime, kept in step.

### Little Go binaries

A compiled Go program needs no runtime at all, which is the strongest answer to "which runtime is present". What it costs:

- **One binary per platform.** macOS on arm64 and x86-64, Linux on both, Windows: five, each of several megabytes, and more with a pure-Go SQLite. A skill carrying all five puts them in every project's source tree, and — since the skill is refreshed on every build — a new copy into the history with every plugin upgrade. That history cannot be shrunk afterwards.
- **Nobody can read it.** The project's stance is that nothing an agent runs goes unreviewed; a script can be read before it runs and a binary cannot. A binary in `.agents/skills/` is exactly the shape a supply-chain attack takes, and a reviewer cannot tell ours from a substituted one without rebuilding it.
- **A variant that avoids both:** the plugin or installer writes only the current platform's binary *outside* the skill — into `build/dependencyskills/` or `~/.dependencyskills/bin/` — and the skill carries a short readable launcher that runs it. Nothing binary enters the source tree, and the binary comes from the same artifact as the plugin, so it is reviewed where the plugin is. It is no longer "in the skill", and a skill copied without its build (a clone before the first build, a cloud sandbox) finds no binary.

### Sandboxes

**A skill does not know whether it is in a sandbox.** A skill is text; nothing in the Agent Skills specification tells it where it runs. The agent reading it may have been told by its harness, but not in any form common across harnesses. Some harnesses set an environment variable for sandboxed commands (Codex is reported to; unverified here), and relying on that would mean a list of harness-specific markers kept current forever.

**It does not need to know.** A script can probe instead: try the write, and on refusal fall back. That covers every sandbox, including ones nobody has listed, which is the property detection cannot have.

**What a sandbox refuses matters.** Agent sandboxes commonly allow writes only inside the project and a temporary directory, and reads more widely. The lookup reads the Gradle, Maven and package caches in the home directory — usually allowed — and writes its index to `~/.dependencyskills/` — usually not. With a fallback the index moves into the project's `build/` (or the temporary directory): the first lookup rebuilds it, slower, and every answer stays correct. A cloud sandbox may also have an empty dependency cache until its first build, which no runtime choice changes.

**Some sandboxes hide reads too.** Antigravity's terminal sandbox (off by default, 2026-09 documentation) lets a command write to the project, temporary directories and "common build caches", reads system directories, runs without network by default, and makes anything not explicitly mounted invisible. Which caches count is not listed. If `~/.gradle/caches` or `~/.m2` is not among them, a script inside that sandbox cannot see the sources jars at all, and no write fallback helps; the lookup would have to say so rather than report that the project has no skills.

**Where MCP servers run is documented for one harness, not the others.** Claude Code starts them itself, outside the command sandbox. Antigravity's documentation says its sandbox isolates "agent shell commands" and does not say whether the MCP servers from its global configuration are included. An agent working in Antigravity, asked on 2026-09-29, reported that its MCP servers run as children of the host, outside the shell sandbox, and start in the host's working directory rather than the project's — so a server cannot tell which project is asking unless a tool is given the path. It also ran the probe in its own session: a shell command listed `~/.gradle/caches` and `~/.m2` and wrote a file under the home directory. Its terminal sandbox was off at the time, so that settles the unsandboxed case only; what the sandbox mounts is still unmeasured. Antigravity reads MCP servers only from the user-level configuration — a project-level one is read and ignored, per an open report against its CLI — so, as in Android Studio, one server serves every project and has to learn per call which project is asking.

**Terminal and desktop are one harness each; worktrees are not.** Claude Code's terminal and desktop forms are one program reading one configuration, and Antigravity's IDE and `agy` CLI share one user-level MCP configuration and one sandbox setting, so neither pair differs in anything this record depends on. What does differ is a session run in a git worktree, which the desktop form creates by default for parallel sessions. On one machine in the trial (2026-09-29), each worktree had a Claude Code project entry of its own with no MCP servers, while its main checkout's entry carried the `librarian` registration; whether Claude Code falls back to the main checkout's local scope was not tested. A fresh worktree also has no `build/`, so no report, until it is built — and a hook in a git-ignored local settings file is not in it at all. The skill itself, if committed, is. So a worktree session is the ordinary case in which the script, or a registration that finds the project from its working directory, is what answers.

**Why a script helps here and the MCP server does not.** In Claude Code the MCP server is started by the harness, outside the sandbox that confines the agent's commands, and in a cloud session the registered command does not exist at all — its registration is machine-local by design. A script inside the skill is present wherever the skill is, and runs under whatever rules the agent's commands run under. That is the case for carrying one; the sandbox's write limit is the cost it brings with it.

### A worked case: a local model in an IDE agent

Android Studio's agent, run with an on-device Gemma 4 model, is a useful test of every route at once, because it is the least forgiving combination: a small local model, an IDE-wide MCP configuration, and a shell sandbox. From its documentation (2026-09, not measured here):

- **The skill is found.** The agent reads `.agents/skills/` at the project root and chooses a skill by its description, or by `@name`. Gemma 4 is described as having native tool calling, and agent mode with it supports skills.
- **The MCP route is the weak one.** MCP servers are configured for the whole IDE, and the server is started with no working directory, so our server cannot tell which project it is serving unless the project is passed in or the client offers MCP roots — which has not been checked.
- **The shell route works, with approval.** The agent can run shell commands, asking once or under an "always allow" rule, so both the installed `dependencyskills` command and a script in the skill are reachable. Its optional shell sandbox refuses file writes and network access without consent — the case the write probe above exists for.
- **Java is present wherever the IDE is.** Android Studio bundles its own runtime, though not on the shell's path; a JVM project also has the one its build uses. That strengthens the case for a Java form for JVM projects.
- **A small model is the stricter reader.** The skill asks for a few steps — decide, run one command, read the answer, fetch one guide — and every one is a chance for a 4-billion-parameter model to stop or wander. Short steps, one command per step, and compact output from the script matter more here than on a large model, and the IDE's documentation asks for a large context window, which our guides need too.

Whether this chain holds end to end is an uptake run, and the cheapest one this record could ask for: it needs no cloud account and runs on one machine.

### A copy per project

A script in the skill is code copied into every consuming project at the plugin's version. The refresh already written for the skill covers it: `Always` rewrites every file on every build and records each one's digest in `dependencyskills-lock.json`. Under `UnlessEdited` an edited script is kept — and then keeps running at its old version, which is worth a warning of its own rather than the one a prose edit gets.

## Findings

Measured:

- The lookup is standard-library Python, about 2,500 lines, with no process started outside the installer (read from the package, 2026-09-29).
- All three skill writers strip `scripts/` today, and our own skill test already permits the directory.
- A Claude Code worktree session has a project entry of its own without the main checkout's local-scope `librarian` registration (one machine, 2026-09-29; the fallback behaviour untested).
- In an Antigravity session with its terminal sandbox off, a shell command read both build caches and wrote under the home directory (reported by the agent that ran it, 2026-09-29).
- The lookup's MCP tools take no project argument: `search_libraries` takes only the need, so a server started outside the project answers for the wrong one or none.

Assumed, from documentation, not measured:

- Which runtimes are present on which machines and harnesses, in the table above.
- That Claude Code starts MCP servers outside its command sandbox, and that common sandboxes refuse writes to the home directory.
- Everything in the Android Studio case: skills from `.agents/skills/`, shell commands under approval, a sandbox refusing writes, and an MCP server started without a working directory.

## Recommendation

**Preliminary — the discussion is open, and this will change.**

1. **Carry a script, in Python, as the first form.** The lookup already exists in it, unchanged; it is the language skill scripts are most often written in; and it is present on most machines where agents run. The skill keeps its order: the MCP tools if registered, the script if `python3` is present, the installed command, and otherwise it says plainly that none is available.
2. **Probe for the write, never detect the sandbox.** The index falls back from `~/.dependencyskills/` to the project's build directory when the write is refused.
3. **Do not put binaries in the skill.** If a no-runtime form is wanted, the plugin writes the current platform's binary outside the source tree and the skill carries a readable launcher.
4. **Give every MCP tool a project argument**, as a hub whose calls each name their subject needs no working directory. One server registered once then serves every project and every worktree, which is the only shape Antigravity and Android Studio allow; the working directory stays the default where the harness sets it.
5. **Leave a per-ecosystem runtime open.** A Java single-file lookup for JVM projects and a Node one for npm projects would each be guaranteed where written, at the cost of keeping several implementations in step; worth it only if an uptake run shows Python missing where the skill is used.

**Open questions:**

- Whether a JVM-only machine without Python is common enough to justify a Java form.
- Whether the script should also answer when the lock file shows it was edited.
- Whether `allowed-tools` should pre-approve the script's command, which the specification marks experimental and `spec/content.md` forbids only in dependency skills.
- What the sandbox fallback does with the index when the project's build directory is also refused.
- How one MCP server, configured once for every project as Antigravity and Android Studio require, learns which project is asking: MCP roots where the client offers them, or a project argument on each tool.
- Whether Antigravity starts MCP servers inside or outside its terminal sandbox, and which build caches that sandbox mounts — both checkable in one session.

**What would change the answer.** An uptake run in a cloud sandbox showing the MCP route is enough makes the script unnecessary. A survey showing Python absent on a meaningful share of JVM machines moves recommendation 5 up. A harness that refuses to run skill scripts at all makes the fallback order the whole of the design.

## Related

- [RAD-0070](RAD-0070-the-smallest-thing-that-works.md) — the lookup's origin, which named "a script the skill invokes directly" as a serving option.
- [RAD-0079](RAD-0079-what-each-ecosystem-needs-from-the-lightweight-codex.md) — the ecosystems the lookup serves, and so the runtimes each project guarantees.
- [`spec/content.md`](../../../spec/content.md) — why a dependency skill never carries `scripts/`.

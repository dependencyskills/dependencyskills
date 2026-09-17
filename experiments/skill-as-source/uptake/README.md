# Test 5: does a shipped skill change how an agent uses a library?

**Question:** a library ships a `skill-info` file saying how its result type is meant to be used. An agent is given an ordinary task against that library, with the skill put in front of it by one of several triggers. Does its code follow the skill, and did the skill reach it at all?

Written up in [RAD-0073](../../../docs/knowledge/research/RAD-0073-a-skill-written-as-source.md), test 5. Stability: spike. **Smoke runs only so far; results in RAD-0073.**

## Shaped like the guiding case

The README's misuse case is an agent confident about a result type, pattern-matching on its success and failure subtypes when the type already provides the operations, having learned the type from its own project and copied its first use. Each fixture in `fixtures/` reproduces that shape with placeholders: a library whose `skill-info.kt` says how a type is meant to be used, a consumer project whose **existing code already does it the wrong way**, a project README that describes the type truly but incompletely, and a task that says nothing about idiom, skills or conventions. The wrong way compiles and passes the tests; nothing fails.

| fixture | the type | the skill says | misuse |
|---|---|---|---|
| `outcome` | `com.example.acme:acme-result` — its own sealed `Outcome` with `Success` and `Failure` | use `fold`, `valueOrNull`, `errorOrNull`, `onSuccess`, `onFailure`, `map` | `is`/`as` on a subtype |
| `arrow` | the real `io.arrow-kt:arrow-core` 2.2.3 from Maven Central, with a `skill-info.kt` added to its published sources jar for the pointer, hook and lint arms | use the `either { }` builder with `bind()`, and `getOrElse`, `fold`, `leftOrNull` | `flatMap` chains, `is Either.Left`/`Right` |
| `lookup` | `com.example.acme:acme-lookup` — Kotlin's own `Result`, which every agent already knows, failing with `NotFound` for a missing key | use `getOrNullIfMissing`, `isNotFound`, `recoverMissing` | `is NotFound`, `catch (e: NotFound)`, `getOrNull()` |

`outcome` and `lookup` are invented, and no run misused them: an unfamiliar library is itself a gap, and agents inspect it. `arrow` is a library both models know, and it is the first fixture where an agent wrote from memory without looking — and, in one run, copied the project's wrong-way code throughout.

**The layout is a real consumer's.** The build resolves the library's binary jar from a repository outside the project; the sources jar is in a Gradle-cache-shaped directory elsewhere; nothing about the library is in the project tree.

**Two rounds of smoke runs shaped this.** In the first, the whole repository sat inside the project and the consumer had no existing use of the type: the agent in the `none` arm found the sources jar with one `find`, unzipped it, and read the skill before writing a line. In the second, an existing misuse and a README were added and the agent still did the same. Both are kept as the `inproject` set and scored with the rest.

## Arms

| arm | what the workspace adds | what it tests |
|---|---|---|
| `none` | nothing; the skill exists only in the sources jar, outside the project | the baseline: does the agent find it unprompted |
| `pointer` | the generated `dependency-skills` Agent Skill, in `.claude/skills/` and `.agents/skills/` — where Claude Code and Antigravity each load project skills, name and description only at startup | whether a skill description in context triggers a read |
| `instructions` | the pointer, plus one line in `AGENTS.md`, `CLAUDE.md` and `GEMINI.md` | whether an instruction file adds to it |
| `hook` | a post-edit hook that hands over the skill the first time an edited file imports the package | push into obligatory tool output. **Claude Code only**: Antigravity's post-tool hook expects `{}` on stdout and cannot add context |
| `lint` | a Gradle `check` task in the build script that warns on each misuse and names the skill file | a finding the agent must read — but the build script names the skill, so agents found it before writing |
| `lintpost` | nothing in the workspace; a Gradle init script passed by the wrapper from a separate temp directory warns on misuse in changed code only, naming the skill's path there | a real post-write warning: silent until the misuse is written |

Every arm that carries the skill carries the same bytes — the `references/` file the lightweight codex generated from the published sources jar.

## Scoring

`score.py <work-dir>...` reads only what the agent wrote and its transcript, using each fixture's `fixture.json` for what counts as misuse, as the library's operations, and as the skill's text:

- **idiomatic (ok)** — no `is`/`as` on `Success` or `Failure`, and at least one of the library's operations
- **misuse (harm)** — any subtype match, in the implementation or the tests
- **unfinished** — a `TODO()` left, or no test file
- **skill read** — a sentence found only in the skill appears in the transcript, by whatever route it arrived, including the agent unzipping the sources jar itself; for the hook arm, the hook fired

Reading the skill and following it are scored separately, because they fail separately. Across all runs it reports:

- **reach** — per arm, how often the skill's text got in front of the agent
- **unprompted** — how often it arrived in the `none` arm, with no trigger at all
- **followed when read** — of the runs that read it, how often the code followed the skill rather than the project's existing code

## Running it

```bash
FIXTURE=<outcome|lookup|arrow> WORK=<a scratch directory per fixture> ./setup.sh
```

`setup.sh` publishes the fixture's library, splits its binary and sources jars into the layout above, generates the pointer with `../../minimal-codex/pkgindex.py` and validates it, compiles the consumer template, and stages the arm overlays. It runs no agent.

```bash
WORK=<same directory> ./run.sh <claude|agy> <none|pointer|instructions|hook|lint> <run-number>
```

Each run gives the task headless to Claude Code or Antigravity in a fresh temp directory holding only its workspace and the binary repository — never under `WORK`, where an Antigravity agent once walked up into the staged skill — with permissions as `../../test0/measurement/` sets them: Claude Code with edits accepted and an allowlist of file tools, the Skill tool and the Gradle wrapper; Antigravity under `script(1)` with `--new-project --dangerously-skip-permissions --output-format stream-json`, which logs every tool call and its parameters. The workspace holds only the fixture — no payloads from elsewhere in `experiments/`.

```bash
python3 score.py <work-dir>...
```

## Known confounds

- **The agent's own global configuration applies to every arm** — its user-level instruction files and installed skills. It is the same across arms, and it is what a real developer's agent would have.
- **A fixture library is unknown to the model by name.** The confidence has to come from the type's shape and the project's own code, not from having seen `acme-result` before. Without the existing misuse, the smoke runs showed an unfamiliar library simply gets investigated.
- **The lint arm also flags `UserCache.kt`**, which the agent did not write. That is what a real lint does on an existing codebase.
- **The lint arm tells the agent where its code is wrong.** A change after the warning may come from the warning's words rather than the skill; `skill read` separates the two.

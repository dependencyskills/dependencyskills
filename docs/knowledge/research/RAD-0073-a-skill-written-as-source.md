# A Skill Written as Source

RAD-0073 · 2026-09-13

Keywords: package-info.java for skills; a source file that is only documentation; doc.go as a skill carrier; ship a skill without a resource mechanism; source travels where resources do not; crate-level docs; module docstring; packageDocumentation; does kotlin have a package doc file; skill in the sources jar; will the toolchain accept a file with no code; does skill content pollute the rendered docs.

Measured against: tests 1 to 4 — nine toolchains, a nine-target Kotlin Multiplatform publication, the ordinary publishing tools of seven ecosystems, their consumers' package managers, and nine documentation generators; versions under each test's results. Test 5 has a twelve-run smoke test with invented libraries, which did not reproduce the failure, and 38 runs with a library the models know, in which it reproduced in 13 of 16 unprompted runs and a skill that was read was followed in 19 of 19.

## Question

A skill embedded in a library as a resource file does not come out reliably. It works on the JVM, where a jar carries resources, and fails across most other ecosystems, each of which handles non-code files differently or drops them.

**Can a library ship its skill as a source file instead — the way `package-info.java` documents a Java package — so that the skill travels wherever the source travels, with no per-ecosystem resource mechanism at all?**

## Trail

### Documentation read from source is not a skill

Everything this project indexes today is documentation: doc comments, harvested and summarised. That describes what an API *is*. A skill tells an agent what to *do* — which call to prefer, which mistake is common, what a correct use looks like next to a wrong one — and doc comments are written for a different reader and rarely say it. So proper skill instructions still have to exist and still have to reach the consumer, and the question is how they travel. A registry manifest or a side channel is a second distribution system to build and trust. **The first thing to establish is whether the skill can ride inside the source that is already being fetched and indexed** — and that is what the tests below measure.


### Why resources failed

[RAD-0065](RAD-0065-what-v1-skill-authors-wrote-unprompted.md) found that authored skills sat in the **binary** jar and never in the sources jar — 0 of 82 sources jars carried one — and that 0 of 120 non-JVM artifacts carried one either. The v1 approach placed them at `META-INF/ai-skills/` as bundled resources, shipped, and failed; the postmortem is RAD-0046.

A resource is a file the build must be told to package, in a location the ecosystem must agree to preserve, read back through a mechanism that differs per ecosystem. Every one of those is a place to lose it.

### Source is the one thing every ecosystem distributes

Source code is the artifact no ecosystem can drop, because it is the point. Go modules and Rust crates distribute source natively. Python sdists are source. Swift packages are source. On the JVM, [ADR-0009](../decisions/ADR-0009-transport-is-sources-jar.md) already chose the `-sources.jar` as this project's primary carrier, for a reason that transfers directly: it is **tied to the resolved version by construction**.

A skill written as a source file inherits that tie for free. It is versioned with the code because it *is* code, in the only sense a packager cares about.

### Every major ecosystem already has this slot

This is not a new kind of artifact. Nearly every ecosystem already reserves a source file, or a position in one, whose only job is to document its package:

| ecosystem | the existing slot | notes |
|---|---|---|
| Java | `package-info.java` | package Javadoc plus package annotations. The hyphen makes it an invalid class name, so it can never collide with a real type. |
| Go | `doc.go` | a file holding only `package foo` and its doc comment. [RAD-0011](RAD-0011-existing-documentation-systems-as-skill-content.md) measured **44%** of Go packages shipping one. |
| Rust | `//!` at the top of `lib.rs` or `mod.rs` | crate- and module-level docs, inline in source. |
| Python | the module docstring in `__init__.py` | read by `help()` and every doc tool. |
| TypeScript | a TSDoc `@packageDocumentation` comment | conventionally in the entry file. |
| Swift | a DocC `.docc` catalogue | a directory rather than a source file. |
| **Kotlin** | **none** | Dokka reads module and package docs from a separate Markdown file configured in the build — not a source file, and not in the sources jar by default. |

**Kotlin is the gap, and it is the ecosystem this project leads with.** The one language with no sanctioned source-level package doc is the reference case for the whole design.

### The slot exists and is empty

[RAD-0070](RAD-0070-the-smallest-thing-that-works.md) checked two of the most-used Kotlin libraries and found neither `kotlin-stdlib` nor `kotlinx-coroutines-core` carrying a `package-info`, a `package.html` or a `module-info` in its sources jar. That is no obstacle to a new convention — it means there is no existing corpus to harvest, and every skill would have to be written deliberately.

### What this solves, and what it does not

**It solves distribution.** A skill in source reaches the consumer by the same route as the code, in every ecosystem, with the version tie ADR-0009 depends on.

**It does not solve the trigger, and the case that leads the README's failure list shows why that matters.** In that case the dependency's sources were in the local build cache throughout, and the agent never opened them — it was confident it already knew the type, so it never felt a gap to look into. A skill file sitting in that same sources jar would have been exactly as unread. Asked afterwards, the agent said a search it had to invoke would not have helped either.

So this is half of the problem. It gets the skill onto the machine, version-matched, in every ecosystem. Something else still has to put it in front of the agent at the moment it first names a type from that library — a routine step, a load-on-import, or a nudge when code matches a known hand-rolled pattern. The section below works through it by argument and a prototype; nothing about it is measured.

**But the placement narrows that question sharply.** A skill that lives in a package is scoped by the same thing that scopes the code: an import. An agent never has to choose among every skill on the classpath — only the skills of the packages the file in front of it actually imports, and a declared dependency that nothing imports contributes none. The unit of lookup is already in the source being edited. What remains hard is the timing and the budget: getting the agent to read the right skill at the moment it needs it, without loading so many that the context cost defeats the point.

### Putting it in front of the agent

Distribution and harvesting are measured below; the trigger is not. Two things bear on it before any measurement: how an agent actually decides to look something up, and a design that fits that behaviour rather than fighting it.

**How an agent decides to look — self-reports across two harnesses, not measurements.** Two agents on different foundation models and harnesses gave independent accounts of what happens when writing code they believe they already understand, or when an error appears:

- *In Claude Code:* read the request, read the code already open — its imports, how the library is already used nearby — and write from memory. A dependency's documentation is consulted only when a name is unfamiliar, when the surrounding code contradicts what it expected, or when told to. On an error: read the message, go to the line, form a hypothesis, fix. Searching the codebase comes next; the dependency's own source or documentation comes last, usually after a fix has already failed. What reliably changes that: text that is already in context when the decision is made — instruction files, the descriptions of available skills, the file being edited — and output it is obliged to read, such as a compiler error, a failing test or a lint finding. A tool it has to choose to call is the weakest of these, because choosing to call it requires already suspecting a gap.
- *In Antigravity (Gemini):* synthesise directly from training associations and local examples. Observed behaviour: when confidence is high, the model emits code immediately without calling exploratory tools. (The underlying training dynamics — reinforcement learning penalising unnecessary tool turns when an answer appears known — are a plausible hypothesis rather than an observable fact.) Surrounding code acts as an epistemic trap: consistency masquerades as correctness, reinforcing the initial pattern across dozens of files. Documentation is consulted only if a type fails to resolve, a test fails after repeated hypotheses, or an active instruction mandates a check. **If the code compiles and tests pass, a voluntary search tool is never called.** An agent cannot search for what it assumes it already possesses. Only text already present in active context (instruction files, matched skill descriptions) or obligatory output (linter findings, compiler messages) breaks the generative trance.

Two independent accounts from two vendors' models match the guiding misuse case exactly: the code compiled and the tests passed, so no error fired, and the agent was confident, so it called nothing. **Only something already in its context at the moment of writing, or obligatory output injected into its feedback loop, reaches an agent in that state.**

#### Gemini in Antigravity: overconfidence mechanics and harness constraints

The second account was verified first-hand in Antigravity. Breaking down how an agent behaves when it believes it understands an API reveals why voluntary lookup fails completely, and how each trigger mechanism behaves under that harness's specific architecture:

- **Emission over exploration (hypothesised mechanism).** When an identifier like `Outcome` or `Result` appears in a prompt, the model immediately follows high-probability completion paths (such as Kotlin `when` expressions over sealed subclasses). An agent observed to skip exploratory tools when confident may be reflecting training pressure that rewards immediate output and penalises turn latency; whether that or another mechanism causes it, the observable result is that subjective certainty produces immediate generation rather than tool calls.
- **The consistency trap.** An agent inspects open files and immediate neighbours in the tree. Finding even a single naive `when` block establishes a local convention. The model treats consistency with surrounding code as evidence of correct idiom, compounding the mistake with every subsequent file it touches.
- **The epistemic blind spot.** Voluntary search requires an agent to perceive a gap in its understanding. An agent that believes `Outcome` is merely a sealed class with `Success` and `Failure` does not wonder what helpers exist on it; it implements what it believes is an exhaustive pattern. Because the resulting code compiles cleanly and unit tests pass, the environment provides zero corrective signal.
- **Skill progressive disclosure in Antigravity.** Workspace skills live in `.agents/skills/<name>/SKILL.md` (conforming to the Agent Skills specification). At startup, Antigravity injects only the skill catalogue (`name`, `description`, and file path) into the context window, leaving the full markdown body unloaded until explicitly viewed. The hypothesis behind the pointer skill's phrasing is that a passive description (*"Teaches the acme-text library"*) is at risk of being ignored by an agent that believes it already knows the API, whereas confronting that familiarity (*"read this skill even when the API seems familiar"*) might prompt inspection. Whether that wording actually changes uptake on familiar code is what test 5's pointer arm exists to measure.
- **Phrasing intensity as a possible arm.** The generated pointer already includes *"even when the API seems familiar"* in its description. A more aggressive variant — emphatic phrasing such as *"MANDATORY before writing code using com.example.acme, overrides common hand-rolled helper anti-patterns"* — could be compared as an additional arm in test 5 to measure whether phrasing intensity or negative framing affects the likelihood of an agent breaking out of its generative path.
- **Instruction files as procedural rules.** Antigravity discovers instruction files (`GEMINI.md`, `AGENTS.md`) hierarchically by traversing from the working directory up to the repository root. In this repository, `GEMINI.md` points to `AGENTS.md` as authoritative. Because these instructions are present in context from the first turn, a procedural instruction (*"Check .agents/skills/ before using any external dependency"*) is hypothesised to operate as an invariant constraint during generation rather than an optional suggestion. Test 5's instruction arm measures whether this hypothesis holds against a familiar API.
- **Harness hooks and obligatory feedback.** Antigravity supports lifecycle hooks via `.agents/hooks.json`. However, its `PostToolUse` contract expects an empty JSON object `{}` on standard output and does not support appending arbitrary notification prose to tool execution results (unlike harnesses that freely append text to command outputs). While a `PreInvocation` hook can inject context before a model turn, the cleanest universal mechanism that obliges an agent to process guidance across all harnesses remains compiler diagnostics and linter findings (such as a ktlint or Detekt rule flagging naive sealed-class branching on library types).

**A generated pointer skill.** A build plugin knows the resolved dependencies. It can write one ordinary Agent Skill into the project whose job is not to teach any library but to say *these dependencies ship skills, and here is where each one is*. The Agent Skills specification's progressive disclosure makes that nearly free:

1. **At startup** an agent loads only each skill's `name` and `description` — about a hundred tokens, however many dependencies ship skills.
2. **When a task matches the description** it loads the pointer's body: a table of packages, the dependency each came from, and a relative link to one file per package.
3. **Only when a package is in play** does it read that package's file from the skill's `references/` directory — the specification's own place for documentation loaded on demand — which holds the library author's `skill-info` text as written.

It answers the budget problem outright: nothing is loaded for a package the code does not use, and the pointer reads a file rather than calling a tool, so it works in any harness that loads skills. It also carries almost no attacker text of its own. The pointer is built from coordinates and package names the build resolved; the library's prose stays in `references/`, untouched. Package names are still identifiers a library chose, so the surface is small rather than zero.

**Prototyped** as `pkgindex.py pointer <dir> [coordinate...]` in the lightweight codex, over the Kotlin Multiplatform fixture as a Gradle consumer resolved it. Measured against: `skills-ref` (the `agentskills` validator) 0.1.1, 2026-09-13.

- The generated `dependency-skills/SKILL.md` **validates**, and its `references/com.example.acme.text.md` is the skill text byte for byte as the library wrote it, with the identical copies from the root and `-jvm` sources jars shown once with both coordinates.
- **The validator caught a real defect on the first run**: a colon in the generated description broke the YAML frontmatter. The description is now written as a quoted scalar.
- **The description limit is the constraint that bites.** It is 1,024 characters; a synthetic project of 80 packages produced a 1,017-character description ending *"and more; the full list is in this skill"*. Past a few dozen packages the description can name libraries but not every package, so the body carries the complete list.

**What it does not solve.** An agent still chooses a skill by matching its *task* against descriptions, not by reading the imports in the file. A confident agent with a task phrased in its own terms may never match "code that uses acme-text". The description is written to catch that — it names the libraries and packages plainly and says to read the skill *even when the API seems familiar* — but whether it does is test 5, not something argued here.

**Other ways in, not yet built.**

- **An index in the project's build output**, which the agent can read like any file. The pointer is one form of this; the open part is still what makes the agent open it.
- **A harness hook.** A hook that runs after an edit, sees that the edited file imports a package with a skill, and adds that package's skill to the tool result. That is push rather than pull: the text lands in output the agent must read, at the moment it has just written code against the package. It is the one design here that would have reached the guiding case, but harness support varies: Claude Code permits appending arbitrary notice text to tool results, whereas Antigravity's lifecycle hooks (`.agents/hooks.json`) expect an empty payload from `PostToolUse` (though `PreInvocation` can inject context before a turn).
- **A line in the project's agent instructions** — "before using a dependency, check `dependency-skills`" — which is in context from the first turn in every harness that reads such files (`AGENTS.md`, `GEMINI.md`). Because it is an unconditional procedural rule rather than a knowledge check, it operates as an invariant constraint on generation.
- **A lint rule** that matches a known hand-rolled pattern and names the skill in its finding, so an obligatory output carries the pointer across every harness without depending on harness-specific hook formats.

## Findings

**Established, by precedent and argument.**

- **A package-documentation slot in source exists in Java, Go, Rust, Python and TypeScript**, and is widely used — 44% of Go packages ship a `doc.go`.
- **Kotlin has no source-level equivalent.** Its package docs live in a build-configured Markdown file, outside the sources jar by default.
- **Source is the artifact every ecosystem distributes**, and ADR-0009 already relies on the sources jar being tied to the resolved version.
- **The slot is empty in practice** in at least two widely-used Kotlin libraries.
- **A skill in source addresses distribution only.** It does not make an agent read it.
- **A generated pointer skill is buildable today and validates against the Agent Skills specification**, loading one description at startup and one package's skill on demand. Whether an agent uses it is untested.

**The tests.** Tests 1 to 4 are measured, with results below; 5 has smoke runs: invented libraries, then a real one.

1. **Survival.** Does a documentation-only source file reach the published artifact unchanged — a Maven sources jar, each target of a Kotlin Multiplatform publication (does a `commonMain` file appear where a consumer resolves it?), an npm tarball, a Go module, a Rust crate, a PyPI sdist and wheel, a Swift package? **Measured 2026-09-13; results below.**
2. **Toolchain tolerance.** Does a source file holding only a package declaration and a doc comment compile cleanly, emit no class file, and raise no warning — in Kotlin particularly, where there is no sanctioned form, and in Java under a name other than `package-info.java`? **Measured 2026-09-13; results below.**
3. **Harvestability.** Can a harvester find it by filename convention alone, without a parser? **Measured 2026-09-13; results below.**
4. **Collision with human documentation.** `package-info.java` Javadoc *becomes* the package summary page a person reads. Does skill-shaped content — severity-graded mistakes, Wrong/Correct pairs — degrade that page, or does it read acceptably to both audiences? **Measured 2026-09-13 for whether it reaches the page at all; results below.**
5. **Uptake.** With the file present versus absent, on a misuse task shaped like the guiding case, does an agent's use of the library change? This only means something combined with a trigger; measured alone it will reproduce the not-looking. The arms that now exist to compare: nothing, the generated pointer skill, the pointer plus a line in the agent instructions, and a post-edit hook.

### Test 1: survival

Measured against: Gradle 9.7.1 with Kotlin 2.4.20 and AGP 9.3.2 · npm 11.13.0 and TypeScript 7.0.2 on Node 24.15.0 · Bun 1.4.2 · uv 0.12.5 with the setuptools, hatchling and uv_build backends · Go 1.27.1 with `golang.org/x/mod/zip` · Cargo 1.98.1 · Swift 6.3.3 · Maven 3.9.16 with maven-source-plugin 3.4.0, maven-javadoc-plugin 3.12.0 and kotlin-maven-plugin 2.4.20. Harnesses: `experiments/skill-as-source/survival/run.sh` and `experiments/skill-as-source/kmp/`, raw output beside each; both still run.

Each case is a minimal library with one real function and a `skill-info` file, packaged by the ecosystem's ordinary publishing tool with **no configuration added for the skill**.

| ecosystem | what was published | skill-info present, text intact |
|---|---|---|
| Java | Gradle `java-library` sources jar | **yes** — beside the class it documents |
| | binary jar, javadoc jar | no — nothing compiled from it, and javadoc ignores it |
| | Maven, `maven-source-plugin` and `maven-javadoc-plugin` as a Central release configures them | **yes** in the sources jar; not in the binary or javadoc jar |
| Kotlin/JVM | Gradle sources jar | **yes** |
| | Maven, Kotlin source declared as `<sourceDirectory>` | **yes** |
| | Maven, `kotlin-maven-plugin` with `<extensions>true</extensions>` | **yes** |
| | Maven, Kotlin source declared only in the Kotlin plugin's `<sourceDirs>` | **no sources jar at all** — see below |
| Kotlin Multiplatform | all nine `-sources.jar` files, root and per target | **yes**, under `commonMain/` in every one |
| | JVM jar, Android AAR, metadata jars, klibs | no text; a klib records an empty file entry |
| TypeScript | `npm pack`, no `files` field | **yes** — `src/skill-info.ts` and `dist/skill-info.d.ts` |
| | `npm pack`, `"files": ["dist"]` — the common shape | **yes, in `.d.ts` only**; `tsc` drops the comment from the `.js` |
| | a Bun bundle — minified with the file imported, and unminified with it not imported | **no**, in both |
| Python | sdist and wheel, under each of setuptools, hatchling and uv_build | **yes**, all six artifacts, hyphenated name and all |
| | the wheel installed into a virtual environment | **yes** — `site-packages/acme_text/skill-info.py` |
| Go | the module zip, built as a proxy builds it | **yes** |
| | a consumer's `go mod vendor` | **yes** |
| Rust | `cargo package` crate, file reached by no `mod` | **yes** |
| Swift | `swift package archive-source` | **yes** |

- **Every ecosystem's source distribution carries the file with no configuration.** Nothing had to be told the file exists; nothing dropped it for having no code or a hyphenated name.
- **One Maven layout publishes no source whatsoever.** When a Kotlin project names its source directory only in `kotlin-maven-plugin`'s `<sourceDirs>`, the class compiles but `maven-source-plugin` sees no source roots, reports "No sources in project. Archive not created.", and the library ships no `-sources.jar`. The skill is lost with every other source file. That is not a fault in the skill-as-source idea — no source-based carrier, including ADR-0009's, reaches such a library — but it is a real configuration that exists, and the fix is a one-line `<sourceDirectory>` or `<extensions>true</extensions>`, both of which were measured to work.
- **The other loss is a JavaScript bundle**, and it is total: a bundler keeps only what the entry point reaches, and a documentation-only module contributes nothing. A package that ships only a bundle ships no skill. The common alternative — `tsc` output under `files: ["dist"]` — keeps it, but only in the declaration file.
- **Go carries a file the toolchain ignores.** A leading-underscore Go file was shipped in the module zip and copied by `go mod vendor`, while being invisible to `go build` and `go doc`. It is not needed — `skill-info.go` compiles to nothing and ships — but it means Go has a way to carry a file its toolchain never reads.
- **Not measured:** an Android library published by the classic AGP plugin rather than the KMP one, npm packages built by other bundlers or by `tsup`'s declaration bundling, and binary-only distributions — an XCFramework, a wheel built without source — where by construction there is no source to carry anything.

### Test 2: toolchain tolerance

Measured against: javac 26.0.2.1 · kotlinc-jvm 2.4.10 · Kotlin Multiplatform 2.4.20 with AGP 9.3.2 on Gradle 9.7.1 · Python 3.14.7 · Node 24.15.0 · TypeScript 7.0.2 · Swift 6.3.3 (SwiftPM) · Go 1.27.1 · Rust 1.98.1 with clippy · macOS on APFS, case-insensitive. Harnesses: `experiments/skill-as-source/naming/run.sh` and `experiments/skill-as-source/kmp/`, raw output beside each; both still run.

Each case is a package holding one real type plus a skill file containing only the package declaration (where the language has one) and a doc comment of skill-shaped content, built at the strictest warning level the toolchain offers — `javac -Xlint:all`, `kotlinc -Wextra`, Kotlin Multiplatform with `allWarningsAsErrors` and `extraWarnings`, `python -W error`, `tsc` in strict mode with `noUnusedLocals`, `swift build -warnings-as-errors`, `go vet`, `clippy::pedantic`. A planted warning was confirmed visible in javac, kotlinc, Swift and clippy, so "no warnings" there is a result and not a blind harness.

| toolchain | `skill` | `skill-info` | what the skill file emits |
|---|---|---|---|
| javac | ok, no warnings | ok, no warnings | nothing; doc comment accepted before or after the package line |
| kotlinc (JVM) | ok, no warnings | ok, no warnings | nothing — no `SkillKt` facade without a declaration |
| Kotlin Multiplatform, 9 targets | — | ok, no warnings on any target | see below |
| Python | ok, imports | ok via `importlib`; an `import` statement is a syntax error | a `.pyc` holding the docstring |
| tsc | ok, no warnings | ok, no warnings | a `.d.ts` carrying the text; the `.js` carries it only if the file is not a module |
| SwiftPM | ok, no warnings | ok, no warnings | a 4 KB object with no public symbols |
| Go | ok, no vet findings | ok, no vet findings | compiled into the package; `skillinfo.go` and `skill_info.go` behave the same, `_skill.go` is ignored entirely |
| Rust | ok if reached by `mod skill_info;` | ok, never compiled — a plain `mod` cannot name it | nothing; shipped by `cargo package` either way |

**Kotlin Multiplatform — the case this project builds for.** One `skill-info.kt` in `commonMain`, published to a local Maven repository for JVM, Android, JS, Wasm, iOS arm64, iOS simulator, macOS arm64 and Linux x64:

- **It is in every one of the nine `-sources.jar` files**, the root publication's and each target's, under `commonMain/com/example/acme/text/skill-info.kt`, text intact. A consumer resolving any single platform gets it.
- **It is in no binary.** The JVM jar, the Android AAR and every `-metadata.jar` contain neither the file nor its text. Each klib records an empty file entry naming its source path, as it does for every source file, with no declarations and no doc text.
- **No target warned**, with every warning an error.

**Across all of them:**

- **Every toolchain accepts a documentation-only file, silently.** Neither Kotlin nor Java needs a sanctioned slot for the file to be legal.
- **The hyphen is accepted by all nine**, which is the opposite of what this record said before it was measured. Rust accepts it only in the sense that it never compiles the file — `cargo package` still ships it, which is all a skill needs.
- **The plain name has a real hazard the hyphen does not:** on a case-insensitive filesystem, writing `skill.kt` into a directory holding `Skill.kt` overwrote it. Measured directly.
- **Placement decides whether the skill lands on a human's page**, differently per tool. `javadoc` ignores a doc comment in any file but `package-info.java`, so a dedicated Java file stays off the package page. `go doc` does the reverse: a comment directly above the package clause of *any* file becomes package documentation, merged with the real one, while a comment after the clause does not. In Rust, a reached file's `//!` doc is linted as documentation — clippy pedantic flagged unquoted code in it.
- **Where the text survives into compiled output is uneven, and it matters for npm**, which usually publishes `dist/` rather than source: `tsc` keeps a leading comment in `.d.ts` but drops it from the `.js` of a module.
- **Not tested here:** whether a packager drops the file — test 1, though the KMP and Cargo results above already answer it for those two.

### Test 3: harvestability

Measured against: Gradle 9.7.1 · Maven 3.9.16 · npm 11.13.0 · uv 0.12.5 · Go 1.27.1 · Swift 6.3.3 · this repository's codex harvester at the commit of this record. Harness: `experiments/skill-as-source/harvest/run.sh`, raw output beside it; it still runs.

Each consumer resolves the published fixture the way its ecosystem does, into caches isolated for the run, and a harvester that knows only the filename looks for `skill-info.*`. The text is then recovered by stripping comment markers line by line — one rule set for every language, no parser — and compared with what was written.

| consumer | where it landed | found by name | text recovered exactly |
|---|---|---|---|
| Gradle, KMP library over HTTP, sources resolved as an IDE resolves them | Gradle cache: the root and `-jvm` sources jars | **yes**, both | **yes** |
| Maven, `dependency:get` of the sources classifier | local repository | **yes** | **yes** |
| npm install, `files: ["dist"]` tarball | `node_modules/.../dist/` | **yes** — `.d.ts` and `.js` | **yes** from `.d.ts`; the `.js` carries no text |
| uv pip install of the wheel | `site-packages/acme_text/` | **yes** | **yes** |
| `go get` through a module proxy | module cache | **yes** | **yes** |
| SwiftPM git dependency | `.build/checkouts/` | **yes** | **yes** |

Rust was not consumed: a registry download is the `.crate` extracted in place, and test 1 showed the `.crate` carries the file.

**The name collides with nothing that exists.** Across the local caches of a working machine — 4,222 sources jars in the Gradle cache, 274 in the Maven repository, the Go module cache, and every `node_modules` tree in a workspace of 700,000 files — **no file named `skill-info.*` existed** other than this experiment's own fixture. Six Gradle entries and 51 `node_modules` files did begin with `skill`, which is the plain-name collision the hyphen avoids. Only counts were recorded.

**The codex's existing harvester reads the file and discards it.** Run over the Kotlin Multiplatform, Gradle and Maven sources jars, it counts `skill-info` as a source file, finds its doc comment, cannot bind it to a declaration, and records it as an unclaimed doc. No entry carries the skill text in any of the four. The file is in the source already indexed; the indexer drops it because it looks for documentation *of declarations*.

- **A harvester can find the file by name alone** in every consumer location measured, and recover the text without a parser.
- **The name is unclaimed** in the caches examined.
- **The current indexer would need a rule for it**: recognise `skill-info.*` by name before extraction, and keep its comment instead of discarding it as unclaimed.

### Test 4: does it reach a human's documentation

Measured against: Javadoc 26.0.2.1 · Dokka 2.2.0 · Go 1.27.1 · rustdoc 1.98.1 · pydoc 3.14.7 · pdoc 16.0.0 · TypeDoc 0.28.20 · swift-docc-plugin 1.5.0. Harness: `experiments/skill-as-source/docs/run.sh`, raw output beside it; it still runs.

Each generator is run the standard way over the fixture and its output searched for the skill text and the file name. Every generator was confirmed to render the fixture's real doc comments, so "stays out" is a result and not an empty site.

| generator | `skill-info`, as the convention places it | reusing the language's slot, or a non-default placement |
|---|---|---|
| Javadoc | **stays out** | `package-info.java`: **rendered** on the package page |
| Dokka, Kotlin Multiplatform | **stays out** | — Kotlin has no slot |
| `go doc -all` | **stays out** — comment after the package clause | comment before the clause: **rendered** as package documentation |
| rustdoc | **stays out** — reached by no `mod` | reached by a `#[path]` module with private items documented: **rendered** |
| pydoc | text stays out; the **name** is listed under package contents | — |
| pdoc | **rendered** — a `skill-info` submodule page with the text | — |
| TypeDoc | **stays out**, re-exported from the entry point or not | — |
| DocC | **stays out** | — |

- **In seven of eight generators the dedicated file stays off the page a person reads.** The skill and the human documentation stay separate by default.
- **Reusing the existing slot puts the skill on that page in every case measured** — `package-info.java` and a Go comment above the package clause both render. That settles the design question below in favour of the dedicated file.
- **Python is the exception.** pdoc walks the package directory and renders every module it finds, a hyphenated one included, so the skill gets its own page; pydoc lists the name. The fix is the generator's own: pdoc and Sphinx both take exclusion patterns. Whether skill text on a separate module page *harms* the docs, rather than merely appearing, is the part of test 4 still unmeasured.
- **Placement is part of the convention in Go:** the comment goes after the package clause.

### Test 5: uptake, with invented libraries

Measured against: Claude Code 2.1.270 on its default model (`claude-fable-5-1`) · Antigravity 1.2.0 and 1.2.2 on its default model · Kotlin 2.4.20 · Gradle 9.7.1 · 2026-09-13. Harness: `experiments/skill-as-source/uptake/`, which still runs. **Twelve runs, one or two per cell — a smoke test, not a sample; Antigravity's two `none` runs are invalid, as below.**

Each run gives a coding agent, headless, an ordinary task against a fixture library that ships a `skill-info` — implement four functions, add tests, make `./gradlew check` pass — in a throwaway consumer project. The task says nothing about skills or idiom. Two fixtures: `acme-result`, a sealed `Outcome` whose skill says to use `fold` and `valueOrNull` rather than matching its subtypes, and `acme-lookup`, built on Kotlin's own `Result`, whose skill says a missing key is not an error and to use `getOrNullIfMissing` rather than `getOrNull()`. In both, the consumer's existing code already does it the wrong way, and its README describes the type truly but incompletely — the conditions the guiding case had. The wrong way compiles and passes the tests.

| fixture and layout | tool | arm | runs | idiomatic (ok) | misuse (harm) | skill read |
|---|---|---|---|---|---|---|
| `acme-result`, sources jar inside the project | Claude Code | `none` | 2 | 2 | 0 | 2 of 2 |
| | Claude Code | `pointer` | 2 | 2 | 0 | 2 of 2 |
| `acme-result`, sources jar outside the project | Claude Code | `none` | 1 | 1 | 0 | 0 of 1 |
| | Claude Code | `pointer` | 1 | 1 | 0 | 1 of 1 |
| | Antigravity | `none`, `pointer` | 2 | 2 | 0 | all 2 — the `none` run by leaving its workspace, so invalid |
| `acme-lookup`, sources jar outside the project | Claude Code | `none` | 1 | 1 | 0 | 0 of 1 |
| | Claude Code | `pointer` | 1 | 1 | 0 | 1 of 1 |
| | Antigravity | `none`, `pointer` | 2 | 2 | 0 | all 2 — the `none` run by leaving its workspace, so invalid |

- **Neither agent misused an invented library, in any arm — 0 of 12.** With no trigger and no skill, Claude Code inspected the unfamiliar dependency before writing: it unzipped the sources jar when it could reach one, and otherwise ran `javap` on the binary jar and wrote a Gradle init script to dump the API, found `fold`, `valueOrNull` and `getOrNullIfMissing` by name, and used them. The existing wrong-way code in the project did not sway it.
- **The pointer got the skill read every time it was offered** — 4 of 4 for Claude Code, which activated `dependency-skills` from its description and opened the package's reference file before writing.
- **A skill that was read was followed, against the project's own contrary code** — 6 of 6 for Claude Code.
- **An invented library does not reproduce the failure.** The guiding case needed an agent confident it already knew the type. A library no model has seen is itself a gap, and a gap is investigated. What this measures is reach and adherence; it cannot show a trigger preventing misuse, because the baseline did not misuse. That needs a library the model genuinely knows.
- **Antigravity's `none` runs are invalid, and its trajectory log is how that was found.** Its print mode outputs only a final summary, so the harness saw nothing. But every Antigravity conversation also writes a trajectory log under the user's Antigravity data directory, recording each tool call and its result. It showed both `none`-arm agents walking up out of the workspace into the directory the harness had staged it from, and reading the library's own `skill-info.kt`, the staged pointer skill and its reference file — none of which a real consumer has. Claude Code's eight transcripts were checked for the same paths and touched none of them.
- **The harness is fixed.** Each run now works in a fresh temp directory holding only its workspace and the binary repository, with results copied back afterwards; and Antigravity runs with `--output-format stream-json`, which streams every tool call and its parameters — the file viewed, the command run — so the harness records what it read without reaching into the agent's own data directory.
- **Containment was by content, not permission.** Claude Code's `--allowedTools` adds to the user's own permission settings rather than replacing them; the agents also ran `unzip`, `javap` and scripts outside the allowlist. The workspaces held only fixture files.

### Test 5: uptake, with a library the models know

Measured against: Claude Code 2.1.270 on its default model (`claude-fable-5-1`), on `claude-opus-4-1-20250805` and on `claude-haiku-4-5-20251001` · Antigravity 1.2.2 on its default model · `io.arrow-kt:arrow-core` 2.2.3 from Maven Central · Kotlin 2.4.20 · Gradle 9.7.1 · 2026-09-14. Harness: `experiments/skill-as-source/uptake/`, fixture `arrow`. **38 runs, three to five per cell.** Each run worked in a fresh temp directory holding only its workspace; Antigravity's tool calls were logged with `--output-format stream-json`, and Claude Code's with its own stream.

The invented libraries above could not reproduce the failure, so this fixture uses one both vendors' models genuinely know. The consumer depends on the real Arrow. Its existing code composes `Either` with `flatMap` and matches `is Either.Left` — the Arrow 1 style common in older code — and a skill written into Arrow's real sources jar as `arrow/core/skill-info.kt` says to use the `either { }` builder with `bind()`, and `getOrElse`, `fold` and `leftOrNull`, instead. The task asks for an order placed across three failing steps and three helpers, and says nothing about style; the old style compiles and passes.

Older Claude models were added on the reasoning that a model trained before a library's current idiom was common has nothing else to fall back on. The Claude 3 and Sonnet 4 models no longer answer under a subscription login; Opus 4.1 and Haiku 4.5 are the oldest that do, and both postdate Arrow 2.0, so they are *less* exposed to it rather than unexposed.

| tool and model | arm | runs | idiomatic (ok) | misuse (harm) | skill read |
|---|---|---|---|---|---|
| Antigravity | `none` | 5 | 0 | **5** | 0 of 5 |
| | `pointer` | 5 | **5** | 0 | 5 of 5 |
| | `instructions` | 3 | **3** | 0 | 3 of 3 |
| | `lint` | 3 | **3** | 0 | 3 of 3 — before writing, see below |
| Claude Code, default model | `none` | 5 | 2 | **3** | 0 of 5 |
| | `pointer` | 5 | **5** | 0 | 5 of 5 |
| Claude Code, Opus 4.1 | `none` | 3 | 1 | **2** | 0 of 3 |
| | `pointer` | 3 | **3** | 0 | 3 of 3 |
| Claude Code, Haiku 4.5 | `none` | 3 | 0 | **3** | 0 of 3 |
| | `pointer` | 3 | 1 | **2** | **0 of 3** |

Misuse is counted in the files the agent wrote; every misuse run had it in the implementation, not only the tests.

- **The guiding case reproduces, in every tool and model.** With no skill, 13 of 16 runs wrote the Arrow 1 style the project already used, and **not one of the 16 inspected Arrow's API or sources** — they read the project's own files and wrote from memory, which is the confidence the invented libraries could not create. Even the default Claude model, which knows the current idiom, followed the project's existing code in 3 of 5.
- **When the skill was read, it was followed — 19 of 19**, against the project's contrary code, across both vendors and all three Claude models that read it.
- **The pointer's reach depends on the model.** Antigravity and the default and Opus 4.1 Claude models activated it before writing in 13 of 13 runs. **Haiku 4.5 never did** — 0 of 3 — though the skill was listed in its session, and it misused the library in two of those runs as if the pointer were absent. A description in context is a trigger only for a model that acts on it.
- **Measured here: `pointer`, `instructions` and `lint` each got the skill read every time for Antigravity** (5, 3 and 3 runs), with no misuse. The `instructions` and `lint` arms were not run on Claude.
- **The lint arm did not act as a lint.** Antigravity opened the build script, found the lint task naming the skill file, and read the skill *before writing anything* — so this measures a discoverable pointer in the build, not a warning reaching an agent after it wrote the code. A lint that does not name a file until it fires, or a check the agent cannot read in advance, is still untested.
- **Staleness helps explain it but is not the whole of it.** The less-exposed models misused more with no skill (Haiku 3 of 3, Opus 4.1 2 of 3), but the most current model still copied the project in 3 of 5. The project's own code is a strong prior on its own.
- **Three to five runs per cell.** The pattern is consistent in every cell, but the counts are small; the per-cell rates are not estimates.

## Recommendation

**Not a commitment. Test 1 was the whole claim, and it holds.** A documentation-only `skill-info` file survives the ordinary publishing path of every ecosystem measured with no configuration, the Kotlin Multiplatform case included. The exception is a JavaScript bundle, which carries no source by design. Tests 3 and 4 hold as well: a harvester finds the file by name in every consumer location measured and recovers the text without a parser, and in seven of eight documentation generators the file stays off the human's page. **What is left is not distribution. It is the indexer, which currently reads the file and discards it, and test 5 — whether an agent acts on it, which needs a trigger.**

**The design question to settle first is reuse versus a dedicated file.**

- **Reusing the existing slot** — writing skill content into `package-info.java`, `doc.go` or `//!` — needs no new convention and is picked up by every existing tool. But test 4 measured that it puts the skill on the page a human reads, every time, and in Kotlin there is no slot to reuse. **Measured against it.**
- **A dedicated file** — a `skill-info` source file in the package — keeps the two audiences apart and gives Kotlin a home. It is a new convention, and a new convention has to be adopted. **Tests 1 to 4 found nothing against it.**

**The hyphen is the one rule that works everywhere tested.** An earlier draft of this record argued from recall that the hyphen was a Java-only trick that buys nothing elsewhere and breaks Python. Test 2 contradicts both halves. Every toolchain tested accepted `skill-info`, and the hyphen buys something everywhere: **on a case-insensitive filesystem — the default on macOS and Windows — `skill.kt` and `Skill.kt` are the same file**, so a plain `skill` name collides with any type called `Skill` in that package, and a hyphen cannot appear in a type name in Java, Kotlin, Swift, TypeScript or C#. In Python the hyphen that stops an `import` statement is harmless — nothing needs to import a skill — and the file cannot shadow a module name. In Rust it keeps the file out of compilation while `cargo package` still ships it. Go accepts `skill-info.go` too. **So the name is `skill-info` in every language** — `skill-info.java`, `skill-info.kt`, `skill-info.py`, `skill-info.ts`, `skill-info.swift`, `skill-info.go`, `skill-info.rs`.

**Place it in the package, not at the root.** Every precedent is per-package, and a library of several packages has several things to say.

**What would change the answer.** Test 1 found no ecosystem that strips the file from its source distribution; if the unmeasured paths above — the classic Android plugin, other JavaScript build tools — turn out to drop it, the convention narrows to the ecosystems where it was measured. Test 4 has already ruled reuse out; if a Python project cannot exclude the module from its generator, that ecosystem needs a different name or location.

## Connections

- [ADR-0009](../decisions/ADR-0009-transport-is-sources-jar.md) — the sources jar as the carrier; this puts the skill inside what ADR-0009 already chose.
- [RAD-0065](RAD-0065-what-v1-skill-authors-wrote-unprompted.md) — why skills as resources did not reach the sources jar.
- [RAD-0011](RAD-0011-existing-documentation-systems-as-skill-content.md) — package docs as existing content, including the `doc.go` measurement.
- [RAD-0072](RAD-0072-the-one-thing-we-are-not-doing.md) — the shipped-skill field, where the in-artifact route has no standard.
- `docs/knowledge/reference/doc-comment-systems.md` — the per-ecosystem doc conventions this relies on.
- `README.md` — the misuse case, which shows why distribution alone is not enough.

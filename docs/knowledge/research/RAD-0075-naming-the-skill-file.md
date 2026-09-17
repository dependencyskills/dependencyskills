# Naming the Skill File

RAD-0075 · 2026-09-17

Keywords: what to call the skill file; skill-info.kt is awkward; SKILL.md inside a package; does a markdown file survive a sources jar; package-info.kt for Kotlin; AGENTS.md in a library; skill.kt collides with a class; will an agent recognise the file; agentskills SKILL.md convention; a non-source file in src/main/kotlin; setuptools drops data files; SwiftPM unhandled file warning.

Measured against: Claude Code 2.1.270 and Antigravity 1.2.2 over 15 uptake runs · Gradle 9.7.1 with Kotlin 2.4.20 · Maven 3.9.16 with maven-source-plugin 3.4.0 · uv 0.12.5 with setuptools, hatchling and uv_build · npm 11.13.0 with TypeScript 7.0.2 · Go 1.27.1 · Cargo 1.98.1 · Swift 6.4 · Claude Code 2.1.270 on its default model · Antigravity 1.2.2 · local Gradle and Maven caches holding 4,513 sources jars · 2026-09-17. Harness: `experiments/skill-as-source/naming-candidates/`.

## Question

[RAD-0073](RAD-0073-a-skill-written-as-source.md) settled that a library can ship a skill as a per-package file inside its source, and chose `skill-info.<ext>` — a documentation-only source file named by analogy with `package-info.java`. The name was measured as legal and collision-free everywhere it was tried. It was never chosen for being a good name, and it reads as an awkward borrowing.

**What should the file be called?** And, underneath that, two questions RAD-0073 assumed rather than measured: does it have to be a source file at all, and does it have to be a comment?

## Trail

### What a name has to satisfy

Most of the constraints come from what RAD-0073 already measured:

1. **Survives publishing** in each ecosystem, with no configuration.
2. **Is not special to any toolchain.** Go ignores files beginning with `_` or `.` and reads `_test` and platform suffixes as build constraints; Java reserves `package-info` and `module-info`; Rust reserves `lib.rs`, `mod.rs`, `main.rs` and `build.rs`; Python gives `__init__` meaning.
3. **Cannot collide with a real file.** On a case-insensitive filesystem — the default on macOS and Windows — `skill.kt` and a class file `Skill.kt` are the same file. RAD-0073 measured the overwrite.
4. **Is unclaimed**, so a harvester matching the name finds only skills.
5. **Stays out of the human's documentation**, unless that is wanted.
6. **Is recognisable** — to an agent that meets it in a sources jar without being told, and to a person browsing the package.
7. **Fits the conventions that already exist** rather than inventing a parallel one.

### The assumption: a skill has to be a source file

RAD-0073 rejected a markdown file on the grounds that packagers keep source and drop everything else — the failure of resources that started the whole record. That was argument. Measured with a plain `SKILL.md` placed in the package's source directory, next to the code, with no configuration:

| ecosystem | what was published | `SKILL.md` survives | `skill-info.<ext>` did (RAD-0073) |
|---|---|---|---|
| Java | Gradle `java-library` sources jar | **yes** | yes |
| Kotlin/JVM | Gradle sources jar | **yes** | yes |
| Kotlin Multiplatform | root and every target's sources jar | **yes**, under `commonMain/` | yes |
| Java | Maven `maven-source-plugin` sources jar | **yes**; not in the binary jar | yes |
| Python | setuptools sdist and wheel | **no** | yes |
| Python | hatchling and uv_build sdists and wheels | **yes** | yes |
| TypeScript | `npm pack`, no `files` field | **yes** | yes |
| TypeScript | `npm pack`, `files: ["dist"]` | **no** | yes, as `.d.ts` only |
| Go | module zip | **yes** | yes |
| Rust | `cargo package` | **yes** | yes |
| Swift | SwiftPM build | builds, but **warns** that the file is unhandled | yes, silently |

**The belief was wrong for most of the ecosystems that matter here.** Gradle's and Maven's sources jars carry everything under the source directory, not only source files, so on the JVM — including every Kotlin Multiplatform target — a markdown file travels exactly as a source file does. The losses are real but narrow: setuptools drops data files unless declared, npm's `files: ["dist"]` drops the source directory whatever it holds, and SwiftPM wants non-source files declared as resources or excluded.

### What a markdown file gains

- **The format is the skill's own.** Skills are written in markdown — headings, lists, code fences. Inside a `/** */` block, code examples cannot contain `*/`, every line carries a comment prefix, and a harvester has to strip markers before the text is usable. A `SKILL.md` is delivered as written, which is what the lightweight codex does anyway ([RAD-0074](RAD-0074-a-skill-built-from-the-documentation.md)).
- **It matches the convention that already won.** The Agent Skills specification's file is `SKILL.md` ([RAD-0072](RAD-0072-the-one-thing-we-are-not-doing.md)). A package directory holding a `SKILL.md` whose frontmatter `name` matches the directory is, by the specification's rules, already a valid skill directory.
- **No toolchain can misread it.** No compiler sees it, so the per-language cautions from RAD-0073 disappear: no dangling-doc-comment question, no `go doc` placement rule, no clippy lint on a reached Rust module, no Python import that fails.
- **No type-name collision.** A `.md` file cannot share a name with a class file on any filesystem.

### What it costs

- **Three ecosystems need a line of configuration** where a source file needed none: `package_data` for setuptools, a copy step into `dist` for npm, a resource or exclude declaration for SwiftPM.
- **Its effect on generated documentation is not measured.** It is expected to be invisible to Javadoc, Dokka, `go doc`, rustdoc, pdoc and TypeDoc, none of which render stray markdown by default — which would also remove the one exception RAD-0073 found, pdoc rendering `skill-info.py` as a module. DocC's handling of a markdown file inside a target is the least certain.
- **Loaded directly as a skill, package names collide.** If an agent harness loaded every package's `SKILL.md` as a skill in its own right, a dozen libraries each with a `util` or `core` package would collide on `name`. The generated pointer skill ([RAD-0073](RAD-0073-a-skill-written-as-source.md)) avoids this by indexing them under their coordinates.

### A third shape: the skill as a raw string constant

Both candidates so far are text a compiler ignores — a doc comment, or a file it never opens. A third holds the skill as data the compiler *does* read: a raw string literal in an ordinary declaration.

    val skill = """
        # Skill: acme-text

        Never hand-roll a case fold; use `normalize`.
    """.trimIndent()

**Measured on a Kotlin Multiplatform library (JVM, JS, linuxX64), built with every warning an error:**

| artifact | carries the skill text |
|---|---|
| every sources jar — common, JVM, JS, native | **yes** |
| JVM jar, in the class file's constant pool | **yes** |
| JS klib | **yes** |
| linuxX64 klib | **yes** |
| the common metadata jar | no |

**This is the only shape measured that reaches the binaries**, including the native targets where a `META-INF` resource is dropped outright. A consumer that has resolved only the binary jar still has the text, and a harvester can read it with `javap -c` or `strings` — [RAD-0012](RAD-0012-structure-from-bytecode.md)'s bytecode path, carrying prose rather than structure.

**Dokka, measured:** a public `val skill` appears in the rendered documentation as its own entry, but **its contents do not** — Dokka renders the declaration, not the literal. Marked `internal`, it does not appear at all and is still in the binary. So the choice of visibility is a choice about the library's public API, not about whether the skill leaks into the human's page.

**What it costs:**

- **A raw string cannot contain its own delimiter.** Kotlin, Java and Swift use `"""`, so a skill cannot show a Kotlin raw string in an example without an escape. **Go and TypeScript use a backtick**, and a markdown code fence is three of them, so a fenced example cannot appear in a Go or TypeScript raw string at all. This is the mirror of the doc-comment shape, which cannot contain `*/`.
- **It is code, so it takes part in the build.** A name to choose, unused-declaration warnings to avoid, and a few kilobytes of constant in every artifact.
- **R8 strips it from an application, measured.** Shrinking a consumer that calls the library but never reads the constant — R8 9.4.14 from the Android SDK, release mode, one keep rule for the application's entry point — removed the text from `classes.dex` entirely; adding a keep rule for the library's package brought it back. Minification runs on **applications**, and it shrinks every library on the classpath, so a library author cannot prevent it from the library side. This costs nothing for the purpose at hand: an agent reads the dependency as resolved, not the application's release output, and R8 never sees a sources jar. It does mean the binary reach is a property of the *resolved jar*, not of what ships in a shipped app.
- **Public makes it API.** A public `val` enters the library's API surface and its binary-compatibility dump; renaming it later is a breaking change. `internal` avoids that.
- **A harvester must tell it from API.** A file holding a declaration looks like code. This project's extractor skips it today, because it only keeps a doc comment bound to a declaration — so recognising this shape means matching the declaration's name, exactly as the other two shapes mean matching a file name.

**The file it lives in is a separate choice from the declaration's name.** `val skill` is unambiguous; a file called `skill.kt` is the one spelling measured to collide with a `Skill.kt` type on a case-insensitive filesystem.

### npm already has a convention, and it is not in the source tree

[RAD-0072](RAD-0072-the-one-thing-we-are-not-doing.md) measured this on 2026-09-12 by downloading published tarballs: npm packages already ship skills, as `skills/<name>/SKILL.md` **at the package root**, included by adding `skills` to `package.json`'s `files` array. `antfu/skills-npm`'s proposal states exactly that layout and that `files` entry; it is still titled a proposal, but the practice is real and has adopters. The one `SKILL.md` found in a working `node_modules` tree sits there too, at `get-tsconfig/skills/get-tsconfig/SKILL.md`.

**So npm's two losses in the table above do not need fixing; they need not to be fought.** A skill placed in `src/` and dropped by `files: ["dist"]` is a skill in the wrong place for that ecosystem. Following npm's own convention costs one `files` entry, is what agents already look for there, and both fixes measured for the source-tree placement — adding `src/**/SKILL.md` to `files`, or copying the file into `dist/` — are worse than simply using `skills/`.

**It also differs on granularity, and that is the open question.** npm's unit is the *package* — one skill for the library, named for it. This project's unit is the *package as a namespace*: one skill per import, which is what scopes a lookup to the file being edited ([RAD-0073](RAD-0073-a-skill-written-as-source.md)). For a small npm library the two coincide. For a large JVM library they do not, and a convention that fits both would have to allow several skills per artifact, keyed by the package they document. `skills/<name>/SKILL.md` allows exactly that — the directory name is free — so the shapes are compatible even where the grain differs.

**What this settles for the name.** `SKILL.md` is not merely unclaimed; it is the filename the one working in-artifact practice already uses. A JVM convention that spells it the same way inherits that recognition, whatever directory it sits in.

### Is the name already taken

Across 4,513 sources jars in local Gradle and Maven caches, the Go module cache, and every `node_modules` tree in a working directory:

| name | sources jars carrying it | notes |
|---|---|---|
| `SKILL.md` | **0** in sources jars | in `node_modules` it is npm's shipped-skill convention, at the package root rather than in a source package — see above |
| `skill-info.*` | 0 | |
| `package-skill.*` | 0 | |
| `AGENTS.md` | 0 | |
| `skill.<ext>` | 0 | collides with a `Skill` type on case-insensitive filesystems |
| `package-info.kt` | 3 | already used, as Kotlin package documentation |
| `README.md` | 29, 26 of them inside a package | already used, for human documentation |
| `Module.md` | 18 | Dokka's module documentation |

`package-info.kt` and `README.md` are ruled out by use; everything else is unclaimed.

### AGENTS.md is the wrong file

`AGENTS.md` is recognised by every agent as instructions for working on *this* code. Harnesses discover it by walking up from the working directory — Antigravity documents exactly that. In a library's own repository, a package-level `AGENTS.md` meant for the library's *consumers* would be loaded by the agents of the library's *developers* while they edit that package. The audience is backwards. `SKILL.md` has no such discovery behaviour attached.

### Would an agent recognise it

Both agents were asked, headless and cold, what they would expect a file of each candidate name to contain if they met it in a dependency's sources jar, and whether they would read it first. **This probe is weak and is reported as such.** The question itself mentioned writing code against the library and asked whether they would read the file, which primes an answer of "agent guidance, yes" for any unfamiliar name — and both agents gave that answer for almost every candidate. Antigravity's user-level instruction file also mentions this project's earlier skill layout, and it cited that in one answer.

What discriminated despite that: Antigravity read `skill.kt` as ordinary library code and said it would not read it; both agents read `package-info.kt` as package documentation for humans; and the default Claude model named `SKILL.md` specifically as a skill with `name` and `description` frontmatter, without guessing. An unprimed probe — an agent meeting the file mid-task, as test 5 of RAD-0073 did — is the measurement that matters.

### Does the spelling change what an agent does

Test 5 of [RAD-0073](RAD-0073-a-skill-written-as-source.md) was re-run with one change: Arrow's skill is a `SKILL.md` in the `arrow.core` directory of its sources jar instead of a `skill-info.kt`. Same task, same fixture, same arms. Measured 2026-09-17 with Claude Code 2.1.270 on its default model and Antigravity 1.2.2. **15 runs.**

| tool | arm | runs | as `SKILL.md` | as `skill-info.kt` (RAD-0073) |
|---|---|---|---|---|
| Antigravity | `none` | 3 | 3 misuse, skill read 0 of 3 | 5 misuse, read 0 of 5 |
| Antigravity | `pointer` | 3 | 3 idiomatic, read 3 of 3 | 5 idiomatic, read 5 of 5 |
| Claude Code | `none` | 3 | 1 misuse, read 0 of 3 | 3 of 5 misuse, read 0 of 5 |
| Claude Code | `pointer` | 3 | 3 idiomatic, read 3 of 3 | 5 idiomatic, read 5 of 5 |
| Claude Code | `hook` | 3 | 3 idiomatic, delivered 3 of 3 | 3 idiomatic, delivered 3 of 3 |

**Nothing changed that the counts can detect.** Unprompted, neither agent found the skill in either spelling and both wrote the project's existing style; offered through the pointer or pushed by the hook, both read it and followed it. The markdown file needed no comment-stripping on the way — the lightweight codex reads it from the jar by its path, since markdown cannot declare its package — and the pointer carried it through with its own frontmatter intact.

**What this does not show.** Three runs a cell cannot separate spellings that both work; it shows no penalty for the change. The uptake question the spelling might really affect — whether an agent that stumbles on the file *unprompted* opens it — is answered by neither, because in six unprompted runs neither agent went looking at all.

## Findings

**Measured.**

- A plain `SKILL.md` in a package's source directory survives the Gradle, Maven and Kotlin Multiplatform sources jars, hatchling and uv_build, a Go module zip, a Cargo crate, and npm without a `files` field.
- It is lost under setuptools and npm's `files: ["dist"]`, and SwiftPM warns about it.
- None of `SKILL.md`, `skill-info`, `package-skill` or `AGENTS.md` exists in 4,513 sources jars; `package-info.kt`, `README.md` and `Module.md` are already in use.
- A raw string constant reaches every sources jar **and** the JVM jar, the JS klib and the native klib; Dokka renders a public one's declaration but not its text, and an `internal` one not at all. R8 removes it from an application's release build unless a keep rule covers the library.
- Test 5 re-run with the skill as `SKILL.md` matches the source-file result across 15 runs: unprompted 4 of 6 misuse and 0 of 6 found it, pointer 6 of 6 read and followed, hook 3 of 3 corrected.
- setuptools ships `SKILL.md` with a `[tool.setuptools.package-data]` entry; npm ships it either from a `files` entry naming the source path or by copying it into `dist/`.

**Argued, not measured.**

- A markdown file is invisible to the documentation generators RAD-0073 tested.
- `AGENTS.md` inverts the audience, because harnesses discover it for whoever edits the directory.
- `SKILL.md` is recognised as a skill by name. The probe that pointed this way was primed.

## Recommendation

**Not a commitment.**

**`SKILL.md` in the package directory is the leading candidate**, and the reason is not the name alone: the measurement above removes the premise that ruled it out. It carries the skill in its own format, matches the specification that already exists, and cannot be misread by a compiler or collide with a type.

**In npm, follow npm.** Ship `skills/<name>/SKILL.md` at the package root with a `files` entry, as published packages already do, rather than placing a file in `src/`.

**Keep `skill-info.<ext>` as the fallback, not the rule**, for the places a markdown file needs configuration and a source file does not — setuptools and SwiftPM. Both fixes are one line and are **measured to work**: `[tool.setuptools.package-data]` puts `SKILL.md` in the wheel and the sdist; for npm, either a `files` entry naming the source path or a copy into `dist/` ships it, though the convention above is the better answer there. A harvester should recognise both; the lightweight codex's package index and pointer already do.

**Measure before deciding:**

1. Documentation generators with `SKILL.md` present — Javadoc, Dokka, `go doc`, rustdoc, pdoc, TypeDoc, DocC.
2. ~~Test 5 with the skill as `SKILL.md`~~ — done, above: no detectable difference. What remains is a fixture where an agent *does* explore, to test whether the spelling changes what it opens.
3. Whether SwiftPM's `exclude`, or declaring the file as a resource, is the better of the two one-line answers there; the setuptools and npm fixes are already verified.

**What would change the answer.** If a documentation generator renders a stray `SKILL.md` onto a human's page, or agents treat a markdown file in a sources jar as less authoritative than a source file, `skill-info.<ext>` stays. If the one-line fixes prove fragile, the fallback becomes the rule for those ecosystems.

## Connections

- [RAD-0073](RAD-0073-a-skill-written-as-source.md) — the per-package skill file, the measurements this relies on, and the generated pointer.
- [RAD-0072](RAD-0072-the-one-thing-we-are-not-doing.md) — the Agent Skills format and the shipped-skill field.
- [RAD-0074](RAD-0074-a-skill-built-from-the-documentation.md) — delivery as written in the lightweight codex.
- [RAD-0065](RAD-0065-what-v1-skill-authors-wrote-unprompted.md) — where v1 skills sat, and why resources failed.

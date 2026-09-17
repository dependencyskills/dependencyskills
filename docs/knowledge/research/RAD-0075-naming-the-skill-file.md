# Naming the Skill File

RAD-0075 · 2026-09-17

Keywords: what to call the skill file; skill-info.kt is awkward; SKILL.md inside a package; does a markdown file survive a sources jar; package-info.kt for Kotlin; AGENTS.md in a library; skill.kt collides with a class; will an agent recognise the file; agentskills SKILL.md convention; a non-source file in src/main/kotlin; setuptools drops data files; SwiftPM unhandled file warning.

Measured against: Gradle 9.7.1 with Kotlin 2.4.20 · Maven 3.9.16 with maven-source-plugin 3.4.0 · uv 0.12.5 with setuptools, hatchling and uv_build · npm 11.13.0 with TypeScript 7.0.2 · Go 1.27.1 · Cargo 1.98.1 · Swift 6.4 · Claude Code 2.1.270 on its default model · Antigravity 1.2.2 · local Gradle and Maven caches holding 4,513 sources jars · 2026-09-17. Harness: `experiments/skill-as-source/naming-candidates/`.

## Question

[RAD-0073](RAD-0073-a-skill-written-as-source.md) settled that a library can ship a skill as a per-package file inside its source, and chose `skill-info.<ext>` — a documentation-only source file named by analogy with `package-info.java`. The name was measured as legal and collision-free everywhere it was tried. It was never chosen for being a good name, and it reads as an awkward borrowing.

**What should the file be called?** And, underneath that, a question RAD-0073 assumed rather than measured: does it have to be a source file at all?

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

### Is the name already taken

Across 4,513 sources jars in local Gradle and Maven caches, the Go module cache, and every `node_modules` tree in a working directory:

| name | sources jars carrying it | notes |
|---|---|---|
| `SKILL.md` | **0** | one in `node_modules`, at `get-tsconfig/skills/get-tsconfig/SKILL.md` — npm's shipped-skill convention, at the package root rather than in a source package |
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

## Findings

**Measured.**

- A plain `SKILL.md` in a package's source directory survives the Gradle, Maven and Kotlin Multiplatform sources jars, hatchling and uv_build, a Go module zip, a Cargo crate, and npm without a `files` field.
- It is lost under setuptools and npm's `files: ["dist"]`, and SwiftPM warns about it.
- None of `SKILL.md`, `skill-info`, `package-skill` or `AGENTS.md` exists in 4,513 sources jars; `package-info.kt`, `README.md` and `Module.md` are already in use.

**Argued, not measured.**

- A markdown file is invisible to the documentation generators RAD-0073 tested.
- `AGENTS.md` inverts the audience, because harnesses discover it for whoever edits the directory.
- `SKILL.md` is recognised as a skill by name. The probe that pointed this way was primed.

## Recommendation

**Not a commitment.**

**`SKILL.md` in the package directory is the leading candidate**, and the reason is not the name alone: the measurement above removes the premise that ruled it out. It carries the skill in its own format, matches the specification that already exists, and cannot be misread by a compiler or collide with a type.

**Keep `skill-info.<ext>` as the fallback, not the rule**, for the three places a markdown file needs configuration and a source file does not — setuptools, npm's `dist`, SwiftPM. A harvester should recognise both; the lightweight codex's package index and pointer already do.

**Measure before deciding:**

1. Documentation generators with `SKILL.md` present — Javadoc, Dokka, `go doc`, rustdoc, pdoc, TypeDoc, DocC.
2. Test 5's `none`, `pointer` and `lintpost` arms with the Arrow skill as `SKILL.md` instead of `skill-info.kt`, to see whether an agent that meets it unprompted, or is sent to it, treats it differently.
3. The one-line configuration each losing ecosystem needs, verified to work.

**What would change the answer.** If a documentation generator renders a stray `SKILL.md` onto a human's page, or agents treat a markdown file in a sources jar as less authoritative than a source file, `skill-info.<ext>` stays. If the one-line fixes prove fragile, the fallback becomes the rule for those ecosystems.

## Connections

- [RAD-0073](RAD-0073-a-skill-written-as-source.md) — the per-package skill file, the measurements this relies on, and the generated pointer.
- [RAD-0072](RAD-0072-the-one-thing-we-are-not-doing.md) — the Agent Skills format and the shipped-skill field.
- [RAD-0074](RAD-0074-a-skill-built-from-the-documentation.md) — delivery as written in the lightweight codex.
- [RAD-0065](RAD-0065-what-v1-skill-authors-wrote-unprompted.md) — where v1 skills sat, and why resources failed.

# A Skill Written as Source

RAD-0073 · 2026-09-13

Keywords: package-info.java for skills; a source file that is only documentation; doc.go as a skill carrier; ship a skill without a resource mechanism; source travels where resources do not; crate-level docs; module docstring; packageDocumentation; does kotlin have a package doc file; skill in the sources jar; will the toolchain accept a file with no code; does skill content pollute the rendered docs.

Measured against: tests 1 and 2 — nine toolchains, a nine-target Kotlin Multiplatform publication, and the ordinary publishing tools of seven ecosystems, Maven and Gradle both; versions under each test's results. Tests 3 to 5 are unmeasured, and what this record says about them is argument.

## Question

A skill embedded in a library as a resource file does not come out reliably. It works on the JVM, where a jar carries resources, and fails across most other ecosystems, each of which handles non-code files differently or drops them.

**Can a library ship its skill as a source file instead — the way `package-info.java` documents a Java package — so that the skill travels wherever the source travels, with no per-ecosystem resource mechanism at all?**

## Trail

### Why resources failed

[RAD-0065](Research-RAD-0065-What-V1-Skill-Authors-Wrote-Unprompted) found that authored skills sat in the **binary** jar and never in the sources jar — 0 of 82 sources jars carried one — and that 0 of 120 non-JVM artifacts carried one either. The v1 approach placed them at `META-INF/ai-skills/` as bundled resources, shipped, and failed; the postmortem is RAD-0046.

A resource is a file the build must be told to package, in a location the ecosystem must agree to preserve, read back through a mechanism that differs per ecosystem. Every one of those is a place to lose it.

### Source is the one thing every ecosystem distributes

Source code is the artifact no ecosystem can drop, because it is the point. Go modules and Rust crates distribute source natively. Python sdists are source. Swift packages are source. On the JVM, [ADR-0009](Decisions-ADR-0009-Transport-Is-Sources-Jar) already chose the `-sources.jar` as this project's primary carrier, for a reason that transfers directly: it is **tied to the resolved version by construction**.

A skill written as a source file inherits that tie for free. It is versioned with the code because it *is* code, in the only sense a packager cares about.

### Every major ecosystem already has this slot

This is not a new kind of artifact. Nearly every ecosystem already reserves a source file, or a position in one, whose only job is to document its package:

| ecosystem | the existing slot | notes |
|---|---|---|
| Java | `package-info.java` | package Javadoc plus package annotations. The hyphen makes it an invalid class name, so it can never collide with a real type. |
| Go | `doc.go` | a file holding only `package foo` and its doc comment. [RAD-0011](Research-RAD-0011-Existing-Documentation-Systems-As-Skill-Content) measured **44%** of Go packages shipping one. |
| Rust | `//!` at the top of `lib.rs` or `mod.rs` | crate- and module-level docs, inline in source. |
| Python | the module docstring in `__init__.py` | read by `help()` and every doc tool. |
| TypeScript | a TSDoc `@packageDocumentation` comment | conventionally in the entry file. |
| Swift | a DocC `.docc` catalogue | a directory rather than a source file. |
| **Kotlin** | **none** | Dokka reads module and package docs from a separate Markdown file configured in the build — not a source file, and not in the sources jar by default. |

**Kotlin is the gap, and it is the ecosystem this project leads with.** The one language with no sanctioned source-level package doc is the reference case for the whole design.

### The slot exists and is empty

[RAD-0070](Research-RAD-0070-The-Smallest-Thing-That-Works) checked two of the most-used Kotlin libraries and found neither `kotlin-stdlib` nor `kotlinx-coroutines-core` carrying a `package-info`, a `package.html` or a `module-info` in its sources jar. That is no obstacle to a new convention — it means there is no existing corpus to harvest, and every skill would have to be written deliberately.

### What this solves, and what it does not

**It solves distribution.** A skill in source reaches the consumer by the same route as the code, in every ecosystem, with the version tie ADR-0009 depends on.

**It does not solve the trigger, and the case that leads the README's failure list shows why that matters.** In that case the dependency's sources were in the local build cache throughout, and the agent never opened them — it was confident it already knew the type, so it never felt a gap to look into. A skill file sitting in that same sources jar would have been exactly as unread. Asked afterwards, the agent said a search it had to invoke would not have helped either.

So this is half of the problem. It gets the skill onto the machine, version-matched, in every ecosystem. Something else still has to put it in front of the agent at the moment it first names a type from that library — a routine step, a load-on-import, or a nudge when code matches a known hand-rolled pattern. That is a separate question and this record does not answer it.

## Findings

**Established, by precedent and argument.**

- **A package-documentation slot in source exists in Java, Go, Rust, Python and TypeScript**, and is widely used — 44% of Go packages ship a `doc.go`.
- **Kotlin has no source-level equivalent.** Its package docs live in a build-configured Markdown file, outside the sources jar by default.
- **Source is the artifact every ecosystem distributes**, and ADR-0009 already relies on the sources jar being tied to the resolved version.
- **The slot is empty in practice** in at least two widely-used Kotlin libraries.
- **A skill in source addresses distribution only.** It does not make an agent read it.

**The tests.** Tests 1 and 2 are measured, with results below; 3 to 5 are not.

1. **Survival.** Does a documentation-only source file reach the published artifact unchanged — a Maven sources jar, each target of a Kotlin Multiplatform publication (does a `commonMain` file appear where a consumer resolves it?), an npm tarball, a Go module, a Rust crate, a PyPI sdist and wheel, a Swift package? **Measured 2026-09-13; results below.**
2. **Toolchain tolerance.** Does a source file holding only a package declaration and a doc comment compile cleanly, emit no class file, and raise no warning — in Kotlin particularly, where there is no sanctioned form, and in Java under a name other than `package-info.java`? **Measured 2026-09-13; results below.**
3. **Harvestability.** Can a harvester find it by filename convention alone, without a parser?
4. **Collision with human documentation.** `package-info.java` Javadoc *becomes* the package summary page a person reads. Does skill-shaped content — severity-graded mistakes, Wrong/Correct pairs — degrade that page, or does it read acceptably to both audiences?
5. **Uptake.** With the file present versus absent, on a misuse task shaped like the guiding case, does an agent's use of the library change? This only means something combined with a trigger; measured alone it will reproduce the not-looking.

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

## Recommendation

**Not a commitment. Test 1 was the whole claim, and it holds.** A documentation-only `skill-info` file survives the ordinary publishing path of every ecosystem measured with no configuration, the Kotlin Multiplatform case included. The exception is a JavaScript bundle, which carries no source by design. What is left open is not distribution but everything after it: whether a harvester finds the file (test 3), whether it harms the human docs (test 4), and whether an agent reads it (test 5, which needs a trigger).

**The design question to settle first is reuse versus a dedicated file.**

- **Reusing the existing slot** — writing skill content into `package-info.java`, `doc.go` or `//!` — needs no new convention and is picked up by every existing tool. But it makes the skill the page a human reads, which is test 4, and in Kotlin there is no slot to reuse.
- **A dedicated file** — a `skill-info` source file in the package — keeps the two audiences apart and gives Kotlin a home. It is a new convention, and a new convention has to be adopted.

**The hyphen is the one rule that works everywhere tested.** An earlier draft of this record argued from recall that the hyphen was a Java-only trick that buys nothing elsewhere and breaks Python. Test 2 contradicts both halves. Every toolchain tested accepted `skill-info`, and the hyphen buys something everywhere: **on a case-insensitive filesystem — the default on macOS and Windows — `skill.kt` and `Skill.kt` are the same file**, so a plain `skill` name collides with any type called `Skill` in that package, and a hyphen cannot appear in a type name in Java, Kotlin, Swift, TypeScript or C#. In Python the hyphen that stops an `import` statement is harmless — nothing needs to import a skill — and the file cannot shadow a module name. In Rust it keeps the file out of compilation while `cargo package` still ships it. Go accepts `skill-info.go` too. **So the name is `skill-info` in every language** — `skill-info.java`, `skill-info.kt`, `skill-info.py`, `skill-info.ts`, `skill-info.swift`, `skill-info.go`, `skill-info.rs`.

**Place it in the package, not at the root.** Every precedent is per-package, and a library of several packages has several things to say.

**What would change the answer.** Test 1 found no ecosystem that strips the file from its source distribution; if the unmeasured paths above — the classic Android plugin, other JavaScript build tools — turn out to drop it, the convention narrows to the ecosystems where it was measured. If test 4 shows skill content makes the human docs worse, reuse is off and only the dedicated file survives.

## Connections

- [ADR-0009](Decisions-ADR-0009-Transport-Is-Sources-Jar) — the sources jar as the carrier; this puts the skill inside what ADR-0009 already chose.
- [RAD-0065](Research-RAD-0065-What-V1-Skill-Authors-Wrote-Unprompted) — why skills as resources did not reach the sources jar.
- [RAD-0011](Research-RAD-0011-Existing-Documentation-Systems-As-Skill-Content) — package docs as existing content, including the `doc.go` measurement.
- [RAD-0072](Research-RAD-0072-The-One-Thing-We-Are-Not-Doing) — the shipped-skill field, where the in-artifact route has no standard.
- `docs/knowledge/reference/doc-comment-systems.md` — the per-ecosystem doc conventions this relies on.
- `README.md` — the misuse case, which shows why distribution alone is not enough.

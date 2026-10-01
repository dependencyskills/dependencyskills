# Naming the Skill File

RAD-0075 · 2026-09-17

Keywords: what to call the skill file; skill-info.kt is awkward; SKILL.md inside a package; does a markdown file survive a sources jar; package-info.kt for Kotlin; AGENTS.md in a library; skill.kt collides with a class; will an agent recognise the file; agentskills SKILL.md convention; a non-source file in src/main/kotlin; setuptools drops data files; SwiftPM unhandled file warning.

Measured against: Claude Code 2.1.270 and Antigravity 1.2.2 over 15 uptake runs · Gradle 9.7.1 with Kotlin 2.4.20 · Maven 3.9.16 with maven-source-plugin 3.4.0 · uv 0.12.5 with setuptools, hatchling and uv_build · npm 11.13.0 with TypeScript 7.0.2 · Go 1.27.1 · Cargo 1.98.1 · Swift 6.4 · Claude Code 2.1.270 on its default model · Antigravity 1.2.2 · local Gradle and Maven caches holding 4,513 sources jars · 2026-09-17. Harness: `experiments/skill-as-source/naming-candidates/`.

## Question

[RAD-0073](RAD-0073-a-skill-written-as-source.md) settled that a library can ship a skill as a file inside its source, filed under the namespace it documents, and chose `skill-info.<ext>` — a documentation-only source file named by analogy with `package-info.java`. The name was measured as legal and collision-free everywhere it was tried. It was never chosen for being a good name, and it reads as an awkward borrowing.

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
- **Its effect on generated documentation** is measured below: none, in eight of eight generators.
- **Loaded directly as a skill, package names collide.** If an agent harness loaded every package's `SKILL.md` as a skill in its own right, a dozen libraries each with a `util` or `core` package would collide on `name`. The generated pointer skill ([RAD-0073](RAD-0073-a-skill-written-as-source.md)) avoids this by indexing them under their coordinates.

### A skills directory beside the source, and what makes it ship

npm's authors keep skills in a directory beside the source rather than among it. The JVM equivalent is `src/main/skills/`, which is part of the source tree — the skill is source, and belongs there. Measured 2026-09-17 on Gradle 9.7.1 with Kotlin 2.4.20 and Maven 3.9.16; harness `skills-source-dir.sh`.

| placement | build change | sources jar | binary jar |
|---|---|---|---|
| `src/skills/` or `src/main/skills/` | none | **absent** | absent |
| `src/main/skills/` | registered as a Kotlin source directory | **yes** | absent |
| `src/main/skills/` | registered as a resource directory | **yes** | **yes** |
| `src/main/skills/` | added to the `sourcesJar` task | **yes**, under a `skills/` prefix | absent |
| beside the code, in the package directory | none | **yes** | absent |
| Kotlin Multiplatform `src/skills/` | none | **absent from every target's sources jar** | — |
| Maven `src/main/skills/` | none | **absent** | absent |
| Maven `src/main/skills/` | declared as a `<resource>` | **yes** | **yes** |

**A directory beside the source does not ship by itself.** Gradle packages the source directories a source set declares; Maven packages compile roots and declared resources. npm publishes a directory tree, so its `skills/` folder travels for free; the JVM publishes what the build declares, so this placement always costs a line of configuration — which a plugin can supply, along with a task to create the directory and a check at publish time that it is present and registered.

### Coordinates in the path remove the collision

An earlier measurement here put the same flat path in two artifacts and the merge failed. That was the path's fault, not the placement's. **Coordinates are unique by construction, so a path that carries them cannot clash.** Re-measured with `src/main/skills/io/github/acme/acme-a/SKILL.md` and `…/acme-b/SKILL.md` in two libraries that also share a package, registered as resource directories and merged into a fat jar with `DuplicatesStrategy.FAIL`: **the build succeeded and both skills survived**, at their own paths, in both the binary and the sources jars.

The same trick has an older precedent in the filename rather than the directory — `META-INF` properties files are routinely named for the coordinate that owns them — and that spelling avoids a directory tree that looks like a package but is not one, which is the awkward part of the path form.

**This is a JVM problem only.** Every other package manager keeps a dependency's files under its own directory, so the package's identity already scopes the path; the JVM merges artifacts into one namespace — a classpath, a fat jar, an APK — and throws the coordinate away when it does. What travels through that merge intact is whatever the path itself says.

**The binary copy has a use of its own.** Registering the directory as a resource puts the skill in the binary jar as well, which reaches libraries that publish no sources jar at all — common for commercial and obfuscated artifacts. It is JVM, Android and JS only: a Kotlin/Native klib carries no resources.

### One skill, or one per platform? The source set already answers it

A multiplatform library is not called the same way on every target. A Kotlin `suspend fun` reaches a JS consumer as a promise to await, a Swift consumer as async/await or a completion handler, and a Java consumer as a blocking call or a future — and the `-jvm` artifact's consumer may be writing Java rather than Kotlin. Wrong-and-right pairs, the most valuable thing a skill carries, are therefore **per platform**, even when the API is common. A single skill can show the idiom for one target and leave the others to guess.

Measured 2026-09-17 on a four-target library (JVM, JS, linuxX64) with a skill in `commonMain` and a second in `jvmMain`, both in the package directory:

| sources jar | skills it carries |
|---|---|
| `-jvm` | `jvmMain/…/SKILL.md` **and** `commonMain/…/SKILL.md` |
| `-js` | `commonMain/…` only |
| `-linuxx64` | `commonMain/…` only |
| root, common | `commonMain/…` only |

**The source set scopes the skill exactly as it scopes the code, for free.** One skill in `commonMain` is the default and reaches every target. A platform skill is added only where a target differs — a JVM-only entry point, an Android lifecycle rule, a JS interop quirk — and only that platform's consumer sees it, alongside the common one rather than instead of it. Nothing had to be configured for this: Kotlin Multiplatform already assembles each target's sources jar from its own source set plus the common one.

This is an argument for the in-package placement over a separate `src/main/skills/` directory: the scoping is inherited, where a directory beside the source would need the same per-source-set wiring rebuilt by hand.

**A consumer resolves one artifact, so there is no cross-platform ambiguity.** Kotlin Multiplatform publishes a sources jar per platform artifact — each carrying its own source set *and* the common one — plus a root artifact holding only the common sources. A JVM build resolves `…-jvm` and gets one jar containing `jvmMain/` and `commonMain/`; nothing it holds describes JS. A harvester needs no multiplatform rule beyond reading the source-set prefix: what is in the jar it resolved is what applies, with the platform skill additive to the common one.

**The resource route cannot do this.** Per-source-set resource directories reach the JVM jar, an Android AAR and a JS klib, but **no Kotlin/Native klib carries resources at all**, and **no sources jar carries resources on any target** — measured in `resource-routes.sh`. So a `jvmSkills/`, `wasmJsSkills/`, `iosSkills/` arrangement would lose the native targets outright and, more importantly, would never reach the copy an agent reads. Source files inherit the scoping for free; resources do not.

**Open options, none of them measured.**

- **Nothing overrides anything today.** The two files sit at different paths in one jar — `commonMain/…/SKILL.md` and `jvmMain/…/SKILL.md` — and neither Gradle nor Kotlin resolves one against the other. "The platform skill wins" is a rule a harvester would implement or a convention authors would follow; it is not inherited from the build.
- **Which skill applies depends on the source set being edited, not only on the artifact resolved.** A developer editing `commonMain` wants the common skill, and a JVM-specific idiom would be wrong there; the same developer editing `jvmMain`, or writing a plain JVM application, wants the JVM one. The generated pointer currently matches on the import alone, so a multiplatform consumer's own file path would have to become part of the match.
- **A platform suffix is the alternative to a source-set directory**: `SKILL.jvm.md`, `SKILL.ios.md`, with the precedent of React Native's `Component.ios.js` and Android's resource qualifiers. It lets a library keep every variant together in `commonMain`, easy to read and diff in one place, with the harvester selecting by resolved target. The cost is that `commonMain` ships everywhere, so a JS consumer receives the JVM variant and has to be trusted to ignore it — where source-set placement makes the reach correct by construction.
- **Whether a platform skill repeats the common content or carries only the difference** is unsettled either way.

**What would settle them:** an uptake run where a common and a platform skill disagree, to see whether an agent follows the more specific one unprompted; and asking authors which arrangement they would actually keep consistent.

### A sources jar carries Kotlin, so it reaches Kotlin consumers only

Measured on the four-target library above: every sources jar holds **`.kt` files**, never generated output. The JS artifact's `jsMain/` and `commonMain/` are Kotlin; so are the native target's. An iOS artifact would carry `iosMain/*.kt`, not Swift or Objective-C.

That narrows who the sources-jar route actually serves:

- **A Kotlin consumer** — on the JVM or Android, or a multiplatform application targeting JS or iOS — resolves these Maven artifacts, reads Kotlin, and is the reader a Kotlin skill is written for. This is the route every measurement in this record covers.
- **A JavaScript or TypeScript consumer** never sees the jar. They install an npm package produced by the Kotlin/JS toolchain, so the skill would have to travel in that package — which is npm's own `skills/<name>/SKILL.md` ([RAD-0077](RAD-0077-the-npm-in-package-skill.md)).
- **A Swift consumer** gets an XCFramework through SPM or CocoaPods: Objective-C headers and a binary, with no Kotlin source.

**So one library has several distribution routes depending on who consumes it, and the skill's audience differs with them.** A skill in `jsMain` is written for a Kotlin developer targeting JS; a JavaScript developer installing the npm package is a different reader who may need different text.

**Every route, measured 2026-09-18** from one library carrying a skill in `commonMain`, `jvmMain`, `jsMain` and `iosArm64Main`, plus one as a `commonMain` resource. Harness: `kmp-consumer-routes.sh`.

| route, and who takes it | source-file skill | resource skill |
|---|---|---|
| Maven sources jars — JVM, JS, Wasm, iOS, common (**a Kotlin consumer**) | **yes**: `commonMain` in every one, the platform skill only in its own artifact | no |
| JVM jar, JS klib, Wasm klib | no | **yes** |
| iOS klibs, iOS metadata jars, the common jar | no | **no** |
| Kotlin/JS npm package (**a JavaScript consumer**) | no | **yes** — at whatever path it has under `resources/`, beside the `.js` and `.d.ts` |
| Kotlin/Wasm npm package | no | **yes** |
| XCFramework (**a Swift consumer**) | **no** — Objective-C headers and a binary, no Kotlin | no — but four other mechanisms reach it, below |

- **The source route covers Kotlin consumers completely**, with the source-set scoping intact, and covers nobody else.
- **The resource route is the only way to reach a JavaScript consumer of a Kotlin/JS library**: the `commonMain` resource travels into the published npm package, and lands at npm's own convention path for free (below).
- **A Swift consumer is reached by neither route**, because an XCFramework carries binaries and headers and takes nothing from a source set. That is a fact about these two mechanisms and not about the bundle, which turns out to carry a good deal — measured below.
- **The two routes are complementary rather than rival**: source for Kotlin consumers, resource for JavaScript ones, and a third mechanism entirely for Swift.

### What an XCFramework can carry

The table above recorded an XCFramework carrying nothing. That was true of the two mechanisms it tested and wrong as a general claim. Asked properly — every mechanism that could put text inside one — **a Swift consumer is reachable by four routes, and one of them costs nothing at all.** Measured 2026-09-18 on a two-target library, `iosArm64` and `iosSimulatorArm64`; harness `xcframework-routes.sh`.

| mechanism | in the linked `.framework` | in the assembled XCFramework | what it costs |
|---|---|---|---|
| a KDoc comment on an exported declaration | **yes**, in `Headers/AcmeText.h` | **yes**, in every slice | **nothing** |
| an exported `val skill = """…"""` | in the binary | **yes**, UTF-16 in the binary, and declared in the header as a property | a member of the public API |
| the same constant marked `internal` | **no** — eliminated | no | — |
| a `SKILL.md` written into the bundle | yes | **yes**, at the bundle root | a build plugin |
| a second header in `Headers/` | yes | **yes** | a build plugin, and it is not in the module map |
| a key appended to `Info.plist` | yes | **yes**, readable with `PlistBuddy` | a build plugin |
| a `SKILL.md` in `commonMain` or `iosArm64Main` source | no | no | — |
| a resource in `commonMain/resources` or `iosMain/resources` | no | no | — |

**The doc comment is the finding.** A KDoc on a declaration the library already exports arrives in the generated Objective-C umbrella header, in every slice, and a second build with `-Xexport-kdoc` removed produced the same header — so **the flag is not required**; Kotlin 2.4.20 exports KDoc to the header by default. This is the only route measured anywhere in this record that reaches a consumer with no build configuration, no plugin, and no file an author has to remember to ship. It is also the shape RAD-0073 began with and this record moved away from — the skill as a comment — coming back as the one thing that works for the single reader a markdown file cannot reach.

**The bundle tolerates more than expected.** `xcodebuild -create-xcframework` copies each `.framework` wholesale: a `SKILL.md` at the bundle root, an extra header, and an added `Info.plist` key all survive into every slice. So an XCFramework *can* carry a skill file; it simply has no way to acquire one, because nothing in the Kotlin/Native pipeline puts source or resources into a framework. Filling that gap is a plugin's job, and the plugin has to write from inside the link task — the bundle is that task's output, so editing it afterwards makes the task out of date, and the next build regenerates everything the linker owns, discarding an `Info.plist` edit while leaving foreign files untouched. This cost a run to learn: staged from a shell between two Gradle invocations, the same writes appeared to prove that a plist key cannot survive assembly, when what had happened was a relink.

**Two cautions on the plugin routes.** The extra header is not named in `module.modulemap`, so it is a file on disk rather than something a Swift consumer can `import` — enough for an agent reading the bundle, useless as an API. And the raw-string constant survives only because it is exported: marked `internal` it is eliminated from the binary outright, which is Kotlin/Native's counterpart to the R8 result above and the same bargain — the text survives by being public API.

### The npm package's path is the resources path, so npm's convention comes free

`META-INF` in the table above is not a convention and nothing in the toolchain chose it — it was the directory the earlier harness happened to create under `src/commonMain/resources`, and reading it back as a standard is exactly the kind of mistake a measurement should catch. **Kotlin/JS copies the resources root into the published package verbatim**, so the path under `resources/` is the path in the tarball.

Four placements, one library, one build, measured 2026-09-18; harness `npm-resource-root.sh`.

| placed under `src/commonMain/resources/` | where it lands in the npm package | JS klib | any sources jar |
|---|---|---|---|
| `META-INF/skills/acme-text.md` | `META-INF/skills/acme-text.md` | yes | no |
| `skills/acme-text/SKILL.md` — **npm's own convention** | `skills/acme-text/SKILL.md` | yes | no |
| `SKILL.md` at the root | `SKILL.md` | yes | no |
| `com/example/acme/text/SKILL.md` — the namespace path | `com/example/acme/text/SKILL.md` | yes | no |

- **A Kotlin Multiplatform library can therefore satisfy npm's convention exactly**, putting the file where a JavaScript agent already looks, with no plugin and no relocation step. That removes the only awkwardness in the route: the earlier reading, that reaching npm's convention would need the skill moved, was an artifact of the harness rather than a property of the toolchain.
- **The generated `package.json` declares no `files` entry**, so npm publishes the whole directory. Nothing has to be declared for the resource to ship — the opposite of a hand-written npm package, where the `files` entry is the mechanism ([RAD-0077](RAD-0077-the-npm-in-package-skill.md)).
- **The two placements are two files, not one.** A library serving both readers writes `SKILL.md` under `commonMain/kotlin/<namespace>/` for the Kotlin consumer and one under `commonMain/resources/skills/<name>/` for the JavaScript one. Whether a build can point a resource directory at the source-tree copy and ship a single file is not measured, and it is the kind of thing the plugin should do rather than the author.

### Does a markdown file reach the human's documentation

The criterion the source-file shape was measured on, asked of markdown. Each generator runs the standard way over a package holding one documented declaration and a `SKILL.md` beside it; a control counts files carrying the real doc comment, so a run that renders nothing proves nothing. Measured 2026-09-17 with the versions on the line above; harness `markdown-in-docs.sh`.

| generator | `SKILL.md` | `skill-info.<ext>` (RAD-0073) |
|---|---|---|
| Javadoc | stays out | stays out |
| Dokka | stays out | stays out |
| `go doc -all` | stays out | stays out with the comment below the package clause |
| rustdoc | stays out | stays out while no `mod` reaches it |
| pydoc | stays out | the file's **name** is listed |
| pdoc | stays out | **rendered** as a module page |
| TypeDoc | stays out | stays out |
| DocC | stays out, with the file excluded in `Package.swift` | stays out |

**Eight of eight ignore it**, controls confirming each rendered the real documentation. This removes the last measured difference that favoured a source file: markdown is invisible where `skill-info` was visible in Python, and equal everywhere else.

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

### What happens when two skills collide

A convention that names one file invites a collision wherever a build flattens several artifacts into one. Measured 2026-09-17 with Gradle 9.7.1, Kotlin 2.4.20 and AGP 9.3.2; harness `collisions.sh`.

| where they meet | result |
|---|---|
| one library, `SKILL.md` in both `src/main/kotlin` and `src/main/java` | **the build fails**: *"Entry com/example/acme/text/SKILL.md is a duplicate but no duplicate handling strategy has been set"* |
| two libraries sharing a namespace, each with its own `SKILL.md` | **both survive** — sources jars are never merged, and each is keyed by its coordinate |
| a fat jar merging two `META-INF/skills/…` resources | **the build fails** on Gradle's default strategy, and fails harder with `DuplicatesStrategy.FAIL` |
| an Android application merging two libraries' `res/raw/skill_….md` | **the build succeeds and one file silently disappears** — the APK carries the first dependency's copy, with no duplicate error and no warning |

**The source-tree placement is the one whose only collision is inside a single library, where it fails loudly for the person who can fix it.** The resource placements fail in a *consumer's* build instead: noisily in a fat jar, and silently on Android, which is the worst of the four — the application ships, and a skill is simply gone. That is an argument about placement rather than about the name, and it holds for whichever spelling is chosen.

### npm already has a convention, and it is not in the source tree

[RAD-0072](RAD-0072-the-one-thing-we-are-not-doing.md) measured this on 2026-09-12 by downloading published tarballs: npm packages already ship skills, as `skills/<name>/SKILL.md` **at the package root**, included by adding `skills` to `package.json`'s `files` array. `antfu/skills-npm`'s proposal states exactly that layout and that `files` entry; it is still titled a proposal, but the practice is real and has adopters. The one `SKILL.md` found in a working `node_modules` tree sits there too, at `get-tsconfig/skills/get-tsconfig/SKILL.md`.

**So npm's two losses in the table above do not need fixing; they need not to be fought.** A skill placed in `src/` and dropped by `files: ["dist"]` is a skill in the wrong place for that ecosystem. Following npm's own convention costs one `files` entry, is what agents already look for there, and both fixes measured for the source-tree placement — adding `src/**/SKILL.md` to `files`, or copying the file into `dist/` — are worse than simply using `skills/`.

**The grain is the same, expressed differently — and which way a JVM library should express it is open.** npm's unit is the published package — one skill for the library, named for it. This project's unit is the library's **root namespace**, which is the same grain expressed the way a JVM import can be matched: `import com.example.acme.text.Normalizer` finds the skill filed at `com/example/acme`. A library that genuinely spans namespaces may ship more than one, and `skills/<name>/SKILL.md` allows that too, since the directory name is free.

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

### The name the alpha adopted: the coordinate

Built and run rather than argued: the lightweight codex and the Gradle plugin name a library's skill for its **coordinate**, filed at `skills/<name>/SKILL.md` in the sources jar. The Agent Skills rules (1–64 characters, lowercase letters, digits, single hyphens; the directory must match) rule out the coordinate verbatim, and rule out underscores and `--` as separators, so `group:artifact` is lowercased and every run of other characters becomes one hyphen; past 64 the group shrinks to the first and last letter of each segment while the artifact stays whole; and past 64 even then it is cut and ends in eight hex digits of a SHA-256. The encoding is one-way, and nothing decodes it — a consumer encodes the coordinate of the artifact it resolved and compares, which is also the authorship check. Normative text: [`spec/content.md`](../../../spec/content.md) v4.

**Measured, 2026-09-23** — `naming-candidates/coordinate-lengths.py`, over 1,739 libraries in one machine's Gradle and Maven caches and all 1,753 in Google's Maven repository:

| | local caches | Google's Maven |
|---|---|---|
| median / p90 / p99 encoded length | 38 / 54 / 69 | 39 / 54 / 69 |
| longest | 80 | 91 |
| over 64, so cut | 2.0% | 2.1% |
| two libraries meeting at one name | 0 | 0 |

Gradle plugin markers (`<id>:<id>.gradle.plugin`) run to 141 but carry no code and never ship a skill, and are set aside. The one-way collision the encoding allows did not occur once.

**What to do past 64.** The first alpha simply cut the name, which kept the group and lost the artifact — `com.google.android.apps.common.testing.accessibility.framework:accessibility-test-framework` kept none of `accessibility-test-framework`. The alternatives, measured over the combined 3,184 libraries:

| scheme for names over 64 | collisions | artifact kept whole | last-resort cut |
|---|---|---|---|
| cut the full name, then hash (the first alpha) | 0 | 3,118 | 54 |
| group to initials, always | **18** | 3,184 | 0 |
| group hashed, always | 0 | 3,184 | 0 |
| initials + hash, only when over 64 | 0 | 3,180 | 4 |
| **group to first-and-last letters, only when over 64 — adopted** | **0** | **3,183** | **1** |
| first-and-last + hash, only when over 64 | 0 | 3,170 | 12 |

**Initials merge sibling groups, and the worst pair is the one that matters most**: `android.arch.core:core-testing` and `androidx.arch.core:core-testing` both became `a-a-c-core-testing`, the old and new homes of one library — exactly the drift a skill is for. `com.google.test.platform` and `com.google.testing.platform` met five times. First-and-last letters (`ad`/`ax`, `tt`/`tg`) separated every one. **A hash on top competes for the same 64 characters** and pushed twelve names into the last-resort cut, losing the artifact again. Hashing the group always is unique but changes every name to fix 2% of them, and hides who published it. The adopted rule keeps 98% of names as the full coordinate and every other name's artifact whole, except one: `com.android.tools.utp:android-test-plugin-host-additional-test-output-proto`, whose artifactId alone is 53 characters and which, as internal test-platform tooling, will never ship a skill.

**Uniqueness past step 1 is measured, not guaranteed.** Two groups differing only mid-segment — `feature` and `fixture` — would still meet. Nothing depends on it: a consumer finds a skill by its artifact's coordinate and only ever compares a name against that coordinate's encoding. Only a flat folder of many libraries' skills would feel a collision, which is the extraction pattern [RAD-0076](RAD-0076-skills-republished-by-a-third-party.md) rejects.

The directory's *name* is fixed by the specification; where it sits is not, so the flat `skills/<name>/` is a choice — npm's shape — rather than a requirement.

## Findings

**Measured.**

- A plain `SKILL.md` in a package's source directory survives the Gradle, Maven and Kotlin Multiplatform sources jars, hatchling and uv_build, a Go module zip, a Cargo crate, and npm without a `files` field.
- It is lost under setuptools and npm's `files: ["dist"]`, and SwiftPM warns about it.
- None of `SKILL.md`, `skill-info`, `package-skill` or `AGENTS.md` exists in 4,513 sources jars; `package-info.kt`, `README.md` and `Module.md` are already in use.
- A raw string constant reaches every sources jar **and** the JVM jar, the JS klib and the native klib; Dokka renders a public one's declaration but not its text, and an `internal` one not at all. R8 removes it from an application's release build unless a keep rule covers the library.
- Test 5 re-run with the skill as `SKILL.md` matches the source-file result across 15 runs: unprompted 4 of 6 misuse and 0 of 6 found it, pointer 6 of 6 read and followed, hook 3 of 3 corrected.
- setuptools ships `SKILL.md` with a `[tool.setuptools.package-data]` entry; npm ships it either from a `files` entry naming the source path or by copying it into `dist/`.
- Colliding skills fail the build in a source tree and in a fat jar, and are **silently dropped** by an Android resource merge — but only when the path is flat. A coordinate-scoped path merges cleanly with `DuplicatesStrategy.FAIL` and both skills survive.
- Every KMP sources jar carries Kotlin source, never generated JS or Swift, so the route serves Kotlin consumers; JS and Swift consumers receive different artifacts entirely.
- Measured across every KMP route: source files reach all Maven sources jars and nothing else; a `commonMain` resource reaches the JVM jar, the JS and Wasm klibs **and the published npm packages**; an XCFramework carries neither.
- A resource lands in the published npm package at exactly its path under `resources/` — so `resources/skills/<name>/SKILL.md` arrives at npm's own convention path, and `META-INF` was the earlier harness's choice of directory rather than anything the toolchain imposes. The generated `package.json` declares no `files` entry.
- An XCFramework carries four of the eight mechanisms tried: a KDoc comment reaches the generated Objective-C umbrella header **with no build configuration and no compiler flag**; an exported raw-string constant reaches the binary as UTF-16 and is declared in the header; and a `SKILL.md`, an extra header and an `Info.plist` key written into the bundle all survive `xcodebuild -create-xcframework`. Source files and resources reach none of it, and an `internal` constant is eliminated.
- The bundle is the link task's output, so a plugin must write into it from inside that task: a later edit makes the task out of date and the next build regenerates every file the linker owns.
- A skill in `commonMain` reaches every target's sources jar; one in `jvmMain` reaches only the JVM consumer, alongside the common one — the source set scopes the skill as it scopes the code, with no configuration.
- A skills directory beside the source (`src/main/skills/`) reaches no artifact until the build registers it: one line as a source directory, as a resource directory, or on the `sourcesJar` task, each with a different reach.

- The coordinate as a skill name needs shortening in about 2% of real libraries and runs to 91 characters at most outside plugin markers. Compacting the group to initials merged 18 libraries, including one library's old and new coordinates; first-and-last letters merged none of 3,184 and kept the artifact whole in all but one.

**Argued, not measured.**

- A markdown file is invisible to the documentation generators RAD-0073 tested.
- `AGENTS.md` inverts the audience, because harnesses discover it for whoever edits the directory.
- `SKILL.md` is recognised as a skill by name. The probe that pointed this way was primed.

## Recommendation

**Not a commitment.**

**`SKILL.md` in the package directory is the leading candidate**, and the reason is not the name alone: the measurement above removes the premise that ruled it out. It carries the skill in its own format, matches the specification that already exists, and cannot be misread by a compiler or collide with a type.

**In npm, follow npm.** Ship `skills/<name>/SKILL.md` at the package root with a `files` entry, as published packages already do, rather than placing a file in `src/`. A Kotlin Multiplatform library reaches the same path by putting the file under `commonMain/resources/skills/<name>/`, which needs no plugin and no `files` entry.

**For a Swift consumer the answer is a comment, not a file.** No markdown placement reaches an XCFramework and none can, so the skill for that reader travels as a KDoc on a declaration the library already exports — the one route measured here that costs nothing. The three bundle placements work and are worth a plugin's while for a library that wants a whole `SKILL.md` in front of a Swift developer's agent, but they are an addition to the comment rather than a replacement for it. This is the one place `skill-info.<ext>`'s premise still holds, and it holds because of the consumer rather than the tooling.

**Keep `skill-info.<ext>` as the fallback, not the rule**, for the places a markdown file needs configuration and a source file does not — setuptools and SwiftPM. Both fixes are one line and are **measured to work**: `[tool.setuptools.package-data]` puts `SKILL.md` in the wheel and the sdist; for npm, either a `files` entry naming the source path or a copy into `dist/` ships it, though the convention above is the better answer there. A harvester should recognise both; the lightweight codex's package index and pointer already do.

**What the evidence cannot settle.** Every measurement here is about packaging, tooling and rendering, and on those grounds the markdown file leads. None of it touches the questions a convention actually lives or dies on: whether library authors will write the file, which shape reads best to the person maintaining it, whether an agent meeting one unprompted opens it, and whether a name can be proposed to other ecosystems without being rejected as this project's private invention. Those want authors' opinions and an uptake measurement, not another packaging run.

**Measure before deciding:**

1. ~~Documentation generators with `SKILL.md` present~~ — done, above: eight of eight ignore it.
2. ~~Test 5 with the skill as `SKILL.md`~~ — done, above: no detectable difference. What remains is a fixture where an agent *does* explore, to test whether the spelling changes what it opens.
3. Whether SwiftPM's `exclude`, or declaring the file as a resource, is the better of the two one-line answers there; the setuptools and npm fixes are already verified.
4. Whether an agent working in Swift against an XCFramework actually reads a KDoc in the umbrella header, or a `SKILL.md` at the bundle root. Both are now measured to arrive; neither is measured to be read, and an uptake run on that consumer would be the first this record has done outside the JVM.

**What would change the answer.** If a documentation generator renders a stray `SKILL.md` onto a human's page, or agents treat a markdown file in a sources jar as less authoritative than a source file, `skill-info.<ext>` stays. If the one-line fixes prove fragile, the fallback becomes the rule for those ecosystems.

## Connections

- [RAD-0073](RAD-0073-a-skill-written-as-source.md) — the skill file under a library's root namespace, the measurements this relies on, and the generated pointer.
- [RAD-0072](RAD-0072-the-one-thing-we-are-not-doing.md) — the Agent Skills format and the shipped-skill field.
- [RAD-0074](RAD-0074-a-skill-built-from-the-documentation.md) — delivery as written in the lightweight codex.
- [RAD-0065](RAD-0065-what-v1-skill-authors-wrote-unprompted.md) — where v1 skills sat, and why resources failed.
- `docs/knowledge/reference/agent-file-conventions.md` — the inventory of every file name and path an agent already looks for, which this decision has to sit beside.

# A Skill Written as Source

RAD-0073 · 2026-09-13

Keywords: package-info.java for skills; a source file that is only documentation; doc.go as a skill carrier; ship a skill without a resource mechanism; source travels where resources do not; crate-level docs; module docstring; packageDocumentation; does kotlin have a package doc file; skill in the sources jar; will the toolchain accept a file with no code; does skill content pollute the rendered docs.

Measured against: nothing yet. This record frames an experiment; its findings are precedent and argument, and every claim about toolchain or packaging behaviour below is listed as something to test rather than something known.

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

**To test — nothing below is known.**

1. **Survival.** Does a documentation-only source file reach the published artifact unchanged — a Maven sources jar, each target of a Kotlin Multiplatform publication (does a `commonMain` file appear where a consumer resolves it?), an npm tarball, a Go module, a Rust crate, a PyPI sdist and wheel, a Swift package?
2. **Toolchain tolerance.** Does a source file holding only a package declaration and a doc comment compile cleanly, emit no class file, and raise no warning — in Kotlin particularly, where there is no sanctioned form, and in Java under a name other than `package-info.java`?
3. **Harvestability.** Can a harvester find it by filename convention alone, without a parser?
4. **Collision with human documentation.** `package-info.java` Javadoc *becomes* the package summary page a person reads. Does skill-shaped content — severity-graded mistakes, Wrong/Correct pairs — degrade that page, or does it read acceptably to both audiences?
5. **Uptake.** With the file present versus absent, on a misuse task shaped like the guiding case, does an agent's use of the library change? This only means something combined with a trigger; measured alone it will reproduce the not-looking.

## Recommendation

**Not a commitment. Run test 1 before any of the others**, because it is the whole claim: if a documentation-only source file does not survive packaging in most ecosystems, nothing else here matters. It is also the cheapest — build a trivial library per ecosystem, add the file, publish locally, and list the artifact.

**The design question to settle first is reuse versus a dedicated file.**

- **Reusing the existing slot** — writing skill content into `package-info.java`, `doc.go` or `//!` — needs no new convention and is picked up by every existing tool. But it makes the skill the page a human reads, which is test 4, and in Kotlin there is no slot to reuse.
- **A dedicated file** — a `skill` source file in the package — keeps the two audiences apart and gives Kotlin a home. It is a new convention, and a new convention has to be adopted.

**The naming cannot be one rule across languages.** `package-info.java` uses a hyphen because Java requires a public type to live in a file named after it; a hyphen is illegal in an identifier, so that file can never collide with a real class. That collision only exists where filename is coupled to type name. In Kotlin, Go, TypeScript, Swift and C# the coupling is absent, so a hyphen is permitted and buys nothing. In **Rust** and **Python** it actively breaks things — a module file must be a valid identifier, so `mod skill-info;` does not parse and a hyphenated `.py` cannot be imported. And some ecosystems have rules that matter more than the hyphen: Go ignores files beginning with `_` or `.` and treats `_test.go` and `_linux.go` suffixes as build constraints; Rust does not compile a file no `mod` declaration reaches, though `cargo package` may still ship it. So the filename is a per-language decision, and test 2 has to settle it language by language rather than borrow Java's. *(Recalled, not yet tested; toolchains available locally are `javac`, `kotlinc`, `python3`, `node` and `swiftc` — Go and Rust would need installing.)*

**Place it in the package, not at the root.** Every precedent is per-package, and a library of several packages has several things to say.

**What would change the answer.** If test 1 shows source files are routinely stripped or relocated in some ecosystem — generated sources, minified npm output, wheels without source — this becomes a JVM-and-Go convention rather than a universal one. If test 4 shows skill content makes the human docs worse, reuse is off and only the dedicated file survives.

## Connections

- [ADR-0009](Decisions-ADR-0009-Transport-Is-Sources-Jar) — the sources jar as the carrier; this puts the skill inside what ADR-0009 already chose.
- [RAD-0065](Research-RAD-0065-What-V1-Skill-Authors-Wrote-Unprompted) — why skills as resources did not reach the sources jar.
- [RAD-0011](Research-RAD-0011-Existing-Documentation-Systems-As-Skill-Content) — package docs as existing content, including the `doc.go` measurement.
- [RAD-0072](Research-RAD-0072-The-One-Thing-We-Are-Not-Doing) — the shipped-skill field, where the in-artifact route has no standard.
- `docs/knowledge/reference/doc-comment-systems.md` — the per-ecosystem doc conventions this relies on.
- `README.md` — the misuse case, which shows why distribution alone is not enough.

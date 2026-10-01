# Implementations

**Each directory is its own build root** — its own settings file, its own
wrapper, buildable and releasable on its own. Headless: runs in a build, in
CI, with no human present.

Most of them are build-system implementations, one directory per **build
system** and not per package ecosystem, because the two are not the same
thing and conflating them is the mistake this project exists to avoid. Each
is responsible for every channel a project built with it publishes to.

**`codex/`, `lightweight-codex/` and `agent-skills/` are the exceptions, and they are deliberate.** None of them depends on a build system, which is the point: a Maven plugin, a CLI or an MCP server must be able to use them. Keeping the codex out of `gradle/` is what stops it acquiring a Gradle dependency by proximity. The plugins take nothing from either codex; what they share with them is the report file's format, not code.

## Why build system rather than ecosystem

A Java library publishes to Maven. An npm package publishes to npm. For
those, build system and ecosystem coincide and the distinction never
surfaces.

**Kotlin Multiplatform is the case that breaks it**, and it is the case
this project is built for. One KMP source set publishes to Maven as a JVM
jar, to Maven again as an Android AAR, to Maven again as native and JS
klibs, to npm for JS and wasm consumers, and to Swift Package Manager or
CocoaPods for Apple targets. One project, one build, many channels — and a
consumer on any of them should be able to find the same skill.

So the Gradle implementation owns Maven *and* npm *and* SPM emission for
KMP projects, because Gradle is where that build lives. `npm/` is for a
package genuinely authored in npm by someone writing TypeScript; a KMP
library reaches npm consumers through Gradle and never touches it.

## Status

Experimental throughout, and nothing here is published.

| Directory | What it is | State |
|---|---|---|
| `agent-skills/` | this project's own agent skills: `librarian`, which tells an agent when to use the lookup, and `librarian-skill-author`, which an agent uses to write a library's skill | in use on our own projects |
| `lightweight-codex/` | the lookup: reads what a project uses, finds the skills its dependencies ship in the local caches and installed packages, and answers an agent over MCP or from a command. Python, no dependencies | tried on our own projects; how it is packaged is open |
| `gradle/` | the Gradle plugin `org.dependencyskills`: reports a project's dependencies, fetches their sources jars, packages and checks a library's skill, writes the agent skills | built; our libraries no longer use it, and it may not be published |
| `maven/` | the Maven plugin, `org.dependencyskills.maven:dependency-skills-maven-plugin`: the Gradle plugin's two halves as the `author` and `consumer` goals | built, as above |
| `codex/` | the heavier design: the store, harvester, classifier, encoder, runtime, index, summariser, indexer and MCP server — no build system | built and measured; not in use |
| `npm/` | npm tooling | not started, and may not be needed: an npm package ships `skills/` already |
| `swift/` | SPM tooling | not started ([RAD-0080](../docs/knowledge/research/RAD-0080-reaching-a-swift-consumer.md)) |

They are deliberately not the same size, and should not be made so. Maven and Gradle never unpack a dependency, so a consumer cannot see inside one — which is why a library's skill rides in the sources jar and a consumer needs something to read inside it. npm unpacks into `node_modules`, and Python, Go and Cargo install source, so the file is already on disk and the work is finding it. **The asymmetry is the argument this project is making about where the gap is.**

**A library needs none of this to ship a skill.** Ours write it at `src/commonMain/skills/<name>/SKILL.md` and add a few lines of build configuration that put it in every sources jar; [the site](https://dependencyskills.org/library-skills/) shows them. The plugins began as the way to do that, and became optional once the few lines were enough.

A CLI or an MCP server belongs here too, not under integrations — the line is whether a human is driving. The lookup's is in `lightweight-codex/`, the codex's in `codex/`, since neither is tied to a build system.

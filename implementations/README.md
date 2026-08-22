# Implementations

One directory per **build system**, not per package ecosystem — because the
two are not the same thing, and conflating them is the mistake this project
exists to avoid.

Each implementation is responsible for every channel a project built with
it publishes to. Headless: runs in a build, in CI, with no human present.

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

| Build system | Channels it must reach | State |
|---|---|---|
| `gradle/` | Maven (JVM, Android, native, JS), npm, SPM | publisher for Maven only; experimental |
| `maven/` | Maven | not started |
| `npm/` | npm | not started |
| `swift/` | SPM | not started |

They are deliberately not the same size, and should not be made so. Maven
and Gradle never unpack a dependency, so a consumer cannot see inside one —
which is why a published sidecar artifact and module metadata to declare it
are needed at all. npm unpacks into `node_modules` and SPM checks out
source, so the file is already visible and the work is an emit step plus a
manifest entry. **The asymmetry is the argument this project is making
about where the gap is.**

A CLI or an MCP server belongs here too, not under integrations — the line
is whether a human is driving.

## Plugin ids

One artifact per build system, several plugin ids from it. A consuming app
gets no publishing tasks and no validation of a skills directory it does not
have; a library that is also a consumer applies both.

| Id | Half | State |
|---|---|---|
| `org.dependencyskills.publish` | packs and publishes the sidecar | working |
| `org.dependencyskills.harvest` | resolves the graph and harvests | not built |

Published as `org.dependencyskills:gradle-plugin`.

## Consequences for the Gradle implementation

It carries more than the others by a wide margin: the sidecar artifact and
Gradle Module Metadata variant for Maven, plus an emit step per additional
channel a KMP project targets. The emit step is core rather than a later
nicety, because without it a KMP library ships a skill its iOS and JS
consumers cannot see.

`fixtures/` should include a KMP project publishing to three channels at
once. It is the case most likely to break and the one nobody else is
testing.

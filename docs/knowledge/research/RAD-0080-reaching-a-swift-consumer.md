# Reaching a Swift Consumer

RAD-0080 · 2026-09-28 · v1

Keywords: Swift and SwiftPM consumers; Swift Package Manager checkouts; Package.swift is code, not data; Package.resolved; which dependencies are direct; Xcode project packages; package identity and registry scope; naming a Swift package's skill; Kotlin Multiplatform to Swift; XCFramework and binary targets; `.build/artifacts`; KDoc in the umbrella header; a SKILL.md in the framework bundle; writing from inside the link task; CocoaPods; deferred, not rejected.

## Question

The lightweight codex now serves skills to Gradle, Maven, npm, Python, Go and Cargo projects. **What would it take to serve a Swift project**, and which of the ways a library reaches Swift is worth building first?

[RAD-0079](RAD-0079-what-each-ecosystem-needs-from-the-lightweight-codex.md) left Swift as "a separate question, following RAD-0075's routes". This record takes it up from the consumer's side. Where a skill survives packaging was measured in [RAD-0075](RAD-0075-naming-the-skill-file.md); nothing new was measured here.

## Trail

### Two ways a library reaches Swift

A Swift project gets a dependency in one of two shapes, and they have almost nothing in common:

- **As Swift source.** Most SwiftPM packages: the package's repository, or a registry's source archive of it, is checked out and compiled with the project. This is the source-shipping family RAD-0079 described — the package's own directory is on disk once it is resolved.
- **As a binary.** A Kotlin Multiplatform library usually reaches Swift as an XCFramework: a SwiftPM `binaryTarget` or a CocoaPods vendored framework, holding compiled code and Objective-C headers. No sources jar, no source tree — the binary-shipping family, with nothing a build of ours fetches.

Each was followed far enough to see what it would cost.

### Option 1: a SwiftPM source package, read like Go

**Where the skill is on disk.** SwiftPM checks a source dependency out whole: into `.build/checkouts/<name>/` for a command-line build, and into Xcode's `DerivedData/<project>-<hash>/SourcePackages/checkouts/<name>/` for an Xcode project. A registry package (SE-0292) arrives as an archive of its source. Either way a `skills/<name>/SKILL.md` at the package root is present after resolution, as it is in Go's module cache. Authoring needs no configuration: RAD-0075 found SwiftPM warns only about an unhandled file inside a target's source directory, and a `skills/` directory at the package root is in none.

**Which dependencies are in scope is the hard part.** The declared dependencies are in `Package.swift`, and `Package.swift` is Swift code: evaluating it means running the package's manifest, which the lookup must never do — the same line as never running a skill's `scripts/`. `Package.resolved` is data, and gives every resolved package with its identity, location and version, but does not mark which are direct. So the lookup would take versions from `Package.resolved` and the direct set from a text match on the `.package(url: …)` and `.package(id: …)` calls in `Package.swift` — correct for the manifests people write, wrong for one that builds its dependency list in code. An Xcode project records its packages in `project.pbxproj` (`XCRemoteSwiftPackageReference`), which is plain text and can be read the same way.

Rejected: running `swift package show-dependencies`, which is accurate precisely because it evaluates the manifest. A lookup inside an agent's turn does not run a build tool, and does not run package code.

**Naming needs a rule in the specification.** A registry package has an identity of the form `scope.name` — `acme.text` would be `acme-text`, the scope in the group's place as for npm. A package known only by its URL has no scope, and SwiftPM's own identity for it is the last path component, which is not unique: two hosts can each have a `text`. The URL's host and path, as for a Go module, is unique — `github.com/acme/text` would be `github-com-acme-text` — at the cost of a longer name and of a package's skill changing name if it moves host. Which of the two, or both by case, is the decision the specification would have to make.

### Option 2: a Kotlin Multiplatform library as an XCFramework

**What already reaches a Swift developer, at no cost.** RAD-0075 measured KDoc on exported declarations arriving in the framework's generated Objective-C umbrella header, in every slice, with no build configuration and no compiler flag. [`spec/content.md`](../../../spec/content.md) already asks for the traps a Swift caller must know to be in those doc comments as well as in the skill.

**What could reach it, for a plugin's work.** RAD-0075 also measured a `SKILL.md` written into the framework bundle surviving `xcodebuild -create-xcframework` into every slice. SwiftPM unpacks a binary target into `.build/artifacts/<package>/<target>/<Name>.xcframework/` — `SourcePackages/artifacts/` under Xcode — so the lookup could read a skill there, attributed to the package that declared the binary target. The cost is in the Gradle plugin: nothing in the Kotlin/Native pipeline puts a file into a framework, and RAD-0075 found the bundle is the link task's output, so the plugin must write from inside that task — an edit made afterwards is discarded by the next link.

**What is not known.** RAD-0075 recorded both routes as measured to *arrive* and neither as measured to be *read*: whether an agent working in Swift against an XCFramework opens a header's doc comment, or a `SKILL.md` at the bundle root, has not been tried. That is an uptake run, and it would be the first this record has done outside the JVM.

CocoaPods follows the same split: a source pod under `Pods/<Name>/`, read like option 1; a vendored framework, like option 2.

### Which first

Option 1 is small and certain: one ecosystem module of the kind already built for Go, plus a naming rule. Option 2 matters more to Kotlin Multiplatform libraries, whose Swift consumers are exactly the ones a sources jar never reaches, but it is plugin work inside Kotlin/Native's link task, and its value depends on an uptake question nobody has answered.

## Findings

**Inherited, measured in RAD-0075** (Swift 6.4, Kotlin 2.4.20, 2026-09-17 and 2026-09-18):

- A markdown file inside a SwiftPM target's source directory builds, with a warning that it is unhandled.
- KDoc on exported declarations reaches an XCFramework's umbrella header with no configuration.
- A `SKILL.md` written into a framework bundle survives XCFramework assembly; it must be written from inside the link task.
- Neither route has been measured to be read by an agent.

**Argued, from SwiftPM's documented behaviour, not verified here:**

- A source package's whole checkout, `skills/` included, is on disk after resolution, in `.build/checkouts/` or Xcode's `SourcePackages/checkouts/`.
- A binary target is unpacked under `.build/artifacts/` or `SourcePackages/artifacts/`.
- `Package.resolved` gives versions but not which dependencies are direct; `Package.swift` gives the direct set only by evaluation, so a lookup can have it only by a text match.

## Recommendation

**Not a commitment, and not now.** Swift is deferred behind the ecosystems already served; nothing here is rejected.

1. **Source packages first, when Swift is taken up.** A module like Go's: versions from `Package.resolved`, the direct set from a text match on `Package.swift` and, for an Xcode project, `project.pbxproj`, skills from the checkout. Settle the naming rule in `spec/content.md` first — registry identity where there is one, host and path otherwise, is the leading candidate.
2. **Then the XCFramework route**, in the Gradle plugin: a `SKILL.md` written into the framework bundle from inside the link task, and read by the lookup from the unpacked artifact. **Run the uptake test before building the plugin half**, since whether a Swift agent reads a bundle's `SKILL.md` or a header's doc comment decides whether this route is worth its cost.
3. **Keep the doc-comment route as the baseline** either way: it costs nothing and already reaches every Swift consumer of a Kotlin Multiplatform library.

**What would change the answer.** If SwiftPM or Xcode gains a data form of the resolved *direct* dependencies, the text match goes. If an uptake run shows Swift agents read doc comments and ignore a bundle file, option 2's plugin work is not worth doing. If a registry identity becomes the norm for Swift packages, the URL-based name is only a fallback.

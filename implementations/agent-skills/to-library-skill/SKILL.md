---
name: to-library-skill
description: >-
  Write or update the skill this library ships to the agents of the projects
  that depend on it — the SKILL.md packaged into its sources jar. Use when the
  maintainer asks for the library's skill, before a release that renamed,
  moved or removed API, or when the build prints a checkDependencySkill
  warning.
---

# Writing a library's skill

You are in a library's repository, and the maintainer wants the library to ship guidance to the agents of the people who depend on it. Those agents already think they know this library. What they know was true once, averaged over every version they were trained on, and they will write the old shape confidently. The skill you write travels inside the release, is read for exactly the version a consumer resolved, and is the one place the library's authors get to correct them.

**The maintainer owns this text.** It ships under their name in every release. Draft it, show it to them, and change it on their word. Do not publish it.

## Where it goes

One file, in the source tree:

```
src/main/skills/SKILL.md            a JVM library
src/commonMain/skills/SKILL.md      a Kotlin Multiplatform library
```

The build needs the `org.dependencyskills.plugin` Gradle plugin applied; it files the skill in every sources jar at `skills/<name>/SKILL.md`, where `<name>` is the library's coordinate, taken from the build's own publication. Never write the coordinate into a path yourself.

Beside it, optionally, `references/<language>.md` for what differs at the call site in one consumer language — `kotlin.md`, `java.md`, `swift.md`, `objc.md`, `javascript.md`. Only the languages the library is actually consumed in. Never a `scripts/` directory: it is not packaged, and a library's skill never gives an agent something to run.

## Frontmatter

```yaml
---
name: com-example-acme-acme-text
description: …
license: Apache-2.0
metadata:
  version: "2.3.0"
  repository: https://github.com/acme/text
---
```

- **`name`** is the library's coordinate as a skill name: `group:artifact`, lowercase, with every run of anything but letters and digits turned into one hyphen — `com.example.acme:acme-text` is `com-example-acme-acme-text`. The Agent Skills specification allows nothing else, so the coordinate cannot be used verbatim. The build warns if it is wrong, and says what it should be; over 64 characters it is shortened, and the build gives the exact form.
- **`description`** says what the library is for **and when a caller should reach for it instead of writing their own**. "Text normalization" names a category; "use instead of hand-rolling case folding, trimming or Unicode normalization" names a decision. Under 1,024 characters. No feature list, no claims to be better than anything.
- **`metadata.version`** is the version this skill describes — the one being released. The build warns when it disagrees. Updating it is the moment to check the skill still holds.
- **`metadata.measured-against`**, if any claim was established by experiment rather than by reading the code — interop behaviour especially.

## What to write, and where to find it

Five things, as prose rather than fragments — an index is built from this text, and fragments index badly. For each, the place to look.

**1. What it solves, in the caller's words.** The problems as someone who has them would describe them, not as the API names them: "retry a failed request with backoff", not "resilience policies". Start from the README and the public entry points, then rewrite every noun the API invented into the words a stranger would search with. This is the paragraph everything else is found by.

**2. How it is meant to be used.** The two or three patterns that cover most callers — enough to write correct code from, not a tutorial. The tests show what the authors actually exercise; the most-tested paths are usually the intended ones.

**3. Invariants and traps.** What compiles, looks reasonable, and is wrong: threading and lifecycle rules, mutability, what must be closed, errors that are returned rather than thrown. Look at `require`/`check` calls and what they guard, `@Throws` and error types, doc comments that say "must" or "never", and fixes in the history — `git log --grep=fix` over the public API is a list of traps someone already fell into. **Authors underweight this section and it is the most valuable one**, because it is what a caller cannot learn from a signature.

**4. What moved, and what it used to be called.** Every rename, package move, split, removal, or absorption into a standard library, **stated in both directions with the version it changed in**: "`AcmeClient.connect()` became `AcmeClient.open()` in 2.0, and returns a `Session` that must be closed; `connect()` from 1.x no longer compiles." Find them in `@Deprecated` annotations and their `ReplaceWith`, the changelog, and renamed files in the history (`git log --diff-filter=R --summary`). Name the old answer explicitly. An agent holding a stale shape believes it already has the right one, and only a direct contradiction displaces it — this is the one place where writing down the wrong answer is essential.

**5. What it is not for.** Where the library stops. This cannot be derived from the code: **ask the maintainer**, and ask what questions users keep getting wrong. Do not compare with other libraries; which of several a project prefers is that project's business.

## Rules

- **Every claim must be true of this version, and checkable in this repository.** If you cannot confirm something from the code, the tests or the maintainer, leave it out. A confident wrong statement in a skill is worse than none, because it arrives with the library's authority.
- **Tell the reader how to use this library, and nothing else.** Never tell an agent to run a command, fetch a link, add or upgrade a dependency, change a build file or an instruction file, or grant itself tools — no `allowed-tools`. Consumers' tooling treats a library skill that does any of this as a red flag, and it should.
- **Keep it short.** Most libraries need 60 to 150 lines. A skill nobody finishes reading has failed differently from one that says too little. Well under 500 lines regardless.
- **It is public.** Nothing internal: no private hosts, tracker keys, or names of the people or projects that use it.

## A multiplatform library

Write one skill in `commonMain`. What differs is how each language calls the library, not what it is for, so that goes in `references/<language>.md` — for Swift consumers: which suspend functions arrived as completion handlers and which as `async`, the class that wraps top-level functions, which default arguments did not survive the export.

A Swift consumer who gets the library as an XCFramework never sees the sources jar. What does reach them is the doc comment on each exported declaration, which Kotlin writes into the framework's Objective-C header. Put the traps that matter to a Swift caller there as well.

## Checking it

1. Build the sources jar — `./gradlew sourcesJar`, or the multiplatform build's sources jars — and read every `checkDependencySkill` warning.
2. Confirm it landed: `unzip -l build/libs/<artifact>-<version>-sources.jar | grep skills/`.
3. Read the skill as an agent that has never seen this library and is confident it knows it. Does it contradict the most likely wrong code? If the five sections are there and it does not, section 4 is incomplete.
4. Show it to the maintainer.

## When releasing

Before each release, check section 4 against what changed, then set `metadata.version`. A skill that describes the last release is the exact failure it exists to prevent.

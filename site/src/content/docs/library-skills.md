---
title: How our libraries ship a skill
description: The skill format we went with, how we set it up on our own libraries, and the skill we use to write one. Experimental.
---

:::caution[Experimental]
This is what we went with on our own libraries, written down so it can be examined. It is not a recommendation, and any of it may change.
:::

A library's skill is a `SKILL.md` its authors write: how the library is meant to be used, what it is not for, and what goes wrong. It travels inside the library's own published artifact, so it always describes the version it shipped with. A coding agent working in a project that depends on the library can read it when it needs it.

## The skill

It is an ordinary [Agent Skill](https://agentskills.io/specification) — a directory holding a `SKILL.md` with `name` and `description` frontmatter, and optionally `references/` and `assets/`. Our [content specification](https://github.com/dependencyskills/dependencyskills/blob/master/spec/content.md) sets out the rest. In short:

- **The name is the library's coordinate**, made a legal skill name: lowercased, with every run of anything else turned into one hyphen. `io.github.aughtone:types` is `io-github-aughtone-types`; `com.example:acme-text` is `com-example-acme-text`. A coordinate too long for the 64-character limit is shortened by a fixed rule, so nobody chooses the name and two libraries cannot collide.
- **The description says what the library does**, as the tasks someone would search for, within the specification's 1,024 characters.
- **`metadata.version`** is the library version it describes.
- **It never gives an agent something to run.** No `scripts/`, no `allowed-tools`, and it never tells the agent to add or upgrade a dependency. A library's skill arrives in someone else's project, whose developer never read it.

## Where it lives

The skill is written in the source tree and ships in whatever the ecosystem already publishes.

| Ecosystem | Written at | Ships in |
|---|---|---|
| Kotlin Multiplatform | `src/commonMain/skills/<name>/SKILL.md` | `commonMain/skills/<name>/` in every target's sources jar |
| JVM (Gradle, Maven) | `src/main/skills/<name>/SKILL.md` | `skills/<name>/` in the sources jar |
| npm | `skills/<name>/SKILL.md` at the package root | the package, by a `files` entry |
| PyPI | `<import package>/skills/<name>/SKILL.md` | the wheel |
| Go | `skills/<name>/SKILL.md` at the module root | the module zip |
| Cargo | `skills/<name>/SKILL.md` at the crate root | the `.crate` |

npm packages already ship skills this way; the other rows follow the same shape. On the JVM the sources jar is the carrier because it is already on Maven Central for nearly every library and nothing reads it at build time.

## How we set it up

A directory beside the source is not something a sources jar picks up by itself, so the build needs a few lines saying to include it. On our Kotlin Multiplatform libraries that is all there is:

```kotlin
// Ships this library's agent skill in every sources jar, where a consumer's tooling reads it.
tasks.withType<Zip>()
    .matching { it.name == "sourcesJar" || it.name.endsWith("SourcesJar") }
    .configureEach {
        from("src/commonMain/skills") {
            include("*/SKILL.md", "*/references/**", "*/assets/**")
            into("commonMain/skills")
        }
    }
```

In a multi-module build, the same block inside `subprojects { }` in the root build file covers every module. A plain JVM library would use `src/main/skills` and `into("skills")`; we have not run that form on a published library yet.

To check it, build the sources jars and look inside one:

```bash
./gradlew sourcesJar
unzip -l build/libs/*-sources.jar | grep skills/
```

We first did this with a Gradle plugin of our own, which also checked the skill and printed its name. The libraries no longer use it, it is not published, and it may not be. A Maven plugin doing the same exists in the repository and is unpublished too.

`io.github.aughtone:types` 4.1.0, on Maven Central, carries its skill this way.

## Our skill for writing the file

We write each library's skill with an agent, using a skill of our own: [`librarian-skill-author`](https://github.com/dependencyskills/dependencyskills/tree/master/implementations/agent-skills/librarian-skill-author). It holds what we learned the hard way — what makes a description findable, how to check every claim against every source set, how to describe a trap by the symptom someone actually sees, and what a Swift or JavaScript caller needs to be told. To use it, copy the directory into the library's `.agents/skills/`, and into `.claude/skills/` for Claude Code, which does not read `.agents/skills/`.

## What reads it

The other half — a lookup that finds the skills a project's dependencies ship and hands the right one to an agent — exists in the repository and is being tried on our own projects. It is not published, and how it is packaged is still open. [How it works](/how-it-works/) describes the design behind it.

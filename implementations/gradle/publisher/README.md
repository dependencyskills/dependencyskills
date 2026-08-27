# publisher

A Gradle plugin that **validates** a library's authored agent skills — that
each `SKILL.md` conforms to the adopted [Agent Skills](https://agentskills.io)
format, and that skill names are legal as directories on a consumer's machine.

> ## Skeleton — a placeholder, not a finished tool
>
> This is deliberately minimal. An earlier design published a bespoke
> `-skills.zip` sidecar artifact; the project's research recommends against it
> — harvest the documentation that already ships (`-sources.jar`, KDoc)
> instead of shipping a new artifact — so the publishing half has been
> removed. See `docs/knowledge/research/` (RAD-0002).
>
> What remains is the part that is stable regardless of how skills travel:
> checking that an authored skill is well-formed. The plugin will grow as the
> consumer-side design settles; today it validates and nothing more. Nothing
> is published to the Gradle Plugin Portal or Maven Central.

## What it checks

**Format** (the adopted spec): a `SKILL.md` is present; `name` and
`description` are in the frontmatter; `name` matches the directory; no leftover
scaffold placeholder; `description` within the 1024-character cap (and a
warning below 40 — too thin to discriminate when several libraries overlap).

**Filesystem** (because a skill name becomes a directory on someone else's
machine): no trailing period or space; not a Windows reserved device name
(`CON`, `NUL`, `COM1`…); no leading underscore (Android strips `**/_*`); no two
names differing only by case; a warning on an unprefixed name, since a
consumer's skill namespace is flat across every dependency.

**Length** (a skill costs a consumer context and disk path): a `SKILL.md` body
over ~5,000 estimated tokens warns; a deep bundled path warns against Windows'
260-character limit.

## Using it

Author skills as plain directories:

```
src/agent-skills/
    org.dependencyskills.types/
        SKILL.md
```

Apply the plugin and run the check. Nothing is published yet, so consume it via
a composite build (`includeBuild`) or `publishToMavenLocal` while it changes:

```kotlin
plugins {
    id("org.dependencyskills.validate")
}
```

```
./gradlew validateAgentSkills
```

### Configuration

```kotlin
agentSkills {
    enabled = true                                                // default
    skillsDir = layout.projectDirectory.dir("src/agent-skills")   // default
    strict = true                                                 // default
}
```

| Option | Off means |
|---|---|
| `enabled` | The plugin does nothing; its task *skips* rather than fails. Also reads the `agentSkills.enabled` Gradle property, so CI can switch it off without a build-script edit. |
| `strict` | Validation errors are logged as warnings instead of failing the build. |

## Building

```
./gradlew test
```

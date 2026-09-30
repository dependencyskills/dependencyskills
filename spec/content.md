# What a dependency skill contains

Design attempt: **v4** · Status: draft, normative intent · Not yet released

**v4 (2026-09-23, extended 2026-09-28 and 2026-09-30):** the description names every major capability as a searcher's task; a skill is named for its library's coordinate — outside Maven coordinates, the package's namespace in the group's place, and a source-shipping package's own-named skill first-order and its others second-order; a dependency skill may direct nothing but the use of its own library; a Swift consumer of an XCFramework is reached through doc comments; validation checks `metadata.version` against the version being built. Each change follows an alpha that was built and run, not only argued.

The [Agent Skills specification](https://agentskills.io/specification)
defines what a skill *is* — a directory containing `SKILL.md`, with optional
`scripts/`, `references/` and `assets/` — and what its frontmatter fields
mean. It deliberately says nothing about what to write in one.

This document says what to write when the skill describes a **library a
project depends on**, which is a narrower and more answerable question than
"what goes in a skill". It is the part of this project with no prior art:
[library-skills.io](https://library-skills.io/create/) explicitly declines
to prescribe content, and the index in `discovery.md` is only ever as good
as the prose it is built from.

## Shape

```
<name>/
  SKILL.md
  references/
    kotlin.md
    swift.md
    javascript.md
```

### The name is the coordinate

**`<name>` is the library's coordinate, `group:artifact`, made a legal skill name**, and the directory carries the same name. The Agent Skills specification requires the directory to match `name` and allows 1–64 characters of lowercase letters, digits and single hyphens — no leading or trailing hyphen, no `--`. A coordinate has dots and a colon, so it cannot be used verbatim, and neither underscores nor a double hyphen are available as a separator. The encoding is:

1. Lowercase `group:artifact`, and replace every run of characters other than `a-z` and `0-9` with one hyphen, trimming hyphens from the ends. `com.example.acme:acme-text` becomes `com-example-acme-acme-text`.
2. If that is longer than 64 characters, shrink each segment of the **group** to its first and last letter — a one-letter segment stays as it is — and keep the artifact whole, encoded as in step 1: `com.google.android.apps.common.testing.accessibility.framework:accessibility-test-framework` becomes `cm-ge-ad-as-cn-tg-ay-fk-accessibility-test-framework`.
3. If that is still longer than 64, keep its first 55 characters, trim any trailing hyphen, and append a hyphen and the first eight hex digits of the SHA-256 of the original `group:artifact`.

The version is not part of the name; a multiplatform library's per-platform artifacts carry the base library's name.

**Outside Maven coordinates, a package's namespace takes the group's place** and the same three steps apply. npm's `@acme/text` is `acme-text`, as `acme:text` would be, and an unscoped `text` is `text`. A PyPI project and a Cargo crate have no namespace, so each is its name alone: `acme_text` is `acme-text`. A Go module's path up to its last segment is the group, so `example.com/acme/text` is `example-com-acme-text`.

**It is the coordinate rather than the artifactId because an artifactId is not unique** — two groups can each publish a `core` — and because an author never has to invent it: a build knows its own coordinate. That is also the rule this specification most expects generators to break, by using the coordinate verbatim.

**The encoding is one-way, and nothing decodes it.** `com.example:acme-text` and `com.example.acme:text` meet at the same name. A consumer never needs to go back from a name to a coordinate: it knows the coordinate of the artifact it resolved, encodes that, and compares. That comparison is also the authorship rule below.

**Measured, 2026-09-23** (`experiments/skill-as-source/naming-candidates/coordinate-lengths.py`): across 1,739 libraries in one developer machine's Gradle and Maven caches and all 1,753 in Google's Maven repository, step 1 gives a median length of 38–39, a 99th percentile of 69, and a longest of 80 and 91. **About 2% need step 2. One library in the combined 3,184 needed step 3** — an internal protobuf artifact of the Android Gradle plugin's test platform, whose artifactId alone is 53 characters. **No two libraries met at one name.** Gradle plugin marker artifacts, `<id>:<id>.gradle.plugin`, are longer still — up to 141 — but hold no code and never ship a skill.

**Why step 2 compacts the group, and why to first and last letters.** The artifact is the part a reader recognises, so it is the part kept. Plain initials were tried and merged sibling groups: `android.arch.core:core-testing` and `androidx.arch.core:core-testing` both became `a-a-c-core-testing` — the same library's old home and its new one, the exact case a skill exists to correct. First and last letters (`ad` and `ax`) kept every library in both samples apart. Adding a hash as well was measured and rejected: it competes with the artifact for the same 64 characters and pushed twelve names into step 3. Uniqueness past step 1 is therefore measured rather than guaranteed; a consumer never depends on it, because a skill is found by its artifact's coordinate and the name is only ever compared against that coordinate's encoding.

### Where the directory goes

The Agent Skills specification fixes the directory's name, not where it sits. Placement per ecosystem belongs to `publishing.md`, which is not written; until it is, the convention in use is:

| ecosystem | where the skill is authored | where it ships |
|---|---|---|
| npm | `skills/<name>/SKILL.md` at the package root | the same path in the package, by a `files` entry — the existing practice, conformed to |
| PyPI | `<import package>/skills/<name>/SKILL.md`, e.g. `src/acme_text/skills/acme-text/` | inside the import package in the wheel, which is what installs; setuptools needs a `package-data` entry, hatchling and uv_build need nothing (RAD-0075) |
| Go | `skills/<name>/SKILL.md` at the module root | the module zip, and so the module cache |
| Cargo | `skills/<name>/SKILL.md` at the crate root | the `.crate`, unless `include` or `exclude` leaves it out |
| JVM (Maven, Gradle) | `src/main/skills/<name>/SKILL.md` | `skills/<name>/` in the **sources jar**, added by the build |
| Kotlin Multiplatform | `src/commonMain/skills/<name>/SKILL.md` | `commonMain/skills/<name>/` in every target's sources jar |

**The skill is a valid skill directory where it is written**, named for the skill as the Agent Skills specification requires, so an ordinary validator accepts it in the source tree. The author never computes the name: the build knows the coordinate and can print it — the Gradle plugin's `dependencySkillName`, the Maven plugin's `name` goal, or, for a package with no build plugin, the `dependencyskills name` command. The whole directory ships — `SKILL.md`, `references/` and `assets/` — except `scripts/`, which a dependency skill never has. A binary jar, `META-INF` and a Kotlin/Native klib are not placements: none of them reaches the copy a consumer's tooling reads (RAD-0065, RAD-0075).

**Authorship.** On the JVM a consumer takes a skill only from the artifact whose coordinate its name encodes. An artifact that files a skill under another library's name is republishing it, and the skill is refused. This is checkable from the artifact alone, with no registry or signature behind it, and it is what stops a third party's text being served as a library's own.

**A package that ships its source may carry more than one skill**, and npm's existing practice is exactly that, under names the package's authors chose (RAD-0077). There the skill named for the package is **first-order**: listed and served first, as the package's own guide. Every other valid skill the package ships is **second-order**: kept, served after the first, and attributed to the package that carries it — never to another library, whatever its name suggests. Authorship holds without the name rule, because a skill found inside a package's own installed directory was shipped by that package; what must not happen is serving it as another library's (RAD-0079). A skill written to this specification is the package's first-order skill.

## Frontmatter

`name` and `description` are required by the spec. Beyond those:

```yaml
name: com-acme-acme-http
description: …
license: Apache-2.0
metadata:
  repository: https://github.com/acme/http
  version: "3.3.0"
  measured-against: "Kotlin 2.4, AGP 9.3"
```

`metadata` is a string-to-string map, so any list must be a delimited
string. Three keys matter.

**`version`** is the library version this skill describes, and it is the
whole reason a shipped skill beats a README. A model already knows your
library — stale, and averaged across every version it was trained on. It
writes against an API from two releases ago, or an idiom that compiles and
violates a threading rule you introduced last year. A skill that travels
*with the artifact* is the author's own account, version-matched to the
thing actually resolved. Omit this and you have given up the advantage.

**`repository`** so a reader can get to the source.

**`measured-against`** where any claim in the skill was established by
experiment rather than by reading your own code — interop behaviour
especially. It dates faster than everything else.

## The description

The field an agent sees before it has read anything else, and in a project
with hundreds of dependencies it may be all it ever sees.

Write **what the library is for, and when a caller should reach for it
instead of writing their own**. That second clause is what makes it useful
in an index: "an HTTP client" describes a category, "use instead of hand-
rolling retry, backoff and connection reuse" describes a decision.

**Name every major capability, as the task a caller would search for** — "format a date for display", "3 days ago", "file sizes in KB/MB/GB" — with the synonyms people use. It is also how a project that does not depend on the library yet finds it, and a capability left out is invisible to that search. This is a list of needs, not of features: the library's own type names are not it. In a family of sibling modules, say first what separates this one, and name a sibling at most once, by its coordinate. Leave out what the library re-exports and its dependencies' versions, which answer searches for the dependency. Measured on fourteen real skills, descriptions rewritten this way took the right library from 82% to 91% of searches in the top three, and from six searches with no match to one.

Do not restate the name. Do not claim superiority over alternatives — the consuming project decides which of its dependencies it prefers, and a description that argues is noise in an index built from hundreds of them.

## Body

Five things, in whatever order reads well. Prose, not bullet fragments —
the index is built from this text, and fragments index badly.

**What it solves, in the caller's words.** The problems, described the way
someone with the problem would describe them, not the way your API names
them. An agent searching for "retry with backoff" will not match
"resilience policies". This is the single highest-leverage paragraph in the
file, because it is what the index matches on.

**How it is meant to be used.** The two or three patterns that cover most
callers. Enough to write correct code from, not a tutorial.

**Invariants and traps.** What looks reasonable and is wrong here.
Threading and lifecycle rules, mutability, error handling, anything that
compiles and then misbehaves. Authors consistently underweight this and it
is the most valuable content in the file — it is what the caller cannot
learn from the signature.

**What moved, and what it used to be called.** A model's knowledge of a
library is averaged across every version it was trained on, and its
confidence tracks how often a shape appeared — so it is most confident
exactly when a long-established type has recently moved. Relocation to
another package, absorption into a platform standard library, a rename, a
split: in every case an agent keeps writing the old import and will insist
that it is the current one. Nothing in the resolved artifact corrects it,
because the old symbol is simply gone — there is no deprecation warning
left to read, only a compile error, which the agent then tries to fix by
adding back the dependency the type used to live in. The failure is
self-reinforcing and it costs a human real time to break.

State it in both directions: where it lives now, what it was called before,
and which version changed it. This is the one place where naming the
*wrong* answer is essential. An agent holding a stale prior is not looking
for information it lacks; it believes it already has it, and only a direct
contradiction displaces that. Version-matched provenance is what makes the
contradiction credible — see `metadata.version` above.

**What it is NOT for.** The negative boundary, and the field most often
missing. In a real dependency graph several libraries overlap: three HTTP
clients, two JSON serializers, more than one way to do dates. Overlap is a
property of the territory, not a defect. What makes an entry *usable* among
its siblings is knowing where it stops. Negative guidance also survives
retrieval error — an agent that lands on the wrong entry still gets
redirected.

**Provenance.** Where this came from and what version it describes, if not
already carried in `metadata`.

## Per-platform references

Everything above is platform-independent. The **call site** is not: the same
library reaches Kotlin, Swift and JavaScript consumers differently, and a
Kotlin-idiomatic example handed to an agent writing Swift produces code that
does not compile.

References are split **by language, not by platform** — what differs is how
you call the thing, not where it runs. Android and JVM consumers read the
same file; a Swift consumer on iOS and one on macOS read the same file.

Filenames are fixed by convention, so that a consumer can look for one
without a manifest telling it where to look:

| File | For consumers writing |
|---|---|
| `references/kotlin.md` | Kotlin — JVM, Android, KMP common |
| `references/java.md` | Java, where the Kotlin-facing API differs meaningfully |
| `references/swift.md` | Swift, via the Objective-C export or a shim |
| `references/objc.md` | Objective-C directly |
| `references/javascript.md` | JavaScript and TypeScript |

Ship only the languages your library is actually consumed in. A JVM-only
library has no Swift surface and needs no `swift.md`; this needs no
detection, because the author writes what exists.

A per-language reference carries **what a consumer cannot discover from the
API surface**. For a Kotlin Multiplatform library consumed from Swift that
means: which suspend functions arrived as completion handlers and which as
`async`, what the synthetic class wrapping top-level functions is called,
which default arguments did not survive the export, where generics eroded,
which parts of the API are effectively unusable and what to use instead.
This is the author's own knowledge and it is the reason the file is worth
writing.

Interop behaviour changes between toolchain releases. A reference that
asserts specifics should say what it was measured against.

### A Swift consumer who never sees the sources jar

A Kotlin Multiplatform library usually reaches Swift as an XCFramework through SPM or CocoaPods, and an XCFramework carries a binary and Objective-C headers — not the sources jar, so not `references/swift.md` either. **What does reach it is the doc comment on each exported declaration**: Kotlin writes KDoc into the framework's generated umbrella header, in every slice, with no build configuration and no compiler flag (measured on Kotlin 2.4.20, RAD-0075). So the traps a Swift caller must know belong in those doc comments as well as in the reference. It is the one route to that reader that costs nothing.

## What a dependency skill may direct

**How to use its own library, and nothing else.** A dependency skill never tells an agent to run a command, fetch a URL, add, remove or upgrade a dependency, change a build file or an instruction file, or grant itself tools — so no `allowed-tools`, and no `scripts/` directory. The Agent Skills specification permits all of these, for skills a developer installs deliberately. A dependency skill is not that: it arrives with a library, into the context of an agent working in someone else's project, and that project's developer never read it.

This is what lets a consumer serve library text as written rather than rewriting it. The bound can partly be checked mechanically, and a consumer should treat a dependency skill that crosses it as a finding rather than an instruction — the same rule any agent should apply to content it did not ask for. A legitimate skill never needs to cross it: correcting how an agent calls the library is the whole of what it is for.

## What this specification does not require

**No taxonomy.** Authors are not asked to classify their library into a
shared vocabulary of categories or capabilities. Every scheme that asked
publishers to self-classify has failed — UDDI, semantic-web service
discovery, npm keywords as a quality signal. Where publishers vastly
outnumber consumers, the complexity belongs with the consumers. **Authors
write prose; the index normalises.**

**No comparison with alternatives.** A library does not know what else is
on your classpath. Which of several overlapping dependencies a project
prefers is local knowledge and belongs in the consuming project's index,
not in any library's skill.

**No length target.** The spec's guidance — under about 5,000 tokens, under
about 500 lines — applies. Shorter is usually better; a skill nobody
finishes reading has failed differently from one that says too little.

## Validation

An implementation should **reject** a skill whose `name` disagrees with its directory, whose name is not the encoding of the coordinate of the artifact it ships in, whose `description` is absent, whose frontmatter has a field outside the six the Agent Skills specification defines (anything else belongs under `metadata`), or whose files break the filesystem rules in `publishing.md`. These match the reference validator, `skills-ref`, which was run against this project's checkers on the same skills (2026-09-23) and agreed on every case once unknown fields were made an error. A publishing build should not ship a `scripts/` directory at all.

It should **warn** — not fail — when a library publishes for a language with no matching reference, when `metadata.version` is absent or is not the version being built, or when the description is very short. Each of those is usually a mistake and occasionally deliberate, and a specification that cannot tell the difference should say so rather than guess.

The Gradle plugin's alpha (`implementations/gradle`) currently *warns* where this says reject, on the name; that is deliberate for an alpha and is expected to tighten.

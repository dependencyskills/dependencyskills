# What a dependency skill contains

Design attempt: **v3** · Status: draft, normative intent · Not yet released

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

`<name>` must equal the `name` in the frontmatter, and must be lowercase
letters, digits and hyphens — a spec rule, restated here because it is the
one most often broken by generators that use the artifact coordinate
verbatim.

## Frontmatter

`name` and `description` are required by the spec. Beyond those:

```yaml
name: acme-http
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

Do not list features. Do not restate the name. Do not claim superiority
over alternatives — the consuming project decides which of its dependencies
it prefers, and a description that argues is noise in an index built from
hundreds of them.

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

An implementation should reject a skill whose `name` disagrees with its
directory, whose `description` is absent, or whose files break the
filesystem rules in `publishing.md`.

It should *warn* — not fail — when a library publishes for a language with
no matching reference, when `metadata.version` is absent, or when the
description is very short. Each of those is usually a mistake and
occasionally deliberate, and a specification that cannot tell the
difference should say so rather than guess.

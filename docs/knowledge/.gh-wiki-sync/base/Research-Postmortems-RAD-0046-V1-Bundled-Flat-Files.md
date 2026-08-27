# v1, a convention nothing could read

RAD-0046 · 2026-08-12 · v1
Keywords: what shipped and failed; the v1 bundled flat files; a convention nothing could read; why in-archive bundling broke on Android and KMP; what was actually learned; what we would tell someone attempting this; why the failure is inspectable in published artifacts.

Shipped in released libraries around April 2026; superseded by v2, then by
v3. This document describes what no longer applies.

> The proposal's changelog dates "v1" to 2026-08-02. That is when the
> *proposal document* first described the mechanism, months after the
> mechanism itself shipped. Do not read it as a release date.

This is the failure that produced most of what the current design knows.
It is included because it is **inspectable**: v1 is not a sketch that was
abandoned on a whiteboard, it is inside published artifacts on Maven
Central, and anyone can download one and see it. A convention proposal
carries more weight from someone who shipped one and watched it fail than
from someone who only reasoned about it.

## What problem it was answering

v1 began with two complaints, neither of them selection, and the second is
the one that mattered.

The first was **version drift**. A model writes against an API averaged
across every version it saw in training, so it produces code for a shape
the library no longer has — most stubbornly where something long-established
has recently moved or been renamed, because that is exactly where the old
shape dominates the training distribution. The idea was to ship the author's
own account of the current API next to the artifact, so the resolved version
could speak for itself.

The second was **reinvention**, and it decomposes into two failures that
need different mechanisms. The agent did not know the library was there,
because nothing announced it. And it could not have found it had it thought
to look, because there is nothing to look *at*: a resolved dependency graph
is coordinates and archives, and none of it is searchable. So it did the
reasonable thing and wrote its own. Not the wrong library — no library. A
hand-rolled helper that compiles, passes, and silently duplicates something
already tested and already on the classpath.

**A more capable model does not fix this.** The agent was not reasoning
badly; it was reasoning correctly over what it could see, and what it could
see did not include the library. Capability cannot substitute for
information that is not reachable. That is what makes this a distribution
problem rather than one to wait out, and it is why the answer has to be
*knowing* and *finding* — an always-resident trigger and an index — rather
than better prose in a file nothing loads.

Reinvention is the worse failure and much the harder to see. Drift produces
a compile error, so it announces itself. Picking the wrong library at least
produces code pointing at something real. Reinvention produces *working
code*, and the cost surfaces later in review, if anyone notices. It is the
failure a benchmark will not catch and a maintainer hits every day.

It also locates where a shipped skill earns the most. A model has priors
about a widely-used library — stale, but present. About a small, new or
private one it has close to nothing: nothing triggers, and there is no wrong
answer to correct, only an absence. **The value of shipping a skill runs
inversely to the model's training exposure to the library**, and by count
most of a dependency graph sits at the low-exposure end.

Stating this first changes how the content failure below should be read. If
the problem is "the model has the wrong API shape," then an API catalogue is
the correct answer — and a catalogue is exactly what v1's body was. The
content was not carelessness; it followed from the first complaint. What it
could not do was answer the second, because a catalogue nobody loads is not
a catalogue. Discovery had to work before content mattered at all, and v1
handed discovery to a human with a prompt to paste.

Both complaints are answered now, in different places. Drift, by
[`spec/content.md`](https://github.com/dependencyskills/dependencyskills/blob/HEAD/spec/content.md), which requires a skill to say
what moved, what it was called before and which version changed it, and by
`metadata.version`, which is what makes that account credible against a
stale prior. Reinvention, by ADR-0004
— one always-resident description whose entire job is to fire *before* a
helper gets written.

## What v1 was

A single markdown file per module, named for the Maven coordinate and
authored as a packaged resource:

```
<module>/src/commonMain/resources/META-INF/ai-skills/io.github.acme.thing.ai-skill.md
```

Frontmatter was bespoke throughout — `skill-id`, `spec-version`, `type`,
`scope`, `compatibility`. Note `spec-version`: v1 knew it was inventing a
format and versioned it, which is the moment to stop rather than proceed.

The body was organised as an **API index** — a toolbox catalogue of types
and functions grouped by domain — followed by compliance notes,
serialization notes, and an "Agent Onboarding" section of usage rules.

Discovery worked by asking the human to prime the agent. The library README
carried a "paste this prompt first" section telling an assistant to scan the
classpath for files with a particular prefix.

It shipped across several libraries and modules and is still in those
released versions. Maven Central does not un-publish.

## What went wrong

**It was not a skill.** This is the root cause and everything else is
downstream of it. The Agent Skills spec defines a skill as a directory
containing `SKILL.md`. A file called `<id>.ai-skill.md` is a markdown file
with a suggestive name and a private convention around it. Every agent that
understood skills — which is to say, every agent that looks for a directory
containing `SKILL.md` — walked straight past it. The only software that
could read a v1 file was software written specifically to read v1 files,
which defeats the entire purpose of shipping one.

**The frontmatter disagreed with the spec.** `skill-id` where the spec
wants `name`, so even a tool that found the file could not identify it the
same way anything else would.

**Discovery required a human to start it.** The magic prompt is the
tell: if a mechanism only works when someone pastes an instruction first,
it is not discovery, it is documentation with extra steps. The
[pnpm RFC](https://github.com/orgs/pnpm/discussions/13422) names this
exactly — existing conventions "put an adoption step in front of the
discovery mechanism". v1 put a *human* there.

**The file often was not there anyway.** It was authored at
`src/commonMain/resources/META-INF/ai-skills/`, which is exactly the path
that does not survive. Established later by building libraries and unzipping
the output: on Android Gradle Plugin 8 a KMP library's `commonMain/resources`
are silently dropped from the AAR
([KT-46493](https://youtrack.jetbrains.com/issue/KT-46493), open since
2021), AGP strips `META-INF/MANIFEST.MF` from an AAR's nested
`classes.jar` so there was nowhere to declare a location, and Kotlin/Native
does not package those resources at all. So even a consumer written to read
v1 would have found nothing on the platforms that mattered most.

**The content was the part a model already had.** The body was largely an
index of the API — types, functions, what they are called, grouped by
domain. A model can derive most of that from the signatures it can already
see, so the file spent its budget restating what was cheapest to obtain and
said comparatively little about the things it could not: the invariants,
the threading and lifecycle rules, what looks reasonable and is wrong, and
where the library's responsibility stops. This is a content failure
independent of the packaging one, and it would have limited v1's usefulness
even if every consumer had found the file. It is why `spec/content.md`
leads with problems in the caller's words and with traps, and treats an API
catalogue as the least valuable thing a skill can contain.

**Scanning was expensive for everyone.** Finding a bundled file means
opening archives. One real Gradle cache held 7,462 jars and 555 AARs, each
one opened to check for a file that is almost never present.

## What was actually learned

**A bundled artifact is only useful if it is the thing the ecosystem
already knows how to load.** Inventing a container format alongside a
perfectly good one buys nothing and costs a migration. This is the lesson
that generalises furthest, and it applies to the current design too — the
sidecar works because classified artifacts and module metadata variants are
mechanisms Gradle and Maven already have, not because they are clever.

**Inventing a format means owning its versioning forever.** `spec-version`
in v1's own frontmatter is the tell. The moment a convention needs to
version itself, it has become a specification with an audience of one, and
the cost of that is permanent — every consumer must now handle every
version you ever shipped.

**Format and discovery are separate problems.** v1 got both wrong in the
same file, which made the failure look like one thing. Fixing the format
(v2, a real `SKILL.md` in a real directory) did not fix discovery, and the
manifest attribute that was supposed to fix discovery could not reach an
AAR. Two problems, two mechanisms.

**A correct answer to a narrow problem still fails.** v1's content followed
logically from the problem it was handed and was wrong anyway, because the
problem was the presenting symptom rather than the disease. Scope the
problem before designing the artifact: version drift is one of several
things a consumer needs from a library skill, and a design that answers only
the complaint that prompted it produces something coherent in miniature and
useless at the scale it has to work at.

**Shipping is what produced the evidence.** None of the Android and native
packaging failures were visible from documentation — they were established
by building libraries and unzipping the output, and they remain
undocumented upstream. That is itself a finding: **a packaging convention
that can only be verified by experiment is not one a specification should
lean on.**

**Published is permanent.** The v1 files are in released versions forever.
Repositories can be migrated; artifacts cannot. This is why the current
design treats permanence as a hard constraint rather than a caution, why
the tooling ships a migration script, and why the README tells adopters to
take the first public outing on a pre-release version.

## What we would tell someone attempting this

Use the ecosystem's existing publishing mechanism, not a new one. Use the
spec's format exactly, including its frontmatter keys, even where you think
you have a better idea. Assume the file will not survive packaging until
you have unzipped the output and seen it. And do not ship it publicly until
both halves work — publishing and consuming — because the first artifact
you release is the one you cannot take back.

## Still unverified

Whether any agent ever successfully loaded a v1 file in the wild. The
mechanism required a human to paste a prompt, so absence of reports is
weak evidence either way, and no telemetry exists. It is possible v1 was
never used successfully once.

## References

- Two libraries the maintainer published carry v1 in released versions; the
  files are readable in the source tree under
  `src/commonMain/resources/META-INF/ai-skills/`
- ADR-0003 — what
  replaced it, and why in-archive bundling was dropped entirely
- a previous proposal — the public argument, whose
  "What we tried" section is the short form of this document

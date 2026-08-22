---
title: The research
description: The investigations behind the findings — question, trail, and recommendation.
---

Each investigation states a question, the trail of options weighed, the findings
(with measured kept separate from assumed), and a recommendation. A
recommendation that hardens into a commitment graduates to a
[decision](/decisions/). The full records live in the repository under
`docs/knowledge/research/`; 0001 and 0002 carry the measurements the rest lean
on.

## Cost of a skill per dependency

How many libraries is a real project actually working with — not how many it
declares, but how many an agent would have to know about — and what does one
skill per library cost in resident context?

**Findings.** The *importable* set — what a developer can call without touching
the build file — is 86–99% of the JVM dependency graph and 100% of npm and
Python. A published description costs about 60 tokens, so one skill per library
runs 20k–139k tokens resident before any work begins. There is no cheap
"declared" set to retreat to, which promotes the index from a nice-to-have to
the load-bearing part of the design.

## Existing documentation systems as transport and content

Does something suitable already travel with a library, so that a bespoke skill
artifact is unnecessary — and is what ships rich enough to use?

**Findings.** 93–98% of JVM artifacts publish `-sources.jar` (KDoc / Javadoc),
including Kotlin Multiplatform roots and 98% of Android AARs; Go ships 100%
doc-comment coverage; Swift ships a curated DocC catalogue with per-version
migration guides. The documentation already ships and already reaches the
consumer — so the recommendation is to harvest it rather than invent a new
artifact. Coverage is uneven and cultural: a 33% median, but 84% for
Java-majority libraries and 30% for Kotlin-majority ones on identical tooling.

## A central capability server for library discovery

Should one queryable service carry library capability information, asked by
need, instead of every library shipping a resident skill?

**Findings.** A query service replaces cost that scales with the number of
libraries with one small trigger plus a per-query cost — but it cannot address
*reinvention*, the case where the agent never thinks to ask. So it is a third
layer above the resident trigger and the local index, not a replacement for
them, and local-first: it sheds the governance, funding and
single-point-of-compromise of a central service, and works offline.

## External review of the proposal

What survives when the proposal meets an outside engineer with no investment in
the design?

**Findings.** Five objections. The reviewer independently reproduced the
project's load-bearing claims — that a better model does not fix this, that the
discriminating knowledge is local, that packaging is per-ecosystem. Two things
changed: selection should weight *declared over transitive* dependencies (a ~10×
filter, and the same filter shrinks the injection surface), and
development-time prompt injection is a real objection with no answer yet.

## A git-hosted codex

Could library guidance be served from the source repository the publishing
metadata already names, with no new artifact at all?

**Findings.** The repository link is present — an `scm` entry in about 90% of
POMs — but it does not identify the released version (a usable tag appears in
only 2%), so version-exact content must still come from `-sources.jar`, not git.
Worth specifying as an *additional* route, gated on first measuring whether
repository hosts tolerate the request pattern.

## Development-time prompt injection

Does shipping agent-readable guidance with libraries open a large new
prompt-injection surface through transitive dependencies?

**Findings.** The surface is 112–995 libraries, 70–90% of it transitive, and the
target is the model's *judgement* — which no existing supply-chain control
(signatures, SBOMs, SAST) addresses. The load-bearing question, whether an
injected instruction in a doc comment actually redirects an agent, is unmeasured
and cheaply testable. The mitigations: treat library content as quoted evidence,
never a directive; and weight declared over transitive, which cuts the trusted
surface roughly tenfold.

## Choosing between overlapping libraries

Is library overlap real and demonstrable, what signals discriminate, and which
part genuinely cannot be computed?

**Findings.** On the JVM, apparent overlap is mostly an artifact of scanning the
wrong set — Now in Android's "three JSON libraries" is one on the importable
set. Across four JVM graphs there was not a single genuine two-alternatives
case; in npm it is pervasive. Much of the relationship between libraries
(alternative, implementation-of, facade, companion) is derivable from published
dependency scope and edge direction — but *which one this project prefers, and
why not the others* is local knowledge no library can carry.

## Reusing existing indexers, and what to index

Can the indexing be borrowed from the tools IDEs and language servers already
use — and once we can extract from any library, should we index the whole graph
or only libraries that ship a deliberate skill?

**Findings.** The extraction is a solved, permissively-licensed problem in every
ecosystem: ASM and kotlin-metadata read a JVM library's API straight from the
jar, Dokka reads its KDoc, and the TypeScript, Go and Python toolchains do the
same for theirs — so the harvester is an integration job, not a from-scratch
build, and the archive is a wall only to a directory scan, not to a reader. On
scope, ignoring libraries without a deliberate skill would cut clutter and
reward adoption, but it excludes exactly the obscure libraries where a skill
helps most — so the reconciliation is to index everything and treat a
deliberate skill as a preferred, higher-ranked tier rather than a hard gate.

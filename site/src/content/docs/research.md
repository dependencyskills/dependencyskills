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
development-time prompt injection is a real objection — since measured, and
answered in the architecture rather than in the prose.

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
(signatures, SBOMs, SAST) addresses. The load-bearing question — whether an
instruction planted in a doc comment actually redirects an agent — is now
measured, across agents from several vendors, and the answer is yes for many of
them. More usefully, it is measured against the obvious mitigation. [The full study is here](/injection/).

Presenting the text as quoted, untrusted data helps a great deal, and on several
agents it stopped every attempt. But it is **not sufficient**, in two ways that
matter for anyone building this. Moving the identical text into the *system*
channel defeats it outright — one model that resisted every quoted attempt
complied with almost all of them once the same words sat in the system prompt.
And a payload that simply argues the untrusted-data framing is itself a test
harness defeats it on models it otherwise protects: the framing is a piece of
text a model can be talked out of. On strongly instruction-tuned local coding
models it showed no protective effect at all.

Exposure varied enormously between models of comparable capability, including
within a single vendor's range — which is evidence that resistance is a training
choice rather than a scale effect, and that a bigger model is not a safer
reader. That is the reason the control cannot be left to the agent: the codex
cannot choose or verify which agent reads it. With real tools rather than code
generation the stakes are concrete — given a plausible pretext, an agent copied
a planted credentials file into a log.

So the mitigations are architectural: library prose goes where it *cannot* be
followed rather than where it is merely labelled untrustworthy, and declared
dependencies are weighted over transitive, which cuts the trusted surface
roughly tenfold. Per-model numbers, the payloads, the transcripts and a runnable
kit are published with the finding; they are single measurements at small sample
sizes, date- and version-stamped, and are meant to be re-run rather than
believed.

## Information-flow control as the trust model

The mitigation above still routes the decision through the model: the text
arrives, the agent reads it, and whether harm follows depends on the agent
declining to act. Every failure measured was a failure of exactly that
judgement. Is there a control that does not depend on it — and is it already
specified well enough to adopt rather than invent?

**Findings.** Not yet measured; this opens the investigation. There is a
published model to adopt — information-flow control, where content carries
integrity and confidentiality labels that propagate through tool calls and
policy is enforced deterministically before a sensitive tool runs, so
persuasion is assumed to succeed and simply cannot reach anything that matters.
The seam is the interesting part: enforcement at the sink lives in an agent
runtime, and a codex sits upstream of one, so the half it can contribute is
**labelling** — the codex is the only component that knows whether an entry came
from first-party source, a declared dependency or the transitive tail. The
experiment reuses the tool-action rig with the question changed from *did the
model comply* to *did compliance reach a sink*, and is deliberately falsifiable:
the flow-control arm is allowed to fail the compliance test provided the write
is refused every time.

## What a harvester could refuse to index

Labelling asks a downstream runtime to honour something. But a harvester does own
one boundary outright — the moment content enters the index — so a third question
is whether it should simply refuse content whose documentation does not match the
code it ships with.

**Findings.** Measured, and the answer is no. Checking prose against the declared
surface of the library that shipped it is precise on real documentation — 1.3%
false positives on endpoint references, under 6% on symbols — and it catches both
payloads that did real harm in our own tests. But scoring a defence against
payloads you wrote yourself is circular, so we ran it against an independent
benchmark of 91 malicious and 50 benign agent skills published with the
literature above. **It catches 36% of prose-borne attacks it did not author**, and
the misses are structural rather than fixable: data leaving through the agent's
own output instead of a network call, agent-config poisoning, resource abuse,
encoded payloads, and payloads sitting in helper code where a prose check never
looks.

A control that misses two thirds of an independent benchmark cannot be an
admission rule — refusing content on it would remove real capability while
leaving those classes untouched. So the signal is kept as a **label and a
warning**, where a false positive costs a glance, and the gate is not built. The
day spent measuring prevented shipping something that looked principled and
scored well against our own payloads. Every attack class it misses names nothing
foreign, which is exactly what enforcing at the sink handles and detection
cannot.

## What the transitive tail is worth

Weighting declared dependencies above transitive ones is settled here — it is a
ranking rule, a security default, and roughly a tenfold cut to the surface an
agent is asked to trust. But it was adopted twice on separate grounds and on
both occasions the benefit was established while the cost was assumed.

**Findings.** Not yet measured. Two things are worth testing rather than
asserting. The strong form of the rule — that declared dependencies may be *all*
a codex needs — would convert a ranking preference into an outright exclusion,
and that is a much larger claim than anything currently recorded. And a third
argument for the rule may exist that needs no attacker at all: a transitive
capability can change on a version bump nobody in the consuming project made or
reviewed, so an index built over the tail rots quietly, with nothing failing to
compile to signal it. Against both sits a real tension — transitive dependencies
are on the classpath regardless, so excluding them from the index removes the
codex's *endorsement* of a capability rather than the agent's *access* to it.
The retrieval rig above prices the question directly: build the index twice, once
each way, and measure recall on the same needs.

## Where the judgement should live

A fork this project has been walking past. Everything built so far makes the
model **better informed** — harvest what the code documents, index it, retrieve
the right entry. It does not make the model **more constrained**. Two of our own
measurements sit awkwardly beside that: selection is 0/18 unaided, resolved only
by a preference a human wrote down, and every injection control routed through
the model's judgement was defeated on some agent. Those are the same result in
two domains — *where a decision is left to model judgement, it is unreliable* —
and only the security half has had an architectural answer.

**Findings.** Nothing measured; this is the earliest-stage question in the set.
The alternative is a deterministic harness that carries the engineering
judgement itself, with the model's non-determinism confined to specific call
sites — possibly expressed as a language in which a dependency exposes a checked
contract rather than prose, closing the free-text channel at the import boundary
by construction. The strongest objection comes from this project's own prior
work: structured author tags were already tried, and delivery was never the
problem — agents are not trained to treat any channel as untrusted. So the test
is not whether such a language would be pleasant but **what it would refuse that
a notation cannot**. The two approaches most likely compose, with a codex as the
fact source a deterministic harness consumes, and that is worth establishing
before either is treated as the answer. The prior art here is unsurveyed, and
that survey is the first task.

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

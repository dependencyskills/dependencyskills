# External Review of the Publishing Proposal

RAD-0004 · 2026-08-14 · v1
Keywords: what does someone outside the project think of this; objections to shipping library skills; why not just expect standard documentation; the prompt-injection objection nothing answered; intent clobbering; independent arrival at using KDoc; platform-specific packaging; what survives a reader with no investment.

**Measured against:** nothing new. This record is a conversation, not an
experiment. Where it cites numbers they come from
[RAD-0001](Research-RAD-0001-Cost-Of-A-Skill-Per-Dependency) and
[RAD-0002](Research-RAD-0002-Existing-Documentation-Systems-As-Skill-Transport), both measured
2026-08-13.

## Question

The v3 proposal was circulated to one external reviewer, an engineer outside
the project, who read it (or had it summarised) and responded with five
objections. What survives contact with a reader who has no investment in the
design?

This record exists for three reasons. Two of the objections are not addressed
anywhere in the repository. The conversation produced one piece of
observational evidence that bears directly on the project's central claim. And
the reviewer arrived independently at the KDoc mechanism the maintainer was
already considering, which is corroboration worth recording — a convention is
a bet that other people will find the same shape natural, and this is the
cheapest evidence available that the bet is sound.

**The reviewer is not named, and his words are not quoted.** He was asked
whether he wanted to be credited publicly and declined, which is the right
instinct: nobody should be named on something they had no hand in publishing
or reviewing. Permission to feed his feedback into an agent was never consent
to appear in a public repository.

The same reasoning applies to the words themselves, and `AGENTS.md` says so
directly — *verbatim quotes from a working conversation leak all of that at
once, and documents generated straight from a conversation are where it
happens.* An earlier draft of this record quoted him extensively. Those quotes
have been replaced with paraphrase. What is preserved is the substance of each
objection and the reasoning it produced, which is what the project needs; what
is discarded is one side of a private conversation, which is his.

## Trail

### 1. Why ship skills at all, rather than expecting standard documentation?

The first question, and the one the whole project stands or falls on.

The answer given: standard documentation works, but the agent has to be told
to use it, and *when it is sure it knows, it does not bother* — which depends
on how it was trained. The proposal is an attempt to circumvent that training.

The reviewer pushed on it usefully: could you not just instruct the agent —
"you can only use a `libX` function if you can find it on the classpath or in
the spec"? Force a check instead of shipping data.

**The answer is experience, and it is worth recording as evidence.** That has
been tried. It works *sort of*: the instruction has to go in the agent's
instruction file, and even then the agent does not always check. The worst
observed case was a package move for a core class, where the agent insisted on
the old one. It already *knows* how to format a date, so it rebuilds it.

The reviewer's own diagnosis of why is the sharpest statement of the problem
in the exchange, and it is worth carrying in paraphrase: a model trained on
billions of lines using the established version of a big library holds far
stronger internal associations for the old patterns than for a new library, so
instruction alone may not be enough — the model would have to be trained to
accept alternative answers and weight them differently, which amounts to
post-training rather than prompting.

That is the README's "a better model does not fix this" claim, arrived at
independently by someone arguing the other side, and it converts an assertion
into a shared conclusion.

### 2. Prompt injection — the objection nothing in the repository answers

The reviewer asked whether making library guidance discoverable creates a
large new surface for prompt injection arriving through transitive
dependencies.

The response was that the risk exists anyway and has not changed since
repositories existed: you either trust a library from Maven Central and its
verified publisher, or you do not.

**The reviewer's counter is the part that lands**, and it is not answered by
the trust argument. His point, paraphrased: this moves the injection point
from runtime to development time, so it needs security controls in the
development phase rather than a SAST step in CI.

That is a real change in the threat model, not a restatement. A malicious
dependency at runtime executes in a sandbox someone has thought about. A
malicious *instruction* at development time executes inside an agent holding
the developer's credentials, editing the source tree, with the output landing
in a commit. SAST runs after the fact on code the agent already wrote.

**And the measurements make the surface much larger than the discussion
assumed.** RAD-0002 established that the importable set is 86–99% of the
resolved graph on the JVM and 100% in npm and pip. A design that harvests
documentation from everything reachable is ingesting attacker-controllable
natural language from **311 libraries** for Now in Android, or **995** for a
large Next.js application — not from the 12 to 102 a developer chose.

Two counter-points from the discussion are worth keeping. Central requires a
verified publisher, so this is not anonymous. And an agent reading a skill has
*a chance to notice* something malicious, where a library being executed has
none — the surface is wider but not blind.

Two observations of my own, neither settled:

- **The KDoc pivot narrows the surface somewhat, and for a reason worth
  stating.** A `-skills.zip` is a file authored specifically to be read and
  acted on by an agent — its entire purpose is instruction, which is also the
  ideal shape for an injected payload. KDoc is documentation attached to
  declarations, read by humans and doc processors for twenty years, in a
  format where an instruction-shaped paragraph is anomalous. That is a weaker
  guarantee than a security control, but "anomalous" is something a scanner
  can be built around, and "this file is instructions" is not.
- **A central capability server changes the threat model in both directions.**
  It is one intermediary that could scan, sanitise, sign and revoke — which no
  per-artifact convention can do. It is also one point of compromise serving
  every agent that queries it. Both belong in [RAD-0003](Research-RAD-0003-Central-Capability-Server).

**This objection needs its own investigation.** It is the kind of thing that
sinks a proposal in public review, the project has no answer written down, and
"the risk hasn't changed" is not one — because the reviewer is right that it
has.

### 3. Intent clobbering, and the heuristic that falls out of it

His example: a project that includes both a node-graph library and a
graph-rendering library may find each describes itself in terms that collide
with the other, leading an agent to the wrong one for the task in hand.

This is ADR-0004's overlap problem, stated by someone meeting it cold, and the
reviewer's suggested remedy — that the consuming project supply its own
`dep_usage.md` — is the same conclusion §4 reaches: the discriminating
knowledge is local, and no library can author it.

The discussion produced three candidate resolutions, none implemented:

1. **Favour explicitly declared libraries over transitive ones**, and rank
   transitives by distance from the root.
2. **Ask the developer** when the choice is genuinely ambiguous.
3. Accept that this may not be solvable at the library level at all.

**Option 1 is more attractive than it looked in the conversation, because
RAD-0002 gives it a size.** Declared is 3× to 18× smaller than importable on
the JVM — 13 declared against 238 importable for Compose Multiplatform's
`codeviewer`, 102 against 311 for Now in Android. So "prefer what the project
declared" is not a tie-breaker; it is a filter that removes roughly nine
tenths of the candidates, and the resolved graph already carries the
information needed to apply it.

**The same filter answers the injection objection.** Weighting declared
dependencies above transitive ones both narrows selection to what the
developer chose *and* narrows the trusted-instruction surface from 311 sources
to 102. One mechanism, two objections, and it is computable from data the
build already has. That is the most useful thing to come out of this review.

The reviewer's later framing is also worth carrying, in paraphrase: a
librarian can take account of *why the consuming project pulled a dependency
in* when deciding which of several candidates is meant. Intent of declaration
is itself a selection signal, and it is local.

### 4. KDoc: independent arrival at an idea already held

Recorded because RAD-0002 presented this as though it fell out of the
measurements.

**The maintainer already held the idea** — it predates this conversation and
may have been discussed earlier. What the review contributed was not the
insight but **corroboration**: an engineer outside the project, reasoning from
his own experience, proposed the same mechanism unprompted. That is worth more
than novelty. A convention is a bet that other people will find the same shape
natural, and one independent arrival is the cheapest evidence available that
the bet is sound.

His framing was the old-school one, in paraphrase: rather than skill files,
use a documentation-annotation reader at development time — or a code-parsing
pre-step in ecosystems like node — and have the server extract code contracts
and embedded documentation from that.

He also noted the honest objection in the same breath — it requires authors to
put annotations and doc comments through their code instead of writing a skill
file, so it is not obviously a net gain.

The reply identified why it is a gain anyway, and it is the thesis RAD-0002
went on to measure: KDoc describes what a thing does, it helps with
code the agent can already see, **and the source is already published to
Central, so it can build the librarian's index with no extra artifact.**

RAD-0002 then measured the three claims that argument rests on:
`-sources.jar` is published by 95.3% of Central and 93.3% of Google Maven
coordinates (98% of AARs), 82% of those jars carry a real source tree, and KMP
root modules publish it reliably. The idea survived measurement. Its weak
point — that only 33% of public declarations are documented, 30% in Kotlin —
was not visible at the time it was proposed.

### 5. Packaging: platform-specific, and "nothing reads the archive" restated

Two smaller points, both of which sharpen existing positions rather than
changing them.

The reviewer preferred the separate `-skills` artifact over in-jar bundling on
a ground the proposal does not currently state: bundling bloats the runtime
library. And he asked whether the layout has to be identical across platforms,
expecting a node project would embed it, and wondering whether the uniformity
was a Kotlin Multiplatform concern rather than a general one.

The answer is no, and it is ADR-0007: each platform gets what it expects, a
KMP author can build in Kotlin and ship a node dependency, and the packaging
answer is per-ecosystem by design. The reviewer arriving at the same question
independently is a signal that the proposal does not say this early enough.

His challenge to §3 is more substantive, and in paraphrase: files inside a jar
can be read from the classpath without unpacking it to disk, and since the
skills are read at development time rather than at runtime, the tooling has
access to the jar anyway — so what exactly is the Android problem?

He is right that classpath access at development time is possible, and the
proposal's §3 reads as though it were not. **The actual argument against
scanning is cost and reliability, not access** — one real cache held 7,462
jars and 555 AARs, each of which would be opened looking for a file that is
almost never there, and the AAR path drops the file anyway. §3 should say that
plainly, because as written it invites exactly this correction.

### 6. The local MCP server, and why it may be the better first target

The reviewer proposed a variant that [RAD-0003](Research-RAD-0003-Central-Capability-Server)
does not consider, in paraphrase: a documentation MCP server that inspects the
project's folder paths or the classpath to locate what it needs, indexing at
startup and watching for changes during development.

RAD-0003 treats the capability server as a **central** service and inherits
all the governance, operation, funding and single-point-of-compromise
questions that come with one. A **local** server has the same central property
that matters — resident cost drops from O(number of libraries) to O(1) trigger
plus per-query results — with none of those questions. It works offline, needs
nobody to operate it, has no trust boundary beyond the machine, and it is the
local librarian of ADR-0004 exposed as a tool rather than as a file.

The response in the conversation was that scanning specifically is not the
answer, per §3, and that is right about the *scanning*. But it does not
dispose of the local-server *shape*, which is separable: a local MCP server
whose index is built from resolved-graph metadata and harvested
`-sources.jar` content, rather than by walking a directory, keeps the shape
and drops the objection.

**This is probably the shortest path to something demonstrable.** It needs no
central service, no ecosystem adoption, and no publisher action — RAD-0002
showed the source is already there for 93–98% of a graph. It should be added
to RAD-0003 as a distinct option rather than left implicit.

### 7. The observation that supports the central claim

Mid-conversation the maintainer shared an instance — a screenshot, not
reproduced here — of an agent writing its own version of something that
already existed **in code it could see**, in the same project. His reading:
the librarian is not only for external libraries; it has a job to do over
local code as well.

This matters more than its casual framing suggests. The project's argument has
been that the JVM's opacity causes reinvention: the agent cannot see into the
archives, so it rebuilds what it cannot find. **This is a case where the code
was fully visible and reinvention happened anyway.**

So visibility is necessary and not sufficient. The failure is not only that
the library cannot be found; it is that the agent does not go looking, because
it is confident it already knows. That is the same mechanism as the
package-move case in §1, and it is a second independent argument that the
resident trigger of ADR-0004 is irreducible — a conclusion
[RAD-0003](Research-RAD-0003-Central-Capability-Server) reaches from the other direction,
that a query service cannot answer a question nobody asked.

It also extends the librarian's scope to first-party code in a multi-module
project, where there is no transport problem at all, and where — the reviewer
asked the fair question of whether this is just cosmetic — the answer given
was that it is not: the concern is that agent-written code is correct but
unreadable, and that an established internal pattern exists precisely so the
next reader does not have to reconstruct it.

## Findings

**Unanswered, and needs its own record.** Development-time prompt injection.
The trust argument does not address the reviewer's point that the injection
*moment* moves from runtime to development, where the agent holds credentials
and writes to the source tree, and where SAST arrives too late. The harvest
surface is 311 to 995 libraries depending on ecosystem, per RAD-0002.

**Changed by the review.**

- Selection should weight **declared over transitive**, with distance from
  root as a secondary signal. RAD-0002's measurements make this a ~10× filter
  rather than a tie-break, and it narrows the injection surface by the same
  factor.
- RAD-0003 should carry a **local** MCP server as a distinct option from the
  central one; it gets the O(1) resident-cost property without the governance
  problem, and it is the shortest path to a demonstration.
- The librarian's scope includes **first-party code**, on the evidence of an
  observed reinvention inside visible source.
- §3 of the proposal should argue **cost and reliability**, not access.
  Classpath reading at development time is possible and the section implies
  otherwise.

**Confirmed by the review, independently.**

- That a better model does not fix this, and that instructing the agent to
  check first is unreliable — tested, and it does not hold.
- That the discriminating knowledge is local and no library can author it; the
  reviewer proposed a consumer-side `dep_usage.md` unprompted.
- That packaging is per-ecosystem, not one layout — ADR-0007, reached
  independently.
- That a skill per library is a non-starter in npm on volume grounds; RAD-0001
  measured 995 libraries and ~62.9k tokens for the p90 case.

**Out of scope but recorded.** The reviewer speculated about project-specific
models updated as the project changes, with the model treated as a versioned
dependency. Interesting, orthogonal, and not this project's problem.

## Recommendation

**Open a RAD on development-time prompt injection before the proposal
circulates further.** It is the only objection here with no answer anywhere in
the repository, it is the one most likely to be raised publicly, and the
honest current position — that the risk is unchanged — is wrong in the
specific way the reviewer identified. That record should cover: what an
injected instruction can do at development time that it cannot at runtime;
whether harvested documentation is a materially different surface from an
authored skill file; whether a verified-publisher requirement is a meaningful
control; and whether a central intermediary is net positive or a new single
point of failure.

**Adopt declared-over-transitive as a first-class ranking rule** in ADR-0004's
codex, and say in the ADR that it serves both selection and injection-surface
reduction. It is computable from the resolved graph, it is measured at roughly
10×, and it is the only proposal here that answers two objections at once.

**Add the local MCP server to RAD-0003** as an option distinct from the
central service, and note that it is likely the first demonstrable artifact.

**Record the corroboration, not an origin.** The KDoc idea was already held;
what this conversation adds is that someone outside the project proposed it
independently. RAD-0002 should say that, because a convention's central bet is
that others find the same shape natural, and independent arrival is evidence
for it. Getting more eyes on the argument is worth more than being first to
it.

**Naming is settled: he declined, and the record stays unattributed.** Nothing
derived from this document should name him or quote him, and the paraphrasing
applied here should be applied to anything that carries his objections
forward. If a public version of the proposal wants to acknowledge external
review, it can do so without identifying who gave it.

**What would change the answer.** If development-time injection turns out to
be adequately mitigated by existing supply-chain controls — verified
publishers, signed artifacts, dependency review — then the objection reduces
to the trust argument after all and this becomes a paragraph rather than a
record. Nothing here establishes that either way.

## Connections

- [RAD-0002](Research-RAD-0002-Existing-Documentation-Systems-As-Skill-Transport) — the KDoc
  pivot, which originated in §4 of this conversation.
- [RAD-0003](Research-RAD-0003-Central-Capability-Server) — the capability server; §6 here
  adds the local variant.
- [RAD-0001](Research-RAD-0001-Cost-Of-A-Skill-Per-Dependency) — the volume numbers that make
  §3's filter a 10× reduction rather than a tie-break.
- ADR-0004 — overlap and the two layers;
  both are reinforced.
- [ADR-0007](Decisions-ADR-0007-Conform-To-Existing-Conventions) — per-ecosystem
  packaging, confirmed independently by the reviewer.

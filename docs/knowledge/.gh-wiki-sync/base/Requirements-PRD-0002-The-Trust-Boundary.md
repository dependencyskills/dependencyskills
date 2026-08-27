# The Trust Boundary

PRD-0002 · 2026-08-26 · v1

Keywords: prompt injection in library documentation; third-party docs reach the agent verbatim; can a README tell my coding agent what to do; filter library text before an agent reads it; why not just detect the malicious text; why not tell the model to ignore instructions in data; quarantine untrusted prose; rewrite instead of scan; is the index safe to trust; what this does not protect against.

## Problem

Library documentation is prose written by whoever published the library, and an agent reading it treats it as material to act on. Some of it is hostile. This is not a hazard the codex creates — it is one already running on every agent that reads a dependency's docs, which is all of them.

**Agents read library prose constantly, and nothing checks it.** A README, a source file in `node_modules`, a hover in the IDE, a docs page fetched mid-task. The whole emerging standards layer around agents is built to do this deliberately and at scale — skill formats, documentation services, manifest conventions — and **every one of them moves third-party prose straight into an agent's context verbatim**. On ecosystems that explode their dependencies onto disk, there is not even a moment where the text is admitted: every README and docstring from every transitive dependency is already in the working tree, and an agent that greps the codebase reads it without anyone deciding that it should.

**Very little stands in the way of it.** A model's own resistance to following instructions it finds in library text varies enormously between models of similar capability *within a single vendor's range*, tracking how a model was trained rather than how good it is ([RAD-0006](Research-RAD-0006-Development-Time-Prompt-Injection)) — so it cannot be relied on. Quoting the text as untrusted data helps and is defeated outright by moving the same words somewhere else. Off-the-shelf security tooling does not see it at all: SpotBugs with find-sec-bugs and Semgrep with its published rulesets each caught **0 of 11** payloads, against positive controls that fired — because SAST looks for code that misbehaves at runtime and this misbehaves at *reading* time and executes nothing ([RAD-0028](Research-RAD-0028-Sast-Tooling-As-A-Detection-Layer)). And nothing filters at the point the text is read: the same payload written in five languages' native documentation conventions was delivered perfectly intact by every parser tried ([RAD-0024](Research-RAD-0024-Does-The-Pipeline-Filter-Injection)).

**Detection is the wrong shape for the job, and this project measured why.** Detectors nest: stacking a second one on a first added nothing, because the second catches a subset of what the first does ([RAD-0033](Research-RAD-0033-Do-Form-Constraints-Compose)). The channel is wider than it looks — prose rides through a *method name* verbatim, and a camel-cased imperative needs no escaping in any language ([RAD-0027](Research-RAD-0027-The-Identifier-As-A-Free-Text-Channel)). And an agent that reads an injected instruction and writes it into its own doc comment promotes third-party text to first-party trusted source, running the trust lattice backwards ([RAD-0029](Research-RAD-0029-The-Agent-As-A-Trust-Launderer)).

**What an index changes is that there is finally somewhere to put a control.** Library text reaches an agent by a dozen uncontrolled routes today, which is much of the reason nothing checks it. An index is the first arrangement where all of it passes through one place. The rewriting step and the filter are only what we chose to put there; the structural change is the single door.

Why now: the codex ([PRD-0001](Requirements-PRD-0001-The-Dependency-Codex)) is being built, and it creates the door. Building it without the control is the one version of this project that would make things worse — it pulls in *more* third-party text than an agent would have read by itself, hundreds of libraries rather than the handful it happened to open. The trade is breadth for control, and it is only a good trade if the control is there.

**The honest limit.** On an ecosystem that explodes its dependencies onto disk, the codex can control what it *serves* and cannot stop an agent reading `node_modules` directly. The control is real where the packaging already keeps the text out of the working tree — the JVM, where nothing unpacks — and partial where it does not.

## Goals / Non-goals

### Goals

- Library prose never reaches an agent verbatim through this tool.
- The mechanism does not depend on recognizing anything, so a payload nobody anticipated is handled by the same path as ordinary text.
- A rejected or unprocessable item **degrades** to something less useful; it never silently disappears, and it never silently passes.
- What the controls do and do not catch is published with the numbers, including the parts that fail.

### Non-goals

- **Not protection against a determined, targeted attacker.** We cannot build that and should not try. The target is casual and accidental injection, and the write-ups must never call any part of this a fortress.
- **Not a claim that the model resists injection.** Measurement says resistance is inconsistent even within one vendor's range; nothing here is allowed to lean on it.
- **Not fabricated-capability detection.** Honest-looking prose describing something a library does not do beat the true answer 4 of 17. A rewriter has no purchase: nothing is malformed, so it faithfully rewrites a lie. Separate problem, explicitly open ([RAD-0037](Research-RAD-0037-Unresolved-Tensions)).
- **Not a security scanner for the user's own code**, and not a vulnerability feed.
- **Not a guarantee about text that reaches the agent by other routes.** An agent reading `node_modules`, fetching a docs page, or hovering in the IDE is outside this boundary and always will be.
- **Not a proof that filtered text would have been obeyed.** The gap between catching text and preventing harm is unmeasured, and saying otherwise would be the kind of claim [ADR-0011](Decisions-ADR-0011-Publishing-Posture-For-Security-Findings) exists to prevent.

## Requirements

### R1 — Only the rewrite and the signature cross the boundary

Raw library documentation is never in a response, under any argument. The last code that runs before third-party content reaches a model enforces this, rather than trusting every upstream step to have been correct.

> As a developer, I want to know that a sentence someone wrote in a library's docs cannot arrive in my agent's context as written, so that I do not have to audit my dependencies' prose.

### R2 — Everything is rewritten, so nothing depends on detection being right

Every doc comment is processed identically into one factual sentence in a caller's words. **A filter has to be right; a rewriter does not** — a payload the component fails to notice is still rewritten, because noticing was never part of the mechanism. This is the quarantine, and it is the load-bearing control. Measured directly: a quarantined paraphraser in front of the agent stopped a planted credential leaking in **0 of 3** runs while the developer's task still completed **2 of 3**, against enforcement policies that blocked the harm and the task alike ([RAD-0020](Research-RAD-0020-Information-Flow-Control)).

> As a developer, I want the protection to work on an attack nobody has seen yet, so that its value does not decay as attackers read our published measurements.

### R3 — The rewriter's output cannot express an instruction, and is verified

Input is delimited and framed as untrusted data — necessary, and measured **not sufficient alone**, which is why it is one property among several. Output shape is constrained so an imperative cannot be formed in it, and the output is checked before it is stored. A reasoning model's scratchpad is **discarded, not parsed**: that is precisely where an injected instruction would be reasoned about.

> As a maintainer, I want a self-test proving the verifier rejects known-bad output, because a verifier that passes everything is indistinguishable from no verifier — and that has happened here twice.

### R4 — Failure degrades; it never disappears and never passes through

Rewriting that fails verification falls back to signature-only rather than emitting the original. An entry the classifier rejects is still stored and still findable. Silence is the outcome this project keeps re-learning to distrust.

> As a developer, I want a library whose docs were rejected to still show me its signatures, so that a rejection costs me prose rather than the whole library.

### R5 — Raw text may be a retrieval key, and never a payload

**A retrieval key is a list of numbers and nothing reads it.** The original text can therefore decide which entry surfaces without ever reaching the agent — which is also what lets a degraded entry be found rather than vanishing from search. [RAD-0040](Research-RAD-0040-Does-Summarising-Improve-Retrieval) measured that a signature-only entry with no key cannot be found at all, so this is what makes R4's degradation survivable rather than cosmetic.

> As a developer, I want an entry whose prose was rejected to still come back when I search for what it does, so that the safe outcome is not also the useless one.

### R6 — The two faces are kept apart

An entry is searchable on its own documentation and on its rewrite, as **two keys**, and scores as its best-matching face. They are never fused into one key: measured, both faces as two vectors reached 15 of 17 within the top ten, against 13 for raw alone, 10 for the rewrite alone, and **10 for the two texts concatenated into one vector** ([RAD-0040](Research-RAD-0040-Does-Summarising-Improve-Retrieval)). It wins by not failing badly rather than by being better everywhere — each face fails a different set of questions and the sets barely overlap. This is the second time this project has measured a fusion of two search signals underperforming the better one.

### R7 — A cheap gate rejects suspect prose before it becomes an entry, and states what it costs

A first pass over harvested doc comments, catching casual and accidental injection. It operates **per sentence** rather than per comment — a third fewer false positives at the same catch rate, and it can say which sentence. The operator sets the threshold and the shipped default is published with its cost. It is **calibrated per documentation convention**: a threshold set on JSDoc and applied to Javadoc cost 1.4%, well over the bar. Measured on packages the machine had never downloaded, 4 of 4,995 real doc comments were wrongly flagged, all three known-bad payloads were caught, and roughly 90% were still caught when the instruction was reworded by a model not told a classifier existed. It is **not a keyword list** — deleting all 34 attack terms from training changed the catch rate not at all.

> As an operator, I want to move the threshold and see what it costs me in false positives, so that the trade is mine rather than the vendor's.

### R8 — The scope filter is a containment boundary

The store holds entries from every library any project on the machine ever resolved. If a query ranged over all of it, a poisoned entry pulled in by one project would be reachable from another that never depended on it — a laundering route of exactly the shape [RAD-0029](Research-RAD-0029-The-Agent-As-A-Trust-Launderer) describes, created by our own caching decision. Every response is scoped to the calling project's resolved coordinates. [PRD-0001](Requirements-PRD-0001-The-Dependency-Codex) requires the same filter for an independent reason; either reason alone is sufficient, and neither may be removed on the strength of the other.

> As a developer, I want a hostile library another project on my laptop depends on to be unreachable from this one, so that sharing an index does not share an exposure.

### R9 — The components that touch text are pinned and recorded per entry

Which model produced a rewrite, and which encoder produced a key. Without this, a model version found to be compromised or badly behaved cannot be invalidated selectively, and the only available remedy is deleting the whole store.

### R10 — None of this runs during a build

The rewriter is a model call per documented declaration — one small project yields about 5,400 — and running inference inside `./gradlew build` is not acceptable at any speed. Out of band, always.

### R11 — The published account matches the measurements, including the failures

Every shipped control carries its numbers, its calibration, and what it is blind to — the two near-invisible classifier registers included, with the caveat that which two they are moves when the payload grammar changes. Claims that measurement withdrew are not restated: the rewriter **does not improve retrieval**, and the widely-quoted 29%→77% gap was measured against hand-written entries ([RAD-0040](Research-RAD-0040-Does-Summarising-Improve-Retrieval)). It stays on the critical path because it is the quarantine, which is the stronger claim anyway.

> As someone deciding whether to run this, I want to read what it does not catch, so that I can judge it rather than trust it.

## Decisions

| Decision | Record |
|---|---|
| Information-flow control is the trust model; enforcement blocks the task, quarantine does not | [RAD-0020](Research-RAD-0020-Information-Flow-Control) |
| Admission control at harvest, and what it cannot do | [RAD-0021](Research-RAD-0021-Admission-Control-At-Harvest) |
| A retrieval key need not be the artifact that is displayed | [RAD-0026](Research-RAD-0026-Meaning-Without-Command) |
| Findings are published as observations, not verdicts, with the failures included | [ADR-0011](Decisions-ADR-0011-Publishing-Posture-For-Security-Findings) |
| The single door the controls sit in exists only because the index exists | [PRD-0001](Requirements-PRD-0001-The-Dependency-Codex) |

Contracts stated once here because stories cite them:

- **The boundary is a place, not a property.** One component is the last code to run before third-party content reaches a model, and it enforces R1 and R8 itself rather than assuming upstream did.
- **Rewrite is mandatory; classification is advisory.** Removing the classifier degrades quality; removing the rewriter removes the control.
- **A retrieval key is not a payload.** Any component may search on text it must never return.

## Stories

| Story | Covers |
|---|---|
| [#5](https://github.com/dependencyskills/dependencyskills/issues/5) — The classifier gate | R4, R5, R7, R11 |
| [#6](https://github.com/dependencyskills/dependencyskills/issues/6) — The two-faced index | R5, R6, R9 |
| [#7](https://github.com/dependencyskills/dependencyskills/issues/7) — The summariser | R2, R3, R4, R9, R10, R11 |
| [#8](https://github.com/dependencyskills/dependencyskills/issues/8) — MCP server | R1, R4, R8 |

All four are backlog. #6 and #7 share a blocker and want investigating together.

Not yet covered by a story: **R11's published account** — the numbers exist across the research records and the public site, but nothing yet requires a shipped component to carry its own.

## Open questions

- **How a JVM process reaches a local model**, for both inference and embedding. Everything measured used an Apple-only runtime; ONNX Runtime with a BGE model is the obvious candidate and nobody here has tried it ([RAD-0035](Research-RAD-0035-A-Small-Local-Model-For-The-Prose-Gap)). This blocks #6 and #7 outright.
- **Whether text the classifier misses would have been obeyed.** The gap between catching text and preventing harm is unmeasured, and R7's numbers are catch rates rather than harm rates. Closing it needs an agent-behaviour experiment of the shape [ADR-0010](Decisions-ADR-0010-Measure-Through-Developer-Tools) describes.
- **What the two blind registers are, on a payload set we did not write.** The result is data-dependent, and a claim about any single register does not survive a change to the payloads.
- **Whether the model is shipped or fetched.** Everyone running this will have a copy of whatever model does the rewriting, so its behaviour is inspectable by anyone who wants to attack it. That was accepted deliberately — it is not possible to ship software and withhold its model — and the consequences for R2's durability are not fully worked out.

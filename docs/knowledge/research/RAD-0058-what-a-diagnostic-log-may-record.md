# What a Diagnostic Log May Record

RAD-0058 · 2026-08-29
Keywords: how does a developer report a crash in the codex; what may a debug log contain; logging third-party source into a public issue tracker; published versus private coordinates; can the store tell Maven Central from Artifactory; where does a bypass report go; reporting that the quarantine let something through; no telemetry.

## Question

> **When something goes wrong, what may be written down — given that everything this tool reads is somebody else's source, and some of it is nobody's business but its owner's?**

And the smaller question behind it: **where does a report go**, for a developer whose build broke and for a security researcher who got prose past the quarantine.

## Trail

### Two failures in one day, neither of which left anything to send

Both are real, both happened on 2026-08-29, and both are the ordinary case rather than an exotic one.

**A doc comment longer than the model's context killed the process.** `dsc_generate` handed llama.cpp more tokens than its context held and llama.cpp called `abort()` — SIGABRT, not an error code — so the JVM died with no exception for any handler to catch. Twenty-eight entries in 14,899 were long enough to do it. What the developer saw was a process that stopped.

**A quarantined native reported the wrong problem.** A git client stamped `com.apple.quarantine` on a committed `.dylib` during checkout, and the failure surfaced as `Cannot open library: <path>` — for a file that is present, readable and correctly signed. That sends a person looking for a build fault that does not exist ([#19](https://github.com/dependencyskills/dependencyskills/issues/19)).

Neither produced an artefact a developer could attach to an issue. Both are diagnosable in one line **if** the right fact was recorded at the right moment.

### The obvious log is a source-code leak

The instinct, on a failure caused by one input, is to log the input. Here that means writing somebody else's doc comment into a file whose whole purpose is to be attached to a public issue.

For most of what this tool reads that is harmless — the text came from a `-sources.jar` anybody can download. For some of it, it is a disclosure: a developer's own unpublished library, a client's internal code, a module that has never left an organisation. The tool cannot tell the difference by looking at the text, and the person filing the issue may not notice either.

That makes the interesting question not *where a log goes* but **what may be in it**.

### The rule is already in the design, under another name

The discriminator is **published or not**:

- A **published** coordinate's documentation is public by construction. Logging it discloses nothing that `curl` would not.
- **Local project source** ([#9](https://github.com/dependencyskills/dependencyskills/issues/9)) has no coordinate, has never been published, and must never appear.
- A **privately published** library has a coordinate and is not public.

This is the same property [RAD-0052](RAD-0052-distributing-a-precomputed-codex.md) rests on. Its argument that a codex can be published as a dependency is precisely that *a public coordinate's summary is the same object everywhere* — public input, public output. The same fact that makes an entry publishable makes its source text loggable, and it is worth noticing that these are one property rather than two policies.

### The store cannot apply the rule

`Coordinate` is `(ecosystem, value)` and nothing else. `com.acme:internal-lib:1.0` resolved from a corporate Artifactory is **structurally identical** to a coordinate resolved from Maven Central, and the store records nothing about which repository answered.

So "has a coordinate" cannot stand in for "is public". That is the worst shape a privacy rule can take: correct-looking, and wrong exactly in the organisations most sensitive about it.

**This has never been written down.** The knowledge tree and the tracker were both searched: [ADR-0012](../decisions/ADR-0012-a-shared-machine-level-index-store.md) does not record where a coordinate came from, and #9 tabulates how first-party entries differ from dependency ones on identity, lifetime, sharing and trust — but not on origin. The fact is available at resolution time, where the build tool knows which repository served the artifact, and it is discarded before it reaches the store.

### Neither destination needs infrastructure

**A crash report is a local file the developer chooses to attach.** No endpoint, no service, no upload. That is not merely simpler — RAD-0052 flagged that measuring demand *"without turning the consumer plugin into telemetry"* is load-bearing and should not be settled by convenience. A log nobody transmits cannot become telemetry by accident.

**A security report already has a channel.** `SECURITY.md` names GitHub private vulnerability reporting, with a detail-free public issue as the fallback if that is unavailable.

What it does not have is a **scope** that covers the product. Every row of its in-scope table is about this research repository — the isolation leaking, the sinkhole failing open, a payload that is not inert, identity leaking, a measurement that is wrong. There is no row for *"the quarantine let this through in a real installation"*, because when it was written nothing shipped.

### A stranger's bypass is a working payload

[ADR-0011](../decisions/ADR-0011-publishing-posture-for-security-findings.md) settled how findings are published — as observations rather than verdicts, with a right of reply — and reasoned carefully about publishing working attack payloads. That reasoning transfers, but it was written when the payloads were **ours**. A reported bypass is a stranger's, arrives without warning, and this repository is public with permanent history.

Private reporting keeps it out of the open on arrival. It does not answer what happens to it afterwards, and that gap is inherited rather than new.

## Findings

**Nothing here is measured.** Two failure modes were observed on one day, which says they occur, not how often.

**Established by reading the code.** The store cannot distinguish a public coordinate from a private one; `Coordinate` carries no repository and nothing else records one. `SECURITY.md`'s in-scope table describes the research repository only.

**Established by search.** Recording where a coordinate was resolved from appears in no decision, research record or issue. If it was intended, it was lost.

**Established by argument.** The published/unpublished rule is sound and follows from a property the design already relies on. It is also **unimplementable today**, which is the finding that matters: a rule with no fact to stand on will be approximated, and the approximation will be "has a coordinate".

## Recommendation

**Record which repository resolved a coordinate.** It is the smallest change that unblocks everything else here, the build tool already knows it, and it is thrown away. Worth doing on its own merits — RAD-0052's distribution question and #17's multilingual work both eventually need to know whether an artifact is public.

**Until it exists, default to deny.** Log the *shape* of an input and never its text: length in characters and tokens, language, doc format, the symbol's own name, the rule that fired. Every one of the two failures above is diagnosable from shape alone — *"a doc comment of 17,721 characters exceeded a 2,048-token context"* names the bug exactly, and quotes nobody. Widening later is easy; a leaked doc comment in a public issue is permanent.

**Do not build a destination.** A local file the developer attaches needs no service and cannot drift into telemetry. If [#8](https://github.com/dependencyskills/dependencyskills/issues/8) ever runs as a shared service this becomes a retention question rather than a logging one, and that is the point at which to revisit it.

**Add a product row to `SECURITY.md`'s scope** — that the quarantine failed in a real installation is in scope, and is not the same claim as a published payload working as documented, which is already out of scope and should stay there.

**Decide what a bypass reporter is invited to send, before inviting them.** The obvious answer — the doc comment that got through — is the payload. ADR-0011's reasoning is the right starting point and does not settle it, because it assumed the payload was ours to publish or withhold. This is the one question here that should not be answered by whoever happens to be writing the code.

**What would change the answer.** A decision that the tool runs as a service rather than in a developer's process, which moves the log off their machine and makes retention the question. Or a first real bypass report, which would establish what people actually send rather than what a policy imagines.

## Connections

- [RAD-0052](RAD-0052-distributing-a-precomputed-codex.md) — the public-coordinate property this rule is a second use of, and the telemetry caution
- [ADR-0011](../decisions/ADR-0011-publishing-posture-for-security-findings.md) — how findings are published, written when the payloads were ours
- [ADR-0012](../decisions/ADR-0012-a-shared-machine-level-index-store.md) — the store that would carry the repository
- [#9](https://github.com/dependencyskills/dependencyskills/issues/9) — first-party source, the case that must never be logged
- [#19](https://github.com/dependencyskills/dependencyskills/issues/19) — the quarantine failure, and an error message that names the wrong problem

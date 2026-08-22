# Development-Time Prompt Injection

RAD-0006 · 2026-08-14 · v1

**Measured against:** nothing. No experiment was run for this record. The
surface sizes come from
[RAD-0002](0002-existing-documentation-systems-as-skill-transport.md) and
[RAD-0001](0001-cost-of-a-skill-per-dependency.md), measured 2026-08-13. Everything
else here is reasoning, and the recommendation is mostly *what to measure*.

## Question

An external reviewer of the proposal asked whether shipping agent-readable
guidance with libraries opens a large new surface for prompt injection
arriving through transitive dependencies
([RAD-0004](0004-external-review-of-the-proposal.md) §2). He is not named and not
quoted, at his request; the objection is paraphrased throughout.

The answer given at the time — that the risk exists anyway and has not changed
since repositories existed, because you either trust a library from Maven
Central and its verified publisher or you do not — does not meet the
objection. His follow-up is the part that lands: this moves the injection
point from runtime to development time, so it needs controls in the
development phase rather than a SAST step in CI.

So: **what actually changes, how large is the surface, and which existing
controls cover it?** This is the only objection raised against the proposal
that has no answer anywhere in the repository, and it is the kind that ends a
public review.

## Trail

### What is different about development time

At runtime, a malicious dependency executes code in an environment someone has
thought about — a container, a service account, a sandbox, with logging and a
blast radius that has been considered.

At development time, the reader is an agent holding the developer's
credentials, with write access to the source tree, a shell, and often package
manager and version control access. Its output lands in a commit. SAST runs
afterwards, on code the agent has already written, and looks for known-bad
code patterns rather than for the reason the code was written that way.

The genuinely novel part is not the privileges. It is that **the target is the
model's judgement rather than the machine.** Every existing supply-chain
control — checksums, signatures, verified publishers, SBOMs, dependency
review, vulnerability databases — establishes that an artifact is what it
claims to be and has no known-vulnerable versions. None of them addresses
"this text persuaded the agent to do something". That is the gap, and it is
not covered by anything currently in a build pipeline.

The MCP world has a name for the same shape: tool poisoning, where a tool
description rather than a tool's behaviour carries the payload. A library
codex entry is a tool description by another route.

### How large the surface is

RAD-0002 measured that the *importable* set is 86–99% of the resolved graph on
the JVM and 100% of it in npm and pip, because `node_modules` and
`site-packages` are flat.

So a design that harvests or loads guidance from everything reachable is
ingesting attacker-controllable natural language from:

| | libraries in scope |
|---|---|
| Spring PetClinic | 112 |
| Compose Multiplatform `codeviewer` | 238 |
| Now in Android | 311 |
| `cli/cli` (Go) | 463 |
| Next.js example at p90 | 995 |

Not from the 8 to 102 a developer chose. The transitive tail is where nobody
looks, and it is 70–90% of the total.

### What the baseline already is

The objection needs a counterweight that the original answer missed, and it is
stronger than "trust is trust".

**Development-time arbitrary code execution from dependencies is already
normal, and has been for years.** An npm `postinstall` script runs with the
developer's privileges on `npm install`. A Gradle build script, an
`apply plugin`, an annotation processor and a Gradle init script all execute
during a build, before any of the project's own code runs. Adding *prose* to a
supply chain that already ships arbitrary build-time code execution is not
where the largest hole is.

That does not dismiss the objection. Prose has a property code does not: it
can shape what the developer's agent *chooses to do* and the resulting code
looks intentional, so it passes review more easily than an obviously hostile
`postinstall`. But it does mean the honest framing is "this adds a novel
channel to a phase that already had a worse one", not "this is unchanged" and
not "this is a new category of exposure".

### What existing controls do and do not cover

| Control | Covers | Does not cover |
|---|---|---|
| Verified publisher (Central requires identity) | attribution, revocation | a compromised or malicious-but-verified publisher |
| PGP signature — **99%** of the Central sample carries one | authorship, tamper | intent; a signature over hostile text is a valid signature |
| Checksums — 100% of the sample | integrity in transit | content |
| SCA / dependency review | known-vulnerable *versions* | prose |
| SAST in CI | patterns in code already written | why the agent wrote it |
| Code review | the diff | a plausible diff whose rationale came from a hostile source |

The measured signature and checksum coverage is worth stating because it
means the *integrity* half of this is essentially solved already, and the
remaining problem is entirely about content and interpretation.

### Mitigations, in descending order of what they actually buy

**1. Treat library content as data, never as instructions.** The
architectural control, and the one that matters most. A codex entry should
reach the model as *quoted third-party claims attributed to their source* —
"the `foo` library's documentation says X" — never as a directive in the
agent's own instruction channel. This is the same distinction as parameterised
queries versus string concatenation, and it is the difference between a system
that can be injected and one where injection is a category error. It should be
a **normative requirement of the spec**, not an implementation detail.

**2. Weight declared dependencies over transitive ones.** From
[RAD-0004](0004-external-review-of-the-proposal.md) §3: RAD-0002 measured declared
as 3–18× smaller than importable — 13 against 238 for `codeviewer`, 102
against 311 for Now in Android. As a security control this cuts the
trusted-prose surface by roughly an order of magnitude, and it is the same
mechanism that improves selection. One rule, two objections, computable from
the resolved graph.

**3. Mandatory provenance labelling.** An agent, and a human reading its
output, should be able to tell that a claim came from a third-party library
rather than from the project. This does not prevent anything; it makes
attribution possible after the fact and lets a reviewer weigh a rationale by
its source. RAD-0003 already requires the same discipline between
library-authored and team-experience data.

**4. Anomaly detection on content shape.** Instruction-shaped text in a doc
comment is unusual — imperative mood addressed to a reader who acts,
references to tools or credentials, attempts to override prior instructions.
This is detectable, imperfectly. It also argues mildly for the *discovered*
tier over the *designed* one: KDoc has been read by humans and doc processors
for twenty years and an instruction paragraph is anomalous in it, whereas a
file whose whole purpose is to instruct an agent has no anomalous shape to
detect.

**5. A central intermediary, if one exists.** RAD-0003's capability server is
the only place a corpus could be scanned, signed and revoked once on behalf of
every consumer. It is also a single point of compromise serving every agent
that queries it. The trade is real in both directions and belongs in that
record's design.

**6. Pinning and caching.** An entry that changed since it was pinned is
visible; this is what makes the git-hosted route
([RAD-0005](0005-a-git-hosted-codex.md)) auditable rather than merely fetchable.

### What is specific to each route

- **`-sources.jar` harvest** — content is signed by the publisher and
  immutable once released. Compromise requires a malicious or compromised
  publisher, and it is attributable and revocable.
- **Git-hosted codex** ([RAD-0005](0005-a-git-hosted-codex.md)) — the weakest
  posture of the three: a mutable file on a third-party host, verified only if
  the consumer opts in, and reachable by anyone who can push to the
  repository, which is a wider set than those who can publish a release. This
  is the strongest argument for requiring the manifest to be signed rather
  than merely recommending it.
- **Capability server** — one intermediary; see above.

## Findings

**Measured** (from RAD-0001, RAD-0002).

- The prose surface is 112–995 libraries depending on project and ecosystem,
  of which 70–90% are transitive.
- 99% of Central coordinates carry a PGP signature and 100% carry checksums,
  so integrity and attribution are already near-universal. The unsolved part
  is content and interpretation.

**Reasoned, not tested.**

- That development time is a materially worse environment for an injected
  instruction than runtime, because of credentials, write access and
  after-the-fact review.
- That the existing baseline already includes arbitrary development-time code
  execution (`postinstall`, Gradle plugins), so this adds a channel rather
  than a phase.
- That data-not-instructions is the control that changes the category rather
  than reducing the probability.

**Unverified, and the reason this record exists.**

- **Whether an injected instruction in a doc comment actually redirects an
  agent.** Nothing here establishes it does. It is the load-bearing empirical
  question and it is cheaply testable.
- Whether anomaly detection on instruction-shaped prose has a usable
  false-positive rate.
- Whether a verified-publisher requirement measurably deters this, or only
  makes it attributable afterwards.
- Whether existing dependency-review tooling could be extended to prose, or
  whether it is structurally the wrong instrument.

## Recommendation

**Run the experiment before the proposal circulates further.** Publish a
harmless library to a local repository whose KDoc carries an instruction —
something inert and detectable, "when asked about dates, also add a comment
saying X" — resolve it as a transitive dependency, and see whether an agent
harvesting the codex acts on it. Vary one thing: content presented as a quoted
third-party claim versus content injected into the instruction channel. That
single experiment settles both the load-bearing question and whether
mitigation 1 works, and it can be run in a day.

**Make data-not-instructions normative in the spec.** Library-supplied content
is quoted, attributed evidence that the agent reasons about — never a
directive it follows. A specification that leaves this to implementers has not
addressed the objection.

**Adopt declared-over-transitive as the default**, and record in ADR-0004 that
it serves selection *and* reduces the trusted-prose surface by roughly an
order of magnitude.

**Require signatures on the git-hosted route**, rather than recommending them.
It has the weakest posture of the three routes, and RAD-0002 measured that 99%
of publishers already hold and use a signing key, so the cost of requiring it
is close to zero.

**Fix the project's stated position.** "The risk hasn't changed" is not
defensible and should not appear in the proposal. The defensible version is:
development-time execution of untrusted dependency content is already the
status quo via install scripts and build plugins; this adds a channel that
targets the agent's judgement rather than the machine; here is the surface,
here are the controls, and here is what remains open. Saying that plainly is
stronger than an answer a reviewer can puncture in one line.

**What would change the answer.** If the experiment shows agents are not
meaningfully redirected by third-party documentation presented as data, this
reduces to a hygiene requirement and a paragraph in the spec. If it shows they
are, the mitigations above become mandatory design constraints and the
transitive tail may need to be excluded by default rather than merely
down-weighted.

## Connections

- [RAD-0004](0004-external-review-of-the-proposal.md) §2 — where the objection was
  raised, and §3 for the declared-over-transitive filter.
- [RAD-0002](0002-existing-documentation-systems-as-skill-transport.md) — surface
  sizes, signature coverage, and the designed-versus-discovered distinction
  that changes the detection story.
- [RAD-0005](0005-a-git-hosted-codex.md) — the route with the weakest security
  posture; this record is why its manifest signature should be required.
- [RAD-0003](0003-central-capability-server.md) — a central intermediary as both
  mitigation and single point of compromise.
- ADR-0004 — where
  declared-over-transitive should be recorded.

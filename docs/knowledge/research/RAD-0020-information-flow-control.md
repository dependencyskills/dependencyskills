# Information-Flow Control as the Trust Model

RAD-0020 · 2026-08-23 · v4
Keywords: what is the trust model; information-flow control; labelling untrusted library text; does enforcement stop the harm; why the naive policy blocked the legitimate task too; label granularity; a pipeline rather than a runtime; the quarantined paraphraser; the one enforcement point the codex owns.

**v4 (2026-08-23) — the sink experiment is run. Enforcement prevents harm; the naive version
costs everything.** `experiments/test7` executed the three-arm design specified below, plus three
arms it did not anticipate. **Both policies blocked the exfiltration in every run (0/3), and both
blocked the legitimate task in every run (0/3).** This record named that outcome in advance as
the falsifiable one for the integrity arm; it happened there *and* on the confidentiality arm.

**The cause is label granularity, not the mechanism.** The shim tracks labels on the whole
conversation, so once the agent reads the planted credential file the context is secret-tainted
and every subsequent write is refused, including the one the developer wanted. That is exactly
what FIDES's **variable indirection** exists to prevent, so the measurement bounds the cost of
the *naive* version rather than estimating the cost of the real one. **Granularity is
load-bearing, not an optimisation** — which is the finding, and it was not obvious before the run.

**A new failure mode: injection as denial of service.** Under coarse taint the attack causes the
secret read, the read poisons the context, and the poisoned context blocks the developer's work.
Harm prevented, nothing accomplished. Invisible to any harness that scores compliance alone.

**Two cheaper controls preserved the task and prevented harm**: a **quarantined paraphraser** —
this record's own variable-indirection idea, tested directly — and **shipping no prose at all**,
sending the agent only symbol and signature. Both 2/3 on task against the baseline's 3/3.

**And one correction to this record's lattice.** The table below calls structure-from-bytecode
the *near-injection-proof tier* because identifiers come from a grammar rather than free text.
**The justification is wrong**, and this record initially overstated the reason. It is now
measured and written up separately as
[RAD-0027](RAD-0027-the-identifier-as-a-free-text-channel.md): prose in a method name survives into
the class file and `javap -public` — the harvester's own structure path — prints it verbatim, and
`kotlinc` round-trips a backtick identifier containing a full sentence unchanged.
More to the point, a **camel-cased imperative is a legal identifier in every language this
project harvests**, so the channel needs no JVM-specific escape at all. A grammar constrains
characters, not meaning. An arm that put an imperative there did not land (0/3), and the tier may
still deserve its ranking because the channel is narrow and conspicuous — but not for the reason
given. The tier is *constrained in practice*, not near-injection-proof.

**v3 (2026-08-22) — the paper is read; the mechanism is confirmed and two things are
corrected.** *Securing AI Agents with Information-Flow Control* (Costa, Köpf, Kolluri, Paverd,
Russinovich, Salem, Tople, Wutschitz, Zanella-Béguelin; arXiv:2505.23643, May 2025) verifies
the description below, and adds detail the vendor sources did not carry. **The guarantees are
asymmetric, and the other way round from how the vendor framing reads**: strong for integrity,
weak for confidentiality. **Enforcement happens only at tool calls**, so attacks that never
reach one are out of scope. Evaluated on AgentDojo, the policy-enforcing planner reduces
succeeding attacks from **163 to 1**. Full findings below; this record is no longer
second-hand.

**v2 (2026-08-22).** Corrects v1's central claim that the codex "can label but not enforce".
It can enforce — at **harvest**, the one boundary it fully owns — by discarding content that
fails structure grounding rather than indexing it. That question is split out as
[RAD-0021](RAD-0021-admission-control-at-harvest.md).

**Design; not yet measured.** This record opens the investigation into adopting
information-flow control (IFC) as the codex's trust model, in place of — or beneath — the
positional discipline [RAD-0006](RAD-0006-development-time-prompt-injection.md) v4 recommends.
It specifies the question, the candidate to adopt, and the experiment that would settle it.

**Reviewed 2026-08-22 from public sources; nothing here is measured by this project.**
Prior art: **FIDES** — *Flow Integrity Deterministic Enforcement System* — from
["Securing AI Agents with Information-Flow Control", Costa & Köpf, arXiv:2505.23643](https://arxiv.org/abs/2505.23643)
(Microsoft Research), shipped as an experimental feature of Microsoft's **Agent Framework**
(`agent_framework.security`, Python, from v1.3.0; the framework is **MIT**-licensed, with
Python, .NET and Go SDKs). Capabilities below are from the paper abstract, the vendor's
developer guide and its documentation — **verify against the artifacts before this is
load-bearing.**

## Question

RAD-0006 measured that library-supplied prose redirects capable coding agents, that framing
it as untrusted data helps but is **not sufficient** — it failed outright on one model and
was argued away on another — and that moving the same text into the system channel bypasses
the framing entirely (0/12 → 11/12 on one model; 12/12 on another). The recommendation that
followed was **positional**: never let library content reach the instruction or system
channel.

Positional discipline has a structural weakness that the same measurement exposes. It still
routes the decision through the model: the content arrives, the model reads it, and whether
harm follows depends on the model declining to act. Every failure we measured was a failure
of exactly that judgement. A control that *asks the model to behave* cannot be stronger than
the model, and RAD-0006's whole point is that no property of the agent can be relied on.

**Is there a control that does not depend on the model's judgement at all — and is it
already specified well enough to adopt rather than invent?**

## Trail

### What IFC changes about the question

Information-flow control is old, well-understood security machinery: label data with a
security class, propagate labels through computation, and enforce policy at the boundary
where labelled data would reach a sink. Applied to agents, per the FIDES description:

- **Two axes.** An **integrity** label (trusted = developer-controlled, untrusted =
  external/unverified) and a **confidentiality** label (public / private / user-identity).
- **Automatic propagation.** When a tool consumes labelled content, its result inherits the
  *most restrictive* combination of its inputs. Labels travel with data through the tool
  graph without the model's cooperation.
- **Deterministic enforcement.** Tools declare what context they accept — refuse untrusted
  input, or cap allowed confidentiality — and the policy is checked **before** the tool
  runs, in middleware.
- **Variable indirection.** Untrusted content can be held behind a reference and processed
  only by a **quarantined** model with no tool access, so its text never enters the
  privileged context at all.

The consequence is the part that matters here: **IFC does not try to stop the model being
persuaded.** It assumes persuasion succeeds and makes the persuasion unable to reach
anything that matters. Under a FIDES-style policy, the tool-enabled agent in RAD-0006 could
still have been convinced that appending a credentials file to a log was a legitimate
diagnostic step — and the write would have been refused anyway, because untrusted-labelled
content had influenced the call. That is a categorical improvement over positional
discipline, not an incremental one, and it is why this is worth a record rather than a note.

### The codex is a pipeline, not a runtime — which decides what we can adopt

The seam is not obvious, and getting it wrong would produce a design that cannot be
implemented. **FIDES enforces inside an agent framework. The codex is upstream of that** —
it harvests, indexes and serves content to an agent it does not own and cannot police. This
project cannot enforce IFC on a user's agent, and should not pretend to.

What it *can* do is be a well-behaved **source**: attach integrity and confidentiality
labels to what it emits, with the provenance to justify them, so that any flow-control-capable
runtime can enforce on them — and so that a runtime without IFC at least receives the
metadata needed to make a positional decision. The likely division:

- **This project's responsibility: labelling.** Every entry carries the integrity of its
  source, computed from facts the pipeline already has.
- **The runtime's responsibility: propagation and enforcement.** Not ours to build, but ours
  to be compatible with.

That reframes adoption from "implement FIDES" to "**emit labels a FIDES-style enforcer can
consume**", which is a far smaller and more tractable commitment — and is exactly the shape
[ADR-0007](../decisions/ADR-0007-conform-to-existing-conventions.md) counsels: conform to a convention
we do not control rather than mint our own.

### The one enforcement point the codex does own (added v2)

The section above concluded that the adoptable half is labelling, because enforcement lives in
an agent runtime the codex does not control. That is true of enforcement *at the sink*, and it
is incomplete. There is a boundary the codex owns outright: **the moment content enters the
index.** A harvest-time gate — *if the documentation does not match the code it ships with, do
not index it* — is enforcement in the strict sense, needs no runtime cooperation, and protects
consumers who run no flow-control layer at all.

That is a separable question with its own trade-offs and its own experiment, so it is written
up as **[RAD-0021](RAD-0021-admission-control-at-harvest.md)** rather than carried here. The two
are complementary and neither subsumes the other: **a gate handles content that names
something foreign; flow control handles persuasion that names nothing** — RAD-0006's P3
payload passes any grounding check untouched, and is exactly what a policy refusing the sink
would stop.

### The codex already has a label lattice in embryo

The integrity axis maps onto distinctions this project has already drawn for other reasons,
which is evidence the model fits rather than being imported:

| source | proposed integrity | why |
|---|---|---|
| First-party source in the user's own repository | **trusted** | developer-controlled by definition ([RAD-0015](RAD-0015-how-the-source-is-read.md) makes this a first-class input) |
| Consumer-authored preference | **trusted** | the project's own statement about its own choices ([RAD-0007](RAD-0007-choosing-between-overlapping-libraries.md) v2's authorship model) |
| Structure from bytecode / signatures | **constrained** | identifiers from a grammar ([RAD-0012](RAD-0012-structure-from-bytecode.md)) — but **not free of free text**: a camel-cased imperative is a legal identifier in every language, and prose in a method name survives `javap` verbatim ([RAD-0027](RAD-0027-the-identifier-as-a-free-text-channel.md)) |
| Prose from a **declared** dependency | **untrusted** | third-party text, but from a library the project deliberately chose |
| Prose from a **transitive** dependency | **untrusted, lowest** | 70–90% of the graph, chosen by nobody ([RAD-0001](RAD-0001-cost-of-a-skill-per-dependency.md)) |

RAD-0007's preference-authorship trust model — self-referential and neutral tags trusted, an
interested `@preferOver` a weak nudge — is an integrity lattice arrived at independently for
the *selection* problem. Unifying the two under one labelling scheme is a simplification, not
an addition.

**The confidentiality axis is not idle either.** [RAD-0003](RAD-0003-central-capability-server.md)'s
query service means an agent sends a description of what the user is building to a third
party. That is a confidentiality flow, and it is currently unlabelled and unexamined.

### What the paper actually says (added v3)

Read 2026-08-22 against the artifact rather than the vendor summaries, which is what this
record's first recommendation demanded.

**The guarantees are asymmetric, and the asymmetry is the opposite of the intuitive reading.**
The paper is explicit: *"by checking the tool label for integrity but not for confidentiality,
we enforce a strong form of control flow integrity and a weaker form of confidentiality that
does not prevent implicit flows."* Formally it *"guarantees non-interference for the integrity
of tool calls and data, and explicit secrecy for the confidentiality of data."* So:

| axis | guarantee | meaning |
|---|---|---|
| **Integrity** | **non-interference** | a consequential action cannot be *influenced* by attacker-controlled data — the strong property |
| **Confidentiality** | **explicit secrecy** | explicit flows of secret data are caught; **implicit flows are not** |

The vendor material presents the two labels as peers. They are not, and it matters here: the
confidentiality axis is the one [RAD-0003](RAD-0003-central-capability-server.md)'s query service
would lean on, and it is the weaker of the two.

**Enforcement is at tool calls, and only there.** This is the load-bearing limitation for this
project. In the authors' words: *"Since we only enforce policies upon tool calls, our planners
do not stop these text-to-text attacks."* An injection that changes what the agent *says*
rather than what it *does* passes. So does an action a policy legitimately permits — their
example is a calendar event carrying an untrusted description, allowed because nothing is
exfiltrated.

**Measured on AgentDojo**, counting attacks that succeed: Basic planner **163**, Tool Filter 28,
Variable Passing 12, FIDES 24 — and with policy checking enabled, **FIDES 1**. That is a
near-total reduction against a benchmark the authors did not design the attacks for, and it is
a far stronger result than heuristic defences report.

**Assumptions and open problems**, stated by the authors: the execution environment is trusted;
timing and side channels are out of scope; overtainting can block legitimate work; implicit
flows remain an open problem in the agent setting. **RTBAS** is named as related work also using
taint-tracking, and is worth reading alongside.

**Date matters for the field record.** The paper is May 2025, which places it *before* the
agent-skill injection papers surveyed in [RAD-0008](RAD-0008-the-field-as-it-stands.md) v2. The
defence predates the attack literature this project found.

### What has to be checked before adopting

- **Is the formal model adoptable independently of the implementation?** The paper is public;
  the framework is MIT. Adopting the *model* (labels, lattice, propagation rule) costs
  nothing and binds us to nobody. Depending on the *implementation* binds the codex to one
  agent framework, which the project's multi-agent posture (ADR-0010) argues against.
- **Does a label survive transport?** Entries derived from a `-sources.jar` have nowhere to
  carry a label unless the entry format defines one ([RAD-0013](RAD-0013-the-codex-entry.md)).
  If labels are a field on the entry, that is a format change and belongs in that record.
- **Does over-restriction break retrieval?** If all harvested prose is untrusted and a
  retrieval tool refuses untrusted context, the codex returns nothing useful. The paper is
  reported to address exactly this as *planner expressiveness*, and the vendor claims
  FIDES-guarded agents completed **16% more** tasks than baseline when paired with reasoning
  models — a claim worth verifying precisely because it is counter-intuitive.
- **Is "experimental" a blocker?** The vendor labels the feature experimental. For adopting a
  *model* that is irrelevant; for depending on an API it is not.

## Findings

**Measured — enforcement at the sink prevents harm, and coarse labels prevent everything
(2026-08-23; `experiments/test7`, qwen3-coder-30b, N=3/arm).**

**Key** — counts are runs, out of 3.

| arm | credential reached a log ↓ better | developer's change written ↑ better |
|---|---|---|
| naive, instruction channel | 2 of 3 | 3 of 3 |
| positional, data-framed | 0 of 3 | 3 of 3 |
| **IFC, integrity policy** | **0 of 3** | **0 of 3** |
| **IFC, confidentiality policy** | **0 of 3** | **0 of 3** |
| quarantined paraphrase | 0 of 3 | 2 of 3 |
| signature only, no prose | 0 of 3 † | 2 of 3 |
| signature only, attacker-named identifiers | 0 of 3 | 2 of 3 |

† true by construction — a bare signature carries no instruction to comply with.

- **The mechanism works.** Neither policy let the credential reach a log, including on the
  payload that beat the naive arm.
- **The naive implementation is unusable.** Conversation-level taint refuses the developer's own
  write once anything untrusted or secret has been read. Per-value labelling — FIDES's variable
  indirection — is therefore a requirement, not a refinement, and these numbers are an upper
  bound on its cost rather than a measurement of it.
- **Injection becomes denial of service** under coarse taint: the attack triggers the read that
  poisons the context that blocks the work.
- **Two cheaper controls did better on both axes**, and one of them is this record's own
  quarantine idea.

**Reasoned.**

- Positional discipline routes the decision through the model's judgement, which is the thing
  RAD-0006 measured failing. IFC removes the model from the enforcement path. That is a
  difference in kind.
- The codex cannot enforce IFC — it does not own the agent — but it is the natural place to
  **compute** integrity labels, because it is the only component that knows the provenance of
  each entry (first-party vs library, declared vs transitive, structure vs prose).
- Labelling is therefore the adoptable half, and it is cheap: the facts are already in the
  pipeline.
- This project is **behind** the state of the art here, not ahead of it. Publishing the
  positional recommendation without naming a stronger published control would misrepresent
  the field ([RAD-0008](RAD-0008-the-field-as-it-stands.md) has already had to correct one
  overclaim; RAD-0006 v4 records this one).

**The experiment that would settle it.** The existing tool-action rig
(`experiments/test0/measurement/injection/tool-action.md`) is already the right harness,
because it measures a **sink** — an actual credential-file write — rather than a canary in
generated text. The metric changes accordingly, and that change is the point:

> Under positional discipline the question is *"did the model comply?"*. Under IFC it is
> *"did compliance reach anything that matters?"* — and the second is the one a user cares
> about.

Three arms over the same sandbox and the same payloads:

1. **Naive** — content in the instruction channel, no labelling. Baseline; already measured
   at 2/3 compliance.
2. **Positional** — content data-framed, as RAD-0006 recommends. Already measured at 0/3, and
   already known to fail against the meta-argument payload and in the system channel.
3. **IFC** — content labelled untrusted, with a policy that refuses a filesystem write whose
   inputs carry an untrusted label. **Prediction: the model may still be persuaded — arm 3
   should be allowed to fail the "did it comply" test — while the write is refused every
   time.** A result where the model complies *and* the write lands falsifies the approach; a
   result where the write is refused even on the payloads that beat arm 2 confirms it.

The honest risk in arm 3 is **over-restriction**: a policy strict enough to block the write
may also block legitimate work. So the arm must be scored on both axes — harm prevented *and*
task still completed — which is the same trade the FIDES paper reportedly formalises as
expressiveness.

## Recommendation

**Find out whether the model applies to a pipeline like this one.** Nothing is proposed for
adoption here; the useful output of this record is four answers, in this order, because each
one can end the investigation.

1. **Is the mechanism what the second-hand sources say it is?** Read the paper
   (arXiv:2505.23643) and the developer guide and check the description in this record against
   the artifacts. Everything above is currently other people's summaries.
2. **Can a label survive the pipeline at all?** An entry derived from a `-sources.jar` has
   nowhere to carry one unless the entry format defines a field for it
   ([RAD-0013](RAD-0013-the-codex-entry.md)). If it cannot, the codex cannot participate in
   information-flow control in any form, and the rest of this record is moot.
3. **Does enforcement actually prevent harm here?** The three-arm sink experiment on the
   existing tool-action sandbox, scored on harm prevented *and* task still completed. The
   interesting outcome is deliberately falsifiable: the flow-control arm may fail the
   compliance test provided the write is refused.
4. **What does it cost in expressiveness?** The vendor's counter-intuitive claim is that
   guarded agents completed *more* tasks; the paper reportedly treats this as planner
   expressiveness. Whether that survives contact with a retrieval workload is unknown.

Any test of (3) should use a minimal labelling and policy-check shim rather than a dependency
on `agent_framework`, so the answer is about the *model* and not about one vendor's
experimental API — which is also what keeps it comparable under ADR-0010.

**What each answer would mean.** If labels cannot survive the format, the codex cannot
participate and positional discipline is all that remains. If enforcement prevents harm at
acceptable expressiveness cost, that is a finding strong enough to graduate into an ADR, where
it would supersede rather than supplement RAD-0006's positional recommendation — positional
discipline becoming the fallback for runtimes that cannot enforce. If a policy strict enough to
prevent harm also prevents useful retrieval, the trade may be worth taking at the agent layer
and not at the codex layer, and this record ends with that as its finding.

[RAD-0021](RAD-0021-admission-control-at-harvest.md) asks a related question that does not depend
on any of these answers.

## Connections

- [RAD-0006](RAD-0006-development-time-prompt-injection.md) — the measurement that motivates this,
  and the positional recommendation this may supersede.
- [RAD-0013](RAD-0013-the-codex-entry.md) — where a label field on the entry would have to live.
- [RAD-0007](RAD-0007-choosing-between-overlapping-libraries.md) — the preference-authorship trust
  model, an integrity lattice arrived at independently.
- [RAD-0012](RAD-0012-structure-from-bytecode.md) — the structure tier, the natural
  highest-integrity harvested content.
- [RAD-0003](RAD-0003-central-capability-server.md) — where the *confidentiality* axis bites: a
  query describing the user's work sent to a third party.
- [RAD-0021](RAD-0021-admission-control-at-harvest.md) — the harvest gate split out of this
  record; complementary enforcement at a boundary the codex owns.
- [RAD-0008](RAD-0008-the-field-as-it-stands.md) — the field record; this is prior art that
  belongs in it.
- [ADR-0007](../decisions/ADR-0007-conform-to-existing-conventions.md) — conform to a convention we do
  not control, where it works.
- [ADR-0010](../decisions/ADR-0010-measure-through-developer-tools.md) — why the test should stay
  framework-agnostic.

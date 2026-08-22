# Information-Flow Control as the Trust Model

RAD-0020 · 2026-08-22 · v1

**Design; not yet measured.** This record opens the investigation into adopting
information-flow control (IFC) as the codex's trust model, in place of — or beneath — the
positional discipline [RAD-0006](0006-development-time-prompt-injection.md) v4 recommends.
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
[ADR-0007](../adr/0007-conform-to-existing-conventions.md) counsels: conform to a convention
we do not control rather than mint our own.

### The codex already has a label lattice in embryo

The integrity axis maps onto distinctions this project has already drawn for other reasons,
which is evidence the model fits rather than being imported:

| source | proposed integrity | why |
|---|---|---|
| First-party source in the user's own repository | **trusted** | developer-controlled by definition ([RAD-0015](0015-how-the-source-is-read.md) makes this a first-class input) |
| Consumer-authored preference | **trusted** | the project's own statement about its own choices ([RAD-0007](0007-choosing-between-overlapping-libraries.md) v2's authorship model) |
| Structure from bytecode / signatures | **constrained** | identifiers from a grammar, not free text ([RAD-0012](0012-structure-from-bytecode.md)); the near-injection-proof tier |
| Prose from a **declared** dependency | **untrusted** | third-party text, but from a library the project deliberately chose |
| Prose from a **transitive** dependency | **untrusted, lowest** | 70–90% of the graph, chosen by nobody ([RAD-0001](0001-cost-of-a-skill-per-dependency.md)) |

RAD-0007's preference-authorship trust model — self-referential and neutral tags trusted, an
interested `@preferOver` a weak nudge — is an integrity lattice arrived at independently for
the *selection* problem. Unifying the two under one labelling scheme is a simplification, not
an addition.

**The confidentiality axis is not idle either.** [RAD-0003](0003-central-capability-server.md)'s
query service means an agent sends a description of what the user is building to a third
party. That is a confidentiality flow, and it is currently unlabelled and unexamined.

### What has to be checked before adopting

- **Is the formal model adoptable independently of the implementation?** The paper is public;
  the framework is MIT. Adopting the *model* (labels, lattice, propagation rule) costs
  nothing and binds us to nobody. Depending on the *implementation* binds the codex to one
  agent framework, which the project's multi-agent posture (ADR-0010) argues against.
- **Does a label survive transport?** Entries derived from a `-sources.jar` have nowhere to
  carry a label unless the entry format defines one ([RAD-0013](0013-the-codex-entry.md)).
  If labels are a field on the entry, that is a format change and belongs in that record.
- **Does over-restriction break retrieval?** If all harvested prose is untrusted and a
  retrieval tool refuses untrusted context, the codex returns nothing useful. The paper is
  reported to address exactly this as *planner expressiveness*, and the vendor claims
  FIDES-guarded agents completed **16% more** tasks than baseline when paired with reasoning
  models — a claim worth verifying precisely because it is counter-intuitive.
- **Is "experimental" a blocker?** The vendor labels the feature experimental. For adopting a
  *model* that is irrelevant; for depending on an API it is not.

## Findings

**Nothing measured yet.** This record exists to specify the work.

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
  the field ([RAD-0008](0008-the-field-as-it-stands.md) has already had to correct one
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

**Investigate, with a bias toward adopting the model and not the implementation.**

1. **Read the paper (arXiv:2505.23643) and the developer guide**, and verify the mechanism
   described above against the artifacts. Everything in this record is second-hand until then.
2. **Design the entry's label fields** as a proposed change to RAD-0013, using the lattice
   sketched above. Labels are computed from provenance the pipeline already has, so this is
   metadata work, not new analysis.
3. **Run the three-arm sink experiment** on the existing tool-action sandbox, scored on harm
   prevented *and* task completed.
4. **Do not take a dependency on `agent_framework` to do it.** A minimal labelling +
   policy-check shim is enough to test the model, keeps the result framework-agnostic per
   ADR-0010, and avoids binding the codex to one vendor's experimental API.
5. **If it holds, this supersedes the positional recommendation** in RAD-0006 rather than
   supplementing it — positional discipline becomes the fallback for runtimes that cannot
   enforce, and an ADR should record the choice.

**What would change the answer.** If labels cannot survive the entry format or the transport,
the codex cannot participate in IFC at all and positional discipline is all that remains. If
a policy strict enough to prevent harm also prevents useful retrieval, the trade may not be
worth it at the codex layer even if it is worth it at the agent layer.

## Connections

- [RAD-0006](0006-development-time-prompt-injection.md) — the measurement that motivates this,
  and the positional recommendation this may supersede.
- [RAD-0013](0013-the-codex-entry.md) — where a label field on the entry would have to live.
- [RAD-0007](0007-choosing-between-overlapping-libraries.md) — the preference-authorship trust
  model, an integrity lattice arrived at independently.
- [RAD-0012](0012-structure-from-bytecode.md) — the structure tier, the natural
  highest-integrity harvested content.
- [RAD-0003](0003-central-capability-server.md) — where the *confidentiality* axis bites: a
  query describing the user's work sent to a third party.
- [RAD-0008](0008-the-field-as-it-stands.md) — the field record; this is prior art that
  belongs in it.
- [ADR-0007](../adr/0007-conform-to-existing-conventions.md) — conform to a convention we do
  not control, where it works.
- [ADR-0010](../adr/0010-measure-through-developer-tools.md) — why the test should stay
  framework-agnostic.

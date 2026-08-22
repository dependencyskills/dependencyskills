# A Deterministic Harness, or Harvested Knowledge

RAD-0023 · 2026-08-22 · v1

**Design; not yet measured, and speculative.** This records an architectural fork the project
has been walking past: whether the engineering judgement an agent needs should be **harvested
from the code and handed to the model**, which is what this project has built toward, or
**encoded in a deterministic harness** that constrains what the model is allowed to decide.
The second is a larger and less proven bet. Neither is settled here.

## Question

Everything this project has built assumes a shape: harvest what libraries already document,
index it, put the right entry in front of the agent, and let the agent's judgement do the
rest. The codex makes the model *better informed*. It does not make it *more constrained*.

Two of this project's own measurements sit awkwardly beside that assumption.

- **[RAD-0018](0018-the-selection-ab.md): selection is 0/18 unaided.** No model, however
  capable, picks the library a project has standardised on. Only an *authored preference* —
  something a human wrote down — resolved it.
- **[RAD-0006](0006-development-time-prompt-injection.md): every control routed through the
  model's judgement failed.** Positional discipline, data-framing, meta-argument resistance —
  each depends on the agent declining to act, and each was defeated on some agent.

Those are the same finding in two domains. **Where a decision is left to model judgement, it
is unreliable — whether the decision is "which library" or "should I follow this text".** The
project already responded to the second with architecture ([RAD-0020](0020-information-flow-control.md),
[RAD-0021](0021-admission-control-at-harvest.md)). It has not responded to the first the same
way.

So: **should the engineering judgement live in a deterministic harness rather than in the
model — and if so, what primitives would that harness need?**

## Trail

### The fork, stated fairly

**Harvest-and-inform** (this project). Discover facts about the code — capabilities,
signatures, relationships, provenance — index them, and retrieve the right ones at the right
moment. The agent remains the decision-maker; the contribution is that it decides with better
information. Cheap, incremental, works with any agent, and measured to change behaviour
(RAD-0016: 0/8 → 8/8).

**Encode-and-constrain.** Build a harness that carries the engineering judgement itself: how
to select a solution space, what to check before writing code, what it is allowed to change.
The agent's non-determinism is confined to specific call sites, and deterministic logic runs
around it. The contribution is that fewer decisions are the model's to get wrong.

They are **not exclusive**, and the interesting position is probably that they compose: a
deterministic harness needs facts about the code to make its deterministic choices, and that
is exactly what a codex produces. The harness would be a *consumer* of the codex, not a
replacement for it. That framing should be tested before either is treated as the answer.

### One concrete form: a language for agent requirements

The idea in its sharpest form: a language in which you `import` a dependency that exposes an
**explicit API or contract**, and then write logic describing how the agent should behave —
requirements, values, what to look for before writing new code — with determinism strung
through the non-deterministic calls. That program compiles or runs into an agent operating
under stricter rules about what it may change.

Two things make this worth examining rather than dismissing:

- **The contract boundary would be structural, not prose.** If a dependency declares its
  capabilities in a checked form rather than in a doc comment, the free-text channel at the
  import boundary closes — which is the injection problem solved by construction rather than
  by detection. That is the same reasoning behind RAD-0012's structure tier and RAD-0021's
  grounding, moved to the front of the pipeline.
- **A compile step is a place to enforce.** Tags and doc comments are read by an agent at
  runtime; a language has a stage the *toolchain* owns. That is the only kind of control this
  project's own injection work found reliable.

### The strongest objection, from the project's own prior work

**Custom tags have already been tried here, and delivery was not the problem.** A structured
tag vocabulary is a smaller version of the same idea: a defined, machine-readable channel for
author intent. It did not solve the underlying issue, because **the failure was never the
format — it was that agents are not trained to treat any channel as untrusted.** A language
that merely provides a *better-shaped* channel inherits that result unchanged.

So the question is sharper than "would a language be nice": **what would controlling the
language buy that a structured tag vocabulary does not already buy?** The candidate answer is
*enforcement rather than expression* — a compiler or runtime that refuses, rather than a
notation that describes. If a proposed design cannot point at something it refuses, it has not
cleared this objection.

### Candidate primitives, as a starting list to attack

The useful research question is what such a harness would need. A first list, offered to be
argued with rather than adopted:

- **A capability or effect declaration** — what a module may do (read files, reach the
  network, spend tokens), checkable before it runs rather than observed after.
- **Provenance and trust labels as first-class values**, so that RAD-0020's integrity lattice
  is a type-system concern rather than a convention.
- **A quarantine construct** — process untrusted text in a context with no tool access,
  returning only a constrained value. This is FIDES's variable indirection expressed as a
  language feature.
- **Explicit non-deterministic call sites**, so the deterministic and probabilistic parts of a
  program are visibly separated and independently testable.
- **Contracts on agent output** — what must be true of generated code before it is accepted,
  checked by the harness rather than asserted in a prompt.
- **Budgets** — context, tool calls, edits — as declared limits rather than emergent behaviour.

Most of these are restatements of controls this project already reached by other routes, which
is either evidence the design is coherent or evidence it is unnecessary. Determining which is
the point.

### The prior art is unsurveyed, and that cuts both ways

[RAD-0008](0008-the-field-as-it-stands.md) has now had to withdraw three claims of novelty, so
the reflex is to assume someone has done this already. That reflex is itself a claim about the
field, and it needs checking rather than asserting — **stating that prior art exists without
looking is the same error as claiming novelty without looking.**

What can be said at each level of confidence, and no more:

- **Capability-safe and effect-typed language design is decades of established prior art.**
  The security half of the primitives list above is a rediscovery of that literature, and any
  design work should start from it rather than reinvent it. This is not in doubt.
- **Frameworks for programming language models declaratively exist**, and the primitives list
  overlaps them. Their current state, and how close any comes to this shape, is **not
  surveyed here**.
- **Whether an existing framework already matches this specific shape is unknown.** Nobody
  has looked.

**So the survey is the first task, and its result is genuinely open.** It may find the idea
well covered, in which case this record closes and the finding is a pointer. It may find the
general shape explored but not the specific application — a harness whose deterministic
choices are driven by harvested facts about a real dependency graph. Either outcome is useful;
guessing which in advance is not.

## Findings

**Nothing measured.** This is the earliest-stage record in the set and should be read as such.

**Reasoned.**

- Selection failure (RAD-0018) and injection failure (RAD-0006) are the same structural
  result: model judgement is an unreliable place to put a decision. The project has responded
  architecturally to one and not the other.
- A better channel does not fix a disposition problem, so any language-shaped proposal must
  identify what it *refuses*, not merely what it can *express*.
- Harvest-and-inform and encode-and-constrain most likely compose, with the codex as the fact
  source a deterministic harness consumes.

**What would have to be found out, roughly in order.**

1. **Does the prior art already answer this?** A survey of declarative LM programming, agent
   harness frameworks, and capability-safe language design. If the primitives list is a
   restatement of existing work, that is the finding and the record can close.
2. **What does a harness refuse that a prompt cannot?** Take one concrete failure this project
   has measured — the 0/18 selection result is the obvious candidate — and ask whether a
   deterministic rule would have prevented it. That is a cheap thought experiment before any
   building.
3. **Does the composition hold?** If a harness needs facts about the graph, the codex is its
   input, and the two records stop competing. Testing that is more valuable than building
   either.

## Connections

- [RAD-0018](0018-the-selection-ab.md) — selection unaided at 0/18; the measurement that makes
  this question live.
- [RAD-0006](0006-development-time-prompt-injection.md) — every judgement-routed control
  failed; the same result in the security domain.
- [RAD-0020](0020-information-flow-control.md) — enforcement rather than persuasion, and the
  source of several candidate primitives.
- [RAD-0021](0021-admission-control-at-harvest.md) — a control the pipeline owns rather than
  asks for.
- [RAD-0007](0007-choosing-between-overlapping-libraries.md) — the authored-preference model,
  which is the smallest existing version of "engineering judgement written down".
- [RAD-0008](0008-the-field-as-it-stands.md) — the field record, and the standing warning
  against claiming novelty before surveying.

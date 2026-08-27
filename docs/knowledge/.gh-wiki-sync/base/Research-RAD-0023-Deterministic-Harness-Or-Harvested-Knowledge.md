# A Deterministic Harness, or Harvested Knowledge

RAD-0023 · 2026-08-22 · v2

**v2 (2026-08-22) — the survey is done, and it closes half this record.** The language idea
exists, is published, and covers nearly the whole candidate primitives list below.
**LBAC** (*Language-Based Agent Control*, arXiv:2605.12863) is the same design executed by PL
researchers; **SPL** (arXiv:2607.07727) is the deterministic/probabilistic composition idea as
a language with its own grammar. **This project should not build either.** What survives is
the *other* half of the question — whether a codex is the fact source such a harness consumes —
and the survey sharpened rather than settled it. Findings below.

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

- **[RAD-0018](RAD-0018-the-selection-ab.md): selection is 0/18 unaided.** No model, however
  capable, picks the library a project has standardised on. Only an *authored preference* —
  something a human wrote down — resolved it.
- **[RAD-0006](RAD-0006-development-time-prompt-injection.md): every control routed through the
  model's judgement failed.** Positional discipline, data-framing, meta-argument resistance —
  each depends on the agent declining to act, and each was defeated on some agent.

Those are the same finding in two domains. **Where a decision is left to model judgement, it
is unreliable — whether the decision is "which library" or "should I follow this text".** The
project already responded to the second with architecture ([RAD-0020](RAD-0020-information-flow-control.md),
[RAD-0021](RAD-0021-admission-control-at-harvest.md)). It has not responded to the first the same
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

### The survey, and what it found (v2)

Run 2026-08-22. The result is unambiguous: **this is an active subfield, not an unexplored
idea.**

**LBAC — Language-Based Agent Control** (Zhou, D'Antoni, Polikarpova; arXiv:2605.12863, May
2026) is the idea in this record, built by people who do programming languages for a living.
It *"brings techniques from programming languages and language-based security to the problem of
agent control"*, and its mechanism is stronger than anything sketched above: agents must
**generate programs that are themselves well typed in the context of the surrounding
scaffolding code**, and *"unsafe programs are rejected by the type-checker before execution"*,
so policies *"apply uniformly across the entire application, including both agent-generated
behavior and developer-written scaffolding."* Expressiveness is preserved — agents may do
arbitrary side-effect-free computation and recursively invoke subagents, which keep tool access
under the same or stricter policies. It builds on **LIO**, the established labelled-IO
information-flow library, which is precisely the "rediscovery of the capability-safe
literature" this record predicted.

**SPL — Structured Prompt Language** (Gong; arXiv:2607.07727, July 2026) is the other half:
*"a declarative language that composes deterministic and probabilistic computation modes in a
single specification"*, with `GENERATE`/`EVALUATE` for probabilistic steps and `SOLVE`/`ASSERT`
for deterministic ones, sharing syntax and bindings, in its own `.spl` grammar. That is
"determinism strung through the non-deterministic calls" as a shipped language.

**And there is more of it.** *Securing Agents With Tracked Capabilities* (ACM Conference on AI
and Agentic Systems), *A Fast, Reliable, and Secure Programming Language for LLM Agents with
Code Actions* (arXiv:2506.12202), *A Language for Describing Agentic LLM Contexts*
(arXiv:2605.01920), and *SoK: Trust-Authorization Mismatch in LLM Agent Interactions*
(arXiv:2512.06914). Adjacent again: DSPy compiles declarative LM calls and optimises prompts;
LMQL constrains decoding at the query level.

### The candidate primitives, scored against what exists

| primitive sketched above | covered by |
|---|---|
| Capability / effect declaration | **LBAC** (LIO effect types) |
| Provenance and trust labels as first-class values | **LBAC** (LIO confidentiality + integrity labels) |
| Quarantine construct | **LBAC** (`toLabeled`; quarantined subagents that keep tool access under label discipline) |
| Explicit non-deterministic call sites | **SPL** (`GENERATE`/`SOLVE` split); LBAC's scaffolding/agent-code split |
| Contracts on agent output | **LBAC**, and more strongly — static type-checking rather than runtime assertion |
| Budgets (context, tool calls, edits) | **not obviously covered** by either |

Five of six, and the sixth is not interesting enough to justify a language. **The question
"what primitives would such a harness need" is answered, by other people, better.**

## Findings

**Surveyed (2026-08-22).** The language half of this record is answered and closed: LBAC and
SPL exist, LBAC covers five of the six candidate primitives with a stronger mechanism than the
one sketched here, and the security primitives are an application of established IFC and
capability-safe language work. **Building a language for agent requirements is not work this
project should do.**

**What the survey did not settle — and sharpened.** The record asked two questions, and only
the second is closed. The first — *should engineering judgement live in a harness rather than
the model?* — is now better posed, because a concrete counterpart exists to compose with rather
than a hypothetical. **LBAC type-checks agent-generated programs against the surrounding
scaffolding; type-checking requires facts about the code.** A codex produces facts about the
code. That is the composition hypothesis this record raised, now with a real system on the
other side of it instead of an imagined one.

**Reasoned.**

- Selection failure (RAD-0018) and injection failure (RAD-0006) are the same structural
  result: model judgement is an unreliable place to put a decision. The project has responded
  architecturally to one and not the other.
- A better channel does not fix a disposition problem, so any language-shaped proposal must
  identify what it *refuses*, not merely what it can *express*.
- Harvest-and-inform and encode-and-constrain most likely compose, with the codex as the fact
  source a deterministic harness consumes.

**What remains to be found out.**

1. ~~Does the prior art already answer this?~~ **Answered: yes.** LBAC and SPL, plus an active
   surrounding literature.
2. **What does a harness refuse that a prompt cannot?** Still worth asking, and now answerable
   against a real system rather than a sketch: would LBAC's type discipline have prevented the
   0/18 selection failure in [RAD-0018](RAD-0018-the-selection-ab.md)? That is a cheap thought
   experiment and it tests whether the fork matters for *this* project's problem, which is
   selection rather than security.
3. **Does the composition hold?** The live question. If LBAC-style systems need facts about the
   code to type-check against, a codex is a supplier to them rather than a competitor — which
   would make this record's fork a false one. Establishing that is worth more than anything
   else here.

## Connections

- [RAD-0018](RAD-0018-the-selection-ab.md) — selection unaided at 0/18; the measurement that makes
  this question live.
- [RAD-0006](RAD-0006-development-time-prompt-injection.md) — every judgement-routed control
  failed; the same result in the security domain.
- [RAD-0020](RAD-0020-information-flow-control.md) — enforcement rather than persuasion, and the
  source of several candidate primitives.
- [RAD-0021](RAD-0021-admission-control-at-harvest.md) — a control the pipeline owns rather than
  asks for.
- [RAD-0007](RAD-0007-choosing-between-overlapping-libraries.md) — the authored-preference model,
  which is the smallest existing version of "engineering judgement written down".
- [RAD-0008](RAD-0008-the-field-as-it-stands.md) — the field record, and the standing warning
  against claiming novelty before surveying.

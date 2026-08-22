---
title: Decisions
description: What this project has committed to, and what is still being worked out.
---

This project keeps a firm line between a **decision** — a hard-to-reverse
commitment — and a **finding** that has not yet hardened into one. Only the
first belongs here; the rest is [research](/research/).

## Settled

**Adopt the Agent Skills standard; do not invent a format.** A skill is a
`SKILL.md` directory with `name` and `description`, per
[agentskills.io](https://agentskills.io). Inventing an alternative beside a
working one buys nothing and costs a migration — it was this project's own
first mistake. The format has since been adopted unchanged by a major vendor,
which makes it the obviously right call.

**Conform where a convention works; reject what cannot be reconciled; propose
only the unclaimed part.** Where an ecosystem already has a convention, emit
into it rather than arriving with a rival layout. Where a convention exists but
carries a defect this project has measured — the in-jar bundling that drops out
of an Android AAR, the scan-everything-into-context loading model that has no
aggregate budget — do not adopt it, and say why with evidence. The one
genuinely unclaimed part, an index that makes many skills usable at scale, is
proposed everywhere.

**Get library content from what already ships.** Library documentation is
obtained from the `-sources.jar` every library already publishes (93–98%
adoption), the first-party source tree, and the git repository named in the
POM — never a bespoke artifact. The sidecar approach is abandoned. This settles
*getting* the content; parsing, storing and querying it are the next stages.

**Library content never reaches the instruction channel, and the transitive
tail is off by default.** An agent that reads harvested library documentation
can be redirected by an instruction planted in it. We measured this across
agents from several vendors, and the result is architectural: presenting the
text as quoted, untrusted data helps, but it is *not sufficient* — moving the
same text into the system channel defeats it outright, and a payload that
simply argues the untrusted-data framing is a test defeats it on models it
otherwise protects. Since no property of the agent can be relied on, the
control has to sit in the codex: library prose is placed where it cannot be
followed, and the transitive tail — the bulk of the surface — is excluded
unless asked for. This is a negative result about this project's own proposal,
and it is published with the mitigation rather than after it.

**Publish security findings as observations, not verdicts.** Per-model results
are reported as what was measured, on what date, at what sample size — never as
a trust judgement about a vendor's model, because the samples are small and the
cost of over-claiming lands on someone else. Conclusions that generalise are
stated about architecture, which every agent tested supports. Payloads
published with the work demonstrate the class and are never tuned to maximise
bypass rates. Named vendors are told before the results go out and are offered
a right of reply, as a courtesy rather than an embargo — prompt injection is a
known, unfixed class, so there is no patch to wait for.

## Still being worked out

Current research directions, not commitments:

- **Parsing, storing and querying the content.** Getting the content is
  decided; extracting the documentation from the carrier (reusing the
  API-extraction tooling IDEs already run at scale), storing it as a codex and
  serving it are the next stages. The measurement underneath them — whether
  harvested documentation actually changes what an agent writes — has since
  been run, and it does; what remains is building the pipeline against real
  corpora rather than synthetic ones.
- **A two-layer index** — a small always-present trigger, plus an on-demand
  catalogue that maps a need to a library and records which one *this* project
  reaches for. Several vendors have independently converged on the shape.
  Retrieval has now been measured in both layers, so what is still open is the
  engineering rather than the premise.
- **Information-flow control as the trust model.** The injection mitigation
  above is positional — it puts library prose where it cannot be followed — but
  it still depends on the agent declining to act, which is where the
  measurement found the weakness. A published alternative labels content and
  enforces policy before a sensitive tool runs, so persuasion cannot reach
  anything that matters. A codex cannot enforce that, but it is the natural
  place to compute the labels. Under investigation, with a falsifiable
  experiment specified.
- **A capability server**, local first, as a query front-end over the corpus.
  What injection means for it is now settled above; what remains open is the
  server's own shape.

The full trail is in [the research](/research/). When a direction hardens into
a commitment, it graduates to a decision and moves up here.

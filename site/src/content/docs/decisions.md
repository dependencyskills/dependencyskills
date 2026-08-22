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

## Still being worked out

Current research directions, not commitments:

- **Parsing, storing and querying the content — and whether it is worth it.**
  Getting the content is decided; extracting the documentation from the carrier
  (reusing the API-extraction tooling IDEs already run at scale), storing it as
  a codex, and serving it are the next stages. The open measurement underneath
  them all: whether harvested documentation, at its real ~33% coverage,
  actually changes an agent's behaviour.
- **A two-layer index** — a small always-present trigger, plus an on-demand
  catalogue that maps a need to a library and records which one *this* project
  reaches for. Several vendors have independently converged on the shape; the
  design here is still open.
- **A capability server**, local first, as a query front-end over the corpus —
  and whether development-time prompt injection changes what is safe to ship.

The full trail is in [the research](/research/). When a direction hardens into
a commitment, it graduates to a decision and moves up here.

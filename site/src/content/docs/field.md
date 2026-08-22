---
title: The field as it stands
description: What others have built, where it corroborates this work, and where each pattern's limits are.
---

The field has moved quickly, and mostly toward this project's conclusions. This
is a summary of what exists, drawn from the research record of the same name;
[the landscape](/landscape/) is the raw source list behind it.

## What has converged

**The format is settled, and a major vendor adopted it unchanged.** Microsoft's
Agent Framework implements the Agent Skills format directly, self-described as
agentskills.io-compatible, with four-stage progressive disclosure. Adopting the
format was the right call — and is now the obviously right one.

**Progressive disclosure of skill *bodies* is mainstream; the resident
*description* budget is addressed nowhere.** Everyone loads bodies on demand.
Nobody bounds the always-resident cost of the descriptions themselves, which
stays proportional to the number of skills: roughly 100 tokens per
advertisement, times 311 libraries for Now in Android or 995 for a large
Next.js app, is 31k–99k tokens before any work begins. That budget is this
project's distinct contribution.

**The instruction channel is the universal default, and that is the exposure.**
Whatever the loading strategy, third-party content lands where the agent treats
it as something to act on — skill bodies, rules files, `llms.txt`, MCP tool
descriptions. Progressive disclosure does not change this; it answers a
context-budget question, not a trust one. That default is precisely the
condition [our injection measurement](/injection/) found worst, so the result
is a property of the category rather than of any one design.

## Where it corroborates this work

**The two-layer shape was arrived at independently, several times.** A small
always-resident trigger plus an on-demand index is what Microsoft's four-stage
model, MCP's Catalog / Inspect / Execute, and llms.txt all describe. Independent
convergence from different starting points is evidence the shape is correct, not
that it was copied.

**The cost has been measured at scale.** SkillReducer analyses 55,315 public
skills: 26.4% carry no routing description, over 60% of body content is
non-actionable, and compression cuts descriptions 48% and bodies 39% at an 86%
pass rate — with a "less-is-more" effect that *raised* performance 2.8%. It
measures a skill *corpus*; this project measures cost per library in a resolved
dependency *graph*. Different populations, same direction.

## Three corrections this research forces

**"Nobody has costed this" is false.** SkillReducer costed it three orders of
magnitude past this project's own corpus. The defensible, still-novel claim is
narrower: *nobody has costed it against a dependency graph* — a population
nobody selected, 311 to 995 libraries deep.

**"Maven has nothing" is false — and the correction helps.** SkillsJars ships
agent skills to Maven Central today, but it ships them *inside the jar*
(`META-INF/skills/…`) — the exact mechanism this project already measured as
broken on Android AARs and Kotlin Multiplatform. The sharpened claim: the JVM
approach that exists works for plain jars and does not survive Android or KMP,
and this project holds the measurements that show why.

**"Nobody has answered the trust problem" would be false — and this is the
correction that costs the most.** Microsoft's Agent Framework ships FIDES:
information-flow control built on published research, where content carries
integrity and confidentiality labels, the labels propagate automatically
through tool calls, and policy is enforced deterministically *before* a
sensitive tool runs. Untrusted text can be held behind a reference and read
only by a quarantined model with no tool access. That is a **stronger class of
control than this project's own recommendation**, because it never asks the
model to decline — and the model declining is exactly where our measurement
found the weakness. There is also published academic work measuring injection
through third-party agent skills, which we have not read. The narrower
defensible claim: this project measured the failure against a *library
documentation* corpus, across agents a developer actually runs locally, and it
is now [investigating adopting the flow-control model](/research/) rather than
competing with it.

## Where the limits are

The field's one answer to scale — *be selective, add skills only for packages
your agent struggles with* (skilld, and Microsoft's filtering predicate) — is
manual curation, per project, by hand. It corroborates the diagnosis while
offering an answer the measurements reject: nobody hand-curates 311 candidates,
and nobody re-curates them at every dependency bump.

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

## Two corrections this research forces

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

## Where the limits are

The field's one answer to scale — *be selective, add skills only for packages
your agent struggles with* (skilld, and Microsoft's filtering predicate) — is
manual curation, per project, by hand. It corroborates the diagnosis while
offering an answer the measurements reject: nobody hand-curates 311 candidates,
and nobody re-curates them at every dependency bump.

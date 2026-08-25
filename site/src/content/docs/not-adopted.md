---
title: What we do not adopt, and why
description: The existing skill and documentation standards, how each one works, and the measured reason this project does not use them — one shared problem, and one nobody has addressed.
---

The field is not empty and the work in it is good. Several efforts, starting from different places,
arrived at the same two-layer design this project arrived at — a small resident trigger and an
on-demand body. That convergence is the useful signal: when independent groups land on the same
shape, the shape is probably right.

So this page is not a list of things that are wrong. **We adopt the format** — see
[adopted standards](/standards/). What we do not adopt is the **discovery model** that ships with
it, for one measured reason that applies to all of them, and one nobody in the field has addressed
yet.

## The problem they share

Every one of these makes a skill **resident**: its description sits in the agent's context so the
agent knows the skill exists. Microsoft's own figure is about **100 tokens per advertisement**, and
our measurement of a public corpus put it near 60 — the same order.

The difficulty is what that multiplies by. A real project's *importable* surface is **112 to 995
libraries**, and 86–99% of a JVM dependency graph can be called without touching the build file.
One resident description per library is **20,000 to 139,000 tokens before any work begins.**

The cost scales with the number of dependencies, and the number of dependencies is not something
the design gets to choose.

## What each one does

Every entry links to the project so you can read it yourself rather than take our summary for it.

### [Agent Skills](https://agentskills.io) — the `SKILL.md` format

A skill is a directory with a `SKILL.md` carrying `name` and `description` in frontmatter, plus
optional `scripts/`, `references/` and `assets/`. The [specification](https://agentskills.io/specification)
and [repository](https://github.com/agentskills/agentskills) are public.

**What it gets right:** a plain, readable, version-controllable file with a strict description
budget. It is diffable, reviewable, and needs no runtime. A vendor adopted it unchanged, which is
about the strongest signal a format can get.

**We adopt this.** It is on the [adopted standards](/standards/) page, not this one.

### [agentskills RFC #81](https://github.com/agentskills/agentskills/issues/81) — discovery in the manifest

Proposes an `agentskills` field in `package.json` listing skill paths, so a consumer reads the
manifest instead of globbing directories.

**What it gets right:** discovery belongs in the manifest. A field that a package *declares* is
unambiguous, survives packing, and does not depend on a directory convention surviving a publish
step. We reached the same conclusion independently.

**Why it is not enough on its own:** it settles *where to look*, which is the easier half. It does
not address how much stays resident once you have looked.

### [Microsoft Agent Framework](https://learn.microsoft.com/en-us/agent-framework/agents/skills) — advertise, then load

Ships `advertise` / `load_skill` / `read_skill_resource` / `run_skill_script`, with a filter
predicate for choosing which skills are offered.

**What it gets right:** the two-layer split — a small resident advertisement, the body fetched on
demand — is the same design this project arrived at from a different starting point, and having a
major vendor ship it is corroboration we could not have manufactured. It also publishes a real
number for the resident cost, which most of the field does not.

**Why we do not use it as-is:** the filter predicate is manual curation, and the resident budget is
left to the operator. At ~100 tokens per advertisement, the arithmetic on a real dependency graph
is the problem this project exists to solve.

### [SkillsJars](https://www.skillsjars.com/docs) — skills inside the jar

Publishes agent skills to Maven Central at `META-INF/skills/<org>/<repo>/<skill>/SKILL.md` inside
the artifact, with Maven and Gradle plugins that package on build and extract on consume.

**What it gets right:** it is the only project to have seriously attacked the JVM gap, and it is
shipping today. Putting the content in the artifact means it is version-exact by construction and
travels through every mirror and proxy without new infrastructure. That is genuinely the right
instinct.

**Why we do not use it:** this project **built and measured the same mechanism**, and it breaks on
Android and Kotlin Multiplatform, where the artifact a consumer resolves is not the one carrying
the file. We are not criticising a road we did not walk — this is our own v2, and the failure is
why there is a v3.

### [skilld](https://github.com/skilld-dev/skilld) — be selective

Add skills only for the packages your agent actually struggles with.

**What it gets right:** it is the most honest answer in the field. It correctly identifies that you
cannot afford a skill per dependency, and it declines to pretend otherwise. The diagnosis is right
and matches ours exactly.

**Why we do not use it:** the remedy is per-project hand curation. Our measurement put a real
project at 311 candidates, and they change at every dependency bump. The approach is sound at a
scale smaller than the one we are trying to reach.

### npm directory conventions — [library-skills.io](https://library-skills.io), [skills-npm](https://github.com/antfu/skills-npm), [Vercel's skills CLI](https://github.com/vercel-labs/skills), [mise](https://github.com/jdx/mise/discussions/9479)

Variations on `skills/<name>/SKILL.md` or `.agents/skills/<name>/SKILL.md` at package root.

**What they get right:** they work now, with no coordination required. A convention that a
publisher can adopt unilaterally is how ecosystems actually change, and these are the reason npm is
the furthest along.

**Why we do not pick one:** several are in circulation. Choosing a favourite would be the
reinvention we are trying to avoid, so we read whichever an ecosystem uses.

### [llms.txt](https://llmstxt.org) — index separate from full text

A small index file pointing at fuller documents.

**What it gets right:** the same index-plus-body split, applied to websites rather than packages,
and it got there first. Convergence from an entirely different domain is the strongest kind.

**Why it is not a substitute:** it is a publishing convention for documentation sites, not a
dependency-resolution mechanism. Complementary rather than competing.

### [maven-tools-mcp](https://github.com/arvindand/maven-tools-mcp) and [Context7](https://context7.com/) — a service, queried by identifier

An MCP server that fetches library information by resolved coordinate.

**What they get right:** they prove the ingestion is tractable and that a capability service keyed
by coordinate is buildable — a claim this project had only argued for. Being remote means one
update reaches everyone.

**Why they are a third layer rather than a replacement:** a query service answers when the agent
thinks to ask. It cannot help with reinvention, which is the case where the agent never asks
because it does not know there is anything to ask about. It also needs the network, and a local
index does not.

## Where we diverge, in one line

They make the library's documentation **resident**. We make it **retrievable** — one index, searched
by need, with only what matches entering context.

That is not a criticism of the resident model; it is what you would build if the number of skills
were small, and for a hand-picked set it is the better answer. It stops working at the size of a
real dependency graph, which is the size this project set out to handle.

## And one thing none of them addresses

Every design here — ours included — moves **prose written by a third party into an agent's
context**. We tested what happens when that prose contains instructions rather than descriptions,
and the answer is that many agents follow them, including strong ones. Framing the text as untrusted
data helps considerably and is **not sufficient**: moving the identical words into the system
channel defeats it outright, and a payload that simply argues the framing is a test harness defeats
it on agents it otherwise protects.

No existing supply-chain control reaches this. Signatures prove *who* published a file; SBOMs record
*what* is present; scanners look for dangerous *code*. The target here is the model's judgement, and
none of those three inspects a sentence.

This is not a gap we are pointing at in someone else's work — **it is a hazard this project's own
proposal creates**, which is why the whole study is published rather than quietly designed around.
The in-jar approaches inherit a sharper version of it, because content placed inside the artifact
arrives looking first-party.

What came out of measuring it is the one design rule we would offer to anyone building in this
space: **removing a channel has worked every time; recognising an attack has failed every time.**

[The full study is here](/injection/), including the payloads, the per-agent numbers and the
mitigations that did and did not hold.

## If we have got your project wrong

These summaries are written from public documentation and from our own measurements, by people who
are not the maintainers. That is a good way to be wrong — a design may have moved since we read it,
a limitation we describe may already be fixed, or we may simply have misread what a project is for.

**If you maintain one of these and we have mischaracterised it, please tell us and we will correct
it.** Open an issue on
[the repository](https://github.com/dependencyskills/dependencyskills/issues) and say what is wrong.
A correction from a maintainer outranks our reading of the docs, and we will say plainly what
changed rather than quietly editing the page.

The same goes for anything we have left out. This list is what we found; it is not a survey, and an
omission is much more likely to be ignorance than judgement.

Nothing here is a competitive claim. Everyone in this space is trying to get more out of an agent
than it manages alone, and most of the designs above arrived at conclusions we independently
reached — which is why we cite them as corroboration in [the field as it stands](/field/) rather
than as alternatives we beat.

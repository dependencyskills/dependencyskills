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

Every entry links to the project so you can read it yourself rather than take our word for it.

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
predicate over an allowlist for choosing which skills are offered.

**What it gets right — and on security it is ahead of us.** The two-layer split (a small resident
advertisement, the body fetched on demand) is the design this project reached independently, and a
major vendor shipping it is corroboration we could not manufacture. It also publishes a real number
for the resident cost, which almost nothing else in the field does.

More importantly, its **FIDES** middleware applies *information-flow control*: content carries
integrity and confidentiality labels, the labels propagate through tool calls, and policy is
enforced before a sensitive tool runs. Its skills documentation treats MCP-sourced skills as
untrusted by design, does not execute scripts fetched from remote sources, and gates skill-loading
behind approval.

**That is stronger than what this project recommends** — closer to a type system for trust than to a
placement rule — and our own research record names it as *prior art to learn from rather than
reinvent*. If you are choosing a framework today and injection is your concern, this is the one that
has thought hardest about it.

We have **not** tested it, and say so rather than implying otherwise.

**Why we do not use it as-is: the curation step.** The filter predicate is the answer to too many
skills, and it is manual selectivity — a human deciding, per project, which dependencies are worth a
skill. The arithmetic is what defeats it. At Microsoft's own ~100 tokens per advertisement:

| a real project | importable packages | resident cost if advertised |
|---|---|---|
| p90 Next.js example | 995 | **99k tokens** |
| default `create-next-app` | 423 | **42k tokens** |
| a JVM project after resolution | 311 | **31k tokens** |

Before any work begins. So something must be filtered out — and the only mechanism offered is a
person choosing, then re-choosing at every dependency bump. **That puts a curation step in front of
the discovery mechanism**, which is the same objection pnpm's RFC #13422 raised against directory
conventions, and the same failure this project's own v1 died of. Nobody hand-curates 311 candidates,
and the ones most worth having are the ones you have not heard of.

An index has no such step: everything is harvested, nothing is resident, and retrieval decides at
query time rather than a human deciding in advance.

**One honest caveat on the FIDES side**, from our own measurements rather than theirs. We built a
coarse information-flow control and measured it: it blocked the credential **0 of 3 times harmful**
and blocked the developer's legitimate task **0 of 3 times successful** — a total denial of service,
because the attack causes the read that taints everything downstream. Label granularity is a
requirement, not a refinement. We do not know how fine FIDES' labels are, and that question decides
whether the approach is usable or merely correct.

### [SkillsJars](https://www.skillsjars.com/docs) — skills inside the jar

Publishes agent skills to Maven Central at `META-INF/skills/<org>/<repo>/<skill>/SKILL.md` inside
the artifact, with Maven and Gradle plugins that package on build and extract on consume.

**What it gets right:** it is the only project to have seriously attacked the JVM gap, and it ships
today. Putting content inside the artifact makes it version-exact by construction and carries it
through every mirror, proxy and air-gapped repository with no new infrastructure. That instinct is
correct, and it is correct for the same reasons we found it convincing.

**Why we do not use it: we designed the same thing independently, shipped two versions of it, and
measured it failing.** The records are public, so you can check the reasoning rather than take our
word for it:

- **v1 — the file inside the artifact**, authored at
  `src/commonMain/resources/META-INF/ai-skills/` and packaged as an ordinary resource. This is
  effectively the SkillsJars mechanism.
  [The postmortem](https://github.com/dependencyskills/dependencyskills/blob/master/docs/knowledge/research/postmortems/RAD-0046-v1-bundled-flat-files.md) records what went wrong and why.
- **v2 — a classified sidecar**, `<artifact>-<version>-skills.zip` published beside the main
  artifact, to sidestep what v1 hit.
  [ADR-0003](https://github.com/dependencyskills/dependencyskills/blob/master/docs/knowledge/decisions/ADR-0003-library-skills-via-repository-artifacts.md) decided it and marks where it
  was superseded.
- **v3 — harvest what already ships**, [ADR-0009](https://github.com/dependencyskills/dependencyskills/blob/master/docs/knowledge/decisions/ADR-0009-transport-is-sources-jar.md).

**We shipped it. It is on Maven Central right now.** Nine libraries under `io.github.aughtone`
publish an `ai-skill.md` at `META-INF/ai-skills/` inside the jar — 1.4 KB to 7.9 KB each, across
several versions. Anyone can download one and unzip it; the files are exactly where they should be.

**And nothing found them.** That is the first failure and it has nothing to do with packaging: a
file inside an artifact has no discovery mechanism. The consumer has to already know the convention,
already know to unpack the jar, and already be looking. Publishing worked perfectly and changed
nothing, because discovery was the actual problem and the artifact does not solve it.

**The second failure is that it does not survive the rest of the ecosystem**, established by
building real libraries and unzipping the output rather than by reading documentation. Three
separate mechanisms, on the platforms that matter most:

| | |
|---|---|
| a KMP library's `commonMain/resources` are **silently dropped from the AAR** | [KT-46493](https://youtrack.jetbrains.com/issue/KT-46493), open since 2021 |
| AGP strips `META-INF/MANIFEST.MF` from an AAR's nested `classes.jar` | so there is nowhere left to *declare* a location either |
| Kotlin/Native does not package those resources at all | the file never exists on native targets |

The word doing the work is **silently**. The build succeeds, the artifact publishes, and the skill
is absent — so a consumer written entirely correctly receives nothing and has no way to know. That
is a worse failure than an error.

It is also why the transport is now the `-sources.jar` that 93–98% of artifacts already publish: a
carrier that already survives every one of those pipelines, because the ecosystem has been shipping
it for years — and one a consumer resolves by coordinate rather than by knowing a convention.

**We walked this road.** It is our own v1 and v2, published under our own name, still sitting in
Maven Central where anyone can check. Their failure is why there is a v3, and it is the reason this
entry is longer than the others — not because SkillsJars is worse than the alternatives, but because
we can say exactly what happens next, having done it.

### [skilld](https://github.com/skilld-dev/skilld) — be selective

Add skills only for the packages your agent actually struggles with.

**What it gets right:** it is the most honest answer in the field. It correctly identifies that you
cannot afford a skill per dependency, and it declines to pretend otherwise. The diagnosis is right
and matches ours exactly.

**Why we do not use it:** the remedy is per-project hand curation. Our measurement put a real
project at 311 candidates, and they change at every dependency bump. The approach is sound at a
scale smaller than the one we are trying to reach.

### npm directory conventions — [library-skills.io](https://library-skills.io), [skills-npm](https://github.com/antfu/skills-npm), [Vercel's skills CLI](https://github.com/vercel-labs/skills), [mise](https://github.com/jdx/mise/discussions/9479)

Variations on `skills/<name>/SKILL.md` or `.agents/skills/<name>/SKILL.md` at package root,
discovered by scanning the unpacked `node_modules` tree.

**What they get right:** they work today, with no coordination required. A convention a publisher
can adopt unilaterally is how ecosystems actually change, and this is why npm is the furthest along
of any ecosystem. The unpacked tree also makes the content trivially readable — no archive to open,
no build step, no plugin.

**Why we do not use it.** Not because there are several to choose from. Because a directory scan is
the wrong shape for this problem in the ecosystem where the problem is largest.

**The scan cannot load less than what is on disk.** That is the whole difficulty in one sentence. A
JVM project can at least distinguish declared from transitive; `node_modules` is **flat**, so every
package is importable and there is no smaller set to fall back to.

| a real npm project | packages on disk | resident cost at ~100 tokens each |
|---|---|---|
| p90 Next.js example | **995** | **99k tokens** |
| default `create-next-app` | **423** | **42k tokens** |

Halving that with a better filter does not help: **halving O(n) is still O(n)**, and the number grows
every time someone adds a dependency. The mechanism has a boundary of roughly tens of skills, and
none of these proposals publishes a number, so none of them states where its own boundary is.

**And the security position is worse in JavaScript than anywhere else**, which matters because this
is the ecosystem the convention is strongest in. Two measured reasons:

- **Every prose payload that worked in our cross-language test worked in JavaScript.** None landed
  in Kotlin, Java or Swift. A JS entry has no type signature to anchor on, so the prose carries more
  of the meaning — and reading the process environment is one line in Node where the JVM languages
  need real file handling.
- **JavaScript has an escape hatch the others do not.** A quoted property key accepts arbitrary
  text where an identifier grammar would refuse it, so the form constraints that close the
  identifier channel elsewhere have a hole here by language design.

Put together: the ecosystem with **100% of packages importable**, the **flattest trust graph**, the
**highest measured prose exposure**, and a **grammar that permits arbitrary text in a code position**
is the one being handed a convention that loads everything on disk into the context window.

That is not a criticism of the people building these — the convention is genuinely the right way to
get adoption, and it is why npm leads. It is that adoption was the easy half. **We index instead:
harvest everything, keep nothing resident, and let retrieval decide at query time** — which is the
same answer regardless of how flat the graph is or how many packages arrive with it.

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

**And in almost every one of these designs, the agent reads that prose directly.** There is nothing
between the library's text and the model: a `SKILL.md` is loaded and becomes instructions, an
`llms.txt` is fetched and pasted in, an MCP tool description arrives as part of the tool contract, a
documentation service returns the text and the agent reads the reply. In each case the words a
third party wrote are the words the model sees, unaltered.

**"Instructions" here means anything a sentence can ask for.** What we planted and measured, in the
register of ordinary API documentation:

- **Send data somewhere.** A setup note saying the process environment must be copied into a
  telemetry log before first use. Many agents wrote code that did it.
- **Call something that does not exist.** A directive naming an API in neither the library nor its
  dependency graph — the agent writes against a surface the publisher controls.
- **Act on real files, not just write code.** Given actual tools, a doc claiming "support
  diagnostics" required appending a credentials file to a log, one agent **staged a planted
  credentials file into a telemetry log in 2 of 3 runs** — it did the thing, rather than describing
  it.

Published attack corpora add configuration poisoning and disclosure to that list. **None of it has
to look like an attack.** The forms that worked read as preconditions, compatibility caveats and
migration advice — the registers a developer expects to be told something in, which is exactly why
they are followed.

That is not an oversight in any of them — it is what they are *for*. Getting the library's own
documentation in front of the agent is the entire point, and a step that rewrote it would look like
a step that damages it. It also means there is **no place to put a check even if someone wanted
one**, which is much of why nobody has.

On most ecosystems the situation is worse still, because the text does not need to be *delivered* at
all. An npm package sits exploded in `node_modules`, a Python one in `site-packages`; every README
and docstring from every transitive dependency is already in the working tree, and an agent that
greps the codebase reads it without any design admitting it.

No existing supply-chain control reaches this either. Signatures prove *who* published a file; SBOMs
record *what* is present; scanners look for dangerous *code*. The target here is the model's
judgement, and none of those three inspects a sentence.

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

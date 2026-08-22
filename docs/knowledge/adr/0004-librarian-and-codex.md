# ADR-0004: A librarian skill and a harvested codex, not one skill per library

Date: 2026-08-05 · Re-minted 2026-08-19 · Status: accepted · v2

**Re-mint note (2026-08-19).** Withdrawn in the ADR cull as premature, then
restored. The **two-layer decision** it makes — a resident *librarian* that
triggers plus an on-demand *codex* that catalogues — has since hardened across the
research (RAD-0003, RAD-0010, RAD-0013) and is corroborated by independent arrivals
in the field (RAD-0008), which is the RAD→ADR graduation bar. Read it for that
architecture. Its **content-generation specifics predate the
documentation-transport pivot**: the codex is no longer built from harvested
`SKILL.md` bodies but from documentation that already ships (ADR-0009 gets it from
the `-sources.jar`; RAD-0011 on content, RAD-0009 on parsing, RAD-0013 on the
entry). Where this record says "harvested skill bodies," read "harvested
documentation."

Companion to the transport decision (now **ADR-0009** — content is got from the
`-sources.jar`). This one decides how an agent finds the right library among many.

## Context

### The ceiling

Agent Skills load in layers: `name` and `description` sit in context always,
the body loads when the skill triggers, bundled resources load when read.
That budget is roughly **100 tokens per skill, permanently resident**.

A project has hundreds of dependencies. At 100 tokens each, 300 dependencies
shipping skills is ~30,000 tokens of description before any work begins. The
platform's promise that you can install many skills without a context penalty
holds somewhere up to 50–150 skills and then stops holding. **One skill per
library does not scale, and the platform default does not cover us.**

Nor do the paths help: `.agents/libraries/org.dependencyskills.types/` says
nothing about what is inside. An agent that needs to know what is available
would end up reading everything — the exact outcome the layering exists to
prevent.

### Overlap is the domain, not a defect

The literature calls competing, semantically overlapping descriptions "skill
collision" and treats it as a quality problem to be cleaned up. In a
dependency graph it is a **property of the territory**. A real project has
several HTTP clients, several JSON serializers, more than one way to do dates
and DI, because that is what dependency graphs look like.

So the goal is not to eliminate overlap. It is to **shortcut the churn** —
stop the agent flailing between equivalent options, or picking the one this
project doesn't use. That reframes the codex's job: not only "what covers
this capability" but "**which one do we reach for here, and why not the
others.**"

That second part is local knowledge. It cannot be harvested from a library,
because no library knows what else is on your classpath.

### What the ecosystem has settled

The two-layer shape — one small always-present entry, plus an on-demand index
— is not speculative. Four independent convergences: MCP's spec now documents
Catalog → Inspect → Execute; Anthropic ships Tool Search with `defer_loading`;
OpenAI ships `defer_loading` with namespaces; llms.txt splits index from full
text.

The decisive measurement: deferring tools behind a search cut context ~85%
**and raised** selection accuracy from 49% to 74%. Indexing is not a cost
compromise; fewer candidates in context makes the choice better.

What nobody has solved: **skills harvested from package dependencies at
scale.** antfu's skills-npm proposal, TanStack and Electric's shipped skills,
and skilld all built distribution and punted on the index — skilld's
documented advice is "be selective, only add skills for packages your agent
struggles with." That is a dodge, and it is the state of the art. The codex
is the novel part of this work; the sidecar is the boring part.

## Decision

### 1. Two layers: a librarian that triggers, a codex that catalogues

```
.agents/skills/librarian/SKILL.md   committed   trigger + how to use the codex
.agents/libraries/CODEX.md          gitignored  the catalogue
.agents/libraries/<name>/SKILL.md   gitignored  harvested bodies
```

The **librarian's `description` is the load-bearing artifact of this entire
design** — more than the plugin, more than the variant. It is the only thing
always in context, and its whole job is to fire at the right moment. It must
name the moments, not the libraries: before writing a helper, an HTTP call,
date handling, serialization, a result type, retry or caching logic. It must
never attempt to summarise what is available; that is the codex's job and it
does not fit.

The **codex is linked, not inlined.** Measured across 2,400 agent tasks on 20
documentation sites: raw HTML averaged 2.23 wrong-path errors per task, plain
markdown 1.42, markdown *linking* to an index 0.11. Linking captured the same
benefit as inlining at a fraction of the tokens.

### 2. What a library skill must contain

Discovery is worthless if the harvested bodies do not say what they cover.
The [Agent Skills spec](https://agentskills.io/specification) requires only
`name` and `description`, has **no capability, tag, or category field**, and
`metadata` is a string→string map (so any list must be a delimited string).
[library-skills.io](https://library-skills.io/create/) explicitly declines to
prescribe content. **This is unspecified ground and we should write it.**

A library skill should carry:

- **`description`** — what it is for and *when a caller should reach for it
  instead of writing their own*. This is what the codex is built from and
  what an agent matches on.
- **Capabilities in prose** — the problems it solves, in the words a caller
  would use to describe the problem, not the words the API uses. An agent
  searching for "retry with backoff" will not match "resilience policies".
- **Usage patterns** — the two or three ways it is meant to be used.
- **Invariants and traps** — what looks reasonable but is wrong here.
  Threading, lifecycle, mutability, error handling.
- **What it is NOT for** — the negative boundary. This is what makes an entry
  discriminative when three libraries overlap, and it is the field most often
  missing.
- **Provenance** — `repository`, `skill-url`, and the resolved version.

**We do not ask library authors to classify into a taxonomy.** schema.org's
own retrospective states the rule: where publishers vastly outnumber
consumers, the complexity belongs with the consumers. UDDI died doing the
opposite; npm keywords survive only as ungoverned free text. Authors write
prose; **we normalise.**

### 3. The codex is harvested and normalised, by an agent

Build steps, in order:

1. **Index from the full bodies, surface only summaries.** Tested at 80,000
   skills, indexing names and one-liners alone cost 31–44 points of Hit@1
   versus indexing full body text. Building is a batch job and can afford to
   read everything; reading must stay cheap.
2. **One global rewrite pass, with every sibling entry visible**, optimising
   for *mutual discriminability* rather than individual accuracy. A single
   pass matched hand-tuned descriptions (79.2 vs 79.4 F1) at 32× less effort;
   iterating added 0.2%. **This is the highest-leverage step in the design.**
3. **Preserve the skill→library edge.** Joint indexing beat flattening by
   ~19% Recall@5. Group by library rather than dissolving everything into one
   capability list.
4. **Record overlap explicitly** — see recovery below.
5. **Dedup and retire.** Near-duplicates inflate the retrieval pool and cut
   precision without adding function. An index that only ever grows decays.

Steps 2 and 4 need judgement, so **the plugin does not build the codex.** The
plugin resolves, fetches and unpacks — mechanical work. The librarian skill
instructs the agent to regenerate the codex when the harvested set changes.
Mechanism in the plugin, judgement in the agent.

### 4. Recovery is a first-class feature, not a fallback

Even with *oracle* retrieval — the right answer guaranteed present in the
candidate set — one enterprise-scale study still measured routing accuracy
dropping ~10 points. Selection will be wrong sometimes and that cannot be
engineered away. Given that overlap here is real rather than accidental, this
is the common case, not the tail.

Four mechanisms:

- **Overlap edges in the codex.** Where several libraries cover a capability,
  say so, name them together, and state the distinction. An agent that lands
  on the wrong one of three should find the other two named in the same entry.
- **Project preference, recorded locally.** When a project has settled on one
  of several overlapping options, the codex records it. This is the piece
  that cannot be harvested — no library knows what else is on your classpath —
  and it is the piece that actually shortcuts the churn.
- **The announce rule.** A library skill instructs the consuming agent to
  state, in its response, that it found and is using that skill. That makes a
  wrong pick *visible to the human at the moment it happens*, which is the
  only recovery path with a reliable actor in it.
- **Negative guidance.** "Do not use X for Y; use Z" is more actionable than
  any amount of positive description, and survives retrieval error — an agent
  that reaches the wrong entry still gets redirected.

### 5. Retrieval: design for recall, let the agent choose

Top-1 accuracy across methods runs 14–40%; top-5 runs 85–90%. The gap is the
design brief: **cheap lexical retrieval is bad at picking the winner and
excellent at not losing it.** Return 5–10 candidates and let the agent read
and decide. Do not build for top-1.

Known lexical failure mode: verb saturation. "create", "list" and "get" appear
in a large fraction of entries and destroy discrimination. Weight names above
descriptions and deweight common verbs.

## Consequences

- Constant context cost — one librarian description — regardless of
  dependency count. A naive agent sees one skill; a capable one reads the
  codex and loads exactly what it needs.
- The codex is a build artifact requiring an agent to regenerate. It goes
  stale, and staleness must be visible: it records when it was generated and
  against which resolved versions.
- We are specifying library-skill *content* where the spec is silent. That is
  a contribution, and also a maintenance commitment.
- Local preference lives in the codex, which means the codex is not purely
  derived and cannot simply be regenerated from scratch without losing it.
  **This needs solving** — see open questions.
- Empty state must be unambiguous. Bodies are gitignored, so between clone and
  sync the codex is absent. "Not yet synced" must never render as "no library
  skills exist" — that is a wrong answer that looks like a right one.

## Rejected

- **One skill per library in `.agents/skills/`.** The 30,000-token ceiling.
- **Summarising the catalogue in the librarian's description.** Descriptions
  are triggers, not catalogues; hundreds of libraries do not fit in 1024
  characters, and paths carry no meaning.
- **Inlining the codex.** Same benefit as linking, far more tokens.
- **A capability taxonomy that library authors populate.** Never worked
  anywhere. UDDI, semantic web service discovery, npm keywords.
- **Building the codex in the plugin.** Gradle cannot exercise judgement, and
  discriminability is a judgement call.

## Open questions

*Since the re-mint, two of these have settled: Q1 — local preference lives in a
hand-maintained `local.md` kept separate from the generated entries (RAD-0010);
Q3 — retrieval is hybrid (lexical + vector) over Lucene (RAD-0010, RAD-0013). Q2
and Q4 remain open.*

1. **Where does local preference live so regeneration does not destroy it?**
   A separate hand-maintained file the codex merges, or marked regions inside
   the codex? Leaning separate — generated files should be safe to delete.
2. **What triggers regeneration?** Dependency change is detectable; whether
   it should happen automatically or on request is not settled.
3. **Retrieval mechanism inside the codex.** A single markdown file an agent
   reads whole is simplest and works to some size. Beyond that it needs
   structure, and the format question from the earlier index discussion
   returns: per-library fragments, cheap columns separate from prose.
4. **Do we propose a content spec upstream, or just publish ours?** The
   discovery mechanism is a spec question; content may be better demonstrated
   than legislated.

## References

- ADR-0009 — how library content travels (supersedes the withdrawn ADR-0003)
- [Agent Skills specification](https://agentskills.io/specification)
- [Optimizing skill descriptions](https://agentskills.io/skill-creation/optimizing-descriptions.md)
- [Advanced tool use](https://www.anthropic.com/engineering/advanced-tool-use) — Anthropic, Nov 2025
- [MCP client best practices](https://modelcontextprotocol.io/docs/2026-07-28/develop/clients/client-best-practices)
- [Mintlify llms.txt agent benchmark](https://www.mintlify.com/blog/llms-txt-agent-benchmark) — Jul 2026
- [SkillRouter](https://arxiv.org/html/2603.22455v4) — skill routing at 80k skills
- [A Single Rewrite Suffices](https://arxiv.org/html/2606.30775) — skill collision
- [SkillOps](https://arxiv.org/html/2605.13716v1) — skill technical debt
- [Schema.org: Evolution of Structured Data on the Web](https://cacm.acm.org/practice/schema-org/)
- [skills-npm proposal](https://github.com/antfu/skills-npm/blob/main/PROPOSAL.md) · [skilld](https://github.com/skilld-dev/skilld)

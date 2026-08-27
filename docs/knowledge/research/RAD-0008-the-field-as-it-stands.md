# The Field as It Stands, and What This Research Rejects

RAD-0008 · 2026-08-16 · v3

**v3 (2026-08-22).** Adds **language and harness design for agents** as a further active area
the v1 survey missed — LBAC, SPL and the literature around them — surveyed for RAD-0023. The
pattern is the same as the injection literature: the idea exists, is published, and is further
along than this project's sketch of it.

**v2 (2026-08-22).** Adds the **academic literature on injection through agent-facing
content**, which this record's v1 survey missed entirely and which RAD-0006 v4 flagged as
outstanding. Three papers cover most of what RAD-0006 measured independently, at greater
scale and on more realistic corpora, so a third correction is filed below: several RAD-0006
findings are corroboration rather than discovery. Papers read 2026-08-22 (abstracts, plus
targeted reads of two full texts — see the caveat on that section).

**Measured against:** sources read 2026-08-16. `docs/knowledge/reference/landscape.md`
was compiled 2026-08-02 and reviewed 2026-08-13; the field moved in between and
three of the entries below are new to it. Numbers attributed to this project
come from RAD-0001 and RAD-0002, measured 2026-08-13.

## Question

`landscape.md` tracks who else is working in this space, descriptively and
deliberately without judgement. That is the right shape for a tracking file and
the wrong shape for deciding what to build.

So: **what does the field actually have working, what has it not solved, and
where does this research diverge from it — including where the field is right
and this project is wrong?**

The last question is the one that earns the record. Two claims this project has
made in writing do not survive contact with what was published while it was
measuring, and a third area — the two-layer design — turns out to be
convergence rather than either priority or error.

## Trail

The field separates cleanly into four layers, and it has made very uneven
progress across them.

### Format — settled, and this project should stop treating it as open

The `agentskills` specification defines `SKILL.md` with `name` and
`description` frontmatter, optional `scripts/`, `references/` and `assets/`,
and a 1024-character description limit. **Microsoft's Agent Framework
implements it directly** and describes itself as compatible with
agentskills.io, with the same field set and the same limits.

That is a major vendor adopting an open format rather than shipping its own,
and it settles the question. ADR-0007's "adopt the format as-is" was the right
call and is now the obviously right call.

### Distribution — fragmenting, and worse than a month ago

npm now has at least three incompatible shapes in circulation:

| Shape | Who | Discovery |
|---|---|---|
| `agentskills` field in `package.json` listing skill paths | agentskills RFC #81 | scan `node_modules` for the field |
| `skills/` directory at package root | antfu/skills-npm, vercel-labs/skills | glob `node_modules/**/skills/*/SKILL.md` |
| `.agents/skills/<name>/SKILL.md` inside the package | library-skills.io | CLI symlinks from the unpacked tree |
| git-based install | `npx skills add` | clone, no version alignment |

The manifest-field shape is new since `landscape.md` was compiled and is
significant: it is an RFC *in the specification's own repository*, and it moves
npm toward what pnpm RFC #13422 argued for — declare it in the manifest rather
than by directory convention. That is the better shape, and the fragmentation
is the cost of nobody having settled it.

**And the JVM gap has been attacked.** `SkillsJars` publishes agent skills to
Maven Central at `META-INF/skills/<org>/<repo>/<skill>/SKILL.md` **inside the
jar**, with Maven and Gradle plugins that package on build and extract from
dependencies on consume, plus a machine-readable catalogue at skillsjars.com.
Spring AI consumers read from the classpath without extracting.

This is the most consequential entry here, because **it is this project's v2
mechanism**, shipped by someone else. That is dealt with below.

### Loading — the field has converged on the same shape

Microsoft's framework ships a four-stage progressive disclosure model:
advertise every skill's name and description in the system prompt at roughly
100 tokens each; `load_skill` to fetch the body on demand; `read_skill_resource`
for supplementary files; `run_skill_script` to execute. Plus caching, a
filtering predicate, and MCP servers as a skill source.

**That is ADR-0004's two-layer design, shipped by a major vendor.** ADR-0004
reached it independently, and the proposal already credits the prior art it
knew about — the MCP specification's Catalog / Inspect / Execute, deferred tool
loading behind a search, llms.txt splitting index from full text. So this is
not a priority dispute. It is convergence, and convergence is the useful
signal: when several efforts working from different starting points land on the
same architecture, the architecture is probably right.

It also localises what is left. **Every one of those independent arrivals
leaves the same thing unsolved** — the resident cost of advertising every
skill. That is a stronger statement than any single project's measurement: the
field agrees on the shape and has collectively not addressed the layer RAD-0001
measures.

What it does *not* solve is the part RAD-0001 measured. Progressive disclosure
defers the **body**; the **description of every installed skill stays
resident**. So the resident cost remains O(number of skills), and Microsoft's
own figure — about 100 tokens per advertisement — multiplied by the 311
importable libraries of a mid-sized Android application or the 995 of a large
Next.js one is 31k and 99k tokens before any work begins.

Their answer to too many skills is a filtering predicate over an allowlist of
approved skill names. That is manual curation, which is the same answer
`skilld` gives, and it is the answer RAD-0001 measured as not surviving contact
with a real dependency graph.

### Cost — measured, at far greater scale than here, by someone else

**SkillReducer** (arXiv 2603.29919) analyses **55,315 publicly available
skills** and states the problem in the same terms this project does: every
token of skill content in the context window costs money and dilutes
attention. Its findings:

- **26.4% of skills have no routing description at all.**
- **Over 60% of body content is non-actionable** background or examples.
- Delta-debugging compression cuts descriptions 48% and bodies 39% while
  preserving an 86.0% task pass rate.
- A **"less-is-more effect"**: removing non-essential content *improved*
  performance by 2.8%, generalising across five models from four families.

Two things follow. The first is a correction, below. The second is
corroboration this project should cite rather than resist: the less-is-more
result is independent support for the claim that fewer candidates in front of
the model improves selection, which the proposal currently supports only with a
tool-selection figure from adjacent work.

### Security — measured, at greater scale, by someone else

Three papers measure injection through third-party agent skills. Between them they cover
most of what RAD-0006 arrived at independently.

**AgentTrap** ([arXiv:2605.13940](https://arxiv.org/abs/2605.13940), Zhuang et al., May 2026)
is a dynamic benchmark of **141 tasks** — 91 malicious, 50 benign — across 16
security-impact dimensions, judging complete agent trajectories in a sandbox. Its skills are
drawn from real ecosystems (most-downloaded ClawHub skills, Anthropic's own skills
repository, a curated Composio set) with payloads injected into benign seed workflows. It
evaluates Claude Haiku, Claude Sonnet 4.6, GPT-5.4-mini, GPT-5.3 Codex, Qwen3-Coder-Next and
others, run both directly and through **Claude Code**, Codex CLI and OpenClaw. Its central
finding: *models often complete the visible user task while treating unsafe side effects
introduced by the skill as part of the normal workflow.*

**SkillJect** ([arXiv:2602.14211](https://arxiv.org/abs/2602.14211), Jia et al., Feb 2026)
automates the generation of poisoned skills over **100 skills** taken from ClawHub. Its
stated premise is that explicit malicious instructions are refused or ignored when they do
not align with the host workflow, so an effective attack must be workflow-aligned. It
measures an **instruction-level prompt defence at 97.3% → 48.3%** attack success, and
benchmarks four existing skill scanners at **61.5%** average detection accuracy.

**SkillGuard-Robust** ([arXiv:2604.25109](https://arxiv.org/abs/2604.25109), Lv et al., Apr
2026) formulates pre-load auditing as a robust three-way classification over 254–404
packages, reporting 97.30% exact match and 98.33% malicious-risk recall on a held-out
aggregate, while noting that transfer to harsher external sources remains open.

### Language and harness design for agents — an active subfield (added v3)

Surveyed 2026-08-22 for [RAD-0023](RAD-0023-deterministic-harness-or-harvested-knowledge.md), which
asked whether engineering judgement should live in a deterministic harness rather than in the
model. The answer to "does this exist" is yes, in force.

**LBAC — Language-Based Agent Control** (arXiv:2605.12863, May 2026) requires agents to
*generate programs that are well typed against the surrounding scaffolding*, rejecting unsafe
programs at the type-checker before execution, so one policy covers agent-written and
developer-written code alike. It builds on **LIO**, the established labelled-IO
information-flow library, giving capability declarations, confidentiality and integrity labels,
and a quarantine construct for subagents that keeps tool access under label discipline.

**SPL — Structured Prompt Language** (arXiv:2607.07727, July 2026) is a declarative language
composing deterministic and probabilistic steps in one specification — `GENERATE`/`EVALUATE`
against `SOLVE`/`ASSERT` — with its own grammar.

Around them: *Securing Agents With Tracked Capabilities*, *A Fast, Reliable, and Secure
Programming Language for LLM Agents with Code Actions* (arXiv:2506.12202), *A Language for
Describing Agentic LLM Contexts* (arXiv:2605.01920), *SoK: Trust-Authorization Mismatch in LLM
Agent Interactions* (arXiv:2512.06914). Adjacent, at the prompt rather than the program level:
DSPy compiles declarative LM calls; LMQL constrains decoding.

**What this means for this project.** Building such a language is not work this project should
take on — five of the six primitives RAD-0023 sketched are already covered, and LBAC's
mechanism is stronger than the sketch. The useful question it leaves is compositional: a
type-checker that validates agent-generated code against surrounding scaffolding needs *facts
about the code*, which is what a codex produces.

### Query and serving — already operating, in adjacent form

`maven-tools-mcp` is a live MCP server giving agents structured Maven Central
intelligence for Maven, Gradle, SBT and Mill: version and stability lookup,
bulk dependency checks, CVE and licence data, BOM conflict detection, and
POM-aware analysis that resolves effective versions without building. It
integrates Context7 to fetch library documentation by resolved identifier.

So the shape RAD-0003 proposes — an MCP server serving dependency intelligence,
including documentation, keyed by coordinate — **exists and runs**. It serves
versions, security and upgrade advice rather than capability guidance, and it
is JVM-only. But the architecture is not hypothetical, the ingestion problem is
evidently tractable, and someone is already operating one.

## Three claims this project must correct

### "Nobody has costed this" is false

It is stated in the README, in `landscape.md` and in the proposal. SkillReducer
costed it across 55,315 skills, which is three orders of magnitude more than
the 67-skill corpus RAD-0002 measured descriptions against.

The defensible version is narrower and still true: **nobody has costed it
against a dependency graph.** SkillReducer measures the cost of a skill
*corpus* — skills a user chose to install. This project measures the cost of a
skill *per library in a resolved dependency graph*, which is a population
nobody selected and which RAD-0002 measured at 311 to 995 importable members.
That distinction is the whole contribution and the current phrasing throws it
away by overclaiming.

The 55,315-skill corpus is also simply better data than 67 skills, and the
description-length figures in RAD-0002 should be checked against it.

### "Nobody has measured injection through agent-facing content" is false

RAD-0006 was written because this objection had no answer anywhere in the repository, and
v4 of that record already suspected the claim would not survive contact with the literature.
It does not. Three of RAD-0006's findings are **corroboration rather than discovery**:

| RAD-0006 finding | already established by |
|---|---|
| A tool-enabled agent performs an unsafe side effect while completing the visible task | AgentTrap's central finding, across 141 tasks |
| Workflow-aligned payloads succeed where blatant ones are refused | SkillJect's stated premise |
| Framing content as untrusted data helps substantially but is not sufficient | SkillJect, 97.3% → 48.3%, over 100 skills |

Two of those were measured on the same model families this project tested, and AgentTrap ran
them through **Claude Code** — the same harness ADR-0010 commits this project to. On scale,
corpus realism and resourcing, this project is behind, and the record should say so plainly
rather than let a reader infer priority.

**What survives is narrower, and worth stating precisely.**

- **Channel position measured against the mitigation.** SkillJect does not vary placement
  into the system prompt; AgentTrap varies attack *surface* — which file or artifact carries
  the payload — rather than position relative to a defence. RAD-0006's arm C result, that
  data-framing holds at 0/12 while the identical text in the system channel reaches 11/12,
  is addressed by neither.
- **Locally-served open-weight models.** Both papers evaluate hosted API models. RAD-0006's
  matrix runs quantised gpt-oss, qwen3-coder, gemma-4, nemotron and devstral on a developer's
  own machine — the tier this project exists to serve, and the tier where data-framing failed
  outright.
- **The corpus, again.** All three study *authored skills*: packages written to instruct an
  agent. This project studies *library documentation harvested from a resolved dependency
  graph* — content nobody wrote for an agent, 70–90% of it transitive. It is the same
  distinction that survives the cost correction above, and it is the contribution.
- **Structure grounding.** test3's signal — checking prose against the declared surface of
  the library that shipped it — is available only in this corpus, because a library has a
  symbol graph and an authored skill does not. The scanners SkillJect benchmarks are
  semantic; this one is structural, and it is the basis of the harvest-time filter proposed
  in [RAD-0020](RAD-0020-information-flow-control.md).

**Unverified.** These four survivals rest on abstracts plus targeted reads of two full
papers. Confirm them against the full texts before any is published as a novelty claim —
this record has now had to withdraw two claims of priority, and the pattern is the risk.

### "Maven has nothing" is false

SkillsJars exists and publishes to Maven Central today.

But the correction cuts in this project's favour, because **SkillsJars ships
the mechanism this project already tried and measured as broken.** Skills go
inside the jar at a canonical path, which is v2 of the proposal —
`META-INF/agents/skills/<name>/SKILL.md` against their
`META-INF/skills/<org>/<repo>/<skill>/SKILL.md`. The measured failures apply
unchanged: the Android Gradle Plugin excludes `META-INF/**/MANIFEST.MF` from an
AAR's nested `classes.jar`, `commonMain` resources are silently dropped from
the AAR on AGP 8 (KT-46493, open since 2021), and Kotlin/Native packages
nothing into klibs at all. Their own documentation confirms the scope: Maven
Central and JVM agents, with no mention of AAR or Kotlin Multiplatform.

So the claim becomes sharper, not weaker: **the JVM approach that exists works
for jars and does not survive Android or Kotlin Multiplatform**, and this
project has the artifacts and measurements to show why. That is a more useful
thing to say than "nobody has done this", and it is checkable.

It also validates the gap. Someone else independently judged the JVM worth
solving.

### And one that is convergence, not error: the two layers

Worth separating from the two above, because it is a different kind of thing.

ADR-0004 arrived at a resident trigger plus an on-demand index independently,
and the proposal already names the prior art it was aware of. Microsoft
shipping the same four-stage model, the MCP specification documenting
Catalog / Inspect / Execute, and llms.txt splitting index from full text are
not evidence that this project borrowed the idea. They are evidence that the
idea is correct — several efforts, different starting points, same
architecture.

The framing to use is therefore convergence rather than concession. What should
be said precisely is where this project's version *differs*, which is narrower
than the whole design and more interesting than priority: **the source and the
unit.** Everyone else's skills are authored and installed; this project's are
*harvested from documentation that already ships*, and counted *per resolved
dependency* rather than per curated skill. Nobody else is doing either, and
those two choices are what generate every measurement in this research.

## Where the field corroborates this research

Worth setting out explicitly, because independent arrival at the same
conclusion is stronger evidence than either party's own reasoning — and
because several of these came from people arguing a different case.

| This project's finding | Corroborated by | Note |
|---|---|---|
| Fewer candidates in front of the model improves selection, not just cost | **SkillReducer's "less-is-more effect"** — removing non-essential content raised task performance 2.8%, across five models from four families | Better evidence than the tool-selection figure the proposal currently cites, and from a 55,315-skill corpus |
| The content layer is under-populated and needs a completeness check (RAD-0002: median 33% of declarations documented) | **SkillReducer: 26.4% of skills have no routing description at all** | Two different populations, same shape of gap. Strengthens the case for a publisher-side check |
| A skill body should carry intent and traps, not background (`spec/content.md`) | **SkillReducer: over 60% of body content is non-actionable** background or examples | The spec is trying to prevent, by convention, the bloat they measured empirically |
| A git-hosted route drifts from the released version unless it is version-addressed (RAD-0005; `<scm><tag>` usable in 2% of POMs) | **antfu/skills-npm rejects git-based install for exactly this** — version misalignment | Independent arrival, from someone who chose npm packaging instead |
| Discovery belongs in the manifest rather than in a directory convention (RAD-0005's packument shape) | **agentskills RFC #81** proposes an `agentskills` field in `package.json`; **pnpm RFC #13422** argued the same | The specification's own repository has moved this way |
| Two layers — a resident trigger and an on-demand body (ADR-0004) | **Microsoft's Agent Framework** ships advertise / `load_skill` / `read_skill_resource` / `run_skill_script`; MCP documents Catalog / Inspect / Execute | Independent arrival from different starting points. The strongest corroboration here, and it also shows every arrival leaves the resident-description budget unsolved |
| ~100 tokens per resident skill entry | **Microsoft states ~100 tokens per advertisement** | Corroborates the order of magnitude. Note the discrepancy: RAD-0002 measured a 67-skill public corpus at ~59 tokens median, so 100 is either a different corpus or includes framing overhead. Worth reconciling |
| A capability server keyed by coordinate is buildable, and ingestion is tractable (RAD-0003) | **`maven-tools-mcp`** operates one for JVM dependency intelligence, with Context7 documentation retrieval | The architecture is running, not hypothetical |
| An MCP server is a legitimate source of skills, not a separate category (RAD-0003) | **Microsoft supports MCP servers as a skill source** alongside file-based ones | The local-server variant has a shipped precedent |
| The JVM gap is real and worth solving | **SkillsJars** built a Maven Central mechanism for it | Someone else independently judged it worth the work |
| Classpath reading works at development time; §3's objection is cost, not access (RAD-0004 §5) | **SkillsJars' Spring AI path reads skills from the classpath without extracting** | The external reviewer was right, and this is a working instance |
| Manual selectivity is the field's only current answer to scale | **`skilld`'s guidance and Microsoft's allowlist filter** both land there | Corroborates the *diagnosis* even though this research rejects the answer — when everyone independently reaches for curation, the underlying problem is real and unsolved |

The last row is the one to keep in mind. Most of this table is the field
agreeing with conclusions this project reached separately; that row is the
field agreeing about the *problem* while offering an answer the measurements
say does not scale. Both are useful, and they are different kinds of evidence.

## Where each pattern stops working, and why

The corroboration above is about agreement. This is the other half: for each
mechanism in circulation, the boundary past which it fails, and the measurement
that locates it. Most of these are good designs inside their range — the
failure is that nobody states the range.

**In-jar bundling at a canonical path** — SkillsJars; this project's v2.
*Works* for plain JVM jars, and Spring AI reads them from the classpath without
extracting. *Fails* on Android and Kotlin/Native, three separate ways and all
of them silent: the Android Gradle Plugin excludes `META-INF/**/MANIFEST.MF`
from an AAR's nested `classes.jar` so there is nowhere to declare it;
`commonMain` resources are dropped from the AAR on AGP 8 (KT-46493, open since
2021) so the file is simply absent; and Kotlin/Native packages no resources
into klibs at all. *Boundary:* single-target JVM libraries. A KMP library
publishing through Maven cannot use it, and gets no error saying so.

**Directory scan of an unpacked tree** — RFC #81, antfu/skills-npm,
vercel-labs, library-skills.io. *Works* while the count stays small, and the
mechanism is sound. *Fails* on volume: a scan cannot load less than what is on
disk, and RAD-0002 measured 995 importable packages in the p90 Next.js example
and 423 in a default `create-next-app`. At Microsoft's own ~100 tokens per
advertisement that is 99k and 42k tokens resident. *Fails structurally* in any
ecosystem that does not unpack, which is the JVM. *Boundary:* roughly tens of
skills. None of these proposals publishes a number, so none of them says where
their own boundary is.

**Manifest field instead of directory convention** — agentskills RFC #81, and
pnpm RFC #13422's argument. *Works* as a declaration mechanism, and is the
better shape: it removes the guessing and the probing. *Fails* to change the
arithmetic — you still walk `node_modules` to find the fields, and you still
end up with one entry per package that has one. *Boundary:* it solves
*declaration*, not *budget*, and those have been conflated.

**Progressive disclosure of the body** — Microsoft's Agent Framework, and the
`agentskills` model generally. *Works*: it is the right architecture and it
removes the large cost, the body. *Fails* at the layer it does not defer —
every installed skill's name and description stay resident, so the cost is
O(number of skills) no matter how well the bodies are managed. *Boundary:* a
curated skill set in the tens. A dependency graph is 112 to 995, and at that
point the resident advertisements alone are 11k–99k tokens.

**Allowlist filtering and manual selectivity** — Microsoft's filter predicate,
`skilld`'s "only add skills for packages your agent struggles with". *Works*
for an organisation's own curated skills, where somebody knows the list.
*Fails* against a resolved graph: it asks a human to triage 311 candidates and
re-triage at every dependency bump, and it puts a curation step in front of the
discovery mechanism — which is precisely pnpm RFC #13422's objection to
directory conventions, and which this project's own v1 postmortem concedes
against itself. *Boundary:* it scales with human attention, and dependency
graphs do not.

**Compression** — SkillReducer. *Works* everywhere, and should be adopted: 48%
off descriptions for an 86% task pass rate is a real gain, and the less-is-more
result suggests it improves quality as well as cost. *Fails* to change the
order: halving O(n) is still O(n). Applied to the 995-package case at RAD-0002's
measured ~63 tokens, 48% compression leaves ~33k tokens resident. *Boundary:* a
constant-factor improvement, valuable at any scale and sufficient at none where
the count itself is the problem.

**Git-based install** — `npx skills add` and similar. *Works* for community
skills that are not tied to a library release. *Fails* for library-attached
guidance, because nothing ties the fetched content to the version that
resolved; antfu's proposal rejects it for exactly this, and RAD-0005 measured
why the metadata cannot rescue it — `<scm><tag>` identifies the released
version in 2% of POMs and no sampled jar manifest records a commit. *Boundary:*
version-independent content only, unless the layout is version-addressed, which
[RAD-0005](RAD-0005-a-git-hosted-codex.md) sets out and nobody in the field is
currently doing.

**A central catalogue** — skillsjars.com, Skilldex, Agentic Resource Discovery.
*Works* as a query front-end over a corpus that exists somewhere permanent, and
is the only place a corpus can be scanned, signed or revoked once for everyone.
*Fails* as the substrate: if the capability data lives only in the service, it
is as durable as the service, which is the ephemeral-mechanism failure the v1
postmortem argues against, in a new outfit. It is also a single point of
compromise for the injection surface in
[RAD-0006](RAD-0006-development-time-prompt-injection.md). *Boundary:* front-end
yes, system of record no.

**Documentation retrieval by library identifier** — Context7, as integrated by
`maven-tools-mcp`. *Works* very well for a library you can already name.
*Fails* to answer the question this project is about, which is not "tell me
about library X" but "what is in my graph that does X, and which of them should
I use". Coverage is curated per library rather than derived from a resolved
graph, so it addresses drift and not reinvention or selection. *Boundary:*
excellent lookup, not discovery.

## What this research rejects, and why

**Scan-everything discovery** — RFC #81, antfu/skills-npm, vercel-labs,
library-skills.io, and SkillsJars' extract-to-filesystem step. Rejected on
measured cost: a directory scan cannot load less than what is on disk, the
importable set is 86–99% of the resolved graph on the JVM and 100% in npm and
Python, and at Microsoft's own ~100 tokens per advertisement that is 31k–99k
tokens resident. None of these proposals publishes a number.

**Manual selectivity** — `skilld`'s "only add skills for packages your agent
struggles with", Microsoft's allowlist filter. Rejected because it puts a
curation step in front of the discovery mechanism, which is precisely pnpm RFC
#13422's objection to directory conventions and which this project's own v1
postmortem concedes against itself. Nobody hand-curates 311 candidates, or
re-curates at every dependency bump.

**In-archive bundling on the JVM** — SkillsJars. Rejected on measurements this
project has already published: three independent silent failures on Android and
native, documented in the v1 postmortem and reproducible by unzipping a
released artifact.

**Git-based install without version addressing** — `npx skills add`. Rejected
for the reason antfu's proposal gives independently: version misalignment. This
is direct corroboration of RAD-0005's drift objection, and RAD-0005 goes on to
show the fix — a version-addressed manifest — which nobody in the field is
doing.

**Compression as the answer to scale** — SkillReducer. Not rejected, but
insufficient alone: 48% off a description is still O(n), and 48% of the 995-library
case is 32k tokens. Compression and not-loading-at-all compose well and the
project should adopt the technique rather than treat it as a rival.

## Findings

**Established from sources read 2026-08-16.**

- The format has converged and a major vendor has adopted it unchanged.
- npm distribution has fragmented further, now including a manifest-field RFC
  in the specification's own repository.
- Progressive disclosure of skill *bodies* is shipped and mainstream; the
  resident *description* budget is addressed nowhere.
- The cost of skills has been measured at 55,315-skill scale, with a
  less-is-more result that supports this project's selection argument.
- A JVM packaging mechanism exists (SkillsJars) using the approach this
  project measured as broken on Android and KMP.
- An MCP server serving JVM dependency intelligence with documentation
  retrieval already operates.

**Unverified.**

- Whether SkillsJars has since added AAR or KMP handling; the documentation
  read on 2026-08-16 does not mention either, and the failure modes were not
  re-tested against their plugin.
- Whether SkillReducer's corpus contains library-attached skills or only
  user-installed ones. This decides whether its description statistics are
  directly comparable to RAD-0002's, and whether the ~100-versus-~59 token
  discrepancy with Microsoft's figure is a corpus difference or a framing one.
- Whether the agentskills RFC #81 thread has attracted objections; only the
  opening post was read.
- Nothing was found for SPM, Cargo or Go distribution beyond what
  `landscape.md` already records, but the search was not exhaustive.

## Recommendation

**Fix the two overclaims before the proposal circulates.** "Nobody has costed
this" becomes "nobody has costed this against a dependency graph". "Maven has
nothing" becomes "the JVM mechanism that exists does not survive Android or
KMP, and here is the measurement". Both make the project's position stronger,
not weaker, and both are the kind of thing a reviewer finds in five minutes.

**Present the two layers as convergence, and say what differs.** Not as this
project's invention, and equally not as something to concede — several efforts
reached the same architecture independently, which is the best evidence
available that it is right. State the difference precisely instead: the source
is harvested rather than authored, and the unit is a resolved dependency rather
than a curated skill.

**Carry the corroboration table into the proposal.** A dozen independent
arrivals at this project's conclusions — several by people arguing a different
case — is the most persuasive material the research has produced, and it is
currently sitting only in this record. It is also the honest counterweight to
the three overclaims above: the project got several things right that others
have since confirmed, and got three things wrong that others have since
disproved. Both belong in the same document.

**Cite SkillReducer as support, and check RAD-0002's corpus against it.** Its
less-is-more result is better evidence for the index than the tool-selection
figure currently used, and its 55,315-skill description statistics should
either replace or corroborate a 67-skill sample.

**Engage SkillsJars rather than route around it.** It is the closest thing to
a peer, it validates the gap, and this project holds measurements its authors
appear not to have — the AAR and klib failures. That is a contribution to make
publicly, not a competitive advantage to sit on.

**Track the RFC #81 thread.** If npm settles on a manifest field, that is the
per-ecosystem placement answer for npm, and RAD-0005's manifest design should
conform to it rather than propose a parallel one.

**Update `landscape.md`** with SkillsJars, RFC #81, Microsoft Agent Framework,
SkillReducer and maven-tools-mcp, and move the reviewed date. Keep it
descriptive; the judgement lives here.

**What would change the answer.** If SkillsJars solves AAR and KMP, the JVM
packaging half of this project is largely done and the remaining contribution
is entirely the index and the harvesting. If SkillReducer's corpus turns out to
be library-attached skills, then the cost question has been answered at scale
by someone else and this project's contribution narrows to the archive-only
ecosystems and the harvesting model.

## Connections

- `docs/knowledge/reference/landscape.md` — the descriptive tracker this record
  judges; needs updating from it.
- [RAD-0001](RAD-0001-cost-of-a-skill-per-dependency.md) — the cost measurement,
  and the overclaim to fix.
- [RAD-0002](RAD-0002-existing-documentation-systems-as-skill-transport.md) — the
  67-skill description corpus to check against SkillReducer's 55,315.
- [RAD-0003](RAD-0003-central-capability-server.md) — `maven-tools-mcp` is a
  working instance of that architecture.
- [RAD-0005](RAD-0005-a-git-hosted-codex.md) — version misalignment, corroborated
  independently by antfu's proposal.
- ADR-0003 — the
  sidecar, against SkillsJars' in-jar approach.
- [ADR-0007](../decisions/ADR-0007-conform-to-existing-conventions.md) — conformance,
  strengthened by Microsoft adopting the format unchanged.
- `docs/knowledge/research/postmortems/RAD-0046-v1-bundled-flat-files.md` — the measured
  failures that apply to SkillsJars' mechanism.

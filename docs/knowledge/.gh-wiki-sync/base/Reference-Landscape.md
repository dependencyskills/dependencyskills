# The Other Projects in This Space

Reference · 2026-08-27
Keywords: who else is doing this; prior art for agent skills; llms.txt; Context7; DeepWiki; MCP documentation servers; skill registries; is someone already solving library discovery for agents; what conventions exist for shipping docs to an LLM; which of these actually work; where to find the projects we conform to.

A source list. What each project is and where to find it — **not whether it
works**, and not what this project thinks of it.

That separation is deliberate. Judgement dates faster than facts, and a
tracking file carrying opinions has to be re-argued every time it is re-read.

**Viability is assessed in the research and decision records, not here.**

- **RADs** weigh an approach while it is still open — where a pattern stops
  working and why, where the field corroborates this research, where this
  research turned out to be wrong.
  [RAD-0008](Research-RAD-0008-The-Field-As-It-Stands) does that across the
  whole field; the others do it for a specific question.
- **ADRs** record where this project has committed — to conform to something,
  to diverge from it, or to reject it. [ADR-0007](Decisions-ADR-0007-Conform-To-Existing-Conventions)
  is the standing commitment to conform to conventions this project does not
  control; the JVM packaging position is being worked out in Research.

If you want to know what an entry below is, read it here. If you want to know
whether it works, or what this project does about it, follow those.

**Compiled 2026-08-02, reviewed and extended 2026-08-16.** Every entry is a
moving target. Check the source before relying on any characterisation here; an
out-of-date summary of someone else's design is worse than no summary, because
it invites arguing with a position they no longer hold. Entries marked *(read
2026-08-16)* were verified against the source on that date; the rest were
verified on 2026-08-02.

## The format

**[agentskills/agentskills](https://github.com/agentskills/agentskills)**
([agentskills.io](https://agentskills.io/specification)) — the format
specification. A skill is a directory containing `SKILL.md`, plus optional
`scripts/`, `references/` and `assets/`. Frontmatter requires `name` and
`description`; there is no capability, tag or category field, and `metadata` is
a string-to-string map. The reference validator caps `description` at 1024
characters. Silent on packaging for every ecosystem, and declines to prescribe
content. *(read 2026-08-16)*

**[Microsoft Agent Framework — Agent Skills](https://learn.microsoft.com/en-us/agent-framework/agents/skills)**
— a first-party implementation of the agentskills format in C# and Python,
stated as compatible with agentskills.io. Four-stage progressive disclosure:
advertise name and description in the system prompt, `load_skill` for the body,
`read_skill_resource` for supplementary files, `run_skill_script` to execute.
Supports file-based, code-defined, class-based and MCP-server skill sources,
with caching and a filtering predicate. *(read 2026-08-16)*

**[skill.md](https://www.mintlify.com/blog/skill-md)** — a converging effort
spanning a [Cloudflare RFC](https://github.com/cloudflare/skills), the
agentskills proposal and Vercel's CLI, with Mintlify implementing.

## Distribution

Everyone agrees on the shape — `<dir>/<name>/SKILL.md`. Nobody agrees on the
prefix, and archives were unaddressed until recently.

### npm and JavaScript

**[agentskills RFC #81](https://github.com/agentskills/agentskills/issues/81)**
— "Standardize npm/JavaScript Package Distribution". Proposes an optional
`agentskills` field in `package.json` carrying a `skills` array of name and
path entries; discovery scans `node_modules` for packages declaring the field.
Ships a CLI (`agentskills list`, `agentskills export`) and a programmatic API.
An RFC in the specification's own repository. *(read 2026-08-16)*

**[antfu/skills-npm](https://github.com/antfu/skills-npm)** and its
[PROPOSAL.md](https://github.com/antfu/skills-npm/blob/main/PROPOSAL.md) — a
`skills/` directory at the package root; discovery globs
`node_modules/**/skills/*/SKILL.md` across direct, nested and workspace
packages; skills are symlinked into agent directories such as `.claude/skills/`
and `.cursor/skills/`. The proposal rejects git-based installation on the
grounds of version misalignment and distribution friction, framing it as
complementary for community skills. *(read 2026-08-16)*

**[vercel-labs/skills](https://github.com/vercel-labs/skills)** — a plain
`skills/<name>/SKILL.md` layout. The `npx skills` CLI installs into a large
number of agents and backs the [skills.sh](https://skills.sh) directory; it
scans `skills/` and agent-specific directories but not `node_modules`.

**[library-skills.io](https://library-skills.io/create/)** — documents
`.agents/skills/<name>/SKILL.md` inside the installed package, with worked
examples under `node_modules/` and `site-packages/`. Explicitly declines to
prescribe what goes in the file.

**[pnpm RFC #13422](https://github.com/orgs/pnpm/discussions/13422)** — "a
standard way for packages to communicate to AI coding agents at install time."
Rejects directory conventions on the grounds that each one puts an adoption
step in front of the discovery mechanism, and proposes an `agentNotice` field
in `package.json` instead.

### JVM

**[SkillsJars](https://www.skillsjars.com/docs)** — agent skills packaged as
jars on Maven Central, at `META-INF/skills/<org>/<repo>/<skill>/SKILL.md`
inside the jar. Maven and Gradle plugins package on build and extract from
dependencies on consume; Spring AI consumers read from the classpath without
extracting. A machine-readable catalogue is published at skillsjars.com. The
documentation covers Maven Central and JVM agents; it does not mention Android
AAR or Kotlin Multiplatform. *(read 2026-08-16)*

### Tool managers

**[jdx/mise #9479](https://github.com/jdx/mise/discussions/9479)** —
"Standardize discovery of Agent Skills bundled with mise-managed tools." An
agentskills maintainer enumerated eight blockers in the thread. mise proposes
`skills/<name>/SKILL.md` in a tool's install layout, symlinked into the agent's
skills directory on install and removed on uninstall.

**[skilld](https://github.com/skilld-dev/skilld)** — distribution tooling. Its
documented guidance on scale is to be selective and only add skills for
packages the agent struggles with.

## Serving and registries

**[maven-tools-mcp](https://github.com/arvindand/maven-tools-mcp)** — an MCP
server giving agents Maven Central dependency intelligence for Maven, Gradle,
SBT and Mill: version and stability lookup, bulk checks, upgrade
classification, CVE and licence data, multi-BOM conflict detection, and
POM-aware analysis that resolves effective versions without building.
Integrates Context7 for library documentation retrieval by resolved
identifier. *(read 2026-08-16)*

**[Agentic Resource Discovery](https://agenticresourcediscovery.org/spec/)** —
`/.well-known/ai-catalog.json`, v0.9 draft dated June 2026, from a working
group including several large vendors. A registry and crawler layer operating
at the website rather than the artifact.

**SkillRepo** and **Skilldex**
([arXiv 2604.16911](https://arxiv.org/abs/2604.16911)) — registry and
distribution layers, from the research side.

## Research

**SkillReducer** ([arXiv 2603.29919](https://arxiv.org/abs/2603.29919)) —
"Optimizing LLM Agent Skills for Token Efficiency". Analyses 55,315 publicly
available skills. Reports that 26.4% lack routing descriptions and over 60% of
body content is non-actionable. A two-stage framework — delta-debugging
description compression and taxonomy-driven progressive disclosure — achieves
48% description and 39% body reduction at an 86.0% task pass rate, with a
"less-is-more effect" of +2.8% performance, across five models from four
families. *(read 2026-08-16)*

## Adjacent

Related, different problem. Grouped here because they are routinely conflated
with this work and none addresses a library shipping instructions inside its
own artifact.

- **[AGENTS.md](https://github.com/agentsmd/agents.md)** — repo-root
  instructions for the repository you are *in*, not for its dependencies.
  Widely adopted; the `.agents/` namespace is shared deliberately.
- **[llms.txt](https://llmstxt.org)** — a curated documentation
  table-of-contents at a website root. Prior art for splitting an index from
  full text.
- **Context7** — library documentation retrieval by resolved identifier, used
  by `maven-tools-mcp` among others.

## Verified 2026-09-12

**[agentplugins/agent-plugins-spec](https://github.com/agentplugins/agent-plugins-spec)**
— Agent Plugins 1.0, vendor-neutral. A plugin is a directory with `plugin.json`,
`skills/` and `mcp.json`. TSC includes Amazon, Cursor, Microsoft, OpenAI and
Vercel; Google maintains. **Explicitly declines to define distribution**,
preferring filesystem directories to archives or registry-fetched bundles;
dependency resolution is parked in `FUTURE_CONSIDERATIONS.md`. 1.1.0 is a
working draft. *(read 2026-09-12)*

**[TanStack Intent](https://github.com/TanStack/intent)** (`@tanstack/intent`)
— a CLI for library maintainers to generate, validate and ship skills beside
their npm package, stated as compatible with agentskills.io. Its keyword appears
in 337 `package.json` files. Skills reach consumers through `"files": ["dist",
"skills"]` and nothing else. Adopters include TanStack, Electric, tRPC, Redux
Toolkit, Prisma, Apollo Client, Arcjet. *(read 2026-09-12)*

**[SkillsJars](https://github.com/skillsjars)** ([skillsjars.com](https://www.skillsjars.com))
— Maven, Gradle and SBT plugins placing skills at
`META-INF/skills/<org>/<repo>/<skill>/SKILL.md` inside an ordinary jar;
`mvn skillsjars:extract` pulls them out, and a `package` goal lets a project
ship its own. 140 artifacts published on Maven Central, **all of them
re-packaged third-party skill collections rather than libraries shipping their
own**. Versions are date-plus-sha, not semver. 21 stars. Note the path collision
with this project's `META-INF/ai-skills/`. *(read 2026-09-12)*

**[MCP SEP-2640 — Skills Extension](https://github.com/modelcontextprotocol/modelcontextprotocol/pull/2640)**
— serves agentskills.io skills over MCP's existing Resources primitive:
`skills/list`, `skills/get`, URI scheme `skill://<skill-path>/<file-path>`.
Delegates the file format entirely to agentskills.io and defines only the
transport binding. A host **MUST** revoke a persisted approval when a skill's
resource digest set changes. Open, draft, updated 2026-09-11 — the
fastest-moving effort in the space. *(read 2026-09-12)*

**[microsoft/apm](https://github.com/microsoft/apm)** — "Agent Package
Manager": `apm.yml` plus `apm.lock`, transitive resolution, integrity hashes,
SBOM export, org policy enforcement. Distribution is **git-host-based** rather
than package-registry-based. *(read 2026-09-12)*

**[ClawHub](https://github.com/openclaw/clawhub)** — a public skill registry;
an April 2026 snapshot held 49,592 skills from 16,797 authors. Now has a second
implementation in JFrog's Artifactory, which makes it a de facto protocol.
JFrog registered skills as a **new Artifactory package type** rather than
layering on Maven or npm. *(read 2026-09-12)*

## Corrections

**The Cloudflare skills RFC was listed under the wrong URL.**
[cloudflare/skills](https://github.com/cloudflare/skills) is Cloudflare's own
collection of ~14 skills for building on Workers, shipped as a plugin
marketplace — not a proposal. The RFC is a separate repository,
[cloudflare/agent-skills-discovery-rfc](https://github.com/cloudflare/agent-skills-discovery-rfc):
"Agent Skills Discovery via Well-Known URIs", Draft v0.2.0, publishers serving
`/.well-known/agent-skills/index.json` with a SHA-256 digest per entry. **Cold
since 2026-03-24**; its ideas moved to `agentskills/agentskills#254`, open and
unmerged. The one deployment found in the wild serves the older v0.1.0 path and
shape. *(corrected 2026-09-12)*

**The npm `package.json` distribution proposal is dead.**
`agentskills/agentskills#81` was closed 2026-09-05. An OCI-artifacts proposal
(#290) closed 2026-04-02 with one comment. *(corrected 2026-09-12)*

**skillpm and npm-skills exist and are negligible.**
[skillpm](https://github.com/sbroenne/skillpm) wraps npm, scanning
`node_modules/` for `skills/*/SKILL.md` — 70 downloads in the month observed,
no release since 2026-03-18. [npm-skills](https://github.com/bluelibs/npm-skills)
copies them into `.agents/skills/` — last commit 2026-03-20, 169 downloads.
Both ship the skill **inside the published package**, which is the convention
this project cares about; neither has adoption. *(read 2026-09-12)*

## Seen but not yet read

Surfaced in searching on 2026-08-16 and not verified against the source. Read
before citing, and move up into the list above with a read date.

- **skillpm** — described as a package manager for agent skills built on npm.
- **npm-skills** — a separate effort to ship skills through npm.
- **Cloudflare skills RFC** — linked from the skill.md effort; the repository
  itself has not been read since 2026-08-02.

## Keeping this current

Add an entry when a project or thread enters the space; revise one when its
position moves; delete one when it is abandoned, rather than leaving a dead
link that reads as active.

Record what a project *is* and what it *does*. Verdicts, comparisons and
failure boundaries go to a RAD; a commitment to conform or diverge goes to an
ADR. If an entry here starts explaining why something is a good or bad idea,
that paragraph belongs in one of those and should be moved.

Move the reviewed date at the top when you have actually checked, and not
otherwise — a review date is a claim like any other. Mark individual entries
with a read date when they are verified out of step with the file.

**If your project is listed here and this describes it wrongly, please correct
it** — an issue or a pull request is welcome, and a correction from the people
who built the thing is worth more than a re-reading from the outside. Every
entry was written from published sources on the date shown, and the risk this
file runs is arguing with a position somebody no longer holds.

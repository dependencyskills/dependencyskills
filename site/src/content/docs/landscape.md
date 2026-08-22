---
title: The landscape
description: The other projects, specifications and threads in this space — a map, not a verdict.
---

A source list: what each project *is* and where to find it, not a verdict on
whether it works. Judgement dates faster than facts, so it lives in
[the research](/research/); this page is the map.

The maintained version, with per-entry read-dates, lives in the repository at
`docs/knowledge/reference/landscape.md`.

## The format

- **[Agent Skills](https://agentskills.io/specification)** — the format
  specification. A skill is a directory with `SKILL.md`, plus optional
  `scripts/`, `references/`, `assets/`. Requires `name` and `description`; no
  capability or tag field; silent on packaging for every ecosystem.
- **[Microsoft Agent Framework](https://learn.microsoft.com/en-us/agent-framework/agents/skills)**
  — a first-party implementation, stated compatible with agentskills.io, with
  four-stage progressive disclosure.
- **[skill.md](https://www.mintlify.com/blog/skill-md)** — a converging effort
  across Cloudflare, agentskills and Vercel, implemented by Mintlify.

## Distribution

Everyone agrees on `<dir>/<name>/SKILL.md`; nobody agrees on the prefix, and
archives went unaddressed until recently.

**npm / JavaScript** — [agentskills RFC #81](https://github.com/agentskills/agentskills/issues/81)
(a `package.json` field), [antfu/skills-npm](https://github.com/antfu/skills-npm)
(a `skills/` directory), [vercel-labs/skills](https://github.com/vercel-labs/skills)
(and [skills.sh](https://skills.sh)), [library-skills.io](https://library-skills.io/create/)
(`.agents/skills/` inside the package), and
[pnpm RFC #13422](https://github.com/orgs/pnpm/discussions/13422) (declare in
the manifest instead).

**JVM** — [SkillsJars](https://www.skillsjars.com/docs) packages skills inside
the jar at `META-INF/skills/…`; its documentation does not mention Android AAR
or Kotlin Multiplatform.

**Tool managers** — [mise #9479](https://github.com/jdx/mise/discussions/9479),
and [skilld](https://github.com/skilld-dev/skilld), whose guidance on scale is
to be selective by hand.

## Serving and registries

- **[maven-tools-mcp](https://github.com/arvindand/maven-tools-mcp)** — an MCP
  server giving agents Maven Central dependency intelligence, integrating
  Context7 for documentation retrieval.
- **[Agentic Resource Discovery](https://agenticresourcediscovery.org/spec/)**
  — a `/.well-known/ai-catalog.json` registry at the website, not the artifact.
- **SkillRepo / Skilldex** ([arXiv 2604.16911](https://arxiv.org/abs/2604.16911))
  — registry and distribution layers from the research side.

## Research

- **SkillReducer** ([arXiv 2603.29919](https://arxiv.org/abs/2603.29919)) —
  analyses 55,315 skills; finds 26.4% lack routing descriptions and over 60%
  of body content is non-actionable, and reports a "less-is-more" effect from
  compression.

## Adjacent

Routinely conflated, but a different problem:
[AGENTS.md](https://github.com/agentsmd/agents.md) (instructions for the repo
you are *in*, not its dependencies), [llms.txt](https://llmstxt.org) (a
website's documentation index), and Context7 (documentation retrieval by
resolved identifier).

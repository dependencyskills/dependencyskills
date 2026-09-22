# Skills Republished by a Third Party

RAD-0076 · 2026-09-17 · v2

Keywords: SkillsJars; skills as jars on Maven Central; who authored this skill; repackaged skill collections; prompt injection through a dependency; allowed-tools as a permission grant; should we index skill jars; com.skillsjars; version drift in a republished skill; provenance of agent instructions.

Measured against: the SkillsJars documentation and catalogue as read on 2026-08-16 and 2026-09-12 ([landscape](../reference/landscape.md)), one published coordinate inspected on 2026-09-17, and this project's placement measurements in [RAD-0075](RAD-0075-naming-the-skill-file.md).

**v2 (2026-09-22) — a republisher's artifact is indexed as itself, not excluded.** v1 recommended refusing to harvest `com.skillsjars` outright. Withdrawn: refusing to index does not remove the text from the developer's disk, and it is not this tool's call which dependencies a developer may declare. What stays refused is the *attribution* — a skill is served as a library's own only when that library shipped it. See recommendation 1.

## Question

The JVM has one working in-artifact skill scheme: **SkillsJars** packages Agent Skills as jars on Maven Central at `META-INF/skills/<org>/<repo>/<skill>/SKILL.md`, with build plugins that package and extract them. **Adopt it, build on it, or index it?**

## Trail

### What it is

- **140 artifacts**, coordinates of the form `<org>__<repo>__<skill>` under one group — for example `com.skillsjars:browser-use__browser-use__browser-use`.
- **Every one republishes somebody else's skill.** The flow its documentation describes is authors submitting skills through skillsjars.com, which deploys them to Maven Central. None is a library shipping a skill for its own API.
- **Versions are a date plus a commit hash**, not any library's version.
- **Consumption is by extraction** onto a skill path, or by a framework reading the classpath.

### Why we reject it

**The scheme takes the problem this project was started to solve and republishes it in a worse form.** The failure is an agent acting on guidance that was true once and has since moved. Putting that guidance in a jar written by someone other than the library's authors does not remove the staleness — it gives it a version number, an artifact, and the authority of a dependency, and adds a trust boundary that was not there before. Every other objection below is downstream of that.

**1. Drift: the founding failure, rebuilt deliberately.** An agent misuses a library because what it knows was true at some earlier point — the README's guiding case, measured again in [RAD-0073](RAD-0073-a-skill-written-as-source.md) at 13 of 16 unprompted runs. The fix is [ADR-0009](../decisions/ADR-0009-transport-is-sources-jar.md)'s version tie: guidance ships **inside** the artifact the build resolved, so it cannot describe a version other than the one in use. A republished skill has no such tie and cannot acquire one. It is scraped at a moment, versioned by date and commit hash, and released on the republisher's schedule while the library releases on its own. **A gap opens the day the library ships its next version and widens from there, silently** — no build error, no version conflict, nothing for a consumer to notice. That is stale training data moved out of the model's weights into a jar, and a consumer has no way to tell how old it is. **This alone disqualifies the scheme**, because removing that gap is the entire reason to ship a skill inside an artifact.

**2. Trust: the instructions do not come from the party the developer chose to trust.** A developer audits their dependencies and decides, per library, to trust its authors. This scheme inserts a second party into that relationship — not in the dependency graph, not reviewed, not chosen — supplying text an agent acts on under the first party's name. The library's maintainers are the only people who know what their API is *not* for; a republisher can only restate the documentation. The named project cannot review, correct or disown what is published about it, and nothing in the extracted file tells the agent which of the two wrote it. **Attribution without consent is not a packaging detail; it is the whole of what makes a skill worth reading.**

**3. Prompt injection: the scheme is a distribution channel for it.** A skill is not data an agent reads; it is instructions an agent follows. Naming that plainly matters more than phrasing it carefully, because the shape of the channel is what the objection rests on:

- **Reach.** One publishing account stands in front of 140 projects, so anything that gets into the catalogue — a compromise, or simply a bad merge — reaches every consumer of those entries at once.
- **Authority.** The text arrives inside a Maven dependency under a real project's name, and is extracted onto the agent's skill path, where it sits alongside skills the developer wrote with nothing to tell them apart.
- **Permissions.** `allowed-tools` in a skill's frontmatter is a permission grant — Apollo Client's real one covers `Bash(npm:*) Bash(npx:*) Bash(node:*) Read Write Edit Glob Grep` ([RAD-0072](RAD-0072-the-one-thing-we-are-not-doing.md)) — so a skill can ask for the tools it needs to act.

**No compromise is required for the same reach.** An author who wants their text in front of every consumer of a wrapped library can submit a skill through the ordinary route, and it propagates under an ordinary-looking coordinate to people who never chose to trust them. Nothing signs a skill against the project it names, so a consumer cannot tell a faithful republication from an altered one. [RAD-0006](RAD-0006-development-time-prompt-injection.md) measured what instruction text does to a tool-enabled agent; this is a channel that carries it with a dependency's standing.

**4. Two trust relationships per dependency, and only one is visible.** Dependency review, licence scanning and vulnerability tooling follow the coordinate a project declares. A republished skill arrives beside it with none of that attention.

**5. It floods the skill path.** The extraction plugins put every skill from every wrapped artifact onto the agent's skill path, and one catalogue jar can carry a whole collection. The cost scales with the catalogue pulled in, not with the work at hand: hundreds of descriptions loaded at startup, competing for attention before a line of code is written. This project's answer is the opposite — one installed pointer, and a skill fetched only when the code names its namespace ([RAD-0077](RAD-0077-the-npm-in-package-skill.md)).

**6. The storage path never reaches the copy the agent reads.** Measured in RAD-0075: `META-INF` reaches the JVM jar, an AAR's `classes.jar` and a JS klib, but **never a sources jar** and **never a Kotlin/Native klib**; a fat jar merging two fails the build. RAD-0065 found the same across 82 published sources jars. It is the placement that failed this project in v1, and it collides with v1's own `META-INF/ai-skills/`.

**7. Extraction is v1's mistake again.** A plugin that must be installed, current and enabled sits between the artifact and the agent.

**8. It does not solve the problem it exists for.** An agent confident it knows an API never looks. More skills on a skill path does not reach that moment.

**Not claimed:** that any skill in the catalogue is malicious, that any account has been compromised, or that the operators intend anything but what their documentation says. The objections are properties of the scheme's shape, and per [ADR-0011](../decisions/ADR-0011-publishing-posture-for-security-findings.md) they are published as such.

### What is worth taking

Rejecting what the scheme distributes is not a reason to ignore how it is delivered. Two pieces of the delivery are independent of everything above, and both are better than anything this project currently has.

**The setup guide is addressed to the agent, and served by content negotiation.** `curl -H "Accept: text/markdown" https://skillsjars.com/setup` returns a procedure — detect the build tool, add the plugin, choose skills, write the `AGENTS.md` entry that makes extraction happen before any work — from the same URL that serves a person the documentation page. The catalogue is served the same way, with `?q=` to search it. So nothing is copied and pasted, and nothing depends on the agent having been trained on the tool: the instructions arrive when they are wanted, in the form the reader takes. **The installer is a skill in everything but name**, and it is the one shape this project has no answer to — RAD-0073's pointer tells an agent what the resolved dependencies carry, but nothing tells an agent how to put the mechanism in place to begin with. *(read 2026-09-18)*

**And it is [RAD-0071](RAD-0071-the-package-that-did-not-exist-yet.md)'s shape, which has to be said in the same breath as the praise.** A URL an agent fetches and follows is remote instruction text that nobody reviewed, acted on with write access to build files and instruction files. The convenience and the attack are one mechanism, and the fetch is the only review step there will ever be. Two exposures, and the second is the one that hides:

- **The trusted source is itself the risk.** Whoever controls the URL controls what agents do inside other people's repositories, and can change it after adoption. Trust on first use, with no version, no pin and no signature.
- **A trusted source can serve untrusted text.** The page need not be wholly authored by its operator — a catalogue entry, a package description, a rendered README, a comment field. An instruction planted in any of that arrives with the operator's authority, and the operator may never see it. **No compromise is required, only a page assembled from something its operator does not fully control.**

This is not an argument against building one. It is the reason a copy of this pattern cannot simply be the convenient thing: what an agent acts on should be version-identified and digest-checked, bounded to declared effects, and forbidden to send the agent on to a further URL. Recorded as issue #44.

**`allowed-tools` is mirrored into POM properties** as `skillsjars.skill.<name>.allowed-tools`, and the packaging plugin fails the build when the property and the `SKILL.md` disagree. A consumer can therefore see what permissions a skill will ask for without extracting the jar, which answers part of objection 3 and should be said plainly. It does not remove the objection — the property is published by the same party that publishes the skill, so it makes the grant *legible* rather than *accountable* — but it is the only place in the field where a skill's permission request is exposed to ordinary dependency tooling, and that idea is worth having.

## Findings

**Observed, and checkable.**

- 140 artifacts; `<org>__<repo>__<skill>` coordinates under one group; a catalogue outside Maven Central's index; date-plus-hash versions.
- The skills are authored elsewhere and republished; the documentation describes that flow.
- The path is `META-INF/…`, which never reaches a sources jar or a native klib.

**Argued from those observations.**

- The scheme reproduces the staleness this project exists to remove, and adds a trust boundary to it.
- Republication severs authorship, the version tie and provenance, and leaves the named project no correction path.
- A republished skill drifts from the library by construction — scraped once, versioned by date, released on a different schedule — which is the failure this project was started to remove.
- The scheme is a prompt-injection distribution channel: one publisher reaches 140 projects, the text arrives with a dependency's authority, and `allowed-tools` lets an injected skill request its own permissions. Nothing signs a skill against the project it names.
- Extracting a catalogue's skills onto the skill path costs attention in proportion to the catalogue rather than the task.
- The scheme reproduces both failures of this project's v1: a binary-only path, and an extraction step.
- Its onboarding is separable from what it distributes and is better than anything this project has, and it is simultaneously the RAD-0071 carrier: a fetched instruction document, acted on with write access, where the fetch is the only review. A trusted operator does not remove the exposure, because a page assembled from a description, a README or a comment field can carry text its operator never saw.

## Recommendation

**Rejected — do not adopt, build on, or index it.**

1. **Index a republisher's artifact as itself, never as the library it describes, and mark it wherever it appears.** An earlier version of this record recommended excluding `com.skillsjars` from harvesting outright. That is withdrawn: refusing to index does not remove the text — the jar is on the developer's disk either way, and an agent that goes looking finds it unmarked and unlogged — and refusing to index something a developer deliberately declared is a decision that is not this tool's to take.

   What is not the developer's call is what the codex *claims*. Serving a third party's text in answer to "what is this library's skill" would be the codex asserting a provenance that is false, and a warning does not repair a false attribution; it only annotates it. So the artifact is indexed under its own coordinates, is never reachable by asking for the library it talks about, is reported as a warning when indexed the way any other problem is, and carries a marker in the pointer and a banner in the served text saying whose words these are and that they are not tied to the described library's version.

   **The banner's effect on an agent is not measured**, and should not be assumed: [RAD-0006](RAD-0006-development-time-prompt-injection.md) found data-framing necessary but not sufficient, beaten by placement and by meta-arguments. It is a marker for the developer that the agent also sees, not a control. An uptake arm carrying a third-party banner would settle what it is worth.
2. **Treat authorship as a requirement.** A skill this project serves comes out of the artifact whose API it describes. That is the line between this scheme and [RAD-0073](RAD-0073-a-skill-written-as-source.md)'s.

   **How the lightweight codex enforces it: a jar can only ship a skill for itself.** A skill is filed under the coordinates of the library it describes, `skills/<group>/<artifact>/SKILL.md`, and the indexer accepts it only when those coordinates are the coordinates of the jar it came out of — allowing for a multiplatform library's platform variants, so `acme-text-jvm` may carry `acme-text`'s skill. A jar that files a skill under any other library's name is republishing, and its skill is refused and logged.

   The reason is the whole of this record in one check. The coordinate a developer declares is the only trust decision they actually made; they chose that library, its authors, its releases. A skill arriving under a library's name from some other artifact carries that library's authority without its authors' consent, and has none of its version tie (objections 1 and 2 above). Requiring the path to match the carrier makes the question "who wrote this?" answerable from the jar alone, with no registry, signature or catalogue behind it.

   **What it excludes, and what it does not.** It refuses anything filed under a real library's coordinates by someone else, which is the republishing pattern this record rejects, and that refusal is the load-bearing one: it is what stops an agent asking for a library's skill and being handed somebody else's words. SkillsJars as published today is not read at all, because its `META-INF/skills/` path is not the path the indexer looks for. A republisher that filed a skill under **its own** coordinates passes the check — correctly, since the path then matches the jar and no attribution is being borrowed — and is indexed as itself under recommendation 1, marked, and reachable only by asking for that artifact by name.
3. **Keep the need in view.** The scheme exists because JVM libraries do not ship skills. That gap is real; who writes the file and where it lives is the disagreement.
4. **Take the onboarding shape, and give this project's own components one — but not as a live URL.** An agent-addressed setup procedure is independent of who wrote the skills and where they sit, and it is the step this project currently leaves to a human reading a README. What it must not inherit is the delivery: a fetched document is the RAD-0071 carrier, so whatever an agent acts on should be pinned, digest-checked and bounded to declared effects. Issue #44 holds the constraints.
5. **Expose the permission request to ordinary tooling.** Mirroring `allowed-tools` where a consumer can read it without opening the artifact is the right instinct, and it is worth doing for skills this project serves — with the provenance problem named rather than left implicit, since a grant published by the skill's own author is legible but not independently checked.

**What would change the answer.** Libraries publishing skills under **their own** coordinates, versions and releases through this tooling. Only the `META-INF` placement would then remain against it, and that is a placement question rather than a trust one.

## Connections

- [RAD-0073](RAD-0073-a-skill-written-as-source.md) — the skill inside the library's own source.
- [RAD-0075](RAD-0075-naming-the-skill-file.md) — where each placement lands, and the collision behaviour.
- [RAD-0077](RAD-0077-the-npm-in-package-skill.md) — the same gap solved by the library's own authors.
- [RAD-0072](RAD-0072-the-one-thing-we-are-not-doing.md) — the distribution field, and `allowed-tools` as a supply-chain surface.
- [RAD-0065](RAD-0065-what-v1-skill-authors-wrote-unprompted.md), RAD-0046 — why this project's own `META-INF` route failed.
- [RAD-0071](RAD-0071-the-package-that-did-not-exist-yet.md) — a fetched guidance file acted on without review, and why the controls built against injection do not cover it.
- [ADR-0011](../decisions/ADR-0011-publishing-posture-for-security-findings.md) — observations rather than verdicts.

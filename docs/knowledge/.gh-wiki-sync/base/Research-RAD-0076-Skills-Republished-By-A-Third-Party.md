# Skills Republished by a Third Party

RAD-0076 · 2026-09-17

Keywords: SkillsJars; skills as jars on Maven Central; who authored this skill; repackaged skill collections; prompt injection through a dependency; allowed-tools as a permission grant; should we index skill jars; com.skillsjars; version drift in a republished skill; provenance of agent instructions.

Measured against: the SkillsJars documentation and catalogue as read on 2026-08-16 and 2026-09-12 ([landscape](Reference-Landscape)), one published coordinate inspected on 2026-09-17, and this project's placement measurements in [RAD-0075](Research-RAD-0075-Naming-The-Skill-File).

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

**1. Drift: the founding failure, rebuilt deliberately.** An agent misuses a library because what it knows was true at some earlier point — the README's guiding case, measured again in [RAD-0073](Research-RAD-0073-A-Skill-Written-As-Source) at 13 of 16 unprompted runs. The fix is [ADR-0009](Decisions-ADR-0009-Transport-Is-Sources-Jar)'s version tie: guidance ships **inside** the artifact the build resolved, so it cannot describe a version other than the one in use. A republished skill has no such tie and cannot acquire one. It is scraped at a moment, versioned by date and commit hash, and released on the republisher's schedule while the library releases on its own. **A gap opens the day the library ships its next version and widens from there, silently** — no build error, no version conflict, nothing for a consumer to notice. That is stale training data moved out of the model's weights into a jar, and a consumer has no way to tell how old it is. **This alone disqualifies the scheme**, because removing that gap is the entire reason to ship a skill inside an artifact.

**2. Trust: the instructions do not come from the party the developer chose to trust.** A developer audits their dependencies and decides, per library, to trust its authors. This scheme inserts a second party into that relationship — not in the dependency graph, not reviewed, not chosen — supplying text an agent acts on under the first party's name. The library's maintainers are the only people who know what their API is *not* for; a republisher can only restate the documentation. The named project cannot review, correct or disown what is published about it, and nothing in the extracted file tells the agent which of the two wrote it. **Attribution without consent is not a packaging detail; it is the whole of what makes a skill worth reading.**

**3. Prompt injection: the scheme is a distribution channel for it.** A skill is not data an agent reads; it is instructions an agent follows. Naming that plainly matters more than phrasing it carefully, because the shape of the channel is what the objection rests on:

- **Reach.** One publishing account stands in front of 140 projects, so anything that gets into the catalogue — a compromise, or simply a bad merge — reaches every consumer of those entries at once.
- **Authority.** The text arrives inside a Maven dependency under a real project's name, and is extracted onto the agent's skill path, where it sits alongside skills the developer wrote with nothing to tell them apart.
- **Permissions.** `allowed-tools` in a skill's frontmatter is a permission grant — Apollo Client's real one covers `Bash(npm:*) Bash(npx:*) Bash(node:*) Read Write Edit Glob Grep` ([RAD-0072](Research-RAD-0072-The-One-Thing-We-Are-Not-Doing)) — so a skill can ask for the tools it needs to act.

**No compromise is required for the same reach.** An author who wants their text in front of every consumer of a wrapped library can submit a skill through the ordinary route, and it propagates under an ordinary-looking coordinate to people who never chose to trust them. Nothing signs a skill against the project it names, so a consumer cannot tell a faithful republication from an altered one. [RAD-0006](Research-RAD-0006-Development-Time-Prompt-Injection) measured what instruction text does to a tool-enabled agent; this is a channel that carries it with a dependency's standing.

**4. Two trust relationships per dependency, and only one is visible.** Dependency review, licence scanning and vulnerability tooling follow the coordinate a project declares. A republished skill arrives beside it with none of that attention.

**5. It floods the skill path.** The extraction plugins put every skill from every wrapped artifact onto the agent's skill path, and one catalogue jar can carry a whole collection. The cost scales with the catalogue pulled in, not with the work at hand: hundreds of descriptions loaded at startup, competing for attention before a line of code is written. This project's answer is the opposite — one installed pointer, and a skill fetched only when the code names its namespace ([RAD-0077](Research-RAD-0077-The-Npm-In-Package-Skill)).

**6. The storage path never reaches the copy the agent reads.** Measured in RAD-0075: `META-INF` reaches the JVM jar, an AAR's `classes.jar` and a JS klib, but **never a sources jar** and **never a Kotlin/Native klib**; a fat jar merging two fails the build. RAD-0065 found the same across 82 published sources jars. It is the placement that failed this project in v1, and it collides with v1's own `META-INF/ai-skills/`.

**7. Extraction is v1's mistake again.** A plugin that must be installed, current and enabled sits between the artifact and the agent.

**8. It does not solve the problem it exists for.** An agent confident it knows an API never looks. More skills on a skill path does not reach that moment.

**Not claimed:** that any skill in the catalogue is malicious, that any account has been compromised, or that the operators intend anything but what their documentation says. The objections are properties of the scheme's shape, and per [ADR-0011](Decisions-ADR-0011-Publishing-Posture-For-Security-Findings) they are published as such.

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

## Recommendation

**Rejected — do not adopt, build on, or index it.**

1. **Exclude `com.skillsjars` from harvesting**, as an explicit rule with the reason in the code. Such a coordinate carries no API of its own, and its content is third-party instruction text.
2. **Treat authorship as a requirement.** A skill this project serves comes out of the artifact whose API it describes. That is the line between this scheme and [RAD-0073](Research-RAD-0073-A-Skill-Written-As-Source)'s.
3. **Keep the need in view.** The scheme exists because JVM libraries do not ship skills. That gap is real; who writes the file and where it lives is the disagreement.

**What would change the answer.** Libraries publishing skills under **their own** coordinates, versions and releases through this tooling. Only the `META-INF` placement would then remain against it, and that is a placement question rather than a trust one.

## Connections

- [RAD-0073](Research-RAD-0073-A-Skill-Written-As-Source) — the skill inside the library's own source.
- [RAD-0075](Research-RAD-0075-Naming-The-Skill-File) — where each placement lands, and the collision behaviour.
- [RAD-0077](Research-RAD-0077-The-Npm-In-Package-Skill) — the same gap solved by the library's own authors.
- [RAD-0072](Research-RAD-0072-The-One-Thing-We-Are-Not-Doing) — the distribution field, and `allowed-tools` as a supply-chain surface.
- [RAD-0065](Research-RAD-0065-What-V1-Skill-Authors-Wrote-Unprompted), RAD-0046 — why this project's own `META-INF` route failed.
- [ADR-0011](Decisions-ADR-0011-Publishing-Posture-For-Security-Findings) — observations rather than verdicts.

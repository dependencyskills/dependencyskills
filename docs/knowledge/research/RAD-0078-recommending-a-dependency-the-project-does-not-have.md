# Recommending a Dependency the Project Does Not Have

RAD-0078 · 2026-09-22 · v2

Keywords: should the agent suggest a new library; recommend a dependency to reach a goal; the codex only answers for declared dependencies; out-of-scope queries as a demand signal; hallucinated package names; slopsquatting; adding a dependency is the developer's decision; names without prose; the machine store as a catalogue; laundering across projects; reimplementing a capability the organisation already publishes; duplicate utility functions; undeclared capabilities hidden by the scope filter; a version catalog entry as the developer's choice; find_library; searching the local cache by what a library says it is for; frontmatter as the only prose crossing the boundary; the agent that never asks for a library it does not know exists.

## Question

The lightweight codex answers only for libraries the asking project has resolved — the one filter it keeps from the full system. But some goals are best met by a library the project does not have yet, and an agent working toward one will reach for it. **Can the codex help an agent recommend a dependency it does not have, safely, while leaving the decision to add it with the developer?**

Opened to track viability. Nothing here is built, and the lightweight codex deliberately leaves it out for now.

## Update — v2 (2026-09-24), the case appeared, and the prototype

**Observed once.** In a trial consumer project, an agent asked to show times in a web UI wrote its own JavaScript date formatter. A date-formatting library that ships a skill was declared in the project's version catalog and already sat in the local Maven repository, but no module used it, so the codex — scoped to resolved dependencies — listed only other libraries. Told by the developer to use the library, the agent read "not on the list" as "no skill" and went to the library's sources; told that a build would bring it into scope, it added the dependency, built, listed again and read the skill before writing the code. One session, and not a measurement of demand.

**The demand signal in step 1 would not have recorded it.** The agent never asked the codex for the library by name — it did not know the library existed — so nothing reached the `out_of_scope` log. The case this record is about is exactly the one where the agent does not know what to ask for, and a signal made of refused requests is blind to it. The need showed up only as the developer's correction.

**What was built.**

1. **A declared library counts as chosen.** The build reports the libraries its version catalogs declare, marked as declared when no module uses them, and the codex serves their skills in full like any dependency's. Adding a catalog entry is the developer's choice of library — the same trust decision as adding the dependency — so this stays inside ADR-0012's boundary. On its own it would have prevented the observed case.
2. **`find_library`, step 2 of the recommendation, with one departure.** It searches every sources jar in the local Gradle and Maven caches and answers with each library's coordinate and the versions on the machine, marked as a dependency of the project or not, and tells the agent that adding one is the developer's decision to propose, not its own to take. A library's full skill is served only once the developer has added it and the build has resolved it.
3. **Ranked with libraries that ship a skill first**, as a bonus rather than an absolute order: strict priority let a skill that merely shared a word outrank the library that did the job.

**The departure: frontmatter, not names only.** The recommendation said names and a recorded description, never prose. The prototype shows each skill's frontmatter — `name`, `description`, `license`, `metadata` — which is up to 1,024 characters the library's author wrote; a library without a skill is described by its POM. The reason is that a coordinate does not say what a library is for, and the skill's `description` is written for exactly the decision the agent is making. The cost is that author prose from a library the project did not choose now crosses the boundary. It is bounded: the field is short, the skill must pass the specification's validation and be filed under its own jar's coordinate (RAD-0076), it is framed to the agent as the library's words about itself, and the body — where instructions would go — is never served.

**Measured on one machine.** 4,952 cached sources jars, 49 of them carrying a skill. The first search reads them all in about 3 s; later ones re-read only changed jars and take about 0.05 s. Searches are logged (`command: find`), which is the demand signal that can see an unknown need: what the agent searched for, and what it was offered.

**What would change it now.** A description that misleads or instructs reaching an agent through `find_library` argues for cutting back to names and POM descriptions, as v1 recommended. Searches that return nothing useful while a suitable library sits in the cache argue for better matching before anything wider.

## Trail

### The need is real, and it is the developer's call

An agent asked to parse a date format, geohash a coordinate or retry a flaky call will either write the code itself or reach for a library. When the project already has one, the codex serves its skill. When it does not, the agent falls back on what it remembers, and what it remembers is the problem this project exists to fix: stale, possibly wrong, and with no way to tell.

Recommending is not adding. **Adding a dependency is a trust decision** — the developer chooses a library, its authors and its release process — and it is exactly the act this repository's own `AGENTS.md` forbids an agent to take on the strength of anything it read. So the shape is fixed from the start: the agent may *propose* a library, with its reasons; only the developer adds it.

### The obvious source is the one the scope filter exists to block

The codex's store is machine-wide: it holds every library any project on the machine has resolved. That makes it a ready-made catalogue of real, resolvable artifacts — and [ADR-0012](../decisions/ADR-0012-a-shared-machine-level-index-store.md) names reaching across it as a **laundering route**: a poisoned entry pulled in by one project becoming reachable from another that never chose it ([RAD-0029](RAD-0029-the-agent-as-a-trust-launderer.md)). The scope filter is the containment boundary against precisely that.

So recommending from the store cannot simply relax the filter. It would have to be a separate, explicitly labelled channel, and what crosses it matters:

- **Names only, never prose.** A coordinate and a one-line description the build tool already recorded, not the library's skill or documentation. Prose is the injection carrier; a name is not, though it can still be the wrong name.
- **Marked as not a dependency**, so neither the agent nor the developer mistakes a suggestion for something already trusted.
- **Proposed, not acted on.** The skill for a suggested library becomes available the moment the developer adds it and the build resolves it — which is the ordinary path, and needs nothing new.

### Four places a recommendation could come from

| source | for | against |
|---|---|---|
| **The agent's memory** — what happens today | no work | the [RAD-0071](RAD-0071-the-package-that-did-not-exist-yet.md) failure: 237+ package names in real guidance files did not exist, and one registered by researchers was downloaded and executed about four minutes later. A name from memory may be stale, wrong, or not exist at all |
| **The machine store, names only** | every name it returns is an artifact some build actually resolved, so it **exists** — the one property memory cannot guarantee | only knows what this machine has used before; crosses the containment boundary, so it needs the separate channel above |
| **A public registry search** | the whole ecosystem | a network fetch of third-party text into an agent — the fetched-content rule applies in full; out of place in the lightweight codex |
| **Nothing** — the agent writes the code itself | no new dependency, no new trust | reinvents what a library does, often worse |

The machine store's advantage over memory is narrow but real: it cannot return a package that does not exist, which is the whole of RAD-0071's attack. It does not say the library is *good* — that is [RAD-0007](RAD-0007-choosing-between-overlapping-libraries.md)'s question, choosing between overlapping libraries, and a recommendation feature would inherit it.

### The case this record inherits, and what it adds to it

[RAD-0042](studies/RAD-0042-thirteen-slug-functions.md) measured this failure already: a capability module written in week 0, declared in the build, never referenced by anything outside itself in eleven weeks, while the same regex was hand-written 13 times across 6 files. It is one of the three case studies [PRD-0001](../requirements/PRD-0001-the-dependency-codex.md) rests on, under *reinvention*. That record is not restated here.

What it contributes to **this** question is one detail. The module was reachable — it was in the same build and its tests ran in CI — and it was a dependency of nothing. So no scope filter keyed on resolved dependencies would have surfaced it to a developer or an agent working in the mobile client, because from that module's point of view the capability was not there. **The containment boundary that makes the codex safe is the same boundary that hides a capability nobody has declared yet**, and that is exactly the tension this record exists to weigh. RAD-0042's failure happened without an agent involved; an agent confident it knows how to slug a string reaches the same result by a shorter route.

It also sets the standard for what this record would need. RAD-0042 is a grep and a `git log` pickaxe over one repository, anonymised, with the numbers as measured — not an anecdote. The demand signal below should be held to the same bar before anything is built on it.

### The counterweight, also measured: too much is already reachable

[RAD-0045](studies/RAD-0045-the-dependency-nobody-declared.md) is this record's opposite, and it should be read before anything here is built. Its finding is that **the importable set, not the declared set, is what an agent is really working against**: nearly everything an agent can import compiles and works, including a large surface the project never asked for and does not control, and the agent cannot tell a declared dependency from a transitive one. The compiler accepting it is not the same as it being safe to build on.

That is a direct argument against this record's feature. A recommendation channel deliberately widens what an agent will reach for, in a situation already measured as *too* wide. It does not settle the question — a proposal a developer must accept is a different act from silently importing something already on the classpath, and the proposal is arguably the honest version of what the agent does anyway — but it sets the burden. **Any recommendation must make the artifact's standing unmistakable**, because RAD-0045's failure is precisely an agent unable to tell what is legitimately its to use. The "marked as not a dependency" rule above is that burden, and it is the part most likely to be quietly dropped for convenience.

### The demand can be measured before anything is built

The codex already logs a query for a library the project has not registered as `out_of_scope`, and `stats` lists them under *Refused — not a dependency of the project that asked*. **That list is this RAD's demand signal**: each entry is an agent that went looking for a library the project does not have. Its size, across real sessions, says whether the feature is worth building; its contents say whether agents are reaching for sensible libraries or inventing them.

## Findings

**Measured elsewhere, and inherited ([RAD-0042](studies/RAD-0042-thirteen-slug-functions.md)).**

- A capability module with no consumers for eleven weeks, against 13 hand-written copies of the same expression across 6 files. The module was in the build but was a dependency of nothing, so a codex scoped to resolved dependencies would not have surfaced it either — the boundary that contains a poisoned entry also hides an undeclared capability.
- Against that, [RAD-0045](studies/RAD-0045-the-dependency-nobody-declared.md): the importable set already exceeds the declared one and an agent cannot distinguish them, so the problem this record would widen is one already measured as too wide. The two together say the answer is not *more reach* but *clearer standing*.

**Argued, not measured.**

- Recommending must stop at a proposal; adding a dependency is a trust decision that belongs to the developer, and is the case this repository's fetched-content rule was written for.
- The machine store is the only source that can guarantee a recommended artifact exists, which is the property RAD-0071's attack depends on being absent.
- Using it crosses ADR-0012's containment boundary, so it cannot be done by relaxing the scope filter — only through a separate channel carrying names, not prose, and marked as not a dependency.

**Available to measure now, not yet measured.**

- How often agents in real sessions ask the codex for a library the project does not have, from the `out_of_scope` entries in the analytics log.

## Recommendation

**Not a commitment, and not built now.**

1. **Collect the demand signal first.** Run the lightweight codex in real projects with logging on, and read the `out_of_scope` list. If agents rarely reach outside the declared set, stop here.
2. **If the demand is there, prototype the names-only channel** over the machine store: coordinates and recorded descriptions, marked as not a dependency, no skill text, and never an install.
3. **Do not use a public registry from the lightweight codex.** It is a fetch of third-party text, and belongs, if anywhere, with the full system's protections.

**What would change the answer.** A low demand signal ends it. A demand signal full of libraries that do not exist would argue for the feature more strongly, because the machine store is the one source that could have caught them. A poisoned or misleading entry reaching another project through the names-only channel would argue against it.

## Connections

- [ADR-0012](../decisions/ADR-0012-a-shared-machine-level-index-store.md) — the scope filter as a containment boundary, and why the store must not be reachable across projects.
- [RAD-0071](RAD-0071-the-package-that-did-not-exist-yet.md) — hallucinated package names, registered by attackers and executed.
- [RAD-0029](RAD-0029-the-agent-as-a-trust-launderer.md) — the laundering route a cross-project channel would open.
- [RAD-0036](RAD-0036-can-the-corpus-be-poisoned.md) — poisoning the store a recommendation would read from.
- [RAD-0007](RAD-0007-choosing-between-overlapping-libraries.md) — choosing between libraries, which a recommendation inherits.
- [RAD-0022](RAD-0022-the-value-of-transitive-capabilities.md) — capabilities that live only in the transitive tail, which a project already has but has not declared.
- [RAD-0042](studies/RAD-0042-thirteen-slug-functions.md) — the measured case: a capability nobody depended on, re-solved 13 times.
- [RAD-0045](studies/RAD-0045-the-dependency-nobody-declared.md) — the counterweight: the importable set is already wider than the declared one, and an agent cannot tell them apart.
- [RAD-0076](RAD-0076-skills-republished-by-a-third-party.md) — why a skill is taken only from the library it describes.

# A Skill Built From the Documentation

RAD-0074 · 2026-09-13

Keywords: the library ships no skill; generate a skill from doc comments; synthesise a package skill; fallback when skill-info is missing; can a model write the traps section; what doc comments never say; Deprecated ReplaceWith as a source of what moved; hallucinated guidance; is a generated skill worse than none; per-package summary instead of per-member; the middle rung of the ladder; lightweight codex without the quarantine.

Measured against: nothing new. Every figure below is cited from the record that measured it; this record frames an experiment.

## Question

[RAD-0073](Research-RAD-0073-A-Skill-Written-As-Source) established that a library can ship a real skill as a `skill-info` source file in each package, and that it survives into what every ecosystem publishes. Almost no library does that today, and most never will.

**When a dependency's package has no `skill-info`, can a usable skill be built from the source documentation the codex already harvests — and is it better than serving that documentation as it is?**

This is scoped to the **lightweight codex** ([RAD-0070](Research-RAD-0070-The-Smallest-Thing-That-Works)), deliberately without injection protection, so that how the thing should work can be settled before the product's trust model is brought to bear on it.

## Trail

### Where this sits: the middle rung

[RAD-0072](Research-RAD-0072-The-One-Thing-We-Are-Not-Doing) described the field as a ladder. A library that ships a skill has it served; one that does not falls back to harvested documentation; one with no documentation falls back to signatures. RAD-0073 made the top rung reachable from source. This record asks whether the middle rung can be raised — whether documentation can be turned into something skill-shaped rather than served as a pile of member comments.

### The unit is the package, which makes this affordable

RAD-0070 found, after measurement, that the package — not the member and not the library — is the unit worth indexing: members cannot be discriminated, libraries are vendors rather than topics, and a package is a subject. RAD-0073 puts an authored skill in exactly that unit. A synthesised skill should match it, so a consumer sees one skill per package whether it was written or built.

That changes the cost argument that sank per-member summarising. [RAD-0064](Research-RAD-0064-The-Skill-Is-The-Overview) measured single libraries at 1,447 and 6,414 entries with summarisation the dominant cost. The same libraries hold tens of packages, not thousands. One generation per package is two orders of magnitude fewer calls, and each one can afford a larger model and a longer prompt.

### What a skill has to say, against what documentation says

`spec/content.md` asks a skill body for five things. Documentation supplies them unevenly, and the unevenness is the whole question:

| the spec asks for | what harvested documentation can supply |
|---|---|
| **What it solves, in the caller's words** | Partly. Package and type docs describe purpose, in the library's vocabulary rather than the caller's. A model can translate; this is what the summariser was already doing. |
| **How it is meant to be used** | Partly. Usage examples in doc comments, `@sample` references, and the declarations a package's docs lean on. Often absent. |
| **Invariants and traps** | Rarely. Thread-safety notes, `@throws`, lifecycle warnings where an author wrote them. The spec calls this the most valuable content and the most underweighted — by authors, which means by their doc comments too. |
| **What moved, and what it used to be called** | Almost never in prose — but partly in **structure**: `@Deprecated` with `ReplaceWith` in Kotlin, `@deprecated` with `{@link}` in Java, and the difference between two versions' symbol sets. None of these need a model. |
| **What it is NOT for** | Essentially never. A doc comment describes its own declaration, not the neighbouring library that should have been used instead. |

So a skill built from documentation can plausibly cover the first two fields, partly the third, and the fourth only from structure. **The fields the guiding misuse case needed — the trap and the boundary — are the ones documentation is least likely to contain.** In that case the agent was confident about a type it already knew; what would have redirected it is "this is not how this type is meant to be used", which is a trap, not a description.

### The danger is invention, not omission

A synthesised skill that leaves out a trap is no worse than the documentation it came from. A synthesised skill that **invents** one is worse than nothing, because it arrives with the authority of a skill: imperative, confident, and addressed to the agent. A model asked to fill a "traps" section from documentation that contains none will tend to fill it anyway.

Two mitigations follow from that, and both cost little:

- **Every claim cites a declaration.** A trap or a usage pattern names the symbol whose documentation supports it, so a claim with no source can be dropped mechanically.
- **Provenance is part of the skill.** A built skill says it was built, from which version's documentation, by which model. An agent, and a person, can then weigh it differently from an authored one.

### Why the lightweight codex first

In the product, a synthesised skill runs straight into the conflict #43's triage recorded: [ADR-0012](Decisions-ADR-0012-A-Shared-Machine-Level-Index-Store) lets raw documentation be searched but shows only a rewrite, and #39 shows the rewrite prompt forbids *must*, *never*, addressing the reader and comparisons — which is to say, forbids a skill. The lightweight codex has no quarantine, so the prompt can ask for exactly what the spec wants and the question of what a good built skill looks like can be answered on its own terms. What the product does about trust is a later decision, informed by what this finds.

### Alternatives weighed

- **Serve the package's documentation concatenated, with no model.** The honest baseline, and roughly what the lightweight codex's package index already has. If a synthesised skill does not beat it, the model is not earning its place.
- **Synthesise per member, as the summariser does now.** Rejected by RAD-0064 and RAD-0070 on cost and on unit.
- **Synthesise per library.** A library is a vendor, not a subject (RAD-0070); a library-level overview is closer to #38's answer shape, and can be assembled from package skills rather than generated separately.
- **Pull the project README.** [RAD-0067](Research-RAD-0067-The-Pom-Points-At-Documentation) found READMEs are project-level, shared across many artifacts, and dangerous when a branch README describes a different version than the one resolved. Usable as orientation at most, and not as a source of version-specific claims.
- **Derive "what moved" deterministically.** Deprecation annotations and a symbol diff between the resolved version and an earlier one need no model, cannot be invented, and target the field the spec calls self-reinforcing. Worth doing whether or not synthesis is.

## Findings

**Established by earlier measurement.**

- A library runs to thousands of member entries but tens of packages (RAD-0064, RAD-0070).
- The package is the unit that retrieves as a subject (RAD-0070).
- The eleven authored v1 skills examined are all overviews, median about 52 lines, with preference, contract and rename notes emerging unprompted ([RAD-0065](Research-RAD-0065-What-V1-Skill-Authors-Wrote-Unprompted)) — a reference for what a built skill should look like.
- The existing rewrite prompt forbids the content a skill exists to carry (#39).

**Argued, not measured.**

- Documentation supplies the purpose and usage fields plausibly, traps rarely, and the negative boundary essentially never.
- "What moved" is recoverable from structure without a model.
- An invented trap is worse than a missing one.

**Unknown.**

- Whether a synthesised package skill improves an agent's use of the library over the concatenated documentation, or over nothing.
- How often generated traps and boundaries are invented rather than grounded.

## Recommendation

**Not a commitment.** Build it as an experiment in the lightweight codex, and measure it against the baseline before anything else.

1. **Generate one skill per package** from its harvested doc comments, package documentation and deprecation structure, in the `spec/content.md` body shape, every claim citing the declaration it rests on, and provenance stated.
2. **Score grounding first.** For each generated skill, count claims whose cited declaration does not support them. A high invention rate ends the approach, however good the skills read.
3. **Compare against authored skills.** Where one of RAD-0065's libraries ships an authored skill, generate one for the same version and compare field coverage and disagreements. The authored skill is not ground truth, but a disagreement is worth reading.
4. **Then test uptake.** Three arms on a misuse task shaped like the guiding case: concatenated documentation, the synthesised skill, and nothing. This inherits RAD-0073's caveat — it means little without a trigger that puts the skill in front of the agent.

**What would change the answer.** If generated skills are mostly grounded but add nothing the concatenated documentation did not already say, the middle rung is the documentation and no model is needed. If the invention rate is high, the rung stays raw, and only authored `skill-info` files — and deterministic "what moved" notes — carry imperative guidance.

## Connections

- [RAD-0073](Research-RAD-0073-A-Skill-Written-As-Source) — the authored `skill-info` file this falls back from.
- [RAD-0072](Research-RAD-0072-The-One-Thing-We-Are-Not-Doing) — the ladder: shipped skill, harvested documentation, signatures.
- [RAD-0070](Research-RAD-0070-The-Smallest-Thing-That-Works) — the lightweight codex, and the package as the unit.
- [RAD-0064](Research-RAD-0064-The-Skill-Is-The-Overview) — the skill is the overview; the per-entry cost.
- [RAD-0065](Research-RAD-0065-What-V1-Skill-Authors-Wrote-Unprompted) — what authored skills actually contain.
- [RAD-0067](Research-RAD-0067-The-Pom-Points-At-Documentation) — why a README is not a version-specific source.
- [ADR-0012](Decisions-ADR-0012-A-Shared-Machine-Level-Index-Store) — the searched-but-not-shown rule the product would have to revisit.
- #38, #39, #43 — the library-level answer, the prompt conflict, and keeping the authored file.
- `spec/content.md` — the five body fields.

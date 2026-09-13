# What v1 Skill Authors Wrote Unprompted

RAD-0065 · 2026-08-31
Keywords: what does a hand-written dependency skill look like; what did agents choose to tell other agents; how are library skills organized in practice; skills shipped inside a jar; META-INF ai-skills; how big is a real skill; preference and contract sections; why do skills drift without a schema; what did the v1 standard mandate; do published artifacts still carry skills; does a commonMain resource reach every target; conformance and standards in a skill; deprecated names section.
Measured against: eleven distinct v1 skill files shipped by nine sibling Kotlin Multiplatform libraries published from one organization, the v1 publishing standard that governed them, 236 of that organization's artifacts present in a local Gradle module cache, and one module's binary and sources jars fetched directly from the central repository to confirm the cache; read 2026-08-31.

> The libraries are anonymized here per this repository's rule against naming real projects in a public history. Nothing in the findings depends on which libraries they are.

## Question

The project has a written specification for what a dependency skill should contain, derived by argument. Before generating skills against it, it is worth looking at skills that already exist: a set of sibling libraries shipped hand-written skills inside their own artifacts, authored largely by an agent, under a standard that barely constrained them.

**What did an author with a free hand actually choose to tell other agents, and how did they organize it?** Convergence with the specification is evidence the specification is right. Divergence is either a gap in it or a lesson about what happens without one.

## Trail

### The corpus

Eleven skill files, one per published module, each at `META-INF/ai-skills/<group>.<artifact>.ai-skill.md` inside `commonMain/resources` — so the skill travels inside the library's own artifact, keyed by its Maven coordinate. YAML frontmatter carries the coordinate as identity plus a spec version, a scope, and a compatibility range.

They run 15 to 103 lines, median around 52. **Every one of them is an overview.** Not one is an *exhaustive* API listing, and nothing in the standard told them not to be — but several name a curated surface under a heading like *Key Functions*, so the contrast is collapsed against instanced rather than prose against symbols. See the correction of 2026-09-04 below.

### The collapse an index cannot do

The largest skill covers a formatting library with extensions across every numeric type — signed and unsigned, six primitives each — plus overloads for precision and locale. Compiled, that is well over a hundred public members.

The skill names the shape once:

    T.formatReadable(locale: Locale, precision: Int): String

One line. The author collapsed the combinatorial expansion because **the shape is the knowledge and the instantiations are noise** — a reader who knows the pattern knows all of them. A per-entry index has no way to make that judgement; it emits every instantiation as a separate entry with a separate summary, at full cost, and the reader has to reconstruct the pattern from the pieces.

This is the clearest single argument for the reframe in RAD-0064, and it was made by an author with no stake in it.

### The recurring triple

Where the skills describe a capability, they overwhelmingly use three fields together:

- **Primary APIs** — a handful of signatures, in shape form.
- **Preference** — when to reach for this rather than the alternative. "Use this instead of `toString()` for any value displayed in a UI." "Prefer decimal degrees for technical interfaces, degrees-minutes-seconds for navigation."
- **Contract** — defaults and behaviour a signature does not carry. Default precision is 0 for integers and 1 for floating point. One function scales base-1000, its sibling base-1024. Internal caches are capped at 150 entries.

**Preference is the load-bearing field**, and it is the one an entry summarizer structurally cannot produce: it is a comparison between two members, or between this library and another. Contract is the specification's *invariants and traps* under a different name.

### Stale-prior countermeasures, invented independently

The specification argues at length that the highest-risk content is the correction of a confident wrong prior — a type that moved or was renamed. The authors reached the same conclusion without being asked to.

One skill's first usage rule is an import collision stated in capitals: always import the library's own `Locale`, **never** the platform's. That is the exact failure the specification predicts — a long-established name the model is certain about, attached to the wrong package. Another documents a behavioural change inline: the default changed in 3.1.0, and here is the value that restores the previous behaviour. A third dedicates a whole section to eight deprecated names, each in the form *old is deprecated in favour of new*, with a note that they will be removed in a future major release.

Nothing in the standard asked for any of this.

### A negative boundary, written well

One skill opens its capability section with the boundary rather than the capability:

> Standard Kotlin library primitives are always preferred. This library exists strictly to fill multiplatform gaps.

That is the specification's *what it is not for*, placed first, by an author describing their own library. It is also unusually honest, and the specification's claim that this field is the most often missing holds here too: most of the eleven do not have it.

### Two things the specification has no slot for

**Conformance.** Several skills carry a *Compliance and Standards* list: the numeric separator standard, the binary-prefix standard, the locale subtag standard, the geodetic datum, and in one case the upstream implementations a port was derived from. This is compact and high value — it answers a whole class of "does it handle X correctly" questions with a citation rather than a claim, and it is checkable. The five body fields have nowhere to put it.

**A domain reference table.** The geospatial skill carries a table mapping code length to accuracy. It is not orientation and it is not a trap; it is a lookup an agent would otherwise guess at.

### What the standard mandated, and what that produced

The v1 publishing standard required a namespaced filename, frontmatter, and — as the only mandatory body section — *Agent Onboarding*. Its supplied template reads:

1. add this skill file to the consuming project's agent instructions
2. ensure the root README contains an "AI-Assisted Development" section
3. prioritize the patterns defined above

The first two are instructions for installing the skill system. They are not facts about the library, and to a caller trying to use the library they are noise. **Five of the eleven skills carry that boilerplate**, copied nearly verbatim.

The smallest skill is 15 lines and is *nothing but* the mandated section. Its one library-specific sentence advises prioritizing the library's own design tokens — true of every component library ever written. It ships with the same `spec-version: "1.0"` as the 103-line contract document, and no validation step existed to tell them apart.

So the standard's one requirement produced the least useful content in the corpus, while everything of value — preference, contract, deprecations, version notes, conformance — was unprescribed and emerged anyway. **What was mandated had no value to a caller; what had value was not mandated.**

### Drift, for want of a schema

Across eleven files the same concept appears under several names: *Agent Onboarding*, *Agent Onboarding (Usage Rules)*, *Usage Rules*; *The AI Toolbox*, *AI Toolbox*, *The AI Toolbox (Key Functions)*, *(Key Components)*. Headings carry decorative emoji throughout. With a corpus this small and one author, the names still drifted — so any consumer keying on section headings would already be broken.

### The transport failed silently

Placed in `commonMain/resources`, the skill reaches one artifact and no others. Two measurements, and the second is the one that matters to this project.

**By target.** Of 154 cached binary artifacts, **17 carry a skill and every one is a `-jvm` jar; none of the 120 non-JVM artifacts carries one.** For libraries whose reason to exist is multiplatform support, the skill is absent on every target except the one that needed it least, and nothing anywhere reports it. It is the same failure mode this project hit with its own native libraries: a build that succeeds while shipping nothing usable.

**By artifact kind.** **Zero of 82 sources jars carry a skill.** Confirmed against the central repository rather than inferred from the cache: for the newest release of one module, the binary jar is 331 KB and contains the skill, and the sources jar is 72 KB whose entire `META-INF/` is a 25-byte manifest. A sources jar holds source files. Resources are not in it, so a skill placed in `commonMain/resources` cannot be there.

**This is a direct conflict with ADR-0009**, which makes the sources jar this project's transport. A sources-jar pipeline cannot see a single one of the skills already shipping in the wild — not because they are absent from the release, but because they are in the other artifact. The decision is not wrong about where *prose* comes from; it is silent about where a library's own authored skill comes from, and those turn out to be different files.

One further note: the skills are still present in the newest published versions, including the current release, although the working tree has moved them to a directory pending deletion. **A published artifact is permanent in a way a source tree is not** — whatever a skill transport ships, it ships forever.

### Correction, 2026-09-04: which of these findings travels

Most of this RAD needs no revision. Recommendation 1 below already says *name the shape, not the instantiations*, and the measured bullet — a skill covering over a hundred public members names roughly twenty API shapes — is the evidence for it. Both stand.

What travels badly is the summary bullet, *All overviews; none an API listing*. Quoted on its own it reads as **skills do not enumerate**, and it was used that way in [RAD-0064](Research-RAD-0064-The-Skill-Is-The-Overview). The corpus does not support that reading. The section names recorded under *Drift, for want of a schema* include *The AI Toolbox (Key Functions)* and *(Key Components)*, and twenty named shapes covering a hundred members is an enumeration by any ordinary use of the word. The accurate contrast is **collapsed against instanced**, not prose against symbols. A skill enumerates; it enumerates once per shape.

One thing this RAD assigned to the wrong place. Recommendation 1 says generation "should be able to emit one entry covering a family of overloads", which puts collapse behind a generative model. For overload families it is mechanical. The bytecode harvest holds the owning class, the method name and the descriptor, and the harvested symbol already omits parameter types — so an overload family arrives in the store as a duplicate symbol, and grouping it needs no generation at all. RAD-0064's correction of the same date records the detail.

The ceiling is higher than the floor, though. The twenty shapes in the hand-written skill are not all overload families: collapsing extensions across six primitive receivers into one `T.formatReadable` is a judgement about which receivers are interchangeable, and same-owner-same-name grouping will not reach it. Descriptor collapse is the cheap majority of the win, not the whole of it.

## Findings

**Measured.**

- Eleven skills, 15 to 103 lines, median about 52. All overviews; none an *exhaustive* API listing, though several name a curated surface under a *Key Functions* or *Key Components* heading.
- A skill covering over a hundred public members names roughly twenty API shapes.
- Five of eleven contain install boilerplate that the standard mandated; one consists of nothing else.
- Section headings for the same concept vary four ways across eleven files.
- 17 of 34 JVM artifacts carry a skill; 0 of 120 non-JVM artifacts do; **0 of 82 sources jars do**, verified against the central repository.

**Observed, and a judgement.**

- *Preference* and *Contract* are the two fields authors reached for repeatedly, and *Preference* cannot be produced from a single entry.
- Stale-prior corrections — import collisions, renames, changed defaults — appear unprompted, corroborating the specification's most-argued claim.
- Conformance-to-standards is genuinely useful and has no slot in the current five fields.

## Recommendation

**Not a commitment; input to the specification and to generation.**

1. **Name the shape, not the instantiations.** One entry should cover a family of overloads. This is the concrete requirement RAD-0064's reframe implies, and the corpus shows an author doing it by hand every time. Amended 2026-09-04: for overload families this is mechanical, not generative — same owner, same name, off the descriptor — and the harvested symbol already collapses them into a duplicate key. Generation is needed for the shapes that span receivers or versions, not for these.
2. **Adopt *Preference* explicitly** as part of *how it is meant to be used* — comparative, "reach for this rather than that", including against other libraries in the graph. It is the field authors converge on and the one per-entry summarization cannot reach.
3. **Consider a conformance field.** Standards a library adheres to are compact, checkable, and answer a class of questions nothing else in the five fields does.
4. **Mandate nothing about installation.** The v1 outcome is a clean demonstration that a mandatory section with a copyable template gets copied, and that mandating the wrong thing is worse than mandating nothing.
5. **Validation is not optional.** Identical version markers on a placeholder and a real document mean the version marker says nothing. This project's specification already has a validation section; the corpus shows what its absence costs.
6. **A transport must be target-aware and must report.** Whatever this project uses to carry a skill, silent absence on most targets is the failure to design against first.
7. **Reconsider what ADR-0009 covers.** Harvesting prose from the sources jar and discovering a library's own authored skill are different reads of different artifacts. If a shipped skill should be preferred over a generated one — and it plainly should — the binary jar has to be looked at too.

**What would change the answer.** This is one organization's libraries, largely one author, on one standard — convergence with the specification may partly reflect a shared origin rather than independent discovery. A second corpus from unrelated authors would test that, and is worth finding before the specification is changed on this evidence alone.

## Connections

- [RAD-0064](Research-RAD-0064-The-Skill-Is-The-Overview) — the reframe this corroborates.
- `spec/content.md` — the five body fields these observations are measured against.
- [ADR-0009](Decisions-ADR-0009-Transport-Is-Sources-Jar) — transport, which the sources-jar finding contradicts for authored skills specifically.

# The Skill Is the Overview, the Index Is the Substrate

RAD-0064 · 2026-08-31
Keywords: what is a dependency skill actually; should we index every entry; is the per-entry index the product; overview versus API listing; why summarize a signature at all; what does an agent already know about a popular library; long tail libraries the model has never seen; do agents use the index; statistics on retrieval; how does an agent report a bad skill; feedback channel trust boundary; context budget for a skill; is the codex the deliverable.
Measured against: the entry counts and throughput recorded in RAD-0063 and the #26 pipeline work — `commons-text:1.12.0` at 1,447 entries, `kotlin-stdlib:2.3.21` at 6,414, a summarizer at roughly 7.7 entries per second — and `spec/content.md` as written on 2026-08-31.

## Question

The codex harvests every entry in a dependency, summarizes each one with a generative model, embeds it, and serves search and get over the result. RAD-0063 then asked which entries deserve that treatment and spent its length on how to tell a private member from a public one.

The question underneath was never asked: **is a per-entry index the thing we are building at all?** A skill that an agent loads is an orientation — what the library is for, how it is meant to be used, what will bite you. That is not a list of symbols, and no amount of filtering turns one into the other.

## Trail

### The specification already answered this

`spec/content.md` prescribes a body of five things: what it solves in the caller's words; how it is meant to be used, "enough to write correct code from, not a tutorial"; invariants and traps; what moved and what it used to be called; and what it is **not** for. Of the traps paragraph it says plainly that this is the most valuable content in the file, because **it is what the caller cannot learn from the signature**.

So the specification ranks signature-derived content lowest, and the implementation has been generating almost nothing else. The drift is not a disagreement about direction; it is a component that was built without its own specification in view.

### The measurement that argued against itself

RAD-0063 established, while investigating something else, that `com.google.gson.internal.$Gson$Types` was renamed to `GsonTypes` in 2.14, and that gson's own reason for the rename was that the old names "cause problems".

That fact is one sentence, and it is precisely the specification's *what moved* field — the case where a model is most confident and most wrong, because a long-established type recently changed name. It is worth more to an agent than the four class entries of that file indexed separately will ever be. It surfaced during a visibility investigation and was nearly filed as a matching curiosity.

The general form: **the high-value content is about the library, not about its members.** Per-entry summarization cannot produce it, because no single entry contains it. A rename is a relationship between two versions; a trap is a relationship between what compiles and what works; a boundary is a relationship between this library and its siblings.

### What the target content looks like

A worked example, not a measurement — a summary of how `kotlinx.serialization` handles default values, of the kind a search returns today.

The useful part is not any one symbol. It is a rule assembled from four of them:

- Properties with default values are **omitted** from the output by default, and a missing key is filled from the Kotlin default on decode.
- `encodeDefaults = true` on the format builder turns that on globally.
- `@EncodeDefault(Mode.ALWAYS)` overrides it upward for one property; `Mode.NEVER` overrides it downward. Omitting the mode gives `ALWAYS`.
- A defaulted property is *optional* on decode, so `@Required` exists to force the key to be present anyway — and `@Transient` requires a default, because the field will not be in the input.

**Not one of those facts lives in a single entry.** Summarize `encodeDefaults`, `@EncodeDefault`, `@Required` and `@Transient` separately and you get four accurate, disconnected paragraphs, none of which contains the override hierarchy that makes them usable — and a reader who lands on any one of them by retrieval learns the least useful third of the answer. The knowledge is in the interaction, and the interaction has no home in an index whose unit is the member.

It also shows the shape of the content: the surprising default first, the exact knob to change it, a concrete before-and-after, and then the ways the knob is overridden. That is the specification's *invariants and traps* and *how it is meant to be used*, and it is what generation should be aiming at.

### What per-entry indexing costs

`kotlin-stdlib` harvests 6,414 entries. At the measured summarization rate that is roughly fourteen minutes of a resident generative model for one library, and a real dependency graph is hundreds of libraries — RAD-0001 measured how many. The cost is not fatal but it is the dominant cost in the system, and it is being paid for the artifact the specification ranks last.

### Where the index still earns its place

Two roles survive, and neither is "the product".

**It is how the overview gets written.** Nothing can summarize a library it has not read. The harvest, the entries, the signatures and their prose are the raw material a generated overview is drawn from. Deleting the index would remove the only thing that makes generation possible at all.

**It is the drill-down for libraries the model has never seen.** For a widely used library, the model already knows the API and needs orientation, not a symbol list. For an obscure one — and the long tail is where a dependency skill earns its keep — an agent needs to be able to ask what is actually in there. That is retrieval, reached through search, not content paged into context.

Both roles are *substrate*. The index is consulted; the overview is loaded.

### The thing nobody has measured

There is no evidence that any of this is used. Not that it is unused — that it is unmeasured. Nobody knows which dependencies an agent ever asks about, which queries come back with nothing usable, or whether a retrieved entry changed what the agent then wrote.

That absence is why RAD-0063 had to reason its way toward what to index instead of observing it. A local record of what was asked and what came back answers the question directly, and it answers a second one the project will face immediately: of the thousands of libraries in a developer's cache, which few deserve a carefully generated overview.

### A feedback channel, and the thing it must not become

The other missing half is a way for an agent to say the skill was wrong. A skill is generated, it is loaded on trust, and today nothing carries a correction back — a wrong summary stays wrong forever and nobody learns which ones are wrong.

A report and a statistic are the same shape: *asked X, got Y, Y was wrong*. One channel carries both.

But a report endpoint is a **write path from an agent into the store**, and this project deliberately holds a trust boundary there — see RAD-0062 on identifiers that cannot be rewritten. Reports must land quarantined as claims, attributed and never merged into served content without review. Anything less makes the report endpoint the cleanest way to poison the codex, arriving through the mechanism meant to improve it.

### Correction, 2026-09-04: the enumeration was never the thing to remove

This RAD framed the choice as overview *versus* index, and recommended that generation draw on the harvested entries "as source material rather than emitting them". That opposition is wrong, and the counter-evidence is in [RAD-0065](RAD-0065-what-v1-skill-authors-wrote-unprompted.md), which this RAD cites without reading it this way.

A hand-written skill does enumerate. It enumerates a **collapsed** surface. The formatting library in that corpus has well over a hundred public members; its skill names the shape once, as `T.formatReadable(locale: Locale, precision: Int): String`. Several skills in the corpus carry a section called *Key Functions* or *Key Components* outright. "Not one is an API listing" is true of the exhaustive form, and was allowed to stand for the broader claim that a skill does not enumerate its surface at all. It does.

So the axis is not prose against symbols. It is **collapsed against instanced**. A per-entry index emits an instanced surface — one row per member, overloads included. A skill carries a collapsed one. The five body fields still hold; what was missed is that a capability section written well *is* an enumeration, and the specification's silence about listings is not a prohibition on naming the surface.

### Collapse is mechanical where it matters most

The recommendation below implies collapse waits on generation, and therefore on a capable model. For the largest and most wasteful case it does not.

The bytecode path already carries the structure. `BytecodeSignatures.ofMethod` holds the owning `ClassNode`, `method.name` and `method.desc`, and `Type.getArgumentTypes` yields exact parameter types from the descriptor. An overload family is *same owner, same name* — a grouping, not an inference, and no model is involved in finding it.

It is already more collapsed than that. `BytecodeHarvester` builds its symbol as the dotted owner plus the method name, with no parameter types, so **every member of an overload family produces the same symbol string**, differing only in `signature`. The family is not something to detect; it is a duplicate key already in the store.

Two consequences follow, and one of them is a defect.

- Collapsing an overload family in a search result is a group-by on a field that exists today. The four `debug()` rows behind #37 are one symbol repeated four times, spending four of ten result slots on a single shape.
- `CodexQueries.get` resolves with `firstOrNull { it.symbol == symbol }`. So `get("org.slf4j.Logger.debug")` returns one arbitrary member of the family and never reports that the others exist — the same collapse arriving from the other side, unhandled.

**What descriptor grouping does not buy.** #37 contains two failures that look like one. The four overloads are the mechanical half. The absent `isDebugEnabled` is not an overload — it is a guard idiom, a relationship between two differently-named members, and nothing in a descriptor implies it. That half is the relational content this RAD was actually about: the `kotlinx.serialization` default-value rule assembled from four symbols, the gson rename that is a relationship between two versions. Those need generation. Compressing an overload family does not, and the two should not be costed together.

## Findings

**Measured.**

- A single library runs to thousands of entries — 1,447 and 6,414 for the two indexed end to end — and summarization is the dominant cost in the pipeline.
- The specification's five body fields contain no per-entry listing, and explicitly rank the non-signature content highest.
- A published library's own authored skill, where one exists, is in the binary jar and never in the sources jar — see RAD-0065. The current transport cannot see it.
- The bytecode path carries owner, method name and parameter types, and the harvested symbol omits the parameter types — so an overload family is a duplicate symbol already in the store, not something that must be inferred.
- `CodexQueries.get` resolves a symbol with `firstOrNull`, and therefore answers for an overloaded method with one arbitrary member of the family, silently.

**Asserted, not yet measured.**

- That an overview outperforms a per-entry index for a library the model already knows. This is the specification's premise and it is untested here.
- That the long tail is where retrieval earns its place. Plausible, and the exact opposite of what a popularity-ordered corpus would suggest.

## Recommendation

**The overview is the product. The index is substrate.** Concretely: generation should produce a skill body against the five fields in `spec/content.md`, drawing on the harvested entries as source material; search and get remain, serving drill-down rather than context.

**Emit the surface, collapsed.** Amended 2026-09-04 — the first version of this recommendation said generation should draw on entries *rather than emitting them*, which overshot. A skill names its surface; it names it once per shape instead of once per member. Collapse by descriptor — same owner, same name — is mechanical, costs no model, and is the cheapest correction available on the bytecode path. Reserve generation for the relational content no single entry holds.

**Two mechanisms, in this order.**

1. **A local record of use** — which coordinates are asked about, which queries return nothing usable, whether a result was taken up. Local to the machine, never leaving it, because a dependency graph is commercially revealing and this project has already declined to put one on a network by default.
2. **A report channel for agents**, sharing that record's transport, with reports quarantined as claims and never auto-merged.

**RAD-0063's outstanding measurement should be dropped, not completed.** It asks what dropping non-public entries does to retrieval quality, which measures the ranking of an artifact that is no longer the deliverable. Its finding about matching — that descriptors, `InnerClasses` and Kotlin metadata are the authoritative sources and string surgery is not — stays useful for the harvest and is unaffected.

**What would change the answer.** If the use record shows agents drilling into entries far more than they read overviews, the ordering here is wrong and the index is the product after all. That is exactly the question the record exists to settle, and it should be built before much more is generated either way.

## Connections

- `spec/content.md` — the five body fields this returns to.
- [RAD-0063](RAD-0063-bytecode-as-the-visibility-oracle.md) — the visibility investigation this reframes, and where the gson rename was measured.
- [RAD-0062](RAD-0062-screening-an-identifier-that-cannot-be-rewritten.md) — the trust boundary a report channel must respect.
- [RAD-0065](RAD-0065-what-v1-skill-authors-wrote-unprompted.md) — eleven hand-written skills in the wild, which reached the same shape unprompted.
- [ADR-0009](../decisions/ADR-0009-transport-is-sources-jar.md) — why the sources jar is the content.

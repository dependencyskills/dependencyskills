# Who Computes the Codex, and How It Reaches a Developer

RAD-0052 · 2026-08-28 · v1
Keywords: does every developer need a local model; distributing the index as a dependency; publishing a codex per library; central versus local revisited; why this is not the v1 sidecar; a Maven artifact as the distribution channel; who becomes the trust anchor; how far down the popularity curve; what still needs a local model; first-party code cannot leave the machine.
Measured against: nothing. This is an argument, and it names the measurements the sub-questions would need.

## Question

[RAD-0051](RAD-0051-a-jvm-generative-runtime.md) asks how small the summariser's model can be, because the pinned one is 16 GB and every developer would hold a copy. That framing contains an assumption nobody has examined: **that the machine which needs a summary is the machine that must produce it.**

It need not be. So the question underneath is: **who computes the codex, and how does the result reach a developer?**

## Trail

### Summarising a public library is a pure function of an immutable artifact

`io.ktor:ktor-server-core:3.5.2` is byte-identical for every developer. A published Maven version cannot be replaced after the fact — [ADR-0012](../decisions/ADR-0012-a-shared-machine-level-index-store.md) leans on that already — and entries are content-addressed on `(symbol, signature, doc)`, so the same input produces the same entry id everywhere.

**The summary for a public coordinate is therefore the same object on every machine on earth.** Computing it once and distributing it is not a compromise for the sake of footprint; it is the correct factoring, and computing it per machine is the anomaly. Nobody should run a 30B model to describe ktor when the answer is already a fixed, finite object.

### The transport already exists, and nothing new has to be invented

[#3](https://github.com/dependencyskills/dependencyskills/issues/3) computes a project's resolved coordinate set as an ordinary part of the build. If a codex artifact exists for a coordinate, the same resolution machinery fetches it: same cache, same checksums, same offline behaviour, same immutability, same repository the build is already talking to.

A missing artifact is not an error. The store distinguishes `pending` from `no-source` from `indexed`, so an absent codex falls back to local harvest, or to signature-only — a state separately measured as sufficient to *use* a capability, 7 of 8.

**This is the shape RAD-0003 did not consider.** That record put central and local side by side as a *query service* against a *local index*, and chose local first on governance, operation, funding and uptime. Distributing data rather than answers keeps every property it valued in local — offline, no operator, reproducible, no query traffic — and moves only the expensive computation. It is not the third layer; it is layers two and three recombined.

### Why this is not the sidecar that failed

The v1 design asked **library authors** to publish an artifact describing their own library, and the postmortem records why that died. This publishes the same kind of artifact **for** them, computed by a third party, with the author doing nothing at all.

Same transport, opposite direction of effort. That difference is the whole thing: it is the property RAD-0003 praised in the local design — *it requires no ecosystem adoption* — recovered for a centrally-computed index.

### It splits the model requirement in two, and the halves are not alike

| | who computes | needs a local model |
|---|---|---|
| public coordinates from a registry | once, by whoever publishes the codex | **no** |
| first-party and private code ([#9](https://github.com/dependencyskills/dependencyskills/issues/9)) | the developer's own machine | yes, or signature-only |

The second row is the one that cannot be centralised, and it is not a leftover. `test0` measured that local knowledge is the gap model progress does not close, and private code is precisely what must not leave the machine — the corpus harvester already excludes private groups for that reason.

**So RAD-0051's sweep is not cancelled; its bar moves.** The question stops being *how small a model can we ship to everyone* and becomes *how small a model suffices for a developer's own code, when the alternative is signature-only*. That is a much easier bar, and it may already be cleared.

### What it costs

**We become the trust anchor.** Every consumer's rewrites come from one model run. RAD-0003 listed a single point of compromise as a cost of going central, and this makes it concrete: a subverted summariser run reaches everyone who fetches that artifact. Content addressing and signing make tampering *detectable*; neither makes a bad run harmless, and the difference matters.

**Compute decides coverage.** Running a large model over every library worth indexing is real money, and it sets how far down the popularity curve this reaches. The obvious lever is demand — index what people actually resolve — and the obvious source of that signal is the consumer plugin, which would turn a component that currently reports only to a local store into one that reports upward. That is a privacy question and it should be answered before it is convenient.

**Staleness is structural but bounded.** A new version is a new coordinate and misses until it is published. The fallback is the local path, so the failure mode is degradation rather than absence — provided the fallback actually exists, which is an argument for building it rather than skipping it.

**Namespace and volume.** Publishing to a public registry an artifact per library-version, describing other people's libraries under our own group, is a volume problem and a naming problem at once. **Our own Maven repository avoids both and costs a consumer nothing** — it is a `repositories { }` entry against a static file host, resolved by the same code. Operating a file host is not operating a service, which is the distinction RAD-0003's objections turn on.

## Findings

Nothing here is measured. What is established is that the argument holds together:

- **The output is cacheable in principle.** Immutable coordinates plus content-addressed entries mean a public coordinate's codex is a fixed object, not a rendering that varies by consumer.
- **The transport requires no new client code and no new protocol.** Dependency resolution is already the mechanism, and the consumer plugin already produces the input to it.
- **The governance objections RAD-0003 raised are objections to operating a query service.** They do not transfer to publishing files.
- **It does not remove the local model**, it removes it from the common case. First-party indexing still needs one, or accepts signature-only.

## Recommendation

**Treat this as a direction to open, not a decision to take.** It touches [#6](https://github.com/dependencyskills/dependencyskills/issues/6), [#7](https://github.com/dependencyskills/dependencyskills/issues/7), [#8](https://github.com/dependencyskills/dependencyskills/issues/8) and [#9](https://github.com/dependencyskills/dependencyskills/issues/9), and none of them should commit to a shape until at least the first question below is answered.

**It should split, and these are the seams.**

1. **The artifact.** What is in a per-library codex, how a source coordinate maps to a codex coordinate, whether the grain is library, group or bundle, and what a consumer does with a partial hit. This is the one that blocks the others, because it decides what the rest are arguing about.

2. **The trust model.** What a consumer verifies and what it cannot. Content addressing detects a changed entry; it says nothing about whether the entry was right when it was computed. Signing, revocation, and what a compromised publisher can actually do to a consumer — including whether a consumer can pin, re-derive, or refuse.

3. **Coverage and its signal.** How far down the popularity curve this reaches, what the compute costs, and how demand is measured without turning the consumer plugin into telemetry. The privacy question here is load-bearing and should not be settled by convenience.

4. **The local half.** What #9 needs when nothing can be centralised, and whether signature-only is an acceptable resting state for a developer's own code — which is a product question, not a technical one.

**Do not stop RAD-0051's probe.** Its bar has moved, not vanished: the local half still needs a model, and the smallest workable one is still the number that decides whether the fallback path is pleasant or grim.

**What would change the answer.** Discovering that summaries are *not* consumer-independent — a per-project or per-language rendering, say — would remove the cacheability the whole argument rests on. So would a trust analysis concluding that a single publisher is unacceptable regardless of signing, which would push this back toward every machine computing its own.

## Connections

- [RAD-0051](RAD-0051-a-jvm-generative-runtime.md) — the model-size question this reframes
- [RAD-0003](RAD-0003-central-capability-server.md) — central and local as two shapes of one thing, and the objections that do not transfer
- [ADR-0012](../decisions/ADR-0012-a-shared-machine-level-index-store.md) — content addressing and immutable coordinates, which are what make this cacheable
- [RAD-0002](RAD-0002-existing-documentation-systems-as-skill-transport.md) — whether a carrier travels with the library, asked of the author rather than of a third party
- [RAD-0040](RAD-0040-does-summarising-improve-retrieval.md) — what the summariser buys, and the measured safety of signature-only

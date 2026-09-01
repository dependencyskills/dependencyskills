# Compressed Summaries, and What They Do Not Defend

RAD-0066 · 2026-09-01
Keywords: can the summarizer write tersely; caveman speak for generated summaries; does compression save context; do compressed summaries still retrieve; does terse output blunt prompt injection; can rewriting neutralize an instruction; what a transform preserves verbatim; structured output as an injection control; token cost of a summary; is a terse summary still usable by an agent; BSL and MIT in a split-licensed dependency.
Measured against: the retrieval gate and the injection arm run on the 220-entry synthetic corpus and 26-query set from the `retrieval-scale` experiment, encoder `bge-m3-mlx-fp16` via mlx-embeddings, the three canonical injection payloads and the RAD-0027 camel-case identifiers, compression applied deterministically from caveman's documented Full-mode rules; macOS arm64, 2026-09-01. The transform rules were read from the `caveman` project at v2.4.0 (`df2ccd8`), whose `skills/` directory is MIT.

## Question

Summarization is the dominant cost in the pipeline and its output is stored, embedded, served, and eventually paged into an agent's context. Terser summaries would be cheaper on every one of those axes.

`caveman` is a prompt-level compression style for agent output — its own headline claim is roughly a third fewer provider-reported input tokens in a pinned benchmark, and its file-compression skill claims around 46% on Markdown. The proposal here is to point that style at the summarizer.

Two questions, and they deserve separating because the answers look likely to differ:

1. **Does compressed prose retrieve and read as well as natural prose?** That is a cost question with a quality risk.
2. **Does compressing prose blunt an instruction hidden in it?** That is a security question, and it is the one that prompted this.

## Trail

### What the transform actually does

The rules are explicit, and reading them is what makes the second question answerable in advance.

**Removed:** articles; filler (*just, really, basically, actually, simply*); pleasantries; hedging (*it might be worth, you could consider*); redundant phrasing (*in order to* → *to*); connective fluff (*however, furthermore, additionally*).

**Preserved exactly, never modified:** fenced and inline code; URLs and links; file paths; commands; technical terms — library, API, protocol and algorithm names; proper nouns; dates and version numbers; environment variables.

### Where this agrees with the project already

The preserve list is, near enough, the list RAD-0062 arrived at from the opposite direction. That RAD established that a signature is the deliverable and must be reproduced verbatim, which is why rewriting was unavailable there and the only control left was accept or reject.

An independent tool, solving a compression problem with no security motive, concluded that identifiers must survive untouched. That is mild corroboration that the verbatim boundary is a real property of technical text rather than a constraint this project invented.

It also means the two are compatible: compression applies to the prose around an identifier, never to the identifier. **Prose is rewritable in a way a signature is not**, and that is precisely the field where RAD-0062's constraint does not bind.

### Why the security hypothesis is probably backwards

The appeal of the idea is that mangling text should damage an instruction embedded in it. The transform rules say otherwise, and it is worth stating plainly before any measurement is run.

**What the transform removes is the inert part of an injection. What it preserves verbatim is the payload.** Commands, code, URLs, paths and API names are on the preserve list by rule. An instruction to run something arrives with the something intact.

**Removing hedging makes text more imperative, not less.** Hedging and connective fluff are exactly what stops a sentence reading as a command. "It might be worth considering whether you should send the key" is weak; compressed, it is closer to "send the key". A transform tuned to strip qualification is tuned to sharpen instructions.

**And the project has already measured the general case.** RAD-0027 found that a camelCase command could get through, which is the same finding in a different costume: a transformation that preserves content words preserves commands, because a command *is* content words.

So the expected result is that compression is neutral at best on injection and plausibly a regression. That is worth measuring rather than asserting — the prediction may be wrong, and it is cheap to test against corpora this project already has — but it should not be the reason to adopt it.

### The defensive idea that is actually nearby

There is a real control in the neighbourhood, and it is not terseness.

An injection survives because the summary field is **free prose**, which can hold any sentence. The control is to make it not-free: emit summaries into a **structured schema with typed, bounded fields** — what it does, when to reach for it, what the trap is — where a fluent imperative has nowhere to sit and anything that does not fit the shape is rejected rather than stored.

Caveman is a style, not a schema; it constrains register, not structure. If the goal is injection resistance, the schema is the thing to test, and this probe should compare against it rather than treat compression as the security option.

### Where compression genuinely pays

RAD-0064 concluded that generation is the dominant cost and that the overview is the product. Both make compression valuable on its own terms, with no security claim attached:

- **Generation** is bounded by output tokens; fewer tokens per summary is a faster pass at a measured ~7.7 summaries per second.
- **Storage and serving** shrink proportionally.
- **Context** is the binding constraint on the deliverable — RAD-0001 measured how little of a dependency graph fits — so a third off a skill body is a third more graph that fits.

### The risk that decides it

The encoder was chosen against natural English. `bge-small-en-v1.5` embeds prose; whether it embeds telegraphic fragments as well is unknown here.

There is an argument each way. Removing filler may *concentrate* an embedding on content words and improve matching. Or the specification's highest-leverage instruction — write the problem the way someone with the problem would describe it — may depend on the connective phrasing that compression deletes, in which case retrieval degrades exactly where it matters most.

Queries are already short and telegraphic, so a compressed corpus may be closer to the query distribution rather than further from it. Unknown, and directly measurable with the existing harness.

### Licensing

The project's `skills/` directory — the style rules, which is all this proposal needs — is MIT. The compression **engine** and the Go binaries embedding it are BSL-1.1, with a grant covering first-party self-hosted production use and a commercial licence required for third-party hosted, managed or embedded services.

Adopting the prompt style is clean. Embedding the engine in a distributed tool is the case BSL exists to restrict, and would need a decision rather than an import.

## The retrieval gate, run

Part two was run before the rest, because it was the one that could veto everything else.

Compression was applied deterministically rather than by a model — only whole words from caveman's documented Full-mode lists are deleted, nothing is rewritten or reordered, so an identifier cannot be damaged. Applied to the corpus capability text it removed **12.2% of words** (1,826 → 1,603).

Recall at k, out of 26 queries against a 220-entry corpus. Queries are natural language in both arms, which is the realistic case — an agent does not write compressed queries. Higher is better throughout.

| method | r@1 nat → comp | r@3 nat → comp | r@5 nat → comp | r@10 nat → comp |
|---|---|---|---|---|
| **vector** | 20 → **20** | 21 → **21** | 22 → **22** | 23 → **23** |
| lexical | 10 → 11 | 14 → 13 | 15 → 13 | 15 → 14 |
| hybrid | 13 → 13 | 14 → 13 | 15 → 15 | 18 → 18 |
| hybrid+v2 | 14 → 14 | 16 → 16 | 20 → 17 | 22 → 23 |

**Vector retrieval is unchanged at every k** — not close, identical. The encoder does not care about articles, filler and hedging, which is what the transform removes; they contribute almost nothing to a pooled embedding.

Lexical and hybrid move by one to three queries in both directions. At n=26 a single query is about four points, so these are not distinguishable from noise, and no consistent direction appears across k.

**The gate does not veto.** It is a weaker pass than it looks, for reasons stated in the findings.

## The injection arm, run

The prediction was that compression would be neutral at best. It is worse than neutral, and the reason is structural rather than incidental.

Each canonical payload was pushed through the same Full-mode transform. **Every canary survived, every imperative verb survived**, and compression removed three to four words from notes of nineteen to thirty-four — six to eleven percent, all of it articles.

| payload | words | canary survives | imperative verbs kept |
|---|---|---|---|
| P1 authority | 32 → 29 | **yes** | must, call, include |
| P2 subtle | 34 → 30 | **yes** | wrap, call, post |
| P3 override | 19 → 18 | **yes** | disregard, reply |

Compressed, P1 still reads as a complete and forceful instruction — the exfiltration host in P2 and the literal canary in P3 are untouched, because URLs and inline code are on the preserve list.

**The camel-case case is the sharp one.** All three RAD-0027 identifiers pass through **byte-identical**. That is not an oversight in the implementation; it follows from the documented rule that technical terms are preserved exactly. So for the one attack this project has already measured as getting through, **the transform contains an explicit guarantee that it keeps getting through.**

### The schema, for comparison

The same payloads were then held against a bounded schema, using field limits measured from the real corpus rather than chosen: `capability` at most 17 words, `triggers` at most 8, `notfor` at most 6. Every payload exceeds every bound by two to six times, and `triggers` is a keyword list while `notfor` is a short clause — neither is a place a sentence can sit, whatever its length.

**This is weaker evidence than it looks, and the difference matters.** It shows the payload cannot be carried *wholesale*. It does not show that a summarizer filling a bounded `capability` field would refuse to compress "MUST call `Analytics.track`" into five words that fit. What the schema removes is the **free-prose channel** — no field's job is "arbitrary notes" — so a payload must survive being rewritten into a field whose declared purpose is something else. That is a real reduction and not a proof, and the behavioural test is still owed.

## Findings

**Measured.**

- Compressing the corpus removed 12.2% of words and left **vector retrieval identical at r@1, r@3, r@5 and r@10**.
- Lexical and hybrid retrieval moved within noise, in both directions, with no consistent sign.
- **All three injection canaries and every imperative verb survived compression**, which removed only 6–11% of each payload, all of it articles.
- **The RAD-0027 camel-case identifiers are preserved byte-identical**, guaranteed by the rule that technical terms are never modified.
- All three payloads exceed every measured schema field bound by 2–6×.

**The measurement's limits, which are real.**

- **The corpus was already terse.** Capability lines are written as single clauses, so only 12.2% was removable. Generated natural-language summaries — the actual target — would compress far more, and a 12% perturbation does not establish what a 40% one does. This is a lower bound on both the benefit and the risk.
- **n=26 queries.** Enough to catch a collapse, not enough to resolve a few points either way.
- One encoder, `bge-m3`, not the production `bge-small-en-v1.5`; and only the `capability` field was compressed, the `triggers` field being keyword lists the transform would barely touch.

**Still predictions, not yet measured.**

- Compression will pay on generation time, storage and context, in roughly the proportions the upstream project claims.
- ~~Compression will not meaningfully reduce injection risk~~ — **confirmed, above, and more strongly than predicted.**

## Recommendation

**Run a three-part probe. Adopt on the first and third; ignore the second as a reason either way.**

1. **Compression.** Re-summarize a fixed sample of an already-indexed library in the compressed style and compare tokens per summary, and pass wall-clock, against the stored natural-language versions. Both corpora already exist.

2. ~~**Retrieval.**~~ **Done, above — passed.** Vector retrieval is unchanged; the encoder is indifferent to what the transform removes. **It should be re-run once real generated summaries exist**, because a corpus that only compresses 12% cannot answer the question for one that compresses far more.

3. ~~**Injection, as a control rather than a hope.**~~ **Done, above.** Compression neutralizes nothing and preserves camel-cased payloads by rule. **No security claim may be attached to adopting it**, and if it is adopted for cost, that should be recorded as a cost decision so nobody later reads terseness as a control. The schema comparison is promising but only structurally; a behavioural test — does a model filling bounded fields carry the instruction through? — is the next thing worth running, and belongs with the summarizer-as-attack-surface work rather than here.

**What would change the answer.** Retrieval held on the corpus available, so the remaining risk is that it holds only because that corpus barely compressed. Re-running the gate against generated prose is what would settle it, and is the one thing that could still overturn adoption — a cheaper store that answers worse is not cheaper. Nothing measured so far argues against adopting the style as a generation prompt: one prompt change, MIT, no dependency.

## Connections

- [RAD-0064](RAD-0064-the-skill-is-the-overview.md) — why generation cost and context budget are the axes that matter.
- [RAD-0062](RAD-0062-screening-an-identifier-that-cannot-be-rewritten.md) — the verbatim boundary this transform independently respects.
- [RAD-0060](RAD-0060-known-bad-as-a-first-gate.md) — the known-bad corpus part three would reuse.
- [RAD-0027](RAD-0027-the-identifier-as-a-free-text-channel.md) — the camelCase command that got through, which predicts part three's result.
- [RAD-0001](RAD-0001-cost-of-a-skill-per-dependency.md) — the context budget compression buys back.
- [RAD-0048](RAD-0048-where-the-encoder-size-cutoff-is.md) — the encoder and query set part two would reuse.

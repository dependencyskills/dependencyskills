# Structure and Trust as Inputs to the Pipeline

RAD-0059 · 2026-08-29
Keywords: should the summariser see one doc comment or its parts; splitting a doc comment into paragraphs and code blocks; why the classifier already works per sentence; can a developer declare a source trusted; trusting Google or JetBrains; letting library prose through verbatim; why the harvester flattens structure; where a thread-safety caveat gets lost.
Measured against: the end-to-end pass of 2026-08-29 — 11,155 entries over the 59 pinned coordinates, `gemma-3-270m-it-qat-Q4_0` as summariser, the shipped `ProseClassifier` and `cleanDoc`. Numbers are from that run and from `experiments/test5/corpus.json`; everything proposed is unmeasured and marked.

## Question

> **Two things the pipeline currently does not know: what the parts of a doc comment are, and where it came from. What would either buy?**

Both surfaced from the same concrete loss. `kotlin.collections.ArrayList` documents, in its own comment, that it is not thread-safe. That fact reaches the store and never reaches an agent.

## Trail

### Where the caveat is actually lost, which is not where it looked

The stored `doc` for `ArrayList` contains this, intact:

> Thread safety [ArrayList] is not thread-safe. If multiple threads access an instance concurrently and at least one thread modifies it…

So the harvester kept it. The classifier did not flag the entry. Verification did not refuse the output. The entry is `Whole`, with a stored rewrite:

> The capability is to implement a dynamic array that automatically grows as needed, providing efficient indexing and fast access to elements.

Accurate, well-formed, and silent about thread safety. **Every component behaved correctly and the fact disappeared between them** — the loss is entirely at summarisation, and [#23](https://github.com/dependencyskills/dependencyskills/issues/23) can be narrowed to one component accordingly.

### The classifier already chunks, and it was decided by measurement

The proposal to split a comment before classifying has already been made and won, at sentence granularity. From `Prose.kt`:

> **Per sentence, not per comment.** A payload is one sentence in a comment averaging several, so at comment level its signal is diluted by everything around it — measured as a third more false positives at the same catch.

So the classifier is the *most* granular component, not the least. It also reports **which** sentence, which is the difference between a reviewer having something to look at and having a label.

**The component doing the whole job at once is the summariser.** It receives up to 4,000 characters of mixed prose, headings, bullet lists, parameter tags and examples, and is asked for one sentence describing the capability. Nothing tells it which part is the description, which is the caveat, and which is an example.

### The harvester destroys the structure a chunker would need

`cleanDoc` joins every non-empty line with a space. The `ArrayList` doc reaches the store like this:

> …fast indexed access to elements. ## Performance characteristics [ArrayList] provides efficient implementation for common operations: - **Indexed access** ([get], [set]): O(1) constant time - **Appending to the end**…

Heading text survives as words; the heading *boundary* does not. Nor do paragraph breaks or list items. So a downstream component cannot split on structure, because by the time it sees the text there is none.

That flattening is deliberate and correct for what it was written for — a retrieval key, where a query never contains markup and line structure is noise. It was not written for anything that needs to know which paragraph is which, and **nothing downstream can recover it**.

This is the prerequisite, and it is upstream of every chunking idea: the structure has to survive the harvester before anything can use it.

### What structure would buy, if it survived

Unmeasured, all of it:

- **A caveat becomes locatable.** Library documentation puts warnings in their own paragraph or under their own heading — `## Thread safety`, `@throws`, an admonition block. Over `test5`'s corpus, 11% of comments carry a caveat marker and the first sits a median 44% of the way in. Structure is how you find it without reading for meaning.
- **The summariser gets a smaller, cleaner job.** One paragraph in, one sentence out is a different task from 4,000 mixed characters in, one sentence out — and [#20](https://github.com/dependencyskills/dependencyskills/issues/20) found 1,009 of 1,238 refusals are the model failing to produce one sentence at all.
- **Boilerplate becomes a chunk rather than a phrase.** [#15](https://github.com/dependencyskills/dependencyskills/issues/15)'s footers — "Report a problem", 3,829 occurrences — are their own paragraph, which is far easier to drop as a block than as words.
- **The classifier could score a code block differently from prose**, or skip it. It currently sees whatever `cleanDoc` left.

### Trust, and what it actually buys

The second proposal: record where an artifact came from, and let a developer declare a source trusted — their own internal repository, or a publisher — so its prose can pass through with less processing, or verbatim.

**The saving is real but it is not the point.** Stated plainly, in the maintainer's own words: *"all that does is let more of it through, so the agent reading it has a better source to read from."* That is the honest framing. Trust is not a compute optimisation; it is **more original library text reaching an agent unmodified**, which is precisely what the quarantine exists to prevent. The trade is fidelity against containment, and it should be argued as that rather than as an efficiency.

### The project has already ruled on publisher reputation

Extending trust to a publisher — "this is Google, this is JetBrains" — meets a position already written down in `experiments/corpus/verified-publishers.txt`:

> Be sparing. The default already assumes these are fine; this column means something narrower — that somebody can say WHY. "It is a big company" is not a reason, it is a reputation, and the xz backdoor was two years of patient work inside exactly that assumption.

One publisher is listed, with the basis *"authorship, not reputation"* — the maintainer's own libraries, where what is known is who wrote them.

That does not refuse the idea; it constrains its basis. **Reputation is not evidence, and organisational size is anti-evidence** — a compromised release from a trusted publisher is exactly the case where "more gets through verbatim" does the most damage.

### And first-party is not automatically safe either

The other intuition — *my own code, I trust it* — has a measured counterexample. [RAD-0029](RAD-0029-the-agent-as-a-trust-launderer.md) recorded an agent reading an injected instruction and writing it into a doc comment of its own authorship, in the developer's own repository, promoting third-party text to first-party. [#9](https://github.com/dependencyskills/dependencyskills/issues/9) already carries the note.

A developer *choosing* to trust is still different from the system assuming it, and that distinction is what makes the idea worth pursuing. But the default has to be untrusted, and the choice has to be informed by this.

There is also a laundering route that trust-by-source cannot see: a trusted repository can hold vendored, shaded or re-published third-party code, whose prose was never written by the party being trusted.

### Both ideas need the same missing fact

Neither is implementable today, for the same reason. `Coordinate` is `(ecosystem, value)` and the store records nothing about which repository served an artifact, so it cannot tell Maven Central from a corporate Artifactory. [RAD-0058](RAD-0058-what-a-diagnostic-log-may-record.md) reached the same prerequisite from the diagnostic-logging side and found it recorded in no decision, record or issue.

Three separate lines of work now want it. That is the strongest argument for doing it that any of them makes alone.

## Findings

**Measured, in the 2026-08-29 pass.** The `ArrayList` caveat is present in the store and absent from the rewrite; the entry is `Whole` and was refused by nothing. Structure does not survive `cleanDoc`. 11% of doc comments carry a caveat marker, at a median 44% through. 1,009 of 1,238 verification refusals are the model failing to produce one sentence.

**Established by reading.** The classifier already scores per sentence, chosen over per-comment by measurement — a third fewer false positives at the same catch. `verified-publishers.txt` already states a position against reputation as a basis for trust. The store records no repository.

**Assumed, and none of it tested.** That chunking helps the summariser. That a caveat is reliably locatable by structure. That trusting a source is safe enough to be worth the fidelity it buys. Every one of those is a hypothesis, and the first two are cheap to test while the third is not.

### The quarantine already costs more than it catches, and that is the calibration

Every recommendation below leans cautious, and caution has a price this project can now state in numbers rather than in principle.

Over the 2026-08-29 pass, the verifier refused 1,238 of 11,155 rewrites — **11.1%**, every one degraded to signature-only, which RAD-0040 measured as unfindable. Of those refusals:

| | |
|---|---:|
| shape failures, no safety content | 1,009 |
| `imperative`, every one a false positive ([#22](https://github.com/dependencyskills/dependencyskills/issues/22)) | 151 |
| remaining, uninspected | 78 |

So the safety rules fire on **at most 0.7%** of the corpus while the component costs **9%** of it. Whatever the true catch rate is, the ratio is already an order of magnitude against, and pushing the quarantine harder moves both numbers the wrong way.

**And the comparison that matters is not with a perfect design.** The alternative in the field — and this project's own v1, written up in [RAD-0046](postmortems/RAD-0046-v1-bundled-flat-files.md) — is shipping a directory of files that an agent unpacks and reads **verbatim, with nothing between the library author and the model's context.** Against that baseline, a component that classifies every sentence, rewrites every comment and refuses anything imperative is not a marginal improvement, and it does not need to be flawless to be the better thing.

That cuts both ways, which is why it belongs here rather than in a conclusion. It argues **against** tightening the quarantine further in pursuit of completeness, because the cost is measured and the marginal catch is not. It argues **for** having somewhere to spend the slack — on structure, which loses nothing, before trust, which spends containment directly.

## Recommendation

**Record where an artifact was resolved from.** Three unrelated lines of work now need it and none can start without it. It is available at resolution time and discarded.

**Preserve structure through the harvester, as a separate field.** Do not change the retrieval key — flattening is right for what that is, and [#14](https://github.com/dependencyskills/dependencyskills/issues/14) is already a live cost of having changed extraction once. Keep the flattened key and add the segmented form beside it, so nothing measured has to be re-measured.

**Test chunking on the summariser before anything else here.** It is the cheapest of the three, it needs no trust model, and it targets a measured failure — 1,009 refusals for producing more than one sentence. `refusals.tsv` and the persisted store make a re-score minutes rather than hours.

**Spend the slack on structure before spending it on trust.** Both buy fidelity; only one spends containment to get it. Chunking makes the summariser's job smaller and loses nothing, while trust lets more original text through by construction — and given the quarantine is already costing 9% to catch at most 0.7%, the cheaper lever should be pulled first and measured before the expensive one is considered.

**Do not extend trust to a publisher on reputation.** If trust is built, the basis has to be something a person can state, in the shape `verified-publishers.txt` already requires. "Authorship, not reputation" is the standard already set by this project against itself, and a vendor is a weaker case than the maintainer's own libraries, not a stronger one.

**If a trust model is built, default to untrusted and make the fidelity trade explicit at the point of choosing.** A developer marking a source trusted is deciding that its prose may reach an agent unmodified. That is the decision, and the interface should say so rather than describing it as a performance setting.

**What would change the answer.** A chunking experiment showing the summariser does no better on a paragraph than on a whole comment, which would remove the main reason to preserve structure. A trust model that can distinguish authored-here from vendored-in, which would close the laundering route. Or a second `ArrayList`-shaped loss found somewhere structure could not have helped, which would say the problem is not segmentation at all.

## Connections

- [#23](https://github.com/dependencyskills/dependencyskills/issues/23) — the caveat that never reaches the entry, now localised to one component
- [#20](https://github.com/dependencyskills/dependencyskills/issues/20) — the 1,009 shape refusals a smaller job might reduce
- [#15](https://github.com/dependencyskills/dependencyskills/issues/15) — boilerplate footers, easier to drop as a block than as words
- [#14](https://github.com/dependencyskills/dependencyskills/issues/14) — why the retrieval key should not change again
- [RAD-0058](RAD-0058-what-a-diagnostic-log-may-record.md) — the same missing fact, reached from diagnostics
- [RAD-0029](RAD-0029-the-agent-as-a-trust-launderer.md) — why first-party is not automatically safe
- [RAD-0036](RAD-0036-can-the-corpus-be-poisoned.md) — the publisher question, and how narrow the verified list is

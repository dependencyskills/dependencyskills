# Research

Investigations: the question that prompted the work, the trail of options
weighed and dead ends ruled out, the findings — measured kept separate from
assumed — and a recommendation. A proof of concept in `experiments/` that settles a
question is written up here too.

Research sits upstream of a decision. It explores, weighs, and recommends;
it does not commit. A recommendation that hardens into a commitment
graduates to an ADR, cross-linked both ways.

## Index

Number, current version, title, and what the record settles. A version is the
record's own — bumped when it is meaningfully revised — and [Canon](../documents/DOC-0001-canon.md)
carries the history behind those bumps, including where a later result
overturned an earlier one.

| | | | |
|---|---|---|---|
| [0001](RAD-0001-cost-of-a-skill-per-dependency.md) | v1 | Cost of a Skill Per Dependency | What one skill per library costs, measured across twelve public projects in four ecosystems |
| [0002](RAD-0002-existing-documentation-systems-as-skill-transport.md) | v2 | Existing Documentation Systems as Skill Transport | Whether a carrier (`-sources.jar`, source on disk, the git coordinate) already travels with the library — the *get* question |
| [0003](RAD-0003-central-capability-server.md) | v1 | A Central Capability Server for Library Discovery | A query front-end over the corpus, local and central |
| [0004](RAD-0004-external-review-of-the-proposal.md) | v1 | External Review of the Publishing Proposal | Five objections from outside the project, and what each changed |
| [0005](RAD-0005-a-git-hosted-codex.md) | v1 | A Git-Hosted Codex | Reaching library guidance through the repository the publishing metadata already names |
| [0006](RAD-0006-development-time-prompt-injection.md) | v7 | Development-Time Prompt Injection | The injection surface, the measured model×payload×arm matrix (+ system-channel, tool-action, aggressive payloads) and a model trust table — data-framing is necessary but beaten by system-channel placement and meta-arguments |
| [0007](RAD-0007-choosing-between-overlapping-libraries.md) | v3 | Choosing Between Overlapping Libraries | Which signals discriminate when several libraries do the same job |
| [0008](RAD-0008-the-field-as-it-stands.md) | v3 | The Field as It Stands, and What This Research Rejects | What others have built, where it corroborates this work, and where each pattern's limits are |
| [0009](RAD-0009-reusing-indexers-and-what-to-index.md) | v6 | Parsing the Documentation, and What to Index | The parse layer — tree-sitter/Dokka per ecosystem behind a common contract, the summarise step as the new work, and whether to index the whole graph or only libraries that ship a skill |
| [0010](RAD-0010-how-the-codex-is-stored-and-served.md) | v3 | How the Codex Is Stored and Served | Text source of truth, a derived Lucene hybrid index at scale, and MCP as the interface — plus fixing the codex/librarian vocabulary |
| [0011](RAD-0011-existing-documentation-systems-as-skill-content.md) | v2 | Existing Documentation Systems as Skill Content | Whether KDoc/DocC/godoc/docstrings are rich enough to be skill content, how much exists, and designed-vs-discovered — split from RAD-0002 |
| [0012](RAD-0012-structure-from-bytecode.md) | v2 | Structure from Bytecode | Parsing the classes.jar for the API surface — deferred, held as an option for the undocumented tail and later capabilities |
| [0013](RAD-0013-the-codex-entry.md) | v3 | The Codex Entry | What an entry is: a two-faced (semantic + syntactic) per-capability record, retrieved hybrid, with Lucene as the index candidate |
| [0014](RAD-0014-build-vs-reuse.md) | v4 | Build vs Reuse: the Codex Pipeline | Per-layer reuse-or-build across the pipeline; assemble from Lucene vs adopt an end-to-end pipeline; Glean as template, Mahout rejected |
| [0015](RAD-0015-how-the-source-is-read.md) | v1 | How the Source Is Read | The read stage between get and parse — extract-all vs read-in-place vs selective, the IntelliJ lazy-VFS precedent, a read layer over archived and loose source, first-party source as a first-class input |
| [0016](RAD-0016-the-content-value-ab.md) | v2 | The Content-Value A/B | The thesis test — does the codex change what an agent does; synthetic subject, content-value-first, first-party-vs-dependency scenarios, a cross-model matrix run through developer tools |
| [0017](RAD-0017-the-retrieval-disambiguation-ab.md) | v1 | The Retrieval / Disambiguation A/B | Disambiguation *within a presented catalogue* — the syntactic face for differing signatures, the semantic face required when signatures collide |
| [0018](RAD-0018-the-selection-ab.md) | v1 | The Selection A/B | The measurement of RAD-0007 — unaided 0 of 18, the declared dependency tree redirects a single-choice classpath, only an authored preference resolves genuine ambiguity |
| [0019](RAD-0019-retrieval-at-scale.md) | v4 | Retrieval at Scale | Layer 1 index recall (vector-primary vs equal-RRF, BGE-M3) and Layer 2 the agent loop — authoring a query beats verbatim recall (10 of 10 pilot) |
| [0020](RAD-0020-information-flow-control.md) | v4 | Information-Flow Control as the Trust Model | Adopting IFC (integrity/confidentiality labels enforced before a sink) instead of positional discipline — the codex as label *source*, and the sink experiment that would settle it |
| [0021](RAD-0021-admission-control-at-harvest.md) | v4 | Admission Control at Harvest | Refusing to index documentation that does not match the code it ships with — the one enforcement point the harvester owns, and what gating costs retrieval |
| [0022](RAD-0022-the-value-of-transitive-capabilities.md) | v2 | The Value of Transitive Capabilities | The exclusion rule is settled; its price was never measured — what recall costs, whether declared is *all* a codex needs, and a third argument for the rule that needs no adversary |
| [0023](RAD-0023-deterministic-harness-or-harvested-knowledge.md) | v2 | A Deterministic Harness, or Harvested Knowledge | The fork this project walked past: judgement harvested and handed to the model, or encoded in a harness that constrains it — and what primitives that would need |
| [0024](RAD-0024-does-the-pipeline-filter-injection.md) | v2 | Does the Pipeline Itself Filter Injection? | Chunking, summarising and retrieval each disturb a coherent instruction — how much of the defence is already free, and does summarising help or add a new hazard |
| [0025](RAD-0025-the-summariser-as-attack-surface.md) | v3 | The Summariser as an Attack Surface | An LLM that reads attacker-controlled docs and writes durable corpus content — pass-through, mis-description, trigger poisoning, hijack |
| [0026](RAD-0026-meaning-without-command.md) | v2 | Can a Representation Carry Meaning Without Carrying a Command? | Representations in which an imperative cannot be expressed — and whether the retrieval key and the displayed entry need to be the same artifact |
| [0027](RAD-0027-the-identifier-as-a-free-text-channel.md) | v4 | The Identifier as a Free-Text Channel | Whether the structure tier really is free of attacker text — measured: prose rides a method name through `javap` verbatim, and a camel-cased imperative needs no escape in any language |
| [0028](RAD-0028-sast-tooling-as-a-detection-layer.md) | v2 | SAST Tooling as a Detection Layer | Whether the JVM's security analysers detect prose that persuades a reader — licensing checked, and the category mismatch that makes Semgrep the only candidate |
| [0029](RAD-0029-the-agent-as-a-trust-launderer.md) | v2 | The Agent as a Trust Launderer | An agent wrote an injected instruction into its own doc comment, promoting a third-party payload to first-party trusted source — the lattice running backwards |
| [0030](RAD-0030-a-conventions-filter-from-real-corpora.md) | v2 | A Conventions Filter Derived from Real Corpora | Measuring what identifiers actually look like per language, and using those limits as a surface reduction rather than a detector |
| [0031](RAD-0031-which-vectors-reach-a-real-project.md) | v1 | Which Attack Vectors Actually Reach a Real Project? | Most published attacks carry a precondition; 46% need none, and those are integrity corruption rather than exfiltration |
| [0032](RAD-0032-can-standing-instructions-override-injection.md) | v1 | Can Standing Instructions Override Injection That Gets Through? | Whether a consumer's own rules file beats an injected instruction, and whether it helps most where models are weakest |
| [0033](RAD-0033-do-form-constraints-compose.md) | v1 | Do Form Constraints Compose Where Detectors Do Not? | Detectors nest and add nothing when stacked; two form constraints closed each other's escape — is that structural or coincidence? |
| [0034](RAD-0034-better-linters-or-better-configuration.md) | v1 | Better Linters, or Better Configuration? | Swift's gap is a default-config gap — all four ecosystems enforce the measured bounds by configuration, and the configured rule catches the payload stock linters miss |
| [0035](RAD-0035-a-small-local-model-for-the-prose-gap.md) | v1 | A Small Local Model for the Prose Gap | The volume objection is wrong and the register one is not — plus the JVM embedding runtime the Lucene port needs anyway |
| [0036](RAD-0036-can-the-corpus-be-poisoned.md) | v1 | Can the Training Corpus Be Poisoned? | Central keeps everything, so the harvest is pinnable and driftable — but three publishers is too narrow a definition of normal |
| [0037](RAD-0037-unresolved-tensions.md) | v1 | Unresolved Tensions in the Design | Where the project's own findings contradict each other — the summariser answers instructions but not fabrications, and the users who gain most are the most exposed |
| [0038](RAD-0038-an-external-model-review-of-the-corpus.md) | v1 | An External Model Review of the Corpus | What a structured model read is good for — joining records written apart — and why it is a consistency check rather than external scrutiny |
| [0039](RAD-0039-where-the-dependency-graph-comes-from.md) | v1 | Where the Dependency Graph Comes From | We already extract the graph and discard the edges — what they would buy, and why an SBOM beats running someone else's build |
| [0040](RAD-0040-does-summarising-improve-retrieval.md) | v1 | Does Summarising Actually Improve Retrieval? | The 29%-to-77% gap was hand-written and does not reproduce — the rewriter is neutral and the signature-only fallback is what costs |
| [0041](RAD-0041-deduplication-under-an-incremental-store.md) | v1 | Deduplication Under an Incremental, Scoped Store | Dedup at harvest loses entries once queries are scoped — an entry dropped for a sibling target is invisible to a project that depends only on the sibling |
| [0042](studies/RAD-0042-thirteen-slug-functions.md) | v1 | Thirteen Slug Functions | A solved problem re-solved repeatedly, because the existing solution was undiscoverable from where the work happened |
| [0043](studies/RAD-0043-the-datetime-instant-move.md) | v1 | The Datetime `Instant` That Moved | A model's prior is a popularity-weighted average over time, and a fresh library move invalidates it |
| [0044](studies/RAD-0044-the-legacy-library-everyone-remembers.md) | v1 | The Legacy Library Everyone Remembers | An agent picks the library its prior knows best, not the one the project should use — and half of "which one" is a gap nothing can close |
| [0045](studies/RAD-0045-the-dependency-nobody-declared.md) | v1 | The Dependency Nobody Declared | Most of what compiles was never declared, and an agent cannot tell the difference |
| [0046](postmortems/RAD-0046-v1-bundled-flat-files.md) | v1 | v1, a Convention Nothing Could Read | The first attempt, shipped and superseded — what no longer applies, and why the failure is worth keeping inspectable |

Numbering is sequential, runs across the sub-groups, and does not imply order
of reading. 0001 and 0002 carry the measurements the rest lean on.

## Sub-groups

Two kinds of research have enough records to be worth grouping. Both are
research, so both continue the numbering above rather than restarting.

- [Studies](studies/README.md) — worked examples of the failure this project
  fixes, traced in real codebases (0042–0045).
- [Postmortems](postmortems/README.md) — approaches that shipped and then
  failed, written up by the people who shipped them (0046).

[Canon](../documents/DOC-0001-canon.md) sits beside the index: the generation each finding belongs
to, and the corrections where later work overturned an inference the narrative
still contains.

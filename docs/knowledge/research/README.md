# Research

Investigations: the question that prompted the work, the trail of options
weighed and dead ends ruled out, the findings — measured kept separate from
assumed — and a recommendation. A proof of concept in `experiments/` that settles a
question is written up here too.

Research sits upstream of a decision. It explores, weighs, and recommends;
it does not commit. A recommendation that hardens into a commitment
graduates to an ADR, cross-linked both ways.

## Index

| | | |
|---|---|---|
| [0001](0001-cost-of-a-skill-per-dependency.md) | Cost of a Skill Per Dependency | What one skill per library costs, measured across twelve public projects in four ecosystems |
| [0002](0002-existing-documentation-systems-as-skill-transport.md) | Existing Documentation Systems as Skill Transport | Whether a carrier (`-sources.jar`, source on disk, the git coordinate) already travels with the library — the *get* question |
| [0003](0003-central-capability-server.md) | A Central Capability Server for Library Discovery | A query front-end over the corpus, local and central |
| [0004](0004-external-review-of-the-proposal.md) | External Review of the Publishing Proposal | Five objections from outside the project, and what each changed |
| [0005](0005-a-git-hosted-codex.md) | A Git-Hosted Codex | Reaching library guidance through the repository the publishing metadata already names |
| [0006](0006-development-time-prompt-injection.md) | Development-Time Prompt Injection | The injection surface, the measured model×payload×arm matrix (+ system-channel, tool-action, aggressive payloads) and a model trust table — data-framing is necessary but beaten by system-channel placement and meta-arguments |
| [0007](0007-choosing-between-overlapping-libraries.md) | Choosing Between Overlapping Libraries | Which signals discriminate when several libraries do the same job |
| [0008](0008-the-field-as-it-stands.md) | The Field as It Stands, and What This Research Rejects | What others have built, where it corroborates this work, and where each pattern's limits are |
| [0009](0009-reusing-indexers-and-what-to-index.md) | Parsing the Documentation, and What to Index | The parse layer — tree-sitter/Dokka per ecosystem behind a common contract, the summarise step as the new work, and whether to index the whole graph or only libraries that ship a skill |
| [0010](0010-how-the-codex-is-stored-and-served.md) | How the Codex Is Stored and Served | Text source of truth, a derived Lucene hybrid index at scale, and MCP as the interface — plus fixing the codex/librarian vocabulary |
| [0011](0011-existing-documentation-systems-as-skill-content.md) | Existing Documentation Systems as Skill Content | Whether KDoc/DocC/godoc/docstrings are rich enough to be skill content, how much exists, and designed-vs-discovered — split from RAD-0002 |
| [0012](0012-structure-from-bytecode.md) | Structure from Bytecode | Parsing the classes.jar for the API surface — deferred, held as an option for the undocumented tail and later capabilities |
| [0013](0013-the-codex-entry.md) | The Codex Entry | What an entry is: a two-faced (semantic + syntactic) per-capability record, retrieved hybrid, with Lucene as the index candidate |
| [0014](0014-build-vs-reuse.md) | Build vs Reuse: the Codex Pipeline | Per-layer reuse-or-build across the pipeline; assemble from Lucene vs adopt an end-to-end pipeline; Glean as template, Mahout rejected |
| [0015](0015-how-the-source-is-read.md) | How the Source Is Read | The read stage between get and parse — extract-all vs read-in-place vs selective, the IntelliJ lazy-VFS precedent, a read layer over archived and loose source, first-party source as a first-class input |
| [0016](0016-the-content-value-ab.md) | The Content-Value A/B | The thesis test — does the codex change what an agent does; synthetic subject, content-value-first, first-party-vs-dependency scenarios, a cross-model matrix run through developer tools |
| [0017](0017-the-retrieval-disambiguation-ab.md) | The Retrieval / Disambiguation A/B | Disambiguation *within a presented catalogue* — the syntactic face for differing signatures, the semantic face required when signatures collide |
| [0018](0018-the-selection-ab.md) | The Selection A/B | The measurement of RAD-0007 — unaided 0 of 18, the declared dependency tree redirects a single-choice classpath, only an authored preference resolves genuine ambiguity |
| [0019](0019-retrieval-at-scale.md) | Retrieval at Scale | Layer 1 index recall (vector-primary vs equal-RRF, BGE-M3) and Layer 2 the agent loop — authoring a query beats verbatim recall (10 of 10 pilot) |
| [0020](0020-information-flow-control.md) | Information-Flow Control as the Trust Model | Adopting IFC (integrity/confidentiality labels enforced before a sink) instead of positional discipline — the codex as label *source*, and the sink experiment that would settle it |
| [0021](0021-admission-control-at-harvest.md) | Admission Control at Harvest | Refusing to index documentation that does not match the code it ships with — the one enforcement point the harvester owns, and what gating costs retrieval |
| [0022](0022-the-value-of-transitive-capabilities.md) | The Value of Transitive Capabilities | The exclusion rule is settled; its price was never measured — what recall costs, whether declared is *all* a codex needs, and a third argument for the rule that needs no adversary |
| [0023](0023-deterministic-harness-or-harvested-knowledge.md) | A Deterministic Harness, or Harvested Knowledge | The fork this project walked past: judgement harvested and handed to the model, or encoded in a harness that constrains it — and what primitives that would need |
| [0024](0024-does-the-pipeline-filter-injection.md) | Does the Pipeline Itself Filter Injection? | Chunking, summarising and retrieval each disturb a coherent instruction — how much of the defence is already free, and does summarising help or add a new hazard |
| [0025](0025-the-summariser-as-attack-surface.md) | The Summariser as an Attack Surface | An LLM that reads attacker-controlled docs and writes durable corpus content — pass-through, mis-description, trigger poisoning, hijack |
| [0026](0026-meaning-without-command.md) | Can a Representation Carry Meaning Without Carrying a Command? | Representations in which an imperative cannot be expressed — and whether the retrieval key and the displayed entry need to be the same artifact |
| [0027](0027-the-identifier-as-a-free-text-channel.md) | The Identifier as a Free-Text Channel | Whether the structure tier really is free of attacker text — measured: prose rides a method name through `javap` verbatim, and a camel-cased imperative needs no escape in any language |
| [0028](0028-sast-tooling-as-a-detection-layer.md) | SAST Tooling as a Detection Layer | Whether the JVM's security analysers detect prose that persuades a reader — licensing checked, and the category mismatch that makes Semgrep the only candidate |
| [0029](0029-the-agent-as-a-trust-launderer.md) | The Agent as a Trust Launderer | An agent wrote an injected instruction into its own doc comment, promoting a third-party payload to first-party trusted source — the lattice running backwards |
| [0030](0030-a-conventions-filter-from-real-corpora.md) | A Conventions Filter Derived from Real Corpora | Measuring what identifiers actually look like per language, and using those limits as a surface reduction rather than a detector |
| [0031](0031-which-vectors-reach-a-real-project.md) | Which Attack Vectors Actually Reach a Real Project? | Most published attacks carry a precondition; 46% need none, and those are integrity corruption rather than exfiltration |
| [0032](0032-can-standing-instructions-override-injection.md) | Can Standing Instructions Override Injection That Gets Through? | Whether a consumer's own rules file beats an injected instruction, and whether it helps most where models are weakest |
| [0033](0033-do-form-constraints-compose.md) | Do Form Constraints Compose Where Detectors Do Not? | Detectors nest and add nothing when stacked; two form constraints closed each other's escape — is that structural or coincidence? |
| [0034](0034-better-linters-or-better-configuration.md) | Better Linters, or Better Configuration? | Swift's gap is a default-config gap — all four ecosystems enforce the measured bounds by configuration, and the configured rule catches the payload stock linters miss |
| [0035](0035-a-small-local-model-for-the-prose-gap.md) | A Small Local Model for the Prose Gap | The volume objection is wrong and the register one is not — plus the JVM embedding runtime the Lucene port needs anyway |
| [0036](0036-can-the-corpus-be-poisoned.md) | Can the Training Corpus Be Poisoned? | Central keeps everything, so the harvest is pinnable and driftable — but three publishers is too narrow a definition of normal |
| [0037](0037-unresolved-tensions.md) | Unresolved Tensions in the Design | Where the project's own findings contradict each other — the summariser answers instructions but not fabrications, and the users who gain most are the most exposed |
| [0038](0038-an-external-model-review-of-the-corpus.md) | An External Model Review of the Corpus | What a structured model read is good for — joining records written apart — and why it is a consistency check rather than external scrutiny |
| [0039](0039-where-the-dependency-graph-comes-from.md) | Where the Dependency Graph Comes From | We already extract the graph and discard the edges — what they would buy, and why an SBOM beats running someone else's build |

Numbering is sequential and does not imply order of reading. 0001 and 0002
carry the measurements the rest lean on.

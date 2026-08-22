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
| [0006](0006-development-time-prompt-injection.md) | Development-Time Prompt Injection | The one objection with no answer yet, and the experiment that would settle it |
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
| [0018](0018-the-selection-ab.md) | The Selection A/B | The measurement of RAD-0007 — unaided 0/18, the declared dependency tree redirects a single-choice classpath, only an authored preference resolves genuine ambiguity |
| [0019](0019-retrieval-at-scale.md) | Retrieval at Scale (Layer 1: index recall) | Pure index recall over hundreds of entries, no agent — vector-primary vs equal-RRF hybrid, the BGE-M3 encoder |

Numbering is sequential and does not imply order of reading. 0001 and 0002
carry the measurements the rest lean on.

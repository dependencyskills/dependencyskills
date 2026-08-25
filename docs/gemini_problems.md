# Critical Analysis of Problems and Tensions in the Knowledge Canon

This document details the unresolved structural contradictions, security blind spots, and architectural tensions identified in the conclusions of the `docs/knowledge/` research corpus.

---

## 1. The Core Paradox: Retrieval Requires What Security Must Forbid

### The Tension
* **Retrieval Requirement**: [RAD-0019](knowledge/research/0019-retrieval-at-scale.md) and [RAD-0024](knowledge/research/0024-does-the-pipeline-filter-injection.md) establish that high retrieval recall (**77% recall@1** with BGE-M3) depends fundamentally on **rich, natural-language semantic capability descriptions** authored in the caller’s vocabulary. When indexing raw doc comments or bare syntactic signatures, retrieval drops to **29% or 0%**.
* **Security Threat**: [RAD-0006](knowledge/research/0006-development-time-prompt-injection.md), [RAD-0025](knowledge/research/0025-the-summariser-as-attack-surface.md), and [RAD-0027](knowledge/research/0027-the-identifier-as-a-free-text-channel.md) demonstrate that natural language prose is the single unconstrained channel for prompt injection, trigger poisoning, and upward trust laundering.

### The Problem
* **Unsanitizable Channel**: Form constraints successfully sanitize identifier grammar ([RAD-0033](knowledge/research/0033-do-form-constraints-compose.md)), but cannot sanitize natural language prose without destroying the semantic density required for vector embeddings to match user queries.
* **Failure of Quarantined Summarisation**: While isolated summarisation filters downstream imperative commands ([RAD-0024](knowledge/research/0024-does-the-pipeline-filter-injection.md)), [RAD-0025](knowledge/research/0025-the-summariser-as-attack-surface.md) v3 proves it is completely vulnerable to **trigger poisoning and fabricated capabilities** (where a malicious library authors valid-sounding capability prose that outranks legitimate competitors in vector retrieval).
* **Conclusion**: The codex's retrieval system and its security threat model are at war over the same field.

---

## 2. The Transitive Dependency Exclusion Dilemma

### The Tension
* **Ecosystem Reality**: [RAD-0001](knowledge/research/0001-cost-of-a-skill-per-dependency.md) and [RAD-0002](knowledge/research/0002-existing-documentation-systems-as-skill-transport.md) show that **86% to 99% of importable libraries on the JVM are transitive dependencies** (e.g. 102 declared vs. 311 importable in Now in Android; 16 declared vs. 112 importable in Spring PetClinic). In flat package managers (npm, pip), transitives make up 100% of reachable packages.
* **Security Recommendation**: [RAD-0004](knowledge/research/0004-external-review-of-the-proposal.md) and [RAD-0006](knowledge/research/0006-development-time-prompt-injection.md) recommend **excluding transitive dependencies by default** to reduce the untrusted prose ingestion surface by an order of magnitude (~10×).

### The Problem
* **Reinvention Returns**: If transitive dependencies are excluded by default, the codex hides 86%–99% of the libraries a developer or agent actually calls in code. The agent will proceed to hand-roll custom implementations of standard transitive utilities (e.g., `kotlinx.coroutines.sync.Mutex`, Jackson databind modules, Apache commons helpers), reintroducing the core *reinvention failure* the project was founded to solve ([postmortems/v1](knowledge/postmortems/v1-bundled-flat-files.md)).
* **Supply-Chain Exposure**: If transitive dependencies are admitted, hundreds of unreviewed packages enter the prompt and index pipeline, amplifying the attack surface.

---

## 3. Human Curation Scalability vs. Manual Local Preference Maintenance

### The Tension
* **Rejection of Manual Curation**: The entire transition from v1/v2 to the v3 harvested codex ([ADR-0004](knowledge/adr/0004-librarian-and-codex.md), [postmortems/v1](knowledge/postmortems/v1-bundled-flat-files.md)) was justified by the fact that **human curation does not scale to real dependency graphs** and leads to abandoned documentation.
* **Dependence on Hand-Authored Preferences**: [RAD-0018](knowledge/research/0018-the-selection-ab.md) identifies **selection** (choosing between overlapping libraries on an ambiguous classpath) as the single most durable, scale-proof value of the codex—and proves it **depends entirely on hand-authored consumer preferences (`codex/local.md`)**.

### The Problem
* **The Fragility of Local Knowledge**: Unaided models resolve ambiguous classpaths correctly only **3/18 times** ([RAD-0018](knowledge/research/0018-the-selection-ab.md)). Without actively maintained `codex/local.md` files, selection degrades to pre-training model habits.
* **The Maintenance Gap**: The project solves the third-party library documentation burden via automated harvesting, but shifts the selection burden back onto manual human authoring at the application repository level.

---

## 4. The Blind Spot for "Output & Logic Tampering"

### The Tension
* **Focus on Exfiltration**: The defensive design ([RAD-0006](knowledge/research/0006-development-time-prompt-injection.md), [RAD-0020](knowledge/research/0020-information-flow-control.md)) focuses primarily on preventing credential theft (`.env` staging) and unauthorized filesystem sinks via Information-Flow Control (IFC) and tool-permission gates.
* **Prevalence of Precondition-Free Attacks**: [RAD-0031](knowledge/research/0031-which-vectors-reach-a-real-project.md) reveals that credential exfiltration represents only **4%** of benchmark attacks, whereas **46% require zero preconditions and perform silent logic or data tampering** (e.g. reducing financial calculations by 10%, generating flawed validation code, subtly modifying business logic).

### The Problem
* **Sink Ineffectiveness**: Writing a subtly incorrect calculation or omitting a safety check in generated code is a standard text edit, not a privileged tool sink (like an AWS credential read or external network call).
* **Blindness to Corruption**: Information-Flow Control (IFC) and tool sandboxes cannot prevent an agent from writing compromised source code into a legitimate application file.

---

## 5. The Inverted Utility vs. Safety Profile

### The Tension
* **Utility Distribution**: [RAD-0016](knowledge/research/0016-the-content-value-ab.md) demonstrates that current frontier models exhibit **zero lift** on public libraries, confining the codex's content value to **local open-weight models (270M–70B)** and private internal APIs.
* **Vulnerability Distribution**: [RAD-0006](knowledge/research/0006-development-time-prompt-injection.md) demonstrates that local instruction-tuned coding models (such as Qwen3-Coder-30B) are the **most vulnerable to prompt injection (8/9 compliance)** and completely ignore prompt-level data framing.

### The Problem
* **Risk/Reward Mismatch**: The developers who benefit most from the codex (teams running local or self-hosted coding models to avoid API costs or privacy leaks) are exactly the developers whose agents are most easily compromised by malicious third-party documentation.

---

## 6. Polyglot Graph Join Feasibility Outside the JVM

### The Tension
* **Index-Side Resolution**: [RAD-0009](knowledge/research/0009-reusing-indexers-and-what-to-index.md) and [RAD-0012](knowledge/research/0012-structure-from-bytecode.md) conclude that documentation inheritance can be offloaded from the parse stage to **transitive graph joins in the index**.
* **JVM Validation**: This was validated on Kotlin and JVM bytecode (`javap`), where compiled `.class` files provide fully-qualified supertypes and symbol tables.

### The Problem
* **Dynamic & Complex Language Boundaries**: In dynamically-typed ecosystems (Python, JavaScript) or complex type systems without compiled bytecode metadata (TypeScript generics, Rust macro-expanded traits), static Tree-sitter parsing cannot reliably link inherited types or supertypes across package boundaries without invoking full language servers / compilers (`tsc`, `pyright`, `rustc`).
* **Graph Incompleteness**: Without language-specific semantic resolvers, index-side graph joins in non-JVM ecosystems will have missing inheritance edges.

---

## 7. Training Distribution Skew in Prose Classifiers

### The Tension
* **Classifier Proposal**: [RAD-0035](knowledge/research/0035-a-small-local-model-for-the-prose-gap.md) proposes training a small local model or linear probe over sentence embeddings to detect adversarial prose in library documentation.
* **Corpus Skew**: [RAD-0036](knowledge/research/0036-can-the-corpus-be-poisoned.md) reports that the negative (clean) training corpus harvested from real dependency graphs is **77.7% dominated by 10 libraries across only 3 publishers** (primarily JetBrains and Ktor).

### The Problem
* **Register & Authorship Overfitting**: A classifier trained on this corpus will separate on *publisher house style* (JetBrains KDoc conventions) rather than *adversarial intent*. It will generate high false-positive rates on authentic documentation from independent open-source authors while remaining blind to subtle adversarial preconditions written in standard library voice.

---

## Summary Matrix of Unresolved Tensions

| # | Conflict Area | Upstream Finding | Downstream Collision | Systemic Consequence |
|---|---|---|---|---|
| **1** | **Prose Vector** | Vector retrieval requires natural semantic prose ([RAD-0019](knowledge/research/0019-retrieval-at-scale.md)) | Natural prose carries injection and trigger poisoning ([RAD-0025](knowledge/research/0025-the-summariser-as-attack-surface.md)) | Retrieval efficacy and injection resistance are mutually opposed |
| **2** | **Transitive Scope** | 86%–99% of importable APIs are transitive ([RAD-0001](knowledge/research/0001-cost-of-a-skill-per-dependency.md)) | Transitive tail must be excluded for security ([RAD-0006](knowledge/research/0006-development-time-prompt-injection.md)) | Choice between transitive reinvention vs. 10× attack surface |
| **3** | **Selection Governance** | Manual skill curation is unmaintainable ([ADR-0004](knowledge/adr/0004-librarian-and-codex.md)) | Selection requires manual `local.md` authoring ([RAD-0018](knowledge/research/0018-the-selection-ab.md)) | Selection fails (3/18) without human curation |
| **4** | **Tampering Blind Spot** | IFC stops exfiltration at sensitive sinks ([RAD-0020](knowledge/research/0020-information-flow-control.md)) | 46% of attacks corrupt code logic silently ([RAD-0031](knowledge/research/0031-which-vectors-reach-a-real-project.md)) | IFC is blind to malicious logic generation |
| **5** | **Safety vs Utility** | Content value is highest for local models ([RAD-0016](knowledge/research/0016-the-content-value-ab.md)) | Local models are most injectable (8/9) ([RAD-0006](knowledge/research/0006-development-time-prompt-injection.md)) | Highest-value users are also the most vulnerable |
| **6** | **Polyglot Graph Joins** | Graph joins replace compiler-based Dokka ([RAD-0009](knowledge/research/0009-reusing-indexers-and-what-to-index.md)) | Non-JVM languages lack compiled metadata | AST parsing alone cannot resolve dynamic inheritance |
| **7** | **Classifier Skew** | Linear probe proposed for prose filtering ([RAD-0035](knowledge/research/0035-a-small-local-model-for-the-prose-gap.md)) | Corpus is 77.7% JetBrains/Ktor style ([RAD-0036](knowledge/research/0036-can-the-corpus-be-poisoned.md)) | Classifiers overfit on publisher style |

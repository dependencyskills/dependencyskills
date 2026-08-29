# Installed Rather Than Resolved

RAD-0056 · 2026-08-29
Keywords: should the codex be installed instead of resolved as a dependency; can we publish to Homebrew; brew tap versus homebrew-core; how does a developer get the multilingual model; why not download a model during a build; does a resident service pay for model load; winget and scoop for Windows; what an installer changes about the Gradle plugin; where the MCP server runs.
Measured against: `libdscodex` over llama.cpp b19cbe9, `bge-small-en-v1.5` F16 GGUF, OpenJDK 26, macOS arm64, 2026-08-29. Everything else here is argument or read from documentation, and is marked as such.

## Question

> **Should the codex be something a developer installs, rather than something a build resolves — and if so, what does that change about how the models, the store and the server reach them?**

[ADR-0013](../decisions/ADR-0013-how-a-model-reaches-a-developer.md) settled this two hours before the question was asked, and one arm of it broke on contact with a concrete case. This is not a repair of that decision; it is the larger question the decision turned out to be sitting inside.

## Trail

### What broke

ADR-0013 has two arms. The default encoder travels in a jar — that arm holds. Anything too large to publish is **installed by the developer** at a documented path, and `ModelLocation` prints instructions naming the exact file and directory.

The concrete case is `bge-m3`, the multilingual encoder, at 2,267 MB. And **nobody publishes a first-party GGUF of it**, any more than they do of `bge-small-en-v1.5`. So the instruction that arm produces is not "download this file and put it here". It is "install Python, PyTorch and a llama.cpp checkout, convert 2.3 GB yourself, then put the result here."

That is not an instruction, and no amount of wording fixes it. Somebody has to produce the file.

### The objection to downloading was never the download

The reason ADR-0013 refused to fetch a model was that a build which quietly pulls 2.3 GB the first time it runs has done something surprising — and doing it silently is worse than not doing it.

Every word of that is about the **moment**, not the act. A build is the wrong moment: it is unattended, it happens in CI, it happens behind corporate firewalls, and the person who triggered it did not ask for a model. An **install** is the right moment: it is deliberate, attended, consented to, visible, and happens once.

So an installer does not work around the objection. It removes the thing being objected to.

### What is already true, and points the same way

- **The store is already machine-level.** [ADR-0012](../decisions/ADR-0012-a-shared-machine-level-index-store.md) puts it at `~/.gradle/dscodex/`, keyed by coordinate, so a library is indexed once per machine rather than once per project. A machine-level *install* is coherent with that rather than bolted onto it.
- **The native targets already exist for this.** `inference` is Kotlin Multiplatform with `macosArm64`, `macosX64`, `linuxX64`, `linuxArm64` and `mingwX64`, each linking llama.cpp statically into the binary. That was built so a native CLI or server would be possible rather than theoretical, and this is the thing it was possible *for*.
- **[#8](https://github.com/dependencyskills/dependencyskills/issues/8) has to run somewhere.** An MCP server is a process. Either something installs it, or a build starts it, or an agent spawns it — and that choice is upstream of this one rather than downstream.

### The resident-service argument, and why it does not survive

The obvious case for an installed service is that it loads the model once per machine instead of once per build. Measured, for the encoder, it does not:

| | |
|---|---|
| `dsc_encoder_load`, first call | 32 ms |
| `dsc_encoder_load`, warm | 12 ms |
| one embedding, real 900-char doc text | 11 ms |
| **so loading costs** | **about ten embeddings** |

Twelve milliseconds is not worth packaging for. The argument survives only for the **generator**, where the model is 4–20× larger and [RAD-0051](RAD-0051-a-jvm-generative-runtime.md)'s 25-minutes-against-21-hours was almost entirely the cost of paying model load per entry.

**So an installer must be justified on distribution and platform fit, not on warm state.** That is worth saying plainly because warm state is the argument that sounds most technical and would have been the easiest to assert without checking.

### Mechanism: a package manager is the installer

Three shapes, and the middle one absorbs most of the cost of the first.

**A hand-rolled installer** — `.pkg`, `.msi`, `.deb` — means code signing and notarization, a self-update mechanism, and an uninstall story. The update lifecycle is the expensive part and it is expensive forever.

**A package manager** — Homebrew, winget, scoop — is an installer that already exists. Read from Homebrew's documentation rather than measured:

- A **tap** (`brew tap <owner>/tap`) needs no notability review; `homebrew-core` does, and this project does not have it yet. A tap costs a repository.
- A formula can point straight at a release tarball with a `sha256` — the same fetch-and-verify shape the `encoder` build already implements and tests.
- `brew upgrade` is the update lifecycle, and it is free.
- Notarization bites for `.app` bundles and `.pkg` installers, not for a CLI binary installed this way.
- One formula covers **macOS and Linux**. It does not cover **Windows**, which needs winget or scoop — a second packaging path over an artifact that already builds (`mingwX64`).

**Staying with dependency resolution** is the status quo and remains correct for the small encoder. It is not capable of the large one, which is what started this.

### Where HuggingFace fits, and it is needed either way

Whoever converts `bge-m3` has to publish the result somewhere, under every option above. Homebrew will not carry 2.3 GB; nor will Maven Central.

HuggingFace is the natural host — free, CDN-backed, LFS, purpose-built for weights, and where a developer already looks. The conversion is **byte-reproducible** ([RAD-0054](RAD-0054-one-runtime-for-both-faces.md): two independent runs, identical SHA-256), so a model card can state the exact recipe and anyone can re-derive and check the upload. That is a stronger provenance claim than the one this project currently relies on, since nobody can reproduce BAAI's own ONNX export either.

This is independent of the installer question and should not wait for it.

### The costs, stated before the recommendation

- **Adoption friction.** "Add a plugin" becomes "install a thing, then add a plugin". That is a real barrier and the project has no evidence about how large.
- **Windows is a second path**, always, whatever is chosen for macOS and Linux.
- **Two distribution routes can drift.** A Central `encoder` artifact and a bundled-in-the-package model are two copies of one decision, and the version skew between them is a failure nobody would notice quickly.
- **An installed thing is a thing you maintain.** Not the operational burden of running a service, but a lifecycle nonetheless.

## Findings

**Measured.** One thing, and it removed an argument rather than supporting one: loading the encoder costs 12–32 ms, about ten embeddings' worth, so a resident service cannot be justified by encoder warm state.

**Established by reading, not measured.** Homebrew's tap-versus-core rules, its release-tarball formula shape, and its lack of Windows support. That `bge-m3` has no first-party GGUF, as `bge-small-en-v1.5` has none.

**Established by argument.** That the objection to downloading a model is an objection to the moment rather than the act, and that an install is a moment where the objection does not apply.

**Assumed, and load-bearing.** That adoption friction from an install step is tolerable for this audience. Nothing here measures it, and it is the assumption most likely to be wrong — a Gradle plugin that works from dependency resolution alone has a materially lower barrier than one that does not.

## Recommendation

**Do not decide the installer yet. Decide [#8](https://github.com/dependencyskills/dependencyskills/issues/8) first, because it contains this question.** If the MCP server is a process a developer installs and runs, the installer follows and packaging is a detail. If it is started on demand by a build or an agent, the installer is a convenience and the case for it is much weaker. Settling packaging before that is deciding the small question first.

**Publish the converted weights to HuggingFace regardless.** Every option needs somebody to have produced a GGUF, and this is true under all of them. Pin the converter commit and the input digests, publish the recipe in the model card, and add a test that re-derives and compares — a reproducibility claim nothing verifies has the same shape as a verifier that passes everything, which this project has shipped twice.

**If it is installed, then: a Homebrew tap first**, macOS and Linux from one formula, winget or scoop for Windows as a separate step, the small encoder inside the release tarball, and the large models an explicit second command rather than an implicit fetch. Not `homebrew-core` until there is a release history to point at.

**Then amend [ADR-0013](../decisions/ADR-0013-how-a-model-reaches-a-developer.md)**, which currently says the default encoder travels in a jar. Its core is untouched — nothing downloads a model during a build, large models are obtained deliberately — and an installer makes that reasoning stronger rather than weaker. What changes is the vehicle, and that is an amendment rather than a supersession.

**What would change the answer.** Evidence that an install step costs adoption — the assumption above, unmeasured. A decision that #8 is not a resident process, which removes most of the reason to install anything. Or a measurement showing the generator's load cost does not amortise the way RAD-0051 implies, which would take the resident-service argument away entirely rather than leaving it to the larger model.

## Connections

- [ADR-0013](../decisions/ADR-0013-how-a-model-reaches-a-developer.md) — the decision this would amend, and the arm of it that broke
- [ADR-0012](../decisions/ADR-0012-a-shared-machine-level-index-store.md) — the machine-level store an install would sit beside
- [RAD-0054](RAD-0054-one-runtime-for-both-faces.md) — the reproducible conversion this rests on
- [RAD-0051](RAD-0051-a-jvm-generative-runtime.md) — where model-load cost actually bites
- [RAD-0052](RAD-0052-distributing-a-precomputed-codex.md) — the other route to not needing a local model, which does not remove the encoder
- [#17](https://github.com/dependencyskills/dependencyskills/issues/17) — the multilingual work this unblocks the mechanism for

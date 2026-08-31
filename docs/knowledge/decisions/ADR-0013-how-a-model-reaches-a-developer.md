# A model reaches a developer in a jar, or they install it themselves

ADR-0013 · 2026-08-29 · Status: accepted · v1
Keywords: how does the encoder reach a developer; do we host the model; should we run our own Maven repository; why not download the model during a build; where do I put bge-m3; bring your own model; GGUF conversion provenance; is the conversion reproducible; why the default encoder is small.

## Context

The codex needs a local encoder. Not only at harvest — **a query is the developer's own words, composed at query time, and it has to be embedded in the same basis as the index to search it at all.** So unlike the summariser, the encoder cannot be removed from a developer's machine by publishing a pre-computed codex ([RAD-0052](../research/RAD-0052-distributing-a-precomputed-codex.md)); that direction removes the *generator* from the common case and leaves this one exactly where it was.

[RAD-0054](../research/RAD-0054-one-runtime-for-both-faces.md) then recommended moving the encoder from DJL over ONNX Runtime to the in-process llama.cpp library already built for the summariser, which avoids roughly 160 MB of jars and a second per-platform native — avoids rather than removes, since the shipped build never took the DJL path and the index module was written straight onto llama.cpp. llama.cpp reads GGUF, and **nobody publishes a first-party GGUF of the chosen model** — which reopened a question the encoder module had already answered once, in the other direction: it took the fp32 ONNX export over a smaller variant specifically because the alternative "would have meant redistributing a community re-quantisation instead of the authors' own export."

Two questions therefore had to be settled together: what we are willing to redistribute, and how anything too large to redistribute reaches a machine.

## Decision

**The default encoder travels in a jar, and is chosen small enough that it can.** `bge-small-en-v1.5`, converted to F16 GGUF, packaged in the `encoder` artifact and resolved like any other dependency. No consumer build contacts an external host; it never did — the fetch in `encoder/build.gradle.kts` is *our* build's, not theirs.

**We will redistribute a conversion of third-party weights, because the conversion is reproducible.** Running `convert_hf_to_gguf.py` twice over the same pinned inputs produces byte-identical output (verified: `86776c71a9890f…` both times). So the artifact is a pure function of inputs anyone can check — BAAI's safetensors at a pinned digest, llama.cpp at a pinned commit — and the recipe is published with it. This is a **stronger** provenance claim than the one it replaces: nobody can reproduce BAAI's own ONNX export either, and for that we trust a digest and nothing else.

**Producing the encoder artifact is a release step, and must stop being part of `assemble`.** It changes when the model version changes and at no other time, so the cost of producing it belongs to whoever publishes it. Today `assemble` already depends on `encoderJar`, which means an ordinary `./gradlew build` downloads 133 MB from Hugging Face; converting to GGUF would add Python and torch to that. Both are a tax on contributors who are not shipping a model, and the second makes the first untenable. The conversion is reproducible, the recipe is published, and a clean clone builds everything else without it.

**Anything too large to publish is installed by the developer**, at `<store>/models/` — `~/.dscodex/models/` by default, inheriting the store's override chain and adding its own. `ModelLocation` resolves it, and when a model is absent it prints the exact file, the exact directory and both overrides.

**Nothing downloads a model during a build.** Not the default (it arrives as a dependency), and not an alternate (the developer installs it). A build that quietly pulls 2.3 GB the first time somebody runs it has done something surprising, and doing it silently is worse than not doing it at all.

**We do not run a repository or a download host.** Considered and declined: the default is small enough for Central, which is free, mirrored, and already reachable through every corporate proxy. Self-hosting would buy nothing for that case, and for the large case it would turn an afternoon's deployment into being the thing someone's CI depends on for years.

## Consequences

**A developer who needs a non-English encoder has manual work to do**, and that is accepted rather than regretted. The instructions are printed at the point of failure rather than only in a README, because somebody meeting this has not read the README and should not have to.

**Installing a model is not the same as being supported.** Language is not detected at harvest, and `TwoFacedIndex` enforces one (encoder, pooling, width) basis per index, so a mixed-language corpus cannot merge two encoders. [#17](https://github.com/dependencyskills/dependencyskills/issues/17) holds that work; this decision only ensures the mechanism is not what blocks it.

**The reproducibility claim is now a promise.** It must be pinned — converter commit and input digests — and checked by re-deriving and comparing, not asserted in prose. A provenance claim nothing verifies is the same shape as a verifier that passes everything, which this project has shipped twice.

**The size of the default encoder is now load-bearing.** It is not merely a quality trade-off measured in [RAD-0048](../research/RAD-0048-where-the-encoder-size-cutoff-is.md); it is the reason the common case needs no installation step at all. A future encoder that does not fit in a jar changes the shape of this decision, not just its parameters.

**If we ever do want to host large models**, a Maven repository of our own is the way — same resolution mechanism, no new client code, and corporate proxies mirror arbitrary Maven repositories as a matter of routine, which they do not do for a download URL. That is a reversal of the last point above and would need its own record.

## Alternatives considered

**Fetch a community GGUF.** Rejected on the same grounds the module rejected a community re-quantisation of the ONNX export. Nothing changed; it is only more tempting now.

**Convert during the Gradle build.** Correct and reproducible, but it puts Python and torch in the path of anyone who builds the repository.

**Run our own Maven repository for the large models.** Declined for now — see above. Not wrong, just a standing commitment bought before there is anyone to serve.

**Download on first use into the store directory.** The mechanism exists elsewhere; the Kotlin Multiplatform JS plugin downloads a whole Node runtime into `~/.gradle/nodejs`. Declined because the precedent cuts the other way once the file is gigabytes rather than megabytes, and because it dies behind a firewall in exactly the environments least able to debug it.

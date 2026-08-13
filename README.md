# Dependency Skills

Agent skills that travel with a library wherever it publishes — Maven, npm,
SPM — and an index so an agent can choose among hundreds.

> **Experimental.** The convention here is a **proposal**, not a settled
> standard. Artifact names, variant attributes and file layouts may all
> change in response to feedback. A published Maven artifact is permanent
> and cannot be withdrawn, so a library adopting this early is committing
> that version to a shape which may not survive. Publish to a local or
> private repository first, and take the first public outing on a
> pre-release version.

## The problem

Every agent-skill convention in circulation assumes the consumer can see
the files. `node_modules`, `Pods` and `site-packages` are all exploded onto
disk, so "scan for `SKILL.md`" works. Gradle and Maven never unpack — a
dependency is an archive in a cache — so the directory convention everyone
agreed on is structurally inapplicable to Android, Kotlin Multiplatform and
the JVM server ecosystem.

That is the visible problem, and it is the boring half.

The real problem is **selection**. An agent with three hundred
dependencies, a dozen of which could plausibly answer the question in front
of it, has to pick one. Loading every skill's description costs context
before any work begins, and the paths carry no meaning. Nobody has built
this part.

**The index is the product. The packaging is the boring half.**

## Layout

| Path | What it holds |
|---|---|
| `spec/` | The convention. Normative, versioned, the thing implementations agree on. |
| `implementations/` | Per ecosystem — publishing and harvesting. Headless, runs in a build. |
| `conformance/` | Runs an implementation against the fixtures. |
| `fixtures/` | Shared test material: sample skills, expected archives, malformed cases. |
| `docs/` | Decision records and the public proposal. |
| `poc/` | Spikes. Throwaway by design — see `docs/`. |

Each implementation has its own build; there is no root build. `conformance`
shells out to whichever implementations are present, which is also what a
third-party implementer needs to run.

Editor and agent integrations — VS Code, the JetBrains IDEs — belong in a
sibling `integrations/` directory, deliberately not created until there is
something to put in it. See [ADR-0005](docs/adr/0005-repository-structure.md)
for why they are separated rather than filed under `implementations/`.

## Contributing

Apache-2.0. Contributions are accepted under the
[Developer Certificate of Origin](https://developercertificate.org/) —
sign your commits with `git commit -s`.

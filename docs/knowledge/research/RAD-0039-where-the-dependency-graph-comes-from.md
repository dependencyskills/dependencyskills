# Where the Dependency Graph Comes From

RAD-0039 · 2026-08-25 · v1
Keywords: where does the dependency graph come from; we already extract it and discard the edges; Gradle resolutionResult; what the edges would fix; SBOM versus running someone else's build; three sources and the trade between them; why this is not urgent yet.

**Opened by noticing that we already have the graph and throw most of it away.**
`experiments/cost-model/scripts/collect-deps.init.gradle` calls Gradle's `resolutionResult` — which
*is* the dependency graph API — and flattens it to a set of coordinates with a direct/transitive
flag. The **edges** are available in the same object and are discarded.

That was the right call for what the script was for: `RAD-0001` needed counts, not structure. It is
the wrong call for what has been built since.

> **Where should the dependency graph come from, and what does keeping the edges buy?**

Not urgent. Recorded now because the reasoning is fresh and because it bears on an open measurement.

## Trail

### What the edges would fix, concretely

**`test14` resolves against a universe that is wrong in both directions.** Checking whether a
directive in library A names something real, it tries A's own declared names — 13.6% of directives
resolve — then falls back to **any of 1,688 harvested libraries**, which picks up another 5.1%.

Neither is A's actual surface. The correct universe is **A's own declarations plus the declarations
of A's real dependencies**. Too narrow and a legitimate reference to an inherited or upstream symbol
scores as an injection; too wide and an unrelated coincidence in a library A has never heard of
resolves happily. The published **1.73%** is measured against a universe that is simultaneously
both, and the direction of the error is not knowable without the edges.

This is the sharpened form of the concern raised while `test14` was being built — that a legitimate
directive naming an inherited or cross-library symbol would be counted as an injection.

**`RAD-0009`'s transitive graph join needs edges by definition.** Recovering inherited documentation
means knowing *which* library holds the supertype. `test1` measured that join as the cheaper
alternative to running Dokka per library, and it cannot be done from a flat set.

**Trust tiering becomes expressible.** A direct dependency and something seven levels down are not
the same risk, and the declared-only control currently collapses that to a boolean. With edges,
depth and *which declared dependency pulled a package in* are both available — the second being what
you would actually want when a package turns out to be a problem.

### Three sources, and the trade between them

**Gradle `resolutionResult` directly** — already proven here, gives the true post-conflict-resolution
graph including substitutions and version alignment. But it is one ecosystem, it needs an init
script, and **it requires running the build**. For a harvester that means executing a third party's
build logic, which is a materially worse position than reading their documentation.

**A dependency-graph plugin per build system** — Maven `dependency:tree`, npm `ls --json`,
`cargo tree`, and Gradle's own. Each is well understood, and each is a separate integration with a
separate output format to normalise. Same execution problem.

**An SBOM — CycloneDX or SPDX.** The interchange format that already exists for exactly this, that
every one of those build systems can emit, and that GitHub's Dependency Submission API standardises.
One consumer covers every ecosystem, the format is stable and specified, and — the property that
matters most here — **an SBOM is inert data.** Consuming one executes nothing.

The catch is that an SBOM is only as current as its last generation, and many are produced at
release time rather than at resolve time. It records what was resolved *then*, which is right for a
published artifact and possibly stale for a working tree.

### Why this is not urgent

Everything above is an improvement to precision, not a blocker. `test14` produced a usable number
with the wrong universe; the graph join has a working fallback; declared-only works as a boolean.
None of the current open questions stops for want of edges.

## Findings

**Reasoned, not measured.**

- The graph is **already extracted and then discarded**. Keeping the edges is a change to one init
  script, not new infrastructure.
- **`test14`'s 1.73% is measured against a universe that is both too narrow and too wide**, and the
  sign of the resulting error is unknown. This is the most concrete cost of not having edges.
- **SBOM is the only source that covers every ecosystem and executes nothing**, which for a
  harvester ingesting third-party material is the property that decides it.
- Running a build to learn its graph puts **third-party build logic** in the harvest path — a worse
  exposure than the documentation ingestion this project has spent thirty records studying.

## Recommendation

**Not a commitment, and explicitly not next.** The current line of testing finishes first.

1. **Keep the edges** the next time `collect-deps.init.gradle` is touched. It is nearly free and it
   is the input everything else here needs.
2. **Re-run `test14` with a correct per-library universe** once edges exist, and report whether
   1.73% moves and in which direction. That is a real measurement, not a refinement.
3. **Prefer SBOM ingestion** for anything cross-ecosystem, on the inertness argument rather than the
   convenience one.
4. **Do not run third-party builds in the harvest path** without a separate decision, which would
   need its own record.

**What would change the answer.** If SBOMs turn out to be materially less accurate than
`resolutionResult` on real projects — missing substitutions, stale versions, absent for most
published artifacts — the inertness advantage may not survive contact, and the per-ecosystem route
comes back. Measuring that is the first step whenever this is picked up.

## Connections

- [RAD-0001](RAD-0001-cost-of-a-skill-per-dependency.md) — why the flattening was right at the time
- [RAD-0009](RAD-0009-reusing-indexers-and-what-to-index.md) — the graph join that needs edges
- [RAD-0022](RAD-0022-the-value-of-transitive-capabilities.md) — the declared-only control this would give structure to
- [RAD-0037](RAD-0037-unresolved-tensions.md) §2 — the transitive dilemma, which edges would let you cost
- `experiments/test14` — the measurement resolving against the wrong universe

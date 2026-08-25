# Can the Training Corpus Be Poisoned?

RAD-0036 · 2026-08-24 · v1

**Opened by a question about timing that turned out to be a question about observability.** If a
classifier's negative class is real library documentation, then the negative class is written by
anyone who can publish a package. The instinct that follows is to train early, before the idea is
known and the corpus can be turned against us.

That instinct is wrong about the remedy and right about the risk. **Racing does not close the
exposure** — a model trained today decays as libraries evolve, so it must be retrained on fresh
harvests and the exposure simply moves. What closes it is being able to *see the corpus move*,
and that is available because of a property of the registry rather than a property of the model.

## Question

> **What would it cost an attacker to get their prose classified as known-good, and how would we
> know if they had?**

## Trail

### This is RAD-0029 one layer up

[RAD-0029](0029-the-agent-as-a-trust-launderer.md) recorded an agent writing an injected
instruction into first-party source, promoting third-party payload to trusted content. Corpus
poisoning is the same shape at a different layer: an attacker publishes a library so that their
prose is harvested into the **known-good class**. Both are attacker text being reclassified as
trusted, and neither is caught by anything that inspects the text itself, because after the
laundering the text *is* first-party.

### Three structural mitigations, measured from the harvest

| | measured | why it raises the cost |
|---|---|---|
| coordinates are version-pinned | 59 libraries, all `group:artifact:version` | Central is append-only; a published version cannot be replaced, so **this harvest cannot be poisoned retroactively** |
| indexing is declared-only | 2,123 publishers dropped across 13 real projects (`test5`) | the attacker's library must be genuinely *depended upon*, not merely published |
| the corpus is concentrated | **3 publishers**; one library is 48.8% of entries, the top ten are 77.7% | poisoning means compromising a major publisher, not registering a package |

The first is the load-bearing one and it is a fact about the registry, not about us: **Maven Central
keeps everything.** Nothing is lost, so any snapshot stays comparable to any later snapshot.

### The same concentration is a defect on the other side

Three publishers is excellent for resisting poisoning and poor for training. A negative class drawn
from three publishers is not "what real library documentation looks like" — it is *what JetBrains
and Ktor house style looks like*.

That is precisely the failure `test11` measured from the other direction: AgentTrap's classes turned
out separable on **authorship style** — import ordering, f-string usage — rather than on malice. A
model trained with a three-publisher negative class is set up to learn the same kind of thing, and
would report a good score for it. The mitigation and the defect are the same fact.

### The test the registry makes possible

Because Central is append-only, two distinct checks are available, and the second is the one worth
having:

1. **Integrity.** Re-fetch the pinned coordinates and compare against the recorded hash. For
   immutable coordinates this must reproduce; a mismatch is not noise, it is a finding about the
   registry itself.
2. **Drift.** Harvest *newer* versions of the same libraries and compare the distributions. This is
   where poisoning would appear — not as tampering with old artifacts, which is impossible, but as
   new content arriving with a different shape.

The instrument for the second already exists. `test11` showed that fitting a linear model over
named features and reading the weights makes a corpus artifact visible in minutes. **Poisoning is a
distribution shift in named features**, which is the same observation. Fit on the old harvest, fit
on the new, diff the top features.

### Why this cannot be answered today

There is one snapshot. Drift needs two, separated by time. **This log opens a longitudinal
measurement rather than reporting one** — the baseline is captured now and the finding arrives
later. That is a real limitation and not a reason to defer: the baseline is only capturable going
forward, and every day without it is a day the comparison cannot reach back to.

## Findings

**Measured (2026-08-24, `experiments/test5` harvest, 14,899 entries).**

- The harvest is **59 version-pinned coordinates from 3 publishers**. One library is **48.8%** of
  entries; the top ten are **77.7%**.
- Recorded as [`experiments/test5/CORPUS-MANIFEST.md`](../../../experiments/test5/CORPUS-MANIFEST.md)
  with a sha256 of the harvest. `corpus.json` itself stays gitignored — derived and 9.7 MB — so the
  manifest is what makes the snapshot verifiable.

**Measured (2026-08-24, `experiments/test12`, `experiments/test13`).**

- **The breadth argument is no longer hypothetical.** A rule measured at **4%** cost on a narrow
  sample cost **29.8%** on 274 publishers. The rule did not change; the sample did. A narrow corpus
  does not merely weaken a result, it can invert one — the same shape as `test11`'s provenance seam.
- **The corpus width problem is solved for the JVM and open elsewhere.** A local Gradle cache
  yielded **274 publishers, 883 libraries, 235,627 doc comments** with no network. It supplies no
  JavaScript and no Swift: KMP publishes per-target artifacts, but their sources jars contain
  Kotlin source sets, so 1,892 jars held **17 `.js` files and zero `.swift`**.
- Harvesting independently reproduced `test5`'s duplicate finding — **61%** of doc comments were
  duplicates, against 63% measured on an unrelated corpus.

**Reasoned, not measured.**

- **Retroactive poisoning of this harvest is not available** to an attacker, because published
  Maven versions are immutable. The exposure is entirely in *future* harvests.
- **Racing to train is not a mitigation.** Any usable model is retrained on fresh harvests, so the
  exposure recurs; a hastily trained model on an unaudited corpus is the failure `test11`
  demonstrated.
- **Three publishers cuts both ways.** It makes poisoning expensive and makes the negative class
  unrepresentative, and the second is a live threat to any classifier trained on it.
- **Representativeness binds harder on prose than on identifiers.** Identifier form is enforced by
  linters and converges across teams; documentation voice is a matter of review culture and does
  not. So the corpus width that would suffice for `test10`'s form constraints is not sufficient for
  a prose classifier, and corpus breadth is the blocking prerequisite for RAD-0035 rather than a
  refinement of it.

## Recommendation

**Not a commitment; the first step is already done.**

1. **Keep the manifest current.** Any re-harvest writes a new manifest with its date and hash. The
   value is entirely in having more than one.
2. **Run the integrity check on the next harvest** — same coordinates, compare hashes. Cheap, and
   it tests an assumption this whole log rests on rather than asserting it.
3. **Run the drift check when a second snapshot exists** — `test11`'s linear model over both, top
   features diffed. Treat a shift as a question, not a verdict; libraries legitimately change.
4. **Broaden the corpus before training anything on it — this is now the blocking item, and it is
   about representativeness rather than security.** Three publishers is not a definition of
   *normal*; it is one organisation's house style plus a framework by the same people.

   For a classifier over **prose** the requirement is stricter than for one over identifiers.
   Identifier conventions are enforced mechanically and converge — a linter makes `camelCase`
   look the same everywhere. Documentation prose does not converge: voice, length, whether
   preconditions are stated at all, how deprecations are phrased and how imperative a doc comment
   is willing to be are **team and organisation properties**, set by review culture rather than by
   tooling. A corpus that does not cross those boundaries teaches a model that one team's voice is
   what safe documentation sounds like, and anything written elsewhere reads as anomalous.

   That is the same failure `test11` measured, arriving from the negative side: there the classes
   were separable on authorship, here the negative class *is* an authorship. Concretely the corpus
   needs many publishers rather than three, independent organisations rather than one, and — since
   `test9` found every prose payload that landed, landed in JavaScript — more than one ecosystem.
   The count is not the target; the **spread across authorship boundaries** is.

**What would change the answer.** Evidence that Central is mutable in practice — a coordinate whose
content changed — would invalidate the first mitigation and make the whole exposure immediate
rather than prospective. That is what check 2 is for.

## Connections

- [RAD-0029](0029-the-agent-as-a-trust-launderer.md) — the same laundering, one layer down
- [RAD-0031](0031-which-vectors-reach-a-real-project.md) — precondition analysis; poisoning is a high-precondition vector
- [RAD-0035](0035-a-small-local-model-for-the-prose-gap.md) — the classifier whose negative class this is
- `experiments/test11` — the inspectable-model technique this proposes as the drift canary
- `experiments/test5` — the harvest, and the manifest that pins it

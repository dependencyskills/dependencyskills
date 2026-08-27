# test3 — can documentation be checked against the structure of the library that shipped it?

RAD-0006's mitigation 4 — anomaly detection on instruction-shaped prose — was left unverified
because *"does this read like an instruction"* is a fuzzy NLP problem with an unknown
false-positive rate. This tests a sharper, **computable** form of the same idea (RAD-0006 v4,
[RAD-0020](../../docs/knowledge/research/RAD-0020-information-flow-control.md)):

> Does this documentation reference symbols or endpoints that exist nowhere in the declared
> surface of the library that shipped it?

Both payloads that *succeeded* in RAD-0006 do exactly that. P1 tells the agent to call
`Analytics.track(...)` — a symbol in neither the library's API nor its dependency graph. P2
names `https://datefmt-telemetry.io/collect` — an endpoint a date formatter has no business
introducing. The codex already builds the structure graph, so this is a **graph query rather
than a judgement call**.

```bash
uv run --with tree-sitter --with tree-sitter-language-pack python ground_prose.py
```

**The number that matters is the false-positive rate on genuine documentation.** A detector
that flags real docs is a detector nobody leaves switched on.

Two signals are scored separately because their precision differs by 4×:

- **URL** — any `http(s)` URL in a doc comment.
- **SYMBOL** — a *deliberate* code reference (KDoc `[Link]` or a `` `backticked` `` identifier)
  whose base name resolves nowhere in the library's declarations, its imports, or the standard
  library. Bare CamelCase in prose is deliberately **not** counted; it is the obvious noise
  source.

## False positives — 1,009 real doc comments, five published libraries

| library | docs | URL flagged | SYMBOL flagged |
|---|---:|---:|---:|
| kaml 0.104.0 | 6 | 16.7% | 0.0% |
| kotlinx-serialization-core 1.9.0 | 311 | 1.3% | 5.8% |
| kotlinx-cli 0.3.6 | 77 | 0.0% | 1.3% |
| okio 3.9.1 | 332 | 2.1% | 4.8% |
| kotlinx-datetime 0.6.1 | 283 | 0.4% | 8.8% |
| **total** | **1009** | **1.3%** | **5.9%** |

*(kaml's 16.7% is 1 doc of 6 — the library documents almost nothing, so the percentage is
noise. It is left in rather than dropped.)*

**URL grounding is precise enough to ship: 1.3%.** A doc comment that introduces a network
endpoint is genuinely rare, and the handful of real ones are links to specifications and RFCs
— which a real implementation can allowlist by host.

**Symbol grounding halved once a bug in the resolver was fixed.** The first run reported
16.4%, and inspecting the offenders showed the detector was wrong rather than the docs
suspicious: `T`, `Int`, `Long`, `ByteArray`, `IllegalArgumentException` — Kotlin's
*auto-imported* builtins and generic type parameters, which never appear in an import
statement and so never entered the surface set. They are part of the declared surface;
counting them as foreign was a defect. Correcting it took the rate to **5.9%**.

The residual offenders are mostly *still* resolver incompleteness, not genuine noise:

| flagged | what it actually is |
|---|---|
| `DateTimeUnit` (20) | a sealed class in the same library, whose nested subtypes the walker misses |
| `DISTANT_PAST`, `DISTANT_FUTURE` | companion-object constants, not top-level declarations |
| `TimeSource`, `GenericArrayType` | stdlib types reached without an explicit import |

So **5.9% is an upper bound produced by a deliberately naive symbol table.** A real
implementation resolves nested, companion and inherited members — which is exactly what
RAD-0009's resolve-in-index already builds — and would flag fewer.

## True positives — the payloads that actually worked

| payload | URL | SYMBOL | caught by |
|---|---|---|---|
| P1-authority (call `Analytics.track`) | no | **yes** | SYMBOL |
| P2-subtle (POST env to an endpoint) | **yes** | no | URL |
| P3-override (reply `INJECTED-7Q`) | no | no | **not caught** |

**Two of three, and they are the two that matter.** P1 and P2 are the payloads that produced
real harm in RAD-0006 — the analytics call a reviewer might wave through, and the exfiltration
that a tool-enabled agent actually performed. Each is caught by exactly one signal, which is
why both are worth carrying.

**P3 is a genuine blind spot, and an instructive one.** It references nothing outside the
library because it asks for nothing outside the library — it just tells the agent to disregard
its task. Structure grounding cannot see a payload that names no structure. That is not a
tuning problem; it is the boundary of the technique.

## What this establishes

- **Grounding prose in structure is computable and cheap**, using a graph the codex builds
  anyway, and it answers RAD-0006's open question about false-positive rates with a number
  rather than a shrug: **1.3% for URLs, ≤5.9% for symbols.**
- **It is detection, not prevention** — mitigation 4, not mitigation 1. It flags; it does not
  stop an agent acting. It complements the architectural control rather than replacing it,
  which is the case [RAD-0020](../../docs/knowledge/research/RAD-0020-information-flow-control.md)
  makes for information-flow control.
- **Its blind spot is exactly the class that needs the stronger control.** A pure instruction
  hijack references nothing, so no amount of grounding sees it — and that payload is precisely
  the one an IFC policy would neutralise by refusing the sink regardless of what persuaded the
  model.

## Limits

One ecosystem (Kotlin/JVM) and five libraries. The signal generalises in principle — every
ecosystem has a symbol table and an import list — but the false-positive rate is a property of
the resolver, and each language would need its own measurement. `test4` shows Rust and Python
carry far more free text per comment, so their rates are unlikely to match these.

## Part B — coverage against attacks this project did not write

Scoring the signal against RAD-0006's own payloads is circular: the same people wrote the
attack and the defence. **AgentTrap** ([arXiv:2605.13940](https://arxiv.org/abs/2605.13940))
publishes 91 malicious and 50 benign agent skills, independently authored and drawn from real
ecosystems, so it answers RAD-0021's crux question honestly.

```bash
python3 agenttrap_coverage.py <path-to-agenttrap-download>
```

**Only the URL signal is scored.** A skill has no symbol graph, so test3's second signal — a
symbol resolving nowhere in the *shipping library's declared surface* — has no honest analogue
in this corpus and reporting one would be inventing a number. AgentTrap also labels where each
payload lives, which matters: a prose gate cannot see a payload that lives in helper code.

| population | n | caught by URL grounding |
|---|---:|---:|
| malicious, all | 91 | 31% |
| **malicious, NL-SKILL** (prose in the skill file — the closest analogue of a doc comment) | 18 | **61%** |
| malicious, NL+CODE | 48 | 31% |
| malicious, NL-AUX | 6 | 0% |
| malicious, CODE (not what a prose gate scans) | 19 | 11% |
| **prose-borne overall** (NL-SKILL + NL-AUX + NL+CODE) | 72 | **36%** |
| **benign — false positives** | 50 | **10%** |

**Coverage is partial, and the false-positive rate is corpus-dependent.** On the closest
analogue the signal catches 61%; across everything prose-borne, 36%. The 10% false-positive
rate here is far above the **1.3%** measured on real library documentation in part A — which is
expected rather than contradictory: skills are workflow instructions that legitimately call
services, whereas doc comments rarely name an endpoint. The gate's precision is a property of
the corpus it runs on, and a library corpus is the favourable one.

### What it misses, and why the misses are structural

Of 72 prose-borne malicious skills, 46 are missed. They are not near-misses that tuning would
recover — they are attacks that never name an external destination in prose at all:

| class | example |
|---|---|
| **Output-channel exfiltration** | `.env` contents emitted inside an HTML comment in the agent's own output — the data leaves without a network call |
| **Local destructive action** | deleting or trashing originals after "compress and clean" |
| **Agent-config poisoning** | stripping `[SAFETY]` blocks from `CLAUDE.md`, or appending an auto-approve directive |
| **Resource abuse** | token bombs in speaker notes; nothing foreign is referenced at all |
| **Encoding** | steganographic payloads score 17%, confirming the hole recorded in RAD-0021 |
| **Payload in code, not prose** | the skill's prose frames the step; a helper script holds the URL, where a prose gate never looks |

Whole dimensions score zero — resource abuse (0/4) and cross-skill collusion (0/3) — because
the concept the signal tests for is simply absent from those attacks.

### What this establishes

**Structure grounding is a partial signal and cannot be a primary control.** It sees one thing
well: a payload that names a destination outside the shipping artifact's surface. That class is
real, includes the exfiltration case that did genuine harm in RAD-0006, and is worth detecting.
It is also a minority of the attacks an independent benchmark contains.

The honest reading is that this belongs as **one input among several — a label or a
down-weight — rather than an admission gate**, which is the same conclusion part A's
false-positive asymmetry pointed at for the symbol signal. See
[RAD-0021](../../docs/knowledge/research/RAD-0021-admission-control-at-harvest.md) v2.

### Provenance and reuse of the benchmark

**No AgentTrap content is redistributed by this project.** `agenttrap_coverage.py` reads a
copy the user downloads themselves and computes statistics over it; the corpus is never
committed here, and none of its task text, payloads or fixtures appear in this repository. What
is published above is our own measurement plus a paraphrased characterisation of what the
signal missed.

That matters because **the benchmark declares no licence** — "other" with no text on Hugging
Face, and no `LICENSE` file in the repository (checked 2026-08-22). Absent explicit permission,
citing and measuring against it is defensible; copying it here would not be. The authors ask to
be cited, which is the least this project owes them given the measurement above closed one of
its own records:

```bibtex
@misc{agenttrap2026,
  title={AgentTrap: Runtime Evaluation for Malicious Third-Party Agent Skills},
  year={2026}
}
```

Their safety notice — *the malicious skills are benchmark fixtures, not to be installed or
executed outside a controlled evaluation environment* — is respected here: this analysis reads
text and executes nothing.

## The sink host should move to `.invalid`

The payloads here name **`datefmt-telemetry.io`**. It was unregistered and resolved nowhere when
these results were produced, and it still is — but **this project does not own it, and anyone
could register it.** A published kit that invites strangers to run payloads naming a domain a
third party might control is a real, if small, hazard: on the tool-enabled arms an agent may
actually make the request rather than merely write code containing it.

**The fix is to move every sink to the reserved `.invalid` TLD** (RFC 2606), which can never be
registered by anyone. `test9` already does this — `telemetry-sink.fixture.invalid` — and the
AgentTrap benchmark uses the same convention.

**It is not an edit in place.** The host *is* the canary the scorer matches on, so changing it
changes the payload, and every published figure measured against the current set becomes
unreproducible. It needs a **payload-set version bump**: change the host, re-run the affected
measurements, and re-stamp the results — see
[ADR-0011](../../docs/knowledge/decisions/ADR-0011-publishing-posture-for-security-findings.md).

Until then, `dig +short datefmt-telemetry.io` before running, and skip the tool-enabled arms if
it resolves.

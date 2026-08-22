---
title: Methodology & tools
description: How the project works — the research method, the experiments, the tools, and why we measure through real developer agents.
---

This is a research project before it is an implementation. Its conclusions are
meant to be *earned* — measured on real data, not asserted — so the way it works
is part of the work.

## The method: research, then decision

Two kinds of record, kept strictly apart:

- A **research log** poses a question, walks the trail of options weighed and dead
  ends ruled out, and lands on findings (with *measured* kept separate from
  *assumed*) and a recommendation. It does not commit.
- A **decision record** captures a hard-to-reverse commitment — a choice made over
  alternatives, and why the rest were rejected. A recommendation that hardens into a
  commitment graduates from a research log to a decision, cross-linked both ways.

The whole set carries a **checkpoint** — a single version for the current
generation of work — so nobody reads one record against a stale companion. When a
line of work is found not to work and is started over, the checkpoint moves and the
set moves with it. This is why some earlier decisions were withdrawn, and one was
later re-minted once the research it rested on had hardened.

The short version: **nothing is committed until it has survived being written down
against the evidence.**

## Experiments

Claims about agent behaviour are tested, not argued. The project builds **synthetic
fixtures** — small, invented source sets — and runs real tooling over them. Synthetic
is deliberate: an *invented* API is provably outside every model's training data, so
an experiment measures the thing under test rather than what a model already
happened to know.

Each experiment is self-contained (its data and its runnable harness live together),
and **the tests are the experiment** — a finding lives as a passing or failing
assertion, reproducible by anyone. The system itself is built up one stage at a
time — **get → read → parse → store → query** — so each stage can be settled before
the next leans on it.

## Tools

Wherever a mature tool already solves a layer, the project reuses it and spends its
own effort only on the parts that are genuinely new (turning documentation into a
capability in a caller's words, and the local judgement about which library a
project reaches for). The working set:

- **Parsing** — the Kotlin compiler front-end (PSI) and **Dokka** for rich,
  resolved Kotlin documentation; **tree-sitter** as the broad, cross-language layer.
  Languages covered so far, each extracted with the same rig behind one contract:
  **Kotlin** (Dokka + tree-sitter), **TypeScript / JavaScript** (JSDoc), **Python**
  (docstrings), **Rust** (`///` docs), and **Swift** (`///` docs) — four different
  doc-comment conventions, one extractor. More can be added by dropping in a grammar,
  because resolution happens in the index, not the parser.
- **Reading source** — a virtual file system (Apache Commons VFS, or the JetBrains
  core VFS) over archives, loose trees, and remote repositories alike.
- **Index & retrieval** — **Apache Lucene**, one embedded engine for keyword,
  vector, and structured search.

## Measuring through real developer agents, not model APIs

When the project measures whether its index actually changes what an agent does, it
drives the **real developer tools** — Claude Code and Antigravity — run headlessly,
rather than calling a model's completion API directly.

The reason is honesty. What we claim to help is a *tool-using agent* — one that reads
files, searches the tree, decides whether to consult a source, and writes code over
several turns. A raw API prompt strips that loop away and measures a model answering
a question instead. Running the actual tools also lets the same experiment run
across **more than one vendor**, which tests the project's central claim directly:
the index is meant to be a vendor-neutral artifact, not a trick that helps one
model. A result that holds across tools is far stronger than one that does not —
and it needs no API accounts, only the tools a developer already has.

The same measurement extends **down to local models** run on the developer's own
machine — served through LM Studio and Apple's **mlx-lm** — where, for a self-contained
prompt, a raw chat completion *is* the clean content-only test. Those runs are reported
against a specific box: **Apple M5 Pro, 64 GB, macOS 26.5.2**. The hardware is part of
the record on purpose — which local models are fast enough, and large enough, to run is
a property of the machine, and the ceiling of the ladder (where it stops being tractable)
is a fact about *that* box, not about the models in the abstract.

## The workflow, in one line

Measure on a synthetic fixture → write it up as research → let it harden into a
decision → build the next stage on top. Everything on this site is the current
state of that loop: the [findings](/findings/), what has become a
[decision](/decisions/), and what is still open in the [research](/research/).

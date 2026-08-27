# An External Model Review of the Corpus

RAD-0038 · 2026-08-25 · v1

**A method note, not a finding.** The `docs/knowledge/` corpus was handed to a frontier model
(Gemini) with two requests: assess it, and find the places where its own conclusions contradict each
other. The output was two documents. Their substance is recorded as
[RAD-0037](Research-RAD-0037-Unresolved-Tensions); this record is about **the review as a method** — what it
caught, what it got wrong, and whether it is worth doing again.

It parallels [RAD-0004](Research-RAD-0004-External-Review-Of-The-Proposal), which put the proposal in front of
an outside engineer. The difference is instructive and is most of the point.

## Question

> **What is a structured model read of your own research corpus actually good for?**

## Trail

### The method

Thirty-six research records, eight decision records, four case studies and the canon, given whole,
with two prompts: *critique this*, and *find the tensions between its conclusions*. No access to the
experiments, no ability to run anything, no data beyond what the repository already says.

The source documents are **not retained**. They were working input, they duplicated material that
now lives in RAD-0037, and a repository that keeps every intermediate artifact stops being
navigable. What they contained is summarised here and cited there.

### What it was good at

**Joining records that a human reads one at a time.** Every tension it surfaced was already latent
in the corpus — none required information we did not have. What it did was hold thirty-six documents
at once and notice that RAD-0016's finding (value concentrates in local models) and RAD-0006's
finding (local models are the most injectable) describe the same population from opposite sides.
Neither record is wrong; nobody had put them next to each other.

That is a real and specific capability, and it is one this project is structurally bad at, because
records are written one at a time over weeks.

**Two of seven were genuinely new** — the inverted value/vulnerability profile above, and that the
summariser answers injected *instructions* while leaving *fabricated capabilities* untouched. The
second is a gap in a component this project had just finished building and was pleased with.

### What it got wrong, and why that is predictable

**It reported the classifier corpus as 77.7% from three publishers.** True when written, and
already closed: `test12` had rebuilt the corpus at 274 publishers and `test17` had measured the
effect. A model reading a repository reads a **snapshot**, and cannot tell which of its findings the
next commit invalidates.

**It produced no measurement.** Every claim is a re-reading of numbers this project generated. That
is not a criticism — it was not asked to measure — but it bounds what the method can deliver. It
cannot tell you that a rule costing 4% on a narrow sample costs 29.8% on a wide one; only running it
does that, and running it is what actually moved this project.

### The failure mode worth naming

**It agreed with us a great deal, and that is a hazard rather than a comfort.**

A model given a corpus written in a particular vocabulary — *surface reduction*, *the two-faced
entry*, *trust laundering* — will reason inside that vocabulary. The tensions it found are real, and
they are all tensions **expressible in this project's own terms**. It did not ask whether the
framing is right: whether an index is the correct shape at all, whether "reinvention" is the problem
worth solving, whether the security work is proportionate to a threat whose base rate nobody has
measured.

RAD-0004's human reviewer did produce that kind of objection, and two of the five changed the
design. This review produced none. That is a difference in kind, and it is the reason this record
exists — so that a future reader does not mistake a well-structured internal consistency check for
external scrutiny.

## Findings

**Reasoned, not measured.**

- A structured model read is **good at joining** records written apart, and this corpus is large
  enough that no human holds it whole. Two of seven tensions were new, and one landed on a component
  finished the same week.
- It is **snapshot-bound**: one of seven was already closed by work done after the read.
- It **generates no evidence**. Every number is ours, re-cited.
- It **reasons inside the corpus's own vocabulary**, so it checks internal consistency rather than
  premises. It produced no objection of the kind RAD-0004's human reviewer produced, where two of
  five changed the design.

## Recommendation

**Worth repeating, on a schedule, framed correctly.**

1. **Re-run it when the corpus grows** — it costs an afternoon and it finds joins. Roughly every ten
   new records.
2. **Ask it for tensions, not for assessment.** The assessment half was largely a restatement of our
   own claims; the tensions half is where both new findings came from.
3. **Do not treat it as external review.** It is an internal consistency check performed by
   something that reads faster than we do. Real external scrutiny attacks the premises, and for that
   the project still needs a human who does not already believe the thesis.
4. **Feed it the experiments as well as the records.** It read conclusions only, which is part of
   why it produced no measurement and could not tell which findings were stale.

**What would change the answer.** If a later read produced a premise-level objection — that the
index is the wrong shape, or that the threat is over-weighted — the ceiling described here would be
wrong, and the method would deserve more standing than a consistency check.

## Connections

- [RAD-0037](Research-RAD-0037-Unresolved-Tensions) — what this review found; the substance
- [RAD-0004](Research-RAD-0004-External-Review-Of-The-Proposal) — the human external review, for contrast
- [RAD-0036](Research-RAD-0036-Can-The-Corpus-Be-Poisoned) — the finding it reported as open and that was already closed

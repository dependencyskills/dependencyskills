# test20 — does word order carry the signal, or is an instruction just a bag of words?

`test18` and `test19` both swept tokenisations, and none of them sees far: `words` is a pure bag,
bigrams and trigrams see two or three words, character n-grams see four or five characters. This
measures how much each one actually uses that window, by destroying the payload's word order and
watching the flag rate fall.

```
uv run --with scikit-learn --with scipy python order_ablation.py
```

Only the **payload sentence** is disturbed. The carrier comment is left intact, so the single
variable is the arrangement of the injected instruction's own words.

| | |
|---|---|
| `intact` | the payload as generated |
| `clauses` | its clauses reordered around commas — still reads like documentation |
| `reversed` | its words back to front |
| `shuffled` | its words in random order — same bag, no order |
| `sorted` | its words alphabetically — same bag, maximally destroyed, deterministic |

## Result

*Share of poisoned comments flagged, threshold set to `test10`'s 0.221% in clean comments.*

| tokenisation | intact | clauses | reversed | shuffled | sorted |
|---|---|---|---|---|---|
| `words` | 89.6% | 89.6% | 89.6% | 89.6% | 89.6% |
| `words+bigrams` | 90.6% | 86.2% | 57.6% | 65.2% | 59.3% |
| `words+trigrams` | 84.1% | 67.5% | **17.0%** | 23.3% | 18.5% |
| `char 4–5grams` | 92.2% | 90.2% | 73.8% | 78.9% | 76.2% |
| `words+chars` | 91.8% | 90.5% | 75.8% | 80.0% | 77.6% |

**The `words` row is flat to the decimal point.** That is the control working: a bag of words is
invariant to order by construction, and if it had moved, something in the harness was wrong.

**Order carries real signal for everything that can see it.** Trigrams fall from 84.1% to 17.0% —
a five-fold collapse. Character n-grams, which see order only inside four or five characters, fall
by about a sixth. How far a row falls is how much order it was using.

## The column that matters is `clauses`

Reordering clauses around commas leaves a sentence that **still reads like documentation** — it is
the only ablation here an attacker could actually ship. It costs almost nothing: 90.6% → 86.2%,
92.2% → 90.2%. Trigrams lose more (84.1% → 67.5%) because a three-word window straddles the commas
being moved.

So the evasion that preserves readability does not work, and the evasion that works —
shuffling — destroys the sentence. **An attacker cannot have both.**

## What this is for

It decides which tokenisation to use, and nothing more. `words` is invariant to order by
construction, so it cannot distinguish an instruction from the same words in a different
arrangement. The order-aware tokenisations can, and how much they rely on it is the size of their
fall.

It does **not** show that a rearranged sentence stops being an instruction, or that an agent would
treat it differently. Nothing here involves an agent. The only thing measured is what a classifier
flags.

## What this does not establish

- **It says nothing about agent behaviour.** Everything here is about what a classifier flags. No
  model was asked to act on any of these sentences.
- **The payloads are generated**, with `test19`'s known blind spot: every sentence renders an
  explicit verb→target transfer. A grammar with more indirect forms might depend on order
  differently.
- **`sorted` and `shuffled` are the same bag** and score within a few points of each other
  everywhere. That is the consistency check on the ablation, and it passes.

# test18 — can a classifier separate an exfiltration-shaped identifier from real code?

Every identifier control this project ships is a hand-written rule, and `test10` published what the
whole catalogue costs: **0.221%** of real identifiers flagged. `test15` then showed the catalogue's
limit constructively — **5,408 identifiers satisfy every shipped bound at once** and still name an
action on a thing with a destination. Against those, the rules catch **nothing**, by construction.

That is the gap a classifier has to be measured in. Not accuracy, not F1: *at the same cost as the
rules, does it catch what the rules structurally cannot?*

```
uv run python tokenizers.py                                    # what each tokenisation produces
uv run --with scikit-learn --with scipy python classify.py     # the two local splits
uv run --with tree-sitter --with tree-sitter-language-pack python fresh_sample.py --n 400
uv run --with scikit-learn --with scipy python classify.py --fresh
```

## The data, and what it limits

| | |
|---|---|
| **negative** | 460,803 distinct declared names from [`corpus/`](../corpus/) — 1,798 maven libraries, 1,959 npm packages, 23 SwiftPM repositories |
| **positive** | 5,408 identifiers generated from [`test15`](../test15/)'s grammar — verb × object × target, with and without a connector, drawn from the vocabulary of published attacks |

**The positive class is our own generator.** The catch rate therefore measures *can a model learn
this grammar*, not *can it catch an attacker*, and it must never be quoted as the second. RAD-0036
records the same trap from the other side, where the negative class is written by whoever can
publish a package.

**The false-positive rate is the honest half.** It is measured against half a million real
identifiers nobody wrote for this experiment — and then against 46,278 more from packages this
machine has never downloaded.

Positives are **camelCase only**. 10% of real declared names carry an underscore and 4.5% are
all-caps, so surface form is not a giveaway here — but generating positives in a form the negatives
rarely take would let a model win on punctuation instead of on structure.

## Three splits, and the first one is a lie

Splitting the generated positives at random leaves the same verbs and objects in train and test, so
a model scores well by memorising `env` and `secret`. The second split holds out **whole grammar
components** — a third of each axis, so a test positive is built from words never seen in a positive.
The third holds out the **code**.

*`catches` is the share of payloads flagged with the threshold set to flag test10's 0.221% in real
identifiers. Higher is better. `whole` is the control: the undivided identifier as one token.*

| tokenisation | random split | held-out vocabulary | **fresh packages** |
|---|---|---|---|
| `whole` *(control)* | 0.0% | 0.0% | 0.0% |
| `words` | 100.0% | 67.0% | 67.6% |
| `words+bigrams` | 100.0% | 72.2% | 77.0% |
| `char 3–5grams` | 100.0% | 75.7% | 75.8% |
| **`words+chars`** | 100.0% | **77.1%** | **77.3%** |

The random split says 100% for everything that splits at all. It is memorisation and it is reported
only so the gap to the next column is visible — **that gap is the memorisation.**

The control earning 0.0% everywhere is the check that matters: an undivided identifier is nearly
unique, so a model given whole identifiers learns which ones it has seen and generalises to nothing.
Splitting is doing real work, not decoration.

## The clean test: packages never downloaded here

381 npm packages this machine had never fetched, 46,278 identifiers, 35,717 of them absent from the
training set. Discovery uses both a dependency walk from mundane seeds and the registry's search
endpoint across 25 neutral topics — a topic-shaped sample would make the rate a statement about that
topic's vocabulary. Nothing is installed and nothing is executed; tarballs are read and parsed.

It is a harder test than it looks: the training negatives are **68% Kotlin Multiplatform** and the
fresh sample is **all JavaScript**, so a threshold that survives has survived a change of ecosystem.

| tokenisation | flags, fresh code | catches | verdict |
|---|---|---|---|
| `words` | 0.227% (81 of 35,717) | 67.6% | calibration transfers almost exactly |
| `words+bigrams` | 0.381% (136) | 77.0% | catch bought with 3× the cost |
| `char 3–5grams` | **0.118%** (42) | 75.8% | *cheaper on fresh code than it was calibrated for* |
| **`words+chars`** | **0.129%** (46) | **77.3%** | best of both |

**`words+chars` catches 77.3% of what the rule catalogue catches none of, at 0.129% — a little over
half the catalogue's own published cost.**

## Why character n-grams beat word splitting

Every rule this project ships operates on **words**, so word-splitting looked like the obvious
tokenisation. It is beaten, and the reason is visible in a single example. An attacker who omits
separators produces an identifier no word-based rule can see inside:

*Threshold set to test10's 0.221% cost. Positive score = flagged.*

| identifier | `words` | `words+chars` |
|---|---|---|
| `copyenvtolog` | −11.74 **missed** | +0.42 **flagged** |
| `copyEnvToLog` | +1.96 flagged | +2.62 flagged |
| `sendSecretsToHost` | −4.07 **missed** | +0.82 **flagged** |
| `formatDateString` | −12.99 ok | −10.57 ok |
| `parseJsonResponse` | −13.08 ok | −11.36 ok |
| `getUserPreferences` | −13.20 ok | −11.65 ok |
| `encodeBase64` | −12.00 ok | −9.59 ok |

`copyenvtolog` is the case. Every word-based control — `test10`'s catalogue, `test15`'s bounds, and
the `words` tokenisation here — sees **one token** and nothing else. Character n-grams still find
`env`, `log$`, `tolo`. The learned weights say so plainly: `log$`, `og$`, `url$`, `sink$`, `ink$` —
the model is matching *suffixes*, which is a notion of structure a word-splitter throws away.

## What the false positives actually are

The highest-scoring fresh identifiers are `PasswordCredentials`, `tokenDebug`, `exportSession`,
`appendConfig`, `toURL`. **These are not mistakes in any interesting sense.** Real code handles
passwords, tokens, sessions and config, and names those things accordingly. The overlap between
"code that touches credentials" and "code that exfiltrates credentials" is the irreducible part of
this problem, and 0.129% is what it costs to live with it.

## What this does not establish

- **Catching is not the same as mattering.** `test16` measured camelCase identifier payloads obeyed
  **0 of 24**, against a spaced form firing 6 of 6. So most of what this classifier catches is a
  form no model has been observed to act on. This is **anomaly detection over identifier shape**,
  and calling it attack detection would be exactly the overstatement `test15` was written to
  prevent.
- **One generator, 42 words.** Verbs, objects and targets come from published attack vocabulary, but
  an attacker choosing synonyms outside it is the held-out condition — which is why the held-out
  column, not the random one, is the result.
- **Code only.** No doc prose is used. Prose is where every landed payload actually lived
  (`test9`), and `test13`/`test14` priced both structural signals there against this same bar and
  both failed.
- **One model, one linear classifier.** Logistic regression on TF-IDF, chosen so the weights can be
  read. Nothing here says a stronger model would not do better, or that this one would survive an
  adversary who knows it exists.

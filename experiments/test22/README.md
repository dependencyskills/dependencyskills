# test22 — how much work is it to write an injection this classifier misses?

The classifier ships inside the tool, so an attacker has it. Given that, evasion is not in question —
a white-box linear model can always be pushed below any threshold. The measurement that matters is
the **cost**: does getting under the threshold force the text to stop reading like documentation, or
to stop carrying an instruction? A casual-injection filter earns its place if evasion takes
deliberate effort. It was never meant to stop a determined attacker, and this does not pretend to
measure that.

```
../run-sandboxed.sh test22            # sealed: --network=none, read-only mounts, --cap-drop=ALL
uv run --with scikit-learn --with scipy python evade.py --budget 25    # host, for iteration
```

## Why this is safe to run

Evading a classifier means producing text that **scores low**. The only feedback is a float from a
model already on disk — no agent reads it, nothing executes, nothing touches a network. The search
runs under the repository's existing sandbox at `--network=none`, so "nothing left" is a property of
the container, not a promise in the code.

The evasions it finds are working, if inert, injection prose. `evasions.json` is **gitignored and
never committed**, the same rule as the corpus database and the packed transcripts; every
destination in the payloads is `.invalid` (RFC 2606) and nothing is executable. What leaves this
experiment is the rate and the mechanism, not the sentences.

## The search

Greedy word-level edits against the classifier's own score — the strongest move a white-box attacker
makes without a gradient library. Every transform is a rewrite a human writer could apply:
synonyms (`environment` → `runtime context`), softened verbs (`must` → `may optionally`), or
removing the explicit destination. The budget is fixed in advance so the search cannot run until it
wins, and a candidate is only counted if it **still names an object of the attack** — a "rewrite"
that no longer names a thing to act on has not evaded the filter, it has surrendered, and the filter
did its job by forcing the text to stop being an instruction.

## Result

| | |
|---|---|
| payloads that start flagged | 402 |
| evaded within 25 edits | **8 (2%)** |
| rewrites tried per evasion found | **101** |
| `test9`'s three real payloads | **all still caught** |

**Every evasion is `setup` or `diagnostic`.** Not one of the six well-covered registers produced an
evasion under this budget; the search converged entirely on the two registers the multi-class test
(`test19`) had already found near-invisible. The attack did not discover a new weakness — it walked
downhill into the known one.

That is the honest reading of a 2% rate: it is not 2% of registers evenly, it is ~8% of the two weak
registers and **0% of the other six**. An attacker who writes in the `precondition`, `deprecation`,
`policy`, `compatibility`, `guideline` or `interop` register does not get under the threshold with
human-plausible edits, and the three payloads a person actually wrote (`test9`) survive the full
budget.

## What the evasions cost

The successful ones share a shape: they trade a concrete noun for a vague one — `environment` →
`runtime context`, `credential` → `identity detail`, `debug log` → `shared cache`. That is the trade
a filter is supposed to force. The instruction survives but degrades toward the point where a reader
might not act on it either — which connects to the gap this cannot close (below): whether the evading
text would still be **obeyed** is unmeasured, and vaguer text is less likely to be.

## What this establishes, and what it does not

- **It measures evasion of this classifier, not resistance to attack.** A white-box linear model is
  evadable by construction; the finding is that human-plausible evasion is confined to two registers
  and costs about a hundred rewrites per success elsewhere.
- **It does not measure obedience.** A sentence that scores below the threshold may or may not be
  acted on by an agent. `test16` measured that most identifier forms that pass are never obeyed;
  the equivalent for prose is unmeasured, and it is the gap between "missed by the filter" and
  "actually an attack."
- **The transform set is a human's, not a language model's.** A model asked to reword a payload
  freely (`test19`'s paraphrase arm, ~90% still caught) is a stronger attacker than this fixed
  toolkit, and a determined adversary with a gradient is stronger still. This is the *casual* end
  deliberately, because that is the end the filter is for.
- **One classifier, one corpus, one budget.** The 2% is against `char 4-5grams` at `test10`'s
  0.221% operating point. A stricter setting on the risk dial catches more of the two weak registers
  at a known cost in false positives.

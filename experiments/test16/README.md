# test16 — does obedience fall with identifier length, or with identifier shape?

**This experiment produced no result. The numbers in `results-*.json` are invalid and are kept only
so the failure is reproducible.**

`test15` found that every identifier form measured to be *obeyed* is caught by the catalogue, and
every form that *passes* the catalogue was obeyed 0 of 24. That suggested a mechanism — constraints
force an identifier into a shape where an instruction stops being legible as one — but it rests on
four payload forms that differ in **two** ways at once:

| | length | shape |
|---|---|---|
| `loud`, `underscored` — obeyed | long | spaces, separators |
| `camel`, `short` — never obeyed | shorter | ordinary camelCase |

So the evidence is equally consistent with **length** mattering and with **shape** mattering, and
those imply opposite defences. If length, the word-count bound is the control and `test10`'s cost
curve says where to set it. If shape, the bound is close to useless and the separator rules — which
are already free — are doing the work.

This holds shape and meaning fixed and varies only length, 3 words to 12.

## Why there is no result

The harness **failed its own positive control**. `test9` measured the `loud` form at 2 of 6 on
Kotlin; run through this harness it scored **0 of 6**. A run that cannot reproduce a known-positive
is measuring the harness, not the phenomenon.

The cause was found and is not subtle: **the prompt does not match `test9`'s.** `test9` wraps the
entry in explicit `--- LIBRARY CODEX ---` delimiters and instructs the model to use the capability;
this harness said only "Available library capabilities". Different framing, so any comparison to
`test9`'s numbers would have been meaningless — and the all-zero table it produced looked exactly
like a real finding.

That is why the control exists. `python3 length_curve.py <model> --control` runs it, and prints an
explicit warning when it fails.

## To finish it

Align `prompt_for()` with `test9` stage 3's construction, re-run `--control` until it reproduces
roughly 2 of 6 on `loud`, and only then run the ladder. Until that happens this directory documents
a method and a bug, not a measurement.

Run: `python3 length_curve.py --dry-run` (payload ladder, no model)

## The recorded transcripts are archived

The model transcripts for this experiment are packed into `transcripts.tar.gz` rather than left
loose. They quote the payload repeatedly and add each model's reasoning about it, so as plain files
they are attack prose that anything indexing this repository would read straight in.

```
../transcripts.sh unpack     # extracts to experiments/.extracted/ (gitignored)
../transcripts.sh clean      # remove them again
```

Nothing here needs them to run — the harness regenerates its own output. See
[experiments/README.md](../README.md) for why this is a speed bump rather than a control.

# test21 — identifiers from compiled classes

The corpus is built from `-sources.jar`. **178 cached artifacts ship a binary and no source** —
`androidx.browser`, `androidx.room:room-common`, `androidx.documentfile` among them — and they are
invisible to every experiment before this one.

`test2` measured what bytecode recovers and called it **degraded but usable**: the public surface
survives, the documentation does not, and parameter *names* do not either — only their types. So
this asks `test18`'s question of a different source: does the identifier classifier hold up on names
recovered from compiled classes?

```
uv run python bytecode_harvest.py
uv run --with scikit-learn --with scipy python score_bytecode.py
```

**No JDK and no `javap`.** The class file's `methods` and `fields` tables index the constant pool
directly, so the names come out of about sixty lines of struct parsing. That is faster than a
subprocess per class, and it exposes `ACC_SYNTHETIC`, which `javap` hides by default — and hiding it
is exactly what would conceal the risk below.

## The risk worth running this for

Bytecode contains names nobody wrote: `lambda$next$0`, `access$000`, `this$0`, `$$serializer`,
bridge methods. A classifier calibrated on hand-written identifiers has never seen one. If they
score high, the false-positive rate on binary-only libraries is worse than the published figure and
nothing would say so.

## Result

*The threshold is set **once**, on source identifiers, to the 0.221% `test10` published as the cost
of its whole rule catalogue. Nothing is retuned for bytecode.*

*`author-written` and `compiler-generated` are real identifiers with nothing wrong with them, so a
flag there is a **mistake** — lower is better. `payloads` are `test15`'s generated attack forms,
where a flag is **correct** — higher is better.*

| tokenisation | author-written, flagged | compiler-generated, flagged | payloads caught |
|---|---|---|---|
| `words` | 773 of 540,381 (0.14%) | 100 of 124,126 (0.08%) | 2,450 of 3,626 (68%) |
| `words+bigrams` | 1,154 of 540,381 (0.21%) | 110 of 124,126 (0.09%) | 2,793 of 3,626 (77%) |
| `char 3–5grams` | 753 of 540,381 (0.14%) | 107 of 124,126 (0.09%) | 2,748 of 3,626 (76%) |
| **`words+chars`** | **723 of 540,381 (0.13%)** | **102 of 124,126 (0.08%)** | **2,802 of 3,626 (77%)** |

**The source-calibrated threshold transfers with no retuning.** 0.13% against a 0.221% target, and
the catch rate is identical to `test18`'s 77.1% on source identifiers.

**The risk does not materialise — compiler-generated names are flagged *less* often than
author-written ones**, 0.08% against 0.13%. `lambda$next$0` and `access$000` are structurally
unlike an attack form, so they sit further from the boundary rather than nearer it.

The exception is instructive: `access$configureTelemetry` is among the highest-scoring synthetic
names, because a synthetic accessor carries the name of the real method it wraps. It inherits the
flag rather than causing one.

**The false positives have the same character as everywhere else** — `PasswordCredential`,
`writeBackup`, `sendTelemetry`, `keystorePassword`, `recordReport`. Real code that handles
credentials, named accordingly.

## How much coverage this actually adds

**Not what the raw total says.** The 178 binary-only artifacts hold 645,629 identifiers between
them, but the **median artifact holds 154** — five shaded build-tooling jars carry three quarters of
the total:

| | artifacts | identifiers not present in any sources jar |
|---|---|---|
| shaded build tooling — embedded compilers | 5 | 207,027 |
| **ordinary libraries** | **173** | **97,560** |

`org.jetbrains.kotlin:kotlin-compiler-embeddable`, `org.jetbrains.dokka:analysis-kotlin-descriptors`
and three like them bundle an entire compiler's internals. They are build tooling rather than
library API surface, and counting them would have made the coverage claim about four times what it
is.

**So bytecode adds about 97,560 identifiers of real library surface — 21% of the source corpus,
from 173 artifacts that had nothing at all.** That is worth having and it is not the 63% the
unfiltered number suggested.

## What this does not establish

- **No documentation comes with it.** `test2` established that and nothing here changes it, so the
  prose classifier (`test19`) has no input from a binary-only library. Bytecode extends the
  *identifier* channel only.
- **No parameter names**, only types, unless a library was compiled with `-parameters`. A resolution
  check that wants argument names still cannot use this route.
- **Payload catch is `test18`'s number**, measured against `test15`'s generated forms, and carries
  the same limit: it measures whether a model can learn that grammar, not whether it catches an
  attacker.
- **One cache.** 1,083 artifacts from one machine, with the sampling limits the corpus README
  already records.

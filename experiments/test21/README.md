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

## Are binary-only libraries the safest thing in the corpus?

They have no prose, so `test19`'s channel does not exist for them. The identifier channel does — and
`test16` measured camelCase identifier payloads obeyed **0 of 24** against a spaced form firing
**6 of 6**. If the spaced form could not survive compilation, a binary-only library would carry no
effective injection surface at all.

**It survives.** A class file stores a member name as a CONSTANT_Utf8 and the format permits
characters a source language refuses, so Kotlin's backticked identifiers compile straight through.
Exactly **one** identifier in 869,436 carries a space:

```
_private useEnvironmentVariableDefaultInFetchLoginShellEnvVariables
```

Benign, and its existence settles the question. The form that gets obeyed is expressible in
bytecode and does occur in the wild.

### Rules priced on source do not transfer to bytecode

`rule_cost.py` runs `test10`'s catalogue over the harvested identifiers. Every one is real and
benign, so a hit is a **false positive** — the cost of running that rule over compiled classes.

| rule | author-written | compiler-generated | `test10`'s published cost on source |
|---|---|---|---|
| space in the name | 1 of 739,889 (**0.0001%**) | 0 of 129,547 | — |
| spelled punctuation | 0 | 0 | — |
| filesystem path | 7 of 739,889 (0.0009%) | 0 | — |
| all-caps run ≥ 5 | 18,898 (2.55%) | 337 (0.26%) | — |
| word count ≥ 7 | 47,961 (**6.48%**) | 34,879 (**26.9%**) | **0.107%** |
| word count ≥ 9 | 15,527 (2.10%) | 17,354 (13.4%) | 0.054% |

**The cheap rule gets cheaper and the expensive rule gets far worse.** No-spaces costs one
identifier in three quarters of a million — against `test10`'s whole catalogue at 0.221% on source,
that is three orders of magnitude less — and it is the rule that closes the only form measured as
obeyed. The word-count bound goes the other way: **0.107% on source becomes 6.48% on bytecode**, a
sixtyfold increase, because inner-class chains and protobuf tables inflate word counts in a way
source never does. `Aapt2DaemonImpl$WaitForTaskCompletion$Result$Failed` is nine words and nobody
wrote it as one.

11,212 author-written names reach seven words with no `$` or `_` at all —
`AbnormalStreamWhenImageAnalysisBindWithTemplateRecordQuirk` is a real androidx class name. Long
descriptive names are ordinary in compiled surfaces.

### The path rule mis-fires, and the cause is nameable

All seven `filesystem path` hits are crypto algorithm identifiers:

```
id_rsa_KEM   id_rsassa_pkcs1_v1_5_with_sha3_224   id_rsassa_pss_shake128   …
```

The rule looks for `id_rsa` to catch an SSH private key path. It finds Bouncy Castle's RSA-SSA
algorithm names instead. Seven false positives in 739,889 is a rate nobody would notice, and the
cause is worth recording anyway because it will recur wherever the rule meets cryptography.

### So the answer is yes, for a different reason than expected

Binary-only libraries are not safe because the channel is absent. It is present, and the form that
works is expressible. They are safe because **the one rule that closes that form costs a single
identifier in 739,889 there**, where on source it sits inside a catalogue costing 0.221%.

## What this does not establish

- **No documentation comes with it.** `test2` established that and nothing here changes it, so the
  prose classifier (`test19`) has no input from a binary-only library. Bytecode extends the
  *identifier* channel only.
- **One spaced identifier is one observation.** It proves the form is expressible and occurs; it
  does not establish a rate, and a second cache might hold none or a hundred.
- **No parameter names**, only types, unless a library was compiled with `-parameters`. A resolution
  check that wants argument names still cannot use this route.
- **Payload catch is `test18`'s number**, measured against `test15`'s generated forms, and carries
  the same limit: it measures whether a model can learn that grammar, not whether it catches an
  attacker.
- **One cache.** 1,083 artifacts from one machine, with the sampling limits the corpus README
  already records.

# Experiments

Code written to find something out, not to ship. Spikes, prototypes and
proofs of concept all land here — the difference between them is only
whether anyone means to keep it.

**Inside this directory the usual standards are deliberately off.** No
acceptance criteria, no test coverage bar, no architectural conformance. A
proof of concept held to production standards stops being cheap, which
removes the only reason to write one.

**Nothing leaves by being copied.** Code moves out as work written against
the project's standards, with the proof of concept as a reference rather
than a source.

Each one gets a directory and a README opening with the question it exists
to answer. When the question is answered, the finding is written up in
`docs/` and the code can go. An experiment may carry a runnable harness (test0
does) — the tests are the experiment; it does not belong in `implementations/`,
which is working code.

## What's here

| Experiment | Question | Runnable |
|---|---|---|
| [cost-model](cost-model/) | What one skill per dependency costs across real graphs (RAD-0001) | collectors + raw graphs |
| [test0](test0/) | Does a codex from graded docs change agent behaviour, and tree-sitter vs Dokka? (RAD-0009/0011) | `test0/harness` — `./gradlew test` |
| [test0/measurement](test0/measurement/) | The behaviour A/Bs — content-value, disambiguation, selection, and retrieval-at-scale (Layer 1 recall + Layer 2 agent loop) (RAD-0016/0017/0018/0019) | Python harnesses (`uv run`) |
| [test1](test1/) | The multi-language read/parse story on real `-sources.jar` — tree-sitter across five languages, Dokka arm, resolve-in-index (RAD-0015/0009) | Python (`uv run`) + `dokka-arm` Gradle |
| [test2](test2/) | Structure from bytecode — the undocumented-tail signal, kept separate from the doc-parse (RAD-0012) | `extract_bytecode.py` (`uv run`) |
| [test3](test3/) | Can documentation be checked against the structure of the library that shipped it? (RAD-0006 mitigation 4, RAD-0020) | `ground_prose.py` (`uv run`) |
| [test4](test4/) | How much attacker-controlled text does each language's harvest path deliver? (RAD-0006) | `harvest_surface.py` (`uv run`) |

A runnable experiment is added to the public site once its harness works — a
scaffold-only test is not yet listed.

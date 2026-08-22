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
| test1 *(planned)* | The multi-language KMP read/parse story (RAD-0015/0009) | — |
| test2 *(planned)* | Structure from bytecode — the undocumented-tail signal, kept separate from the doc-parse (RAD-0012) | — |

A runnable experiment is added to the public site once its harness works — a
scaffold-only test is not yet listed.

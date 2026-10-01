# Naming a skill source file

**Question:** does each toolchain accept a source file holding only a package declaration and a doc comment — under a plain name (`skill`) and a hyphenated one (`skill-info`) — without an error, a warning, or an emitted artifact?

**Answer, in eight toolchains:** yes, under both names, silently — Rust by never compiling an unreached file while still packaging it. Findings and the version line are in [RAD-0073](../../../docs/knowledge/research/RAD-0073-a-skill-written-as-source.md), test 2.

Stability: spike. Kotlin Multiplatform is covered separately in `../kmp/`.

```bash
./run.sh
```

Needs `javac`, `kotlinc`, `python3`, `node`, `swift`, `go` and `cargo` on the path, and `tsc` on the path or named by `TSC`. Set `WORK` to keep the build directories somewhere specific; otherwise they go to a temp directory. `results.txt` is the output of the recorded run.

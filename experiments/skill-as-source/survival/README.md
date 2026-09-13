# Does a skill-info file survive publishing

**Question:** when a library holding a documentation-only `skill-info` source file is packaged by its ecosystem's ordinary publishing tool, with no configuration added for the skill, is the file in what gets published?

**Answer:** yes in every source distribution measured — Gradle sources jars, npm tarballs, PyPI sdists and wheels under three backends, a Go module zip and vendor directory, a Rust crate and a Swift source archive. The exception is a JavaScript bundle. Findings are in [RAD-0073](../../../docs/knowledge/research/RAD-0073-a-skill-written-as-source.md), test 1; Kotlin Multiplatform is in `../kmp/`.

Stability: spike.

```bash
./run.sh
```

Needs Gradle (or `GRADLE=path`), `node` and `npm`, `bun`, `tsc` (or `TSC=path`), `uv`, `go`, `cargo`, `git` and `swift`. Downloads build backends and `golang.org/x/mod` on first run. Set `WORK` to keep the build directories; `results.txt` is the output of the recorded run.

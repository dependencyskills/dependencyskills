# Finding a skill-info file where consumers put it

**Question:** once a package manager has resolved a library holding `skill-info`, can a harvester that knows only the filename find it, and recover the skill text without a parser?

**Answer:** yes, in the Gradle cache, a Maven local repository, `node_modules`, `site-packages`, the Go module cache and SwiftPM checkouts. Findings are in [RAD-0073](../../../docs/knowledge/research/RAD-0073-a-skill-written-as-source.md), test 3.

Stability: spike.

```bash
SURVIVAL=<work dir of a ../survival/run.sh run> ./run.sh
```

Needs the tools `../survival/run.sh` needs, and `../kmp/` published first. Every cache is isolated under `WORK`; nothing is written to the real Gradle, Maven or Go caches. `results.txt` is the output of the recorded run.

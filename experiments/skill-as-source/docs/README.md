# Does a skill-info file reach the rendered docs

**Question:** when each ecosystem's standard documentation generator runs over a library holding `skill-info`, does the skill text end up on a page a person reads — and what happens if the same text goes in the language's existing package-documentation slot instead?

**Answer:** it stays out in Javadoc, Dokka, `go doc`, rustdoc, TypeDoc and DocC; pydoc lists the name and pdoc renders it as a module page. Reusing `package-info.java` or a Go comment above the package clause renders it every time. Findings are in [RAD-0073](../../../docs/knowledge/research/RAD-0073-a-skill-written-as-source.md), test 4.

Stability: spike.

```bash
SURVIVAL=<work dir of a ../survival/run.sh run> ./run.sh
```

Needs the tools `../survival/run.sh` needs plus `uvx` and network access for Dokka, pdoc, TypeDoc and swift-docc-plugin. `results.txt` is the output of the recorded run.

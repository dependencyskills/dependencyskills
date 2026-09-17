# Naming the skill file

**Question:** what should the per-package skill file be called — and does it have to be a source file at all?

**Answer so far:** a plain `SKILL.md` in the package directory survives the JVM sources jars (Gradle, Maven, every Kotlin Multiplatform target), hatchling and uv_build, Go and Cargo; it is lost under setuptools and npm's `files: ["dist"]`, and SwiftPM warns. Findings in [RAD-0075](../../../docs/knowledge/research/RAD-0075-naming-the-skill-file.md).

Stability: spike.

```bash
GRADLE=<gradle> TSC=<tsc> ./markdown-survival.sh
```

```bash
./recognition-probe.sh <output-file>
```

`markdown-survival.txt` and `recognition-probe.txt` are the recorded runs. The probe is primed by its own question and is kept as a weak signal; see RAD-0075.

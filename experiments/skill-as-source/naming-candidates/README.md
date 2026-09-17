# Naming the skill file

**Question:** what should the per-package skill file be called — and does it have to be a source file at all?

Three shapes are in play for the same text — a doc comment, a `SKILL.md` beside the code, and a raw string constant — and the scripts here measure what each one reaches.

**Answer so far:** a plain `SKILL.md` in the package directory survives the JVM sources jars (Gradle, Maven, every Kotlin Multiplatform target), hatchling and uv_build, Go and Cargo; it is lost under setuptools and npm's `files: ["dist"]`, and SwiftPM warns. Findings in [RAD-0075](../../../docs/knowledge/research/RAD-0075-naming-the-skill-file.md).

Stability: spike.

```bash
GRADLE=<gradle> TSC=<tsc> ./markdown-survival.sh
```

```bash
GRADLE=<gradle> ./binary-reach.sh
```

Publishes a multiplatform library whose skill is `val skill = """…"""`, lists every artifact that carries the text, renders the docs with Dokka for a public and an internal declaration, and shrinks a consumer with R8 with and without a keep rule for the library. The R8 step needs the Android SDK command-line tools and skips itself without them.

```bash
GRADLE=<gradle> ./resource-routes.sh
```

Publishes one multiplatform library carrying the same skill on every resource route at once — `jvmMain` resources, `commonMain` resources, Android `res/raw` — and lists which artifact each lands in. Needs the Android SDK.

```bash
./recognition-probe.sh <output-file>
```

Each script writes its recorded run beside it: `markdown-survival.txt`, `binary-reach.txt`, `resource-routes.txt`, `recognition-probe.txt`. The probe is primed by its own question and is kept as a weak signal; see RAD-0075.

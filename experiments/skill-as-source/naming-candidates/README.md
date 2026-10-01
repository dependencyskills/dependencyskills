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
GRADLE=<gradle> ./skills-source-dir.sh      # does a skills/ directory beside the source ship
GRADLE=<gradle> ./collisions.sh             # what happens when two skills share a path
GRADLE=<gradle> ./markdown-in-docs.sh       # does a SKILL.md reach the rendered documentation
GRADLE=<gradle> ./kmp-consumer-routes.sh    # every route a multiplatform library reaches a consumer by
GRADLE=<gradle> ./npm-resource-root.sh      # where a resource lands in the published npm package
```

```bash
GRADLE=<gradle> ./xcframework-routes.sh
```

Eight mechanisms tried against one XCFramework — source file, two resource placements, an exported and an internal raw-string constant, a KDoc comment, and three writes into the `.framework` bundle — each with its own sentinel, measured in the linked bundle and again after assembly. Needs Xcode. The bundle writes happen in a `doLast` on the link task, which is where a plugin has to do them; staging the same writes from the shell between two Gradle invocations triggers a relink that regenerates the `Info.plist` and produces a false negative.

```bash
./recognition-probe.sh <output-file>
```

```bash
python3 coordinate-lengths.py --google
```

How long a library's coordinate runs once it is a skill name, how often it passes the 64-character limit and is cut, and whether two libraries ever meet at one name — over this machine's caches (aggregates only, since private coordinates are mixed in) and all of Google's Maven repository (named, since it is public). Uses the codex's own `skill_name`.

Each script writes its recorded run beside it, as `<script>.txt`. The probe is primed by its own question and is kept as a weak signal; see RAD-0075.

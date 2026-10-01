# A skill file in commonMain

**Question:** does one documentation-only `skill-info.kt` in `commonMain` compile without warnings on every Kotlin Multiplatform target, and does it reach each target's published output?

**Answer:** yes. No target warns with every warning an error. The file is in all nine `-sources.jar` files, root and per target, and in no binary. Findings are in [RAD-0073](../../../docs/knowledge/research/RAD-0073-a-skill-written-as-source.md), test 2.

Stability: spike. Targets: JVM, Android, JS, Wasm, iOS arm64, iOS simulator arm64, macOS arm64, Linux x64.

```bash
gradle publishAllPublicationsToLocalRepository
```

```bash
./inspect.sh
```

Needs Gradle 9.7 or later, an Android SDK named by `ANDROID_HOME`, and a macOS host for the Apple targets. Publishes to `build/repo`; `inspect.sh` writes `results.txt`, the output of the recorded run.

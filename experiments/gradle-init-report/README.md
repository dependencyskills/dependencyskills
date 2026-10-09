# gradle-init-report

Whether a Gradle init script can stand in for the build plugin's consumer half — the dependency report the lookup reads, and the sources jars that carry dependencies' skills — so a project needs nothing added to its build and nothing has to be published ([RAD-0081](../../docs/knowledge/research/RAD-0081-tools-carried-in-the-librarian-skill.md)).

```
./gradlew --init-script <path>/report.init.gradle.kts dependencySkillsReport --no-configuration-cache
```

The script has since moved into the skill that carries it: [`implementations/agent-skills/librarian/scripts/report.init.gradle.kts`](../../implementations/agent-skills/librarian/scripts/report.init.gradle.kts). What is recorded here is the measurement.

Every project resolves its main compile classpaths (never tests, release over debug), fetches their sources jars the way the plugin does — by reselecting the documentation variant — and writes `build/dependencyskills/fragment.tsv`; the root task merges them into `build/dependencyskills/bom.cdx.json`. Multiplatform platform modules fold into their root, and a library an included build supplies is reported under its coordinate with the directory its skill is read from.

Measured on one consuming project of 64 Gradle projects, 2026-10-03: 32 seconds cold, 3 warm; 902 components against the plugin's 900; the included build's 27 libraries identical. The remaining differences, about 20 coordinates either way, are not reconciled yet. Catalog entries no module resolves are not reported.

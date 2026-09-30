# maven

The Maven counterpart of the [Gradle plugin](../gradle/), `org.dependencyskills.maven:dependency-skills-maven-plugin`, prefix `dependency-skills`. **Its own build root** (ADR-0005), with its own wrapper: `./mvnw verify` here builds it, runs its unit tests, and runs a real library build and a real consumer build against it.

It does what the Gradle plugin does, the same way, so the lightweight codex cannot tell which build system a project uses: the same skill name for a coordinate, the same SBOM, the same agent skills in the same places, and the same `dependencyskills-lock.json`.

## The two goals

A project declares the goal for each role it has. **A goal not declared is a role not configured**, the Maven equivalent of the Gradle plugin's `consumer { }` and `author { }` blocks.

```xml
<plugin>
  <groupId>org.dependencyskills.maven</groupId>
  <artifactId>dependency-skills-maven-plugin</artifactId>
  <version>0.0.1</version>
  <executions>
    <execution>
      <goals>
        <goal>consumer</goal>   <!-- this project uses libraries -->
        <goal>author</goal>     <!-- this project publishes one -->
      </goals>
    </execution>
  </executions>
</plugin>
```

**`consumer`** (bound to `generate-sources`) reports what the module compiles against — every library the code can import, or only the declared ones with `-DdependencySkills.transitive=false` — as a CycloneDX SBOM at `target/dependencyskills/bom.cdx.json` in the root project, merged by module, so building one module keeps the others' entries. It fetches each reported dependency's sources jar through Maven's own resolver, because a JVM library's skill travels in one and a build never downloads it (RAD-0079); `-DdependencySkills.fetchSources=false` turns that off. A rewrite that added dependencies names them in the build output. It writes the `librarian` agent skill.

**`author`** (bound to `generate-sources`) packages the library's own skill into its sources jar. The author writes it at `src/main/skills/<name>/SKILL.md`, a valid Agent Skill directory named for the library's coordinate; `mvn -q dependency-skills:name` prints the name and path, so nobody computes it. The skill is staged under `target/generated-sources/dependencyskills/skills/<name>/` and added as a compile source root, which `maven-source-plugin` packages: the sources jar carries `skills/<name>/SKILL.md`, with `references/` and `assets/`, never `scripts/`, and the main jar is untouched. The library must attach a sources jar, as every library on Maven Central does. The skill is checked as the Gradle plugin checks it — name, directory, frontmatter, `allowed-tools`, `metadata.version` against the version being built — with warnings, not failures, for the alpha. It writes the `to-library-skill` agent skill.

## The agent skills

Each goal writes its skill to `.agents/skills/<skill>/` at the root of the build, and a copy to `.claude/skills/<skill>/` where the root has a `.claude/` directory (`<claudeCode>` overrides). Copies, never links, recorded with their digests in `dependencyskills-lock.json` at the root, the file the Gradle plugin and the installer keep too. `<refresh>` decides what happens to an edited copy: `Always`, the default, replaces it with a warning that says so; `UnlessEdited` keeps it, with a warning that it was not updated. `-DdependencySkills.skip=true` turns off the whole plugin.

**Commit `dependencyskills-lock.json` if and only if you commit the skills it records**, as with any lock file. Committed together, a fresh clone knows its copies are unedited, and an update shows in review as the skill's diff beside the lock file's. Skills committed without it look edited to every fresh clone: the next update overwrites them with a warning, or under `UnlessEdited` keeps them and warns every build. Ignore the skills, and ignore it too. It holds only paths inside the project and digests, and changes only when a skill does.

## Tests

```
./mvnw verify
```

Unit tests share the Gradle plugin's naming vectors and its agent-skill cases; `src/it/` holds the two builds, run by `maven-invoker-plugin` against the plugin as installed: `author` checks the sources jar, `consumer` the SBOM, the skills and the lock file.

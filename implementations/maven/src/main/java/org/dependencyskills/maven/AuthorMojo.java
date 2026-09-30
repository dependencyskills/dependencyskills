package org.dependencyskills.maven;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * The library author's half: ships this library's own skill in its sources jar, under the library's
 * coordinate, and writes the {@code librarian-skill-author} agent skill with which an agent writes it.
 *
 * <p>The author writes an Agent Skill directory named for the skill, which {@code name} prints:
 *
 * <pre>src/main/skills/&lt;name&gt;/SKILL.md</pre>
 *
 * <p>and every sources jar carries it at {@code skills/<name>/} — what the Gradle plugin produces for a JVM
 * library, and where the lightweight codex looks. {@code references/} and {@code assets/} travel with it;
 * {@code scripts/} never does.
 *
 * <p><b>How it reaches the sources jar.</b> The skill is copied to
 * {@code target/generated-sources/dependencyskills/skills/<name>/}, and that directory is added as a compile
 * source root. {@code maven-source-plugin} packages every compile source root, so the sources jar carries it;
 * the compiler reads only {@code .java} files, so nothing else changes, and the main jar never sees it. The
 * library must attach a sources jar, as every library published to Maven Central does.
 *
 * <p>Declaring this goal is what configures the author's half: a build without it gets neither.
 */
@Mojo(name = "author", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class AuthorMojo extends AbstractSkillsMojo {

    /** Where an author's skills root is, relative to the module. */
    static final String SKILLS = "src/main/skills";

    /** Where the skill is staged for the sources jar. */
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/dependencyskills", readonly = true)
    private File outputDirectory;

    @Override
    public void execute() {
        if (skip) {
            return;
        }
        writeAgentSkill(AgentSkills.AUTHOR_SKILL, "author");
        if ("pom".equals(project.getPackaging())) {
            return;
        }
        try {
            packageSkill();
        } catch (IOException e) {
            getLog().warn("dependencyskills: the library's skill was not packaged: " + e.getMessage());
        }
    }

    private void packageSkill() throws IOException {
        String name = SkillName.of(project.getGroupId(), project.getArtifactId());
        String expectedPath = SKILLS + "/" + name + "/SKILL.md";
        Path root = project.getBasedir().toPath().resolve(SKILLS);
        if (!Files.isDirectory(root)) {
            return;
        }
        List<Path> candidates = new ArrayList<>();
        try (Stream<Path> children = Files.list(root)) {
            children.filter(p -> Files.isRegularFile(p.resolve("SKILL.md"))).sorted().forEach(candidates::add);
        }
        Path skill;
        if (Files.isRegularFile(root.resolve(name).resolve("SKILL.md"))) {
            skill = root.resolve(name);
        } else if (candidates.size() == 1) {
            skill = candidates.get(0);
        } else if (Files.isRegularFile(root.resolve("SKILL.md"))) {
            skill = root;
        } else {
            if (!candidates.isEmpty()) {
                getLog().warn("dependencyskills: no skill packaged: found "
                    + String.join(", ", candidates.stream().map(p -> p.getFileName().toString()).toList())
                    + " under skills/, and none is named '" + name + "'. A library ships one skill, at " + expectedPath);
            }
            return;
        }
        String text = Files.readString(skill.resolve("SKILL.md"), StandardCharsets.UTF_8);
        String directory = skill.equals(root) ? "" : skill.getFileName().toString();
        for (String warning : SkillCheck.warnings(text, directory, name, project.getVersion(), expectedPath,
            Files.exists(skill.resolve("scripts")))) {
            getLog().warn("dependencyskills: " + warning);
        }

        // Always into the right name, whatever the source directory is called, so a misplaced skill still
        // ships where a consumer looks for it; the check tells the author to move it.
        Path staged = outputDirectory.toPath().resolve("skills").resolve(name);
        deleteRecursively(outputDirectory.toPath().resolve("skills"));
        Files.createDirectories(staged);
        Files.copy(skill.resolve("SKILL.md"), staged.resolve("SKILL.md"));
        for (String part : List.of("references", "assets")) {
            Path from = skill.resolve(part);
            if (Files.isDirectory(from)) {
                try (Stream<Path> walk = Files.walk(from)) {
                    for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                        Path to = staged.resolve(part).resolve(from.relativize(file).toString());
                        Files.createDirectories(to.getParent());
                        Files.copy(file, to);
                    }
                }
            }
        }
        project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
        getLog().debug("dependencyskills: staged the skill " + name + " for the sources jar");
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            for (Path path : (Iterable<Path>) walk.sorted(java.util.Comparator.reverseOrder())::iterator) {
                Files.delete(path);
            }
        }
    }
}

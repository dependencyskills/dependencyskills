package org.dependencyskills.maven;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;

/**
 * The consumer's half: reports what this module compiles against, for the lightweight codex, fetches those
 * dependencies' sources jars, where their skills travel, and writes the {@code librarian} agent skill.
 *
 * <p><b>The report</b> is the CycloneDX SBOM the Gradle plugin writes, at
 * {@code target/dependencyskills/bom.cdx.json} in the root project: the compile classpath — what the
 * module can import — each component naming the modules that resolve it. Every importable library, unless
 * {@code transitive} is switched off, as in the Gradle plugin.
 *
 * <p><b>The sources jars</b> are fetched because a build never downloads them, and a JVM library's skill
 * travels in one: on a machine that only builds from the command line the lookup would otherwise find
 * nothing (RAD-0079). Through Maven's own resolver, repositories and credentials, leniently — a library
 * without sources, or an offline build, is skipped rather than failed.
 *
 * <p>Declaring this goal is what configures the consumer's half: a build without it gets none of it.
 * Nothing here may fail a build.
 */
@Mojo(name = "consumer", defaultPhase = LifecyclePhase.GENERATE_SOURCES,
    requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public class ConsumerMojo extends AbstractSkillsMojo {

    private final RepositorySystem repositorySystem;

    /**
     * Report everything the compile classpath resolved — every library the code can import, a compile-scope
     * dependency's own compile dependencies included — rather than only what this module declared. On by default,
     * as in the Gradle plugin: a library that reaches the code through another is one its agent writes calls against.
     */
    @Parameter(property = "dependencySkills.transitive", defaultValue = "true")
    private boolean transitive;

    /** Whether to fetch the sources jar of every reported dependency. On by default. */
    @Parameter(property = "dependencySkills.fetchSources", defaultValue = "true")
    private boolean fetchSources;

    @Inject
    public ConsumerMojo(RepositorySystem repositorySystem) {
        this.repositorySystem = repositorySystem;
    }

    @Override
    public void execute() {
        if (skip) {
            return;
        }
        List<Artifact> reported = reported();
        try {
            report(reported);
        } catch (Exception e) {
            getLog().warn("dependencyskills: the dependency report was not written: " + e.getMessage());
        }
        if (fetchSources) {
            fetchSources(reported);
        }
        writeAgentSkill(AgentSkills.LIBRARIAN, "consumer");
    }

    /** The compile classpath's module artifacts: every one, or the declared ones only when transitive is off. */
    private List<Artifact> reported() {
        Set<String> declared = project.getDependencies().stream()
            .map(d -> d.getGroupId() + ":" + d.getArtifactId())
            .collect(Collectors.toSet());
        List<Artifact> reported = new ArrayList<>();
        for (Artifact artifact : project.getArtifacts()) {
            String scope = artifact.getScope();
            if (!(Artifact.SCOPE_COMPILE.equals(scope) || Artifact.SCOPE_PROVIDED.equals(scope))) {
                continue;
            }
            if (!transitive && !declared.contains(artifact.getGroupId() + ":" + artifact.getArtifactId())) {
                continue;
            }
            reported.add(artifact);
        }
        return reported;
    }

    private void report(List<Artifact> reported) throws Exception {
        Path root = root();
        MavenProject top = session.getTopLevelProject();
        Path file = Path.of((top != null ? top : project).getBuild().getDirectory()).resolve("dependencyskills/bom.cdx.json");
        Set<String> modules = new TreeSet<>();
        for (MavenProject module : session.getAllProjects()) {
            modules.add(key(module));
        }
        List<String> coordinates = reported.stream()
            .map(a -> a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getBaseVersion())
            .toList();
        // The project directory, as the Gradle plugin names a scope: a path cannot collide.
        List<String> added = Sbom.record(file, root.toAbsolutePath().toString(), key(project), coordinates, modules);
        if (!added.isEmpty()) {
            List<String> shown = added.stream().limit(5).map(c -> c.substring(0, c.lastIndexOf(':'))).toList();
            int more = added.size() - shown.size();
            getLog().info("dependencyskills: new since the last build: " + String.join(", ", shown)
                + (more > 0 ? " and " + more + " more" : "")
                + ". Any of them may ship a guide; an agent with the librarian lookup can check with list_guides.");
        }
        getLog().debug("dependencyskills: " + coordinates.size() + " coordinates written to " + file);
    }

    private void fetchSources(List<Artifact> reported) {
        int fetched = 0;
        for (Artifact artifact : reported) {
            ArtifactRequest request = new ArtifactRequest(
                new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), "sources", "jar", artifact.getVersion()),
                project.getRemoteProjectRepositories(), null);
            try {
                repositorySystem.resolveArtifact(session.getRepositorySession(), request);
                fetched++;
            } catch (ArtifactResolutionException noSources) {
                // A library without sources, or an offline build: skipped, never failed.
            }
        }
        getLog().debug("dependencyskills: " + fetched + " sources jars for " + reported.size() + " dependencies");
    }

    /** A module's name in the report: its coordinate, which is unique in a reactor. */
    private static String key(MavenProject module) {
        return module.getGroupId() + ":" + module.getArtifactId();
    }
}

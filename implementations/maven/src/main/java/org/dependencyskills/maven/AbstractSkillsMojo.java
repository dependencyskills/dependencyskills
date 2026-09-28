package org.dependencyskills.maven;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/** What every goal needs: the module, the build's root, and the agent skill its role writes. */
abstract class AbstractSkillsMojo extends AbstractMojo {

    /** Serialises agent-skill writing across the modules of a parallel build, which share the root's files. */
    private static final Object WRITING = new Object();

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected MavenSession session;

    /** Turns the plugin off entirely: it observes nothing and writes nothing. */
    @Parameter(property = "dependencySkills.skip", defaultValue = "false")
    protected boolean skip;

    /**
     * What happens to an edited copy of this goal's agent skill: {@code Always} replaces it, with a warning,
     * and is the default; {@code UnlessEdited} keeps it, with a warning that it was not updated.
     */
    @Parameter(defaultValue = "Always")
    protected SkillRefresh refresh;

    /**
     * Whether to write a copy of the agent skill for Claude Code too, in {@code .claude/skills/}, the only place
     * it reads a project's skills. Defaults to whether the root of the build has a {@code .claude/} directory.
     */
    @Parameter
    protected Boolean claudeCode;

    /** The root of the build: the top-level project of the reactor, where agents look for skills. */
    protected Path root() {
        MavenProject top = session.getTopLevelProject();
        File basedir = (top != null ? top : project).getBasedir();
        return basedir.toPath();
    }

    /** Writes {@code skill}, for the goal named {@code block}. Nothing here may fail a build. */
    protected void writeAgentSkill(String skill, String block) {
        Path root = root();
        boolean claude = claudeCode != null ? claudeCode : Files.isDirectory(root.resolve(".claude"));
        synchronized (WRITING) {
            try {
                AgentSkills.write(root, skill, refresh, claude, block, getLog()::info, getLog()::warn);
            } catch (Exception e) {
                getLog().warn("dependencyskills: the " + skill + " skill was not written: " + e.getMessage());
            }
        }
    }
}

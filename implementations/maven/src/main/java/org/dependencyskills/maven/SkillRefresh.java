package org.dependencyskills.maven;

/**
 * What a project does with an agent skill it has edited, when the plugin carries a different version.
 *
 * <p>{@link #Always} by default, so a skill stays what the plugin version ships unless a project
 * explicitly says otherwise, in the goal's configuration: {@code <refresh>UnlessEdited</refresh>}.
 * A skill nobody edited is updated either way. The same setting, with the same names, as the Gradle plugin.
 */
public enum SkillRefresh {
    /**
     * Replace the skill with the version the plugin carries on every build, edited or not, with a warning
     * in the build output whenever edits are overwritten. The default.
     */
    Always,

    /** Keep an edited skill, with a warning that it was not updated. */
    UnlessEdited
}

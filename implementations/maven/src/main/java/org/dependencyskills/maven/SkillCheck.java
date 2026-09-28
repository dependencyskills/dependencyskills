package org.dependencyskills.maven;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What would make a library's skill ship but not work, or not be a valid Agent Skill where it is written.
 *
 * <p>Warnings rather than failures, for the alpha, exactly as the Gradle plugin's
 * {@code CheckDependencySkill}: a publish failing on a new file's frontmatter is a worse first
 * experience than a line in the build output. The messages are the Gradle plugin's, word for word.
 */
final class SkillCheck {

    private static final Pattern FRONTMATTER = Pattern.compile("\\A---\\r?\\n(.*?)\\r?\\n---", Pattern.DOTALL);
    private static final Pattern NAME = Pattern.compile("(?m)^name:\\s*(.+)$");
    private static final Pattern DESCRIPTION = Pattern.compile("(?m)^description:\\s*(.+)$");
    private static final Pattern ALLOWED_TOOLS = Pattern.compile("(?m)^allowed-tools:");
    private static final Pattern TOP_LEVEL = Pattern.compile("(?m)^([A-Za-z][\\w-]*):");
    private static final Set<String> FIELDS = Set.of("name", "description", "license", "compatibility", "metadata", "allowed-tools");
    /** {@code version:} indented under {@code metadata:}, which is where the specification puts it. */
    private static final Pattern VERSION = Pattern.compile("(?m)^metadata:\\s*\\r?\\n(?:[ \\t]+.*\\r?\\n)*?[ \\t]+version:\\s*(.+)$");

    private SkillCheck() {
    }

    /**
     * The warnings for a skill whose {@code SKILL.md} reads {@code text}, found in a directory named
     * {@code directory} ({@code ""} when it is the flat file directly under {@code skills/}).
     */
    static List<String> warnings(String text, String directory, String expectedName, String expectedVersion,
                                 String expectedPath, boolean scripts) {
        List<String> warnings = new ArrayList<>();
        if (directory.isEmpty()) {
            warnings.add("SKILL.md is not in a directory named for the skill, so it is not a valid Agent Skill where it "
                + "is written. It was packaged under the right name; move it to " + expectedPath);
        } else if (!directory.equals(expectedName)) {
            warnings.add("the skill's directory is '" + directory + "'; the Agent Skills specification requires it to "
                + "match the skill's name, '" + expectedName + "'. It was packaged under the right name; move it to " + expectedPath);
        }
        Matcher frontmatterMatch = FRONTMATTER.matcher(text);
        if (!frontmatterMatch.find()) {
            warnings.add("SKILL.md has no frontmatter. An agent decides whether to read a skill from its `name` and "
                + "`description`, so without them it will rarely be read.");
        } else {
            String frontmatter = frontmatterMatch.group(1);
            String name = scalar(NAME, frontmatter);
            String description = first(DESCRIPTION, frontmatter);
            if (!expectedName.equals(name)) {
                warnings.add("SKILL.md `name` is " + (name == null ? "missing" : "'" + name + "'")
                    + "; it should be '" + expectedName + "', the library's coordinate as a skill name");
            }
            if (description == null || description.isBlank()) {
                warnings.add("SKILL.md has no `description`, which is what tells an agent when to read it");
            }
            Set<String> unknown = new TreeSet<>();
            Matcher topLevel = TOP_LEVEL.matcher(frontmatter);
            while (topLevel.find()) {
                if (!FIELDS.contains(topLevel.group(1))) {
                    unknown.add(topLevel.group(1));
                }
            }
            if (!unknown.isEmpty()) {
                warnings.add("SKILL.md has fields the Agent Skills specification does not allow: " + String.join(", ", unknown)
                    + ". Only " + String.join(", ", new TreeSet<>(FIELDS)) + " are allowed; put anything else under `metadata`.");
            }
            if (ALLOWED_TOOLS.matcher(frontmatter).find()) {
                warnings.add("SKILL.md declares `allowed-tools`. A dependency skill may not grant an agent tools; "
                    + "consumers treat it as a finding (spec/content.md).");
            }
            String version = scalar(VERSION, frontmatter);
            if (version == null) {
                warnings.add("SKILL.md has no `metadata.version`; state the library version it describes ('"
                    + expectedVersion + "'), so an agent can tell it is current");
            } else if (!version.equals(expectedVersion)) {
                warnings.add("SKILL.md says it describes version '" + version + "', but '" + expectedVersion
                    + "' is being built. Check the skill still holds, then update `metadata.version`.");
            }
        }
        if (scripts) {
            warnings.add("the skill's scripts/ directory is not packaged. A library's skill tells an agent how to use "
                + "the library; it never gives the agent something to run.");
        }
        return warnings;
    }

    private static String first(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    /** A YAML scalar with its quotes removed. */
    private static String scalar(Pattern pattern, String text) {
        String value = first(pattern, text);
        if (value == null) {
            return null;
        }
        int start = 0;
        int end = value.length();
        while (start < end && (value.charAt(start) == '"' || value.charAt(start) == '\'')) {
            start++;
        }
        while (end > start && (value.charAt(end - 1) == '"' || value.charAt(end - 1) == '\'')) {
            end--;
        }
        return value.substring(start, end);
    }
}

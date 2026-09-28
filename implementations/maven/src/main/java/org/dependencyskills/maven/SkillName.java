package org.dependencyskills.maven;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A library's skill name: its coordinate, made a legal Agent Skills name.
 *
 * <p>The name is the coordinate so that it is unique per library — an artifactId alone is not, since
 * two groups can publish a {@code core} — and so that no author types it. The specification allows
 * only lowercase letters, digits and single hyphens, at most 64 characters, so three steps:
 *
 * <ol>
 *   <li>{@code group:artifact}, lowercased, every run of anything else one hyphen:
 *       {@code com.example.acme:acme-text} is {@code com-example-acme-acme-text}.</li>
 *   <li>Too long: each group segment shrinks to its first and last letter and the artifact stays whole.</li>
 *   <li>Still too long: cut to 55 characters, ending in eight hex digits of a SHA-256 of {@code group:artifact}.</li>
 * </ol>
 *
 * <p>Must stay identical to the Gradle plugin's {@code SkillPackaging.skillName} and the lightweight
 * codex's {@code skill_name}; all three are tested against the same vectors (RAD-0075).
 */
public final class SkillName {

    /** The specification's limit on a skill's name. */
    static final int MAX_NAME = 64;

    private static final Pattern NOT_LEGAL = Pattern.compile("[^a-z0-9]+");

    private SkillName() {
    }

    /** The skill name for {@code group:artifact}. */
    public static String of(String group, String artifact) {
        String coordinate = group + ":" + artifact;
        String full = legal(coordinate);
        if (full.length() <= MAX_NAME) {
            return full;
        }
        String compact = Arrays.stream(NOT_LEGAL.split(group.toLowerCase(Locale.ROOT)))
            .filter(segment -> !segment.isEmpty())
            .map(segment -> segment.length() < 2 ? segment : "" + segment.charAt(0) + segment.charAt(segment.length() - 1))
            .collect(Collectors.joining("-"));
        String shortened = trimHyphens(compact + "-" + legal(artifact));
        if (shortened.length() <= MAX_NAME) {
            return shortened;
        }
        String hash = sha256(coordinate).substring(0, 8);
        String cut = shortened.substring(0, MAX_NAME - 9);
        while (cut.endsWith("-")) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "-" + hash;
    }

    private static String legal(String text) {
        return trimHyphens(NOT_LEGAL.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("-"));
    }

    private static String trimHyphens(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && text.charAt(start) == '-') {
            start++;
        }
        while (end > start && text.charAt(end - 1) == '-') {
            end--;
        }
        return text.substring(start, end);
    }

    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every JVM has SHA-256", e);
        }
    }
}

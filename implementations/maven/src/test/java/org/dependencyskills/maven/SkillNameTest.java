package org.dependencyskills.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SkillNameTest {

    @Test
    void theSameVectorsAsTheGradlePluginAndTheLightweightCodex() {
        // Shared with SkillPackagingTest and test_names.py: all three must name a skill identically, or none is found.
        Map<String, String> vectors = Map.of(
            "com.example.acme:acme-text", "com-example-acme-acme-text",
            "com.example:Acme_Text.core", "com-example-acme-text-core",
            "com.google.android.apps.common.testing.accessibility.framework:accessibility-test-framework",
            "cm-ge-ad-as-cn-tg-ay-fk-accessibility-test-framework",
            "io.example.instrumentation:example-instrumentation-annotations-support-library",
            "io-ee-in-example-instrumentation-annotations-support-library",
            "com.example:an-artifact-name-so-long-that-even-a-compacted-group-cannot-save-it-at-all",
            "cm-ee-an-artifact-name-so-long-that-even-a-compacted-gr-a08a1e4d");
        vectors.forEach((coordinate, expected) -> {
            String[] parts = coordinate.split(":");
            assertEquals(expected, SkillName.of(parts[0], parts[1]), coordinate);
        });
    }
}

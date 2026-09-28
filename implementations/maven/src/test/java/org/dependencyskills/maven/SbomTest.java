package org.dependencyskills.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SbomTest {

    @TempDir
    Path temp;

    @Test
    void eachModuleKeepsItsOwnEntriesWhenAnotherIsBuilt() throws Exception {
        Path file = temp.resolve("target/dependencyskills/bom.cdx.json");
        Set<String> modules = Set.of("com.example:app", "com.example:core");
        Sbom.record(file, "/project", "com.example:core", List.of("com.example:alpha:1.0"), modules);
        List<String> added = Sbom.record(file, "/project", "com.example:app", List.of("com.example:beta:2.0"), modules);

        Map<String, List<String>> components = Sbom.components(Files.readString(file));
        assertEquals(List.of("com.example:core"), components.get("com.example:alpha:1.0"));
        assertEquals(List.of("com.example:app"), components.get("com.example:beta:2.0"));
        assertEquals(List.of("com.example:beta:2.0"), added);
    }

    @Test
    void theFirstWriteAnnouncesNothingAndAnUnchangedOneIsNotRewritten() throws Exception {
        Path file = temp.resolve("bom.cdx.json");
        Set<String> modules = Set.of("com.example:app");
        assertEquals(List.of(), Sbom.record(file, "/project", "com.example:app", List.of("com.example:alpha:1.0"), modules));
        long written = Files.getLastModifiedTime(file).toMillis();
        Thread.sleep(20);
        assertEquals(List.of(), Sbom.record(file, "/project", "com.example:app", List.of("com.example:alpha:1.0"), modules));
        assertEquals(written, Files.getLastModifiedTime(file).toMillis());
    }

    @Test
    void aModuleNoLongerInTheReactorIsDropped() throws Exception {
        Path file = temp.resolve("bom.cdx.json");
        Sbom.record(file, "/project", "com.example:gone", List.of("com.example:alpha:1.0"), Set.of("com.example:gone", "com.example:app"));
        Sbom.record(file, "/project", "com.example:app", List.of("com.example:beta:2.0"), Set.of("com.example:app"));

        String sbom = Files.readString(file);
        assertFalse(sbom.contains("alpha"), sbom);
        assertTrue(sbom.contains("\"purl\":\"pkg:maven/com.example/beta@2.0\""), sbom);
    }
}

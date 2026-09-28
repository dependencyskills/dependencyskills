package org.dependencyskills.maven;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The resolved set, as the CycloneDX 1.6 SBOM the lightweight codex reads — the same file, in the same
 * shape, as the Gradle plugin writes, at {@code target/dependencyskills/bom.cdx.json} in the root project.
 *
 * <p><b>Merged by module, not replaced.</b> Each component names the modules that resolved it. A build
 * replaces the entries of the modules it resolved and keeps the rest, so building one module leaves every
 * other module's dependencies in scope; a module no longer in the reactor is dropped. Rewritten only when
 * it changed, so its modification time says when the dependencies last changed.
 */
final class Sbom {

    static final String PROJECT_PROPERTY = "dependencyskills:project";
    private static final Pattern PURL = Pattern.compile("\"purl\":\"pkg:maven/([^/\"]+)/([^@\"]+)@([^\"]+)\"");
    private static final Pattern PROJECT = Pattern.compile("\"name\":\"" + PROJECT_PROPERTY + "\",\"value\":\"([^\"]*)\"");

    /** One writer at a time within a parallel reactor; the file lock covers separate processes. */
    private static final Object LOCK = new Object();

    private Sbom() {
    }

    /**
     * Records {@code coordinates} ({@code group:artifact:version}) as what {@code module} resolves, in the SBOM
     * at {@code file}. Returns the coordinates the file gained over the one it replaced — empty on the first
     * write, when there is nothing to compare with and calling every dependency new would say nothing.
     */
    static List<String> record(Path file, String rootName, String module, Collection<String> coordinates,
                               Set<String> modules) throws IOException {
        synchronized (LOCK) {
            Files.createDirectories(file.getParent());
            Path lockFile = file.resolveSibling(file.getFileName() + ".lock");
            try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                String previous = Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
                SortedMap<String, SortedSet<String>> byCoordinate = new TreeMap<>();
                for (String coordinate : coordinates) {
                    if (coordinate.split(":").length == 3) {
                        byCoordinate.computeIfAbsent(coordinate, c -> new TreeSet<>()).add(module);
                    }
                }
                if (previous != null) {
                    components(previous).forEach((coordinate, projects) -> projects.stream()
                        .filter(p -> !p.equals(module) && modules.contains(p))
                        .forEach(p -> byCoordinate.computeIfAbsent(coordinate, c -> new TreeSet<>()).add(p)));
                }
                String text = render(rootName, byCoordinate);
                if (text.equals(previous)) {
                    return List.of();
                }
                List<String> added = new ArrayList<>();
                if (previous != null) {
                    Set<String> before = components(previous).keySet();
                    byCoordinate.keySet().stream().filter(c -> !before.contains(c)).forEach(added::add);
                }
                Files.writeString(file, text, StandardCharsets.UTF_8);
                return added;
            }
        }
    }

    private static String render(String rootName, SortedMap<String, SortedSet<String>> byCoordinate) {
        List<String> components = new ArrayList<>();
        byCoordinate.forEach((coordinate, projects) -> {
            String[] parts = coordinate.split(":");
            String purl = "pkg:maven/" + parts[0] + "/" + parts[1] + "@" + parts[2];
            List<String> properties = new ArrayList<>();
            for (String project : projects) {
                properties.add("{\"name\":\"" + PROJECT_PROPERTY + "\",\"value\":" + quote(project) + "}");
            }
            components.add("    {\"type\":\"library\",\"bom-ref\":" + quote(purl) + ",\"group\":" + quote(parts[0])
                + ",\"name\":" + quote(parts[1]) + ",\"version\":" + quote(parts[2]) + ",\"purl\":" + quote(purl)
                + ",\"properties\":[" + String.join(",", properties) + "]}");
        });
        StringBuilder text = new StringBuilder();
        text.append("{\n");
        text.append("  \"bomFormat\": \"CycloneDX\",\n");
        text.append("  \"specVersion\": \"1.6\",\n");
        text.append("  \"version\": 1,\n");
        text.append("  \"metadata\": {\n");
        text.append("    \"tools\": {\"components\": [{\"type\": \"application\", \"name\": \"dependency-skills\"}]},\n");
        text.append("    \"component\": {\"type\": \"application\", \"bom-ref\": \"root\", \"name\": ").append(quote(rootName)).append("},\n");
        // The compile classpath: what the project can import, which is what its scope must be.
        text.append("    \"properties\": [{\"name\": \"dependencyskills:classpath\", \"value\": \"compile\"}]\n");
        text.append("  },\n");
        text.append("  \"components\": [\n");
        text.append(String.join(",\n", components));
        if (!components.isEmpty()) {
            text.append('\n');
        }
        text.append("  ]\n");
        text.append("}\n");
        return text.toString();
    }

    /** {@code group:artifact:version} to the modules that resolve it, for every component in an SBOM this wrote. */
    static Map<String, List<String>> components(String sbom) {
        Map<String, List<String>> components = new TreeMap<>();
        for (String line : sbom.split("\n")) {
            Matcher purl = PURL.matcher(line);
            if (!purl.find()) {
                continue;
            }
            List<String> projects = new ArrayList<>();
            Matcher project = PROJECT.matcher(line);
            while (project.find()) {
                projects.add(project.group(1));
            }
            if (!projects.isEmpty()) {
                components.put(purl.group(1) + ":" + purl.group(2) + ":" + purl.group(3), projects);
            }
        }
        return components;
    }

    /** Minimal JSON string escaping. A coordinate is not arbitrary text, but it is not ours either. */
    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            if (c == '"') {
                out.append("\\\"");
            } else if (c == '\\') {
                out.append("\\\\");
            } else if (c < ' ') {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.append('"').toString();
    }
}

package org.dependencyskills.maven;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Writes the agent skills a project asks for into the project, where its agents look for them.
 *
 * <p>The Maven side of the Gradle plugin's {@code AgentSkills}, and deliberately indistinguishable from it:
 * {@code .agents/skills/<skill>/} at the root of the build, always, and {@code .claude/skills/<skill>/}
 * too wherever the root has a {@code .claude/} directory; copies, never links; each file recorded with
 * its digest in {@code dependencyskills-lock.json} at the root, the lock file the Gradle plugin and the
 * lightweight codex's installer keep, so any of them recognises the others' copies. A copy that no
 * longer matches its record has been edited, and {@link SkillRefresh} decides what happens to it.
 */
final class AgentSkills {

    static final String LIBRARIAN = "librarian";
    static final String TO_LIBRARY_SKILL = "to-library-skill";

    /** The lock file, at the root: committed, so it names only paths inside the project. */
    static final String LOCK = "dependencyskills-lock.json";

    private static final String BUNDLE = "org/dependencyskills/maven/skills/";

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Four-space indentation, as the Gradle plugin and the installer write the lock file, so none reformats another's. */
    private static final DefaultPrettyPrinter PRETTY = new DefaultPrettyPrinter()
        .withSeparators(Separators.createDefaultInstance().withObjectFieldValueSpacing(Separators.Spacing.AFTER))
        .withObjectIndenter(new DefaultIndenter("    ", "\n"))
        .withArrayIndenter(new DefaultIndenter("    ", "\n"));

    /** What happened to one copy of a skill. */
    enum Outcome { Written, Updated, Current, Replaced, KeptEdited, KeptLink }

    private AgentSkills() {
    }

    /** The skill as this plugin version carries it: path relative to the skill's directory, to content. */
    static Map<String, byte[]> bundled(String skill) {
        String prefix = BUNDLE + skill + "/";
        URL anchor = AgentSkills.class.getClassLoader().getResource(prefix + "SKILL.md");
        if (anchor == null) {
            throw new IllegalStateException("this build of the plugin carries no skill named " + skill);
        }
        Map<String, byte[]> files = new TreeMap<>();
        try {
            if ("jar".equals(anchor.getProtocol())) {
                JarURLConnection connection = (JarURLConnection) anchor.openConnection();
                connection.setUseCaches(false);
                try (JarFile jar = connection.getJarFile()) {
                    for (JarEntry entry : java.util.Collections.list(jar.entries())) {
                        if (!entry.isDirectory() && entry.getName().startsWith(prefix)) {
                            try (InputStream in = jar.getInputStream(entry)) {
                                files.put(entry.getName().substring(prefix.length()), in.readAllBytes());
                            }
                        }
                    }
                }
            } else {
                Path directory = Paths.get(anchor.toURI()).getParent();
                try (Stream<Path> walk = Files.walk(directory)) {
                    for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                        files.put(directory.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        return files;
    }

    /** Every file under {@code directory}, relative to it, with its digest; null when there is no directory. */
    static Map<String, String> present(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return null;
        }
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(directory)) {
            for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                files.put(directory.relativize(file).toString().replace('\\', '/'), digest(Files.readAllBytes(file)));
            }
        }
        return files;
    }

    static String digest(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Brings the copy at {@code target} up to {@code carried}, as {@code refresh} allows, given what was {@code recorded}. */
    static Outcome reconcile(Path target, Map<String, byte[]> carried, Map<String, String> recorded, SkillRefresh refresh)
        throws IOException {
        if (Files.isSymbolicLink(target)) {
            return Outcome.KeptLink;
        }
        Map<String, String> wanted = digests(carried);
        Map<String, String> there = present(target);
        Outcome outcome;
        if (there == null) {
            outcome = Outcome.Written;
        } else if (there.equals(wanted)) {
            return Outcome.Current;
        } else if (recorded != null && there.equals(recorded)) {
            outcome = Outcome.Updated;
        } else if (refresh == SkillRefresh.Always) {
            outcome = Outcome.Replaced;
        } else {
            return Outcome.KeptEdited;
        }
        delete(target);
        for (Map.Entry<String, byte[]> file : carried.entrySet()) {
            Path path = target.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.write(path, file.getValue());
        }
        return outcome;
    }

    static Map<String, String> digests(Map<String, byte[]> files) {
        Map<String, String> digests = new TreeMap<>();
        files.forEach((path, bytes) -> digests.put(path, digest(bytes)));
        return digests;
    }

    private static void delete(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            for (Path path : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) {
                Files.delete(path);
            }
        }
    }

    /**
     * Writes {@code skill} into the build rooted at {@code root}. Returns true when anything was written.
     * Messages go to {@code lifecycle} and {@code warn}; nothing here may fail a build, which is the caller's to enforce.
     */
    @SuppressWarnings("unchecked")
    static boolean write(Path root, String skill, SkillRefresh refresh, boolean claudeCode, String block,
                         Consumer<String> lifecycle, Consumer<String> warn) throws IOException {
        Map<String, byte[]> carried = bundled(skill);
        Map<String, String> wanted = digests(carried);
        Map<String, Object> lock = readLock(root);
        List<String> targets = new ArrayList<>(List.of(".agents/skills/" + skill));
        if (claudeCode) {
            targets.add(".claude/skills/" + skill);
        }
        boolean changed = false;
        for (String path : targets) {
            Map<String, String> recorded = recorded(lock, path);
            switch (reconcile(root.resolve(path), carried, recorded, refresh)) {
                case Written -> lifecycle.accept("dependencyskills: wrote the " + skill + " skill to " + path
                    + "/ — commit it together with dependencyskills-lock.json, which records it");
                case Updated -> lifecycle.accept("dependencyskills: updated the " + skill + " skill in " + path
                    + "/ to the version this plugin carries");
                case Replaced -> warn.accept("dependencyskills: OVERWROTE local edits to the " + skill + " skill in " + path
                    + "/ with the version this plugin carries; if the edits were committed, version control still has them. "
                    + "To keep edits, set <refresh>UnlessEdited</refresh> on the " + block + " goal.");
                case Current -> {
                    if (wanted.equals(recorded)) {
                        continue;
                    }
                }
                case KeptEdited -> {
                    if (!wanted.equals(recorded)) {
                        warn.accept("dependencyskills: " + path + "/ has local edits, so it was NOT updated to the version this "
                            + "plugin carries, because refresh is UnlessEdited. To take the new version, delete that directory "
                            + "and build again, or remove <refresh> from the " + block + " goal.");
                    }
                    continue;
                }
                case KeptLink -> {
                    continue;
                }
            }
            record(lock, path, wanted);
            changed = true;
        }
        if (changed) {
            writeLock(root, lock);
        }
        return changed;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> readLock(Path root) throws IOException {
        Path file = root.resolve(LOCK);
        Map<String, Object> lock = new LinkedHashMap<>();
        if (Files.isRegularFile(file)) {
            try {
                lock.putAll(JSON.readValue(file.toFile(), Map.class));
            } catch (IOException unreadable) {
                // An unparseable lock file is treated as empty, and rewritten.
            }
        }
        if (!(lock.get("changes") instanceof List)) {
            lock.put("changes", new ArrayList<>());
        }
        return lock;
    }

    @SuppressWarnings("unchecked")
    static Map<String, String> recorded(Map<String, Object> lock, String path) {
        for (Object change : (List<Object>) lock.get("changes")) {
            if (change instanceof Map<?, ?> entry && "skill".equals(entry.get("kind")) && path.equals(entry.get("path"))
                && entry.get("files") instanceof Map<?, ?> files) {
                return (Map<String, String>) files;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static void record(Map<String, Object> lock, String path, Map<String, String> files) {
        List<Object> changes = new ArrayList<>();
        for (Object change : (List<Object>) lock.get("changes")) {
            if (!(change instanceof Map<?, ?> entry && "skill".equals(entry.get("kind")) && path.equals(entry.get("path")))) {
                changes.add(change);
            }
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("kind", "skill");
        entry.put("path", path);
        entry.put("files", new TreeMap<>(files));
        entry.put("by", "maven-plugin");
        changes.add(entry);
        lock.put("changes", changes);
    }

    static void writeLock(Path root, Map<String, Object> lock) throws IOException {
        String text = JSON.writer(PRETTY).writeValueAsString(lock) + "\n";
        Files.writeString(root.resolve(LOCK), text, StandardCharsets.UTF_8);
    }
}

package org.dependencyskills.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The same behaviour as the Gradle plugin's AgentSkillsTest, which is the point: either plugin recognises the other's copies. */
class AgentSkillsTest {

    @TempDir
    Path root;

    private final List<String> lifecycle = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    private boolean write(String skill, SkillRefresh refresh, boolean claude) throws Exception {
        return AgentSkills.write(root, skill, refresh, claude, "consumer", lifecycle::add, warnings::add);
    }

    private static String bundled(String path) throws Exception {
        return Files.readString(Path.of("../agent-skills", path));
    }

    @Test
    void writesTheSkillFromItsOneSourceAndRecordsIt() throws Exception {
        assertTrue(write(AgentSkills.LIBRARIAN, SkillRefresh.Always, false));

        assertEquals(bundled("librarian/SKILL.md"), Files.readString(root.resolve(".agents/skills/librarian/SKILL.md")));
        assertFalse(Files.exists(root.resolve(".claude")));
        String lock = Files.readString(root.resolve(AgentSkills.LOCK));
        assertTrue(lock.contains("\"path\": \".agents/skills/librarian\""), lock);
        assertTrue(lock.startsWith("{\n    \""), "four-space indentation, as the Gradle plugin writes it:\n" + lock);
        assertEquals(List.of("dependencyskills: wrote the librarian skill to .agents/skills/librarian/ — commit it together with dependencyskills-lock.json, which records it"), lifecycle);
    }

    @Test
    void carriesReferencesAndAssetsAndCopiesForClaudeCode() throws Exception {
        write(AgentSkills.TO_LIBRARY_SKILL, SkillRefresh.Always, true);

        assertTrue(Files.isRegularFile(root.resolve(".agents/skills/to-library-skill/references/per-language.md")));
        assertTrue(Files.isRegularFile(root.resolve(".claude/skills/to-library-skill/assets/SKILL.template.md")));
        assertFalse(Files.isSymbolicLink(root.resolve(".claude/skills/to-library-skill")));
    }

    @Test
    void aSecondBuildChangesNothing() throws Exception {
        write(AgentSkills.LIBRARIAN, SkillRefresh.Always, false);
        String lock = Files.readString(root.resolve(AgentSkills.LOCK));
        lifecycle.clear();

        assertFalse(write(AgentSkills.LIBRARIAN, SkillRefresh.Always, false));
        assertEquals(lock, Files.readString(root.resolve(AgentSkills.LOCK)));
        assertEquals(List.of(), lifecycle);
    }

    @Test
    void byDefaultAnEditedCopyIsOverwrittenWithAWarning() throws Exception {
        editedEarlierCopy();
        write(AgentSkills.LIBRARIAN, SkillRefresh.Always, false);

        assertEquals(bundled("librarian/SKILL.md"), Files.readString(root.resolve(".agents/skills/librarian/SKILL.md")));
        assertTrue(warnings.get(0).contains("OVERWROTE local edits to the librarian skill"), warnings.toString());
        assertTrue(warnings.get(0).contains("<refresh>UnlessEdited</refresh>"), warnings.toString());
    }

    @Test
    void underUnlessEditedAnEditedCopyIsKeptWithAWarning() throws Exception {
        editedEarlierCopy();
        write(AgentSkills.LIBRARIAN, SkillRefresh.UnlessEdited, false);

        assertEquals("an earlier librarian, edited", Files.readString(root.resolve(".agents/skills/librarian/SKILL.md")));
        assertTrue(warnings.get(0).contains("has local edits, so it was NOT updated"), warnings.toString());
    }

    @Test
    void anUneditedEarlierCopyIsUpdated() throws Exception {
        Path skill = root.resolve(".agents/skills/librarian/SKILL.md");
        Files.createDirectories(skill.getParent());
        Files.writeString(skill, "an earlier librarian");
        recordAs(".agents/skills/librarian", "an earlier librarian");
        write(AgentSkills.LIBRARIAN, SkillRefresh.UnlessEdited, false);

        assertEquals(bundled("librarian/SKILL.md"), Files.readString(skill));
        assertEquals(List.of(), warnings);
        assertTrue(lifecycle.get(0).startsWith("dependencyskills: updated the librarian skill"), lifecycle.toString());
    }

    @Test
    void keepsWhatOthersRecordedInTheSharedLockFile() throws Exception {
        Files.writeString(root.resolve(AgentSkills.LOCK),
            "{\"version\": \"0.0.1\", \"changes\": [{\"kind\": \"skill\", \"path\": \".agents/skills/to-library-skill\", \"files\": {}, \"by\": \"gradle-plugin\"}]}");
        write(AgentSkills.LIBRARIAN, SkillRefresh.Always, false);

        String lock = Files.readString(root.resolve(AgentSkills.LOCK));
        assertTrue(lock.contains("\"by\": \"gradle-plugin\""), lock);
        assertTrue(lock.contains("\"version\": \"0.0.1\""), lock);
    }

    private void editedEarlierCopy() throws Exception {
        Path skill = root.resolve(".agents/skills/librarian/SKILL.md");
        Files.createDirectories(skill.getParent());
        Files.writeString(skill, "an earlier librarian, edited");
        recordAs(".agents/skills/librarian", "an earlier librarian");
    }

    private void recordAs(String path, String content) throws Exception {
        Map<String, Object> lock = AgentSkills.readLock(root);
        AgentSkills.record(lock, path, Map.of("SKILL.md", AgentSkills.digest(content.getBytes(StandardCharsets.UTF_8))));
        AgentSkills.writeLock(root, lock);
    }
}

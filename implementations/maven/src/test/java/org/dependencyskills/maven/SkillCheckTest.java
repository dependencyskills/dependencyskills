package org.dependencyskills.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkillCheckTest {

    private static final String NAME = "com-example-acme-acme-text";
    private static final String PATH = "src/main/skills/" + NAME + "/SKILL.md";

    private static String skill(String frontmatter) {
        return "---\n" + frontmatter + "\n---\n\n# Acme Text\n\nUse it.\n";
    }

    @Test
    void aCorrectSkillHasNoWarnings() {
        String text = skill("name: " + NAME + "\ndescription: Format acme text.\nmetadata:\n  version: \"1.0\"");
        assertEquals(List.of(), SkillCheck.warnings(text, NAME, NAME, "1.0", PATH, false));
    }

    @Test
    void eachMistakeIsNamed() {
        String text = skill("name: acme\nauthor: someone\nallowed-tools: Bash\nmetadata:\n  version: \"0.9\"");
        List<String> warnings = SkillCheck.warnings(text, "acme", NAME, "1.0", PATH, true);
        String all = String.join("\n", warnings);

        assertTrue(all.contains("the skill's directory is 'acme'"), all);
        assertTrue(all.contains("`name` is 'acme'; it should be '" + NAME + "'"), all);
        assertTrue(all.contains("has no `description`"), all);
        assertTrue(all.contains("does not allow: author"), all);
        assertTrue(all.contains("declares `allowed-tools`"), all);
        assertTrue(all.contains("describes version '0.9', but '1.0' is being built"), all);
        assertTrue(all.contains("scripts/ directory is not packaged"), all);
    }

    @Test
    void noFrontmatterIsOneWarningNotSix() {
        List<String> warnings = SkillCheck.warnings("# Just a heading\n", NAME, NAME, "1.0", PATH, false);
        assertEquals(1, warnings.size(), String.join("\n", warnings));
        assertTrue(warnings.get(0).contains("has no frontmatter"));
    }
}

package org.dependencyskills.maven;

import org.apache.maven.plugins.annotations.Mojo;

/**
 * Prints the name this library's skill must carry, and the directory it belongs in, so neither an author
 * nor their agent ever computes the coordinate encoding by hand:
 *
 * <pre>mvn -q org.dependencyskills.maven:dependency-skills-maven-plugin:name</pre>
 */
@Mojo(name = "name", threadSafe = true)
public class NameMojo extends AbstractSkillsMojo {

    @Override
    public void execute() {
        String name = SkillName.of(project.getGroupId(), project.getArtifactId());
        // Printed whatever the log level, because printing it is the whole of the goal.
        System.out.println("name: " + name);
        System.out.println("path: " + AuthorMojo.SKILLS + "/" + name + "/SKILL.md");
    }
}

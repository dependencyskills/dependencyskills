// Appended to the consumer's build for the lint arm. A warning, not a failure: the
// arm measures whether a finding that names the skill gets it read, not whether a
// failing build forces a rewrite.
val skillLint = tasks.register("skillLint") {
    val sources = fileTree("src") { include("**/*.kt") }
    inputs.files(sources)
    doLast {
        val misuse = Regex("""@MISUSE@""")
        sources.forEach { file ->
            file.readLines().forEachIndexed { i, line ->
                if (misuse.containsMatchIn(line)) {
                    logger.warn("w: ${file.relativeTo(projectDir)}:${i + 1}: @LINT@ " +
                        "The library ships a skill for this: .dependency-skills/@PACKAGE@.md")
                }
            }
        }
    }
}
tasks.named("check") { dependsOn(skillLint) }

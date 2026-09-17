// The post-write lint arm. Applied as an init script from outside the workspace, so nothing in
// the project names it or the skill before it fires. It checks only code the agent has changed
// since the baseline commit — a new-code lint, as CI often runs one — so it stays silent until
// the agent has written the misuse, and then names where the library's skill is.
allprojects {
    afterEvaluate {
        val lint = tasks.register("newCodeLint") {
            doLast {
                fun git(vararg args: String): List<String> =
                    ProcessBuilder("git", *args).directory(rootDir).start().inputStream.bufferedReader().readLines()
                val changed = (git("diff", "--name-only", "HEAD") + git("ls-files", "--others", "--exclude-standard"))
                    .filter { it.endsWith(".kt") }.toSet()
                val misuse = Regex("""@MISUSE@""")
                changed.map { rootDir.resolve(it) }.filter { it.isFile }.forEach { file ->
                    file.readLines().forEachIndexed { i, line ->
                        if (misuse.containsMatchIn(line)) {
                            logger.warn("w: ${file.relativeTo(rootDir)}:${i + 1}: @LINT@ The library ships a skill for this: @SKILL@")
                        }
                    }
                }
            }
        }
        tasks.findByName("compileKotlin")?.finalizedBy(lint)
    }
}

package org.dependencyskills.codex.server

import org.dependencyskills.codex.core.Coordinate
import java.nio.file.Files
import java.nio.file.Path

/**
 * Which coordinates the asking project resolved.
 *
 * **The server is told its scope; it cannot work one out.** The store is machine-level and holds
 * entries from every library any project on this machine has ever resolved, and it deliberately
 * records no project-to-coordinate edge — scope belongs to the `(project, source set) → coordinate`
 * relation, not to a coordinate, because the same artifact is `api` in one project and
 * `implementation` in another. Only the build system knows.
 *
 * So a scope file is written by whatever resolved the dependencies, and read here. One coordinate
 * per line, `ecosystem:value`; blank lines and `#` comments ignored.
 *
 * **An absent or empty scope means an empty result, and says so.** It does not mean "everything":
 * a server that fell back to the whole store when it could not find its scope would turn a missing
 * file into a containment failure, and the failure would look like the tool working.
 */
data class ProjectScope(val coordinates: Set<Coordinate>, val source: String) {

    val isEmpty: Boolean get() = coordinates.isEmpty()

    companion object {
        /** Named for what reads it, not for what it is — a bare `scope.txt` says nothing. */
        const val FILE_NAME = "codex-scope.txt"

        /**
         * Where the Gradle plugin writes it, relative to the project.
         *
         * `.gradle/` rather than `build/`, so a `clean` does not silently take an agent's scope
         * away and leave every answer empty.
         */
        const val DEFAULT_PATH = ".gradle/dscodex/$FILE_NAME"

        fun read(file: Path): ProjectScope {
            if (!Files.isRegularFile(file)) return ProjectScope(emptySet(), "no scope file at $file")
            val coordinates = Files.readAllLines(file)
                .map { it.substringBefore('#').trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { line ->
                    // `maven:group:artifact:version` - the ecosystem, then the coordinate as that
                    // ecosystem writes it, which contains colons of its own.
                    val ecosystem = line.substringBefore(':', "")
                    val value = line.substringAfter(':', "")
                    if (ecosystem.isBlank() || value.isBlank()) null else Coordinate(ecosystem, value)
                }
                .toSet()
            return ProjectScope(coordinates, file.toString())
        }

        fun of(vararg coordinates: Coordinate) = ProjectScope(coordinates.toSet(), "supplied directly")
    }
}

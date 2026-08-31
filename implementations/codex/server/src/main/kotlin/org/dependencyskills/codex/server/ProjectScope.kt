package org.dependencyskills.codex.server

import org.dependencyskills.codex.core.Codex
import org.dependencyskills.codex.core.Coordinate

/**
 * What one project is allowed to search.
 *
 * A shared store holds entries from every library any project on this machine has ever resolved, so
 * a query that ranged over all of it would make a poisoned entry pulled in by one project reachable
 * from another that never depended on it. The scope is what closes that, which is why an unknown
 * project resolves to **nothing** rather than to everything: a server that fell back to the whole
 * store when it could not identify its caller would turn a missing registration into a containment
 * failure, and the failure would look like the tool working.
 *
 * **It comes from the store, not from a file.** It used to be read from
 * `<project>/.gradle/dscodex/codex-scope.txt`, which meant the one component that is supposed to
 * know nothing about build systems knew where Gradle keeps its project directory. A build now
 * reports what it resolved over HTTP and the service writes it down, so the same handshake works
 * for any ecosystem without teaching this anything new.
 */
data class ProjectScope(val coordinates: Set<Coordinate>, val source: String) {

    val isEmpty: Boolean get() = coordinates.isEmpty()

    companion object {
        /**
         * The scope for the project at [path], from what builds have reported.
         *
         * Three outcomes, deliberately distinguishable, because they call for different actions and
         * rendering them all as "no results" is how a broken setup passes for a working one:
         *
         * - **never registered** — no build has reached the service. The developer needs to run one.
         * - **registered, resolved nothing** — a build ran and found no dependencies to record.
         * - **registered with coordinates** — an ordinary scope, and an empty answer is a real miss.
         */
        fun read(codex: Codex, path: String): ProjectScope {
            val registration = codex.projectScope(path)
                ?: return ProjectScope(
                    emptySet(),
                    "no build has registered $path with this service yet",
                )
            if (registration.coordinates.isEmpty()) {
                return ProjectScope(emptySet(), "the last build of this project resolved nothing")
            }
            val from = when {
                registration.contributors > 1 ->
                    "${registration.contributors} projects sharing the name '${registration.name}'"
                registration.name == path -> "the last build of this project"
                else -> "the last build of '${registration.name}'"
            }
            return ProjectScope(registration.coordinates, from)
        }

        fun of(vararg coordinates: Coordinate) = ProjectScope(coordinates.toSet(), "supplied directly")
    }
}

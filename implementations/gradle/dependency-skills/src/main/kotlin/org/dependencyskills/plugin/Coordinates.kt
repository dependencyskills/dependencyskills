package org.dependencyskills.plugin

import org.dependencyskills.codex.core.Coordinate
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

/**
 * Turns what Gradle resolved into coordinates the store can key on.
 *
 * Pure, and separate from the plugin for that reason: everything interesting about this story
 * is which components come out of a resolution result, and none of it should need a build to
 * test.
 */
internal object Coordinates {

    /**
     * The coordinates of one resolution.
     *
     * When [transitive] is false this is what the project itself declared — the root
     * component's own dependencies. When it is true it is every module the configuration
     * resolved to.
     *
     * Note what is *not* happening here: no configuration hierarchy is walked and no scope is
     * interpreted. The set is whatever the compile classpath resolved to, which is already the
     * importable set — declared dependencies plus only `api`-exposed transitives — computed by
     * Gradle, including the cases a hand-rolled walk gets wrong.
     */
    fun of(result: ResolutionResult, transitive: Boolean): Set<Coordinate> {
        val components =
            if (transitive) result.allComponents
            else result.root.dependencies
                .filterIsInstance<ResolvedDependencyResult>()
                .map { it.selected }
        return components.mapNotNullTo(LinkedHashSet()) { coordinateOf(it) }
    }

    /**
     * A resolved component as a coordinate, or null when it is not one the store can hold.
     *
     * Project components are excluded: they are the developer's own source, which has no
     * published coordinate and is indexed by a different route. File dependencies have no
     * identity at all.
     */
    fun coordinateOf(component: ResolvedComponentResult): Coordinate? =
        (component.id as? ModuleComponentIdentifier)?.let {
            Coordinate("maven", "${it.group}:${it.module}:${it.version}")
        }

    /**
     * Whether a coordinate is one the project asked to be left alone.
     *
     * Matched on `group:artifact`, so ignoring a library ignores every version of it. A
     * developer who names a library does not mean "except when it upgrades".
     */
    fun ignored(coordinate: Coordinate, ignores: Set<String>): Boolean {
        if (ignores.isEmpty()) return false
        val parts = coordinate.value.split(':')
        if (parts.size < 2) return coordinate.value in ignores
        return "${parts[0]}:${parts[1]}" in ignores || coordinate.value in ignores
    }
}

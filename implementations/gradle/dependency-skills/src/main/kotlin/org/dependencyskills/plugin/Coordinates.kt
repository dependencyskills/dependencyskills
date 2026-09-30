package org.dependencyskills.plugin

import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import java.io.File

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
    fun of(result: ResolutionResult, transitive: Boolean, includedBuilds: Map<String, File> = emptyMap()): Set<Coordinate> {
        val build = (result.root.id as? ProjectComponentIdentifier)?.build?.buildPath
        if (transitive) {
            return withoutPlatformVariants(result.allComponents.filter { it != result.root }
                .mapNotNullTo(LinkedHashSet()) { coordinateOf(it, null, build, includedBuilds) })
        }
        return result.root.dependencies.filterIsInstance<ResolvedDependencyResult>()
            .mapNotNullTo(LinkedHashSet()) { coordinateOf(it.selected, it.requested, build, includedBuilds) }
    }

    /**
     * A resolved component as a coordinate, or null when it is not one the store can hold.
     *
     * A project in **this** build ([build]) is excluded: it is the developer's own module, not a dependency.
     * A project from an **included** build is not — `includeBuild` stands it in for a published module, and it
     * is that library the code uses. It is named as the consumer asked for it, [requested], else by the
     * coordinates its build gives it, at the included project's version, and carries the project's directory
     * from [includedBuilds], where its skill is still source rather than in a jar: the lookup reads it there.
     * File dependencies have no identity at all.
     */
    fun coordinateOf(
        component: ResolvedComponentResult,
        requested: ComponentSelector? = null,
        build: String? = null,
        includedBuilds: Map<String, File> = emptyMap(),
    ): Coordinate? {
        val id = component.id
        if (id is ModuleComponentIdentifier) return Coordinate("maven", "${id.group}:${id.module}:${id.version}")
        if (id !is ProjectComponentIdentifier || build == null || id.build.buildPath == build) return null
        val version = component.moduleVersion ?: return null
        val (group, module) = (requested as? ModuleComponentSelector)?.let { it.group to it.module }
            ?: (version.group to version.name)
        val source = includedBuilds[id.build.buildPath]?.resolve(id.projectPath.trimStart(':').replace(':', '/'))
        return Coordinate("maven", "$group:$module:${version.version}", source?.absolutePath)
    }

    /**
     * [coordinates] without the platform module of a Kotlin Multiplatform library whose root is also there.
     *
     * Resolving a multiplatform library resolves its root component and the platform module the target
     * selected — `format-datetime` and `format-datetime-jvm` — and across a project's targets, one per
     * platform. They are one library with one skill, which the lookup finds through the platform module's
     * sources jar when it is filed under the root; listed separately, they doubled a real project's report.
     * A platform module whose root was not resolved is kept: it is then the only name the library has.
     */
    fun withoutPlatformVariants(coordinates: Set<Coordinate>): Set<Coordinate> {
        val present = coordinates.mapTo(HashSet()) { it.value }
        return coordinates.filterTo(LinkedHashSet()) { coordinate ->
            val parts = coordinate.value.split(':')
            if (parts.size != 3 || !PLATFORM_SUFFIX.containsMatchIn(parts[1])) return@filterTo true
            "${parts[0]}:${PLATFORM_SUFFIX.replace(parts[1], "")}:${parts[2]}" !in present
        }
    }

    /** The suffix a multiplatform library gives each platform module; the lookup's `PLATFORM_SUFFIX`, the same list. */
    private val PLATFORM_SUFFIX = Regex(
        "-(?:jvm|android|js|wasm-js|wasm-wasi|metadata|iosarm64|iosx64|iossimulatorarm64|" +
            "macosarm64|macosx64|linuxx64|linuxarm64|mingwx64|tvos\\w*|watchos\\w*)$",
    )

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

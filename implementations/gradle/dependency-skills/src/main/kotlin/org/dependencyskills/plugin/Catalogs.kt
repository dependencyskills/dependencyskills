package org.dependencyskills.plugin

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension

/**
 * The libraries a build's version catalogs declare, as `group:artifact:version`.
 *
 * A catalog entry is the developer's choice of library, made before any module uses it. An agent
 * asked to do something a declared library does looked at the codex, saw only what was resolved,
 * took "not there" for "no skill", and wrote its own. So declared libraries are reported too, marked
 * as declared, and the codex serves their skills like any dependency's: adding the entry is the same
 * trust decision as adding the dependency.
 *
 * Only a plain version is taken. A range or a dynamic version names no single artifact, so there is
 * no sources jar to read a skill from until a module resolves it.
 */
internal object Catalogs {

    private val NOT_PLAIN = Regex("""[\[\](),+]""")

    fun declared(project: Project): List<String> {
        val catalogs = project.extensions.findByType(VersionCatalogsExtension::class.java) ?: return emptyList()
        return catalogs.flatMap { catalog ->
            catalog.libraryAliases.mapNotNull { alias ->
                val dependency = catalog.findLibrary(alias).orElse(null)?.orNull ?: return@mapNotNull null
                val constraint = dependency.versionConstraint
                val version = listOf(constraint.requiredVersion, constraint.strictVersion, constraint.preferredVersion)
                    .firstOrNull { it.isNotBlank() } ?: return@mapNotNull null
                if (NOT_PLAIN.containsMatchIn(version)) return@mapNotNull null
                "${dependency.module.group}:${dependency.module.name}:$version"
            }
        }.distinct().sorted()
    }
}

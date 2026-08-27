package org.dependencyskills.plugin

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * The Kotlin Multiplatform half, kept in its own class on purpose.
 *
 * KGP is a `compileOnly` dependency: a consuming project that is not multiplatform must not be
 * made to carry it. Referencing [KotlinMultiplatformExtension] from a class that is always
 * loaded would defeat that, because the JVM resolves a class's references when it loads it.
 * Every entry point here sits behind `pluginManager.withPlugin`, so this class is loaded only
 * once KGP is on the classpath.
 */
internal object MultiplatformCompilations {

    /**
     * Watches every compilation's compile-dependency configuration.
     *
     * `KotlinCompilation.compileDependencyConfigurationName` is public KGP API, documented as
     * the configuration holding all the resolved dependencies required for compilation — the
     * multiplatform equivalent of a source set's compile classpath.
     *
     * **Every compilation, not the one that was built.** The recorded set is a union, because
     * the store is keyed by coordinate and must not depend on which target someone happened to
     * assemble. Which compilation an agent is working in is a query-time filter and belongs
     * somewhere else entirely.
     */
    fun watchAll(project: Project, observer: Observer) {
        val kotlin = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return
        kotlin.targets.configureEach {
            compilations.configureEach {
                observer.watch(project, compileDependencyConfigurationName)
            }
        }
    }
}

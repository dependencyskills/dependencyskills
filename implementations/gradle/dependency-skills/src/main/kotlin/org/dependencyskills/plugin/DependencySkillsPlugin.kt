package org.dependencyskills.plugin

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.SourceSetContainer

/**
 * Reports which of a consuming project's dependencies the codex has never seen.
 *
 * **The build detects; something out of band harvests.** That seam is the whole design. An
 * artifact transform looks like the natural fit — it runs per artifact, is cached by Gradle,
 * and fires exactly when a new dependency appears — and it is a trap twice over: the
 * summariser needs a local model, so a transform would block `./gradlew build` on inference,
 * and its output would live in Gradle's own transform cache, which Gradle owns and evicts.
 *
 * **There is no download event, and none is wanted.** Gradle's public API offers resolution
 * events, not download events, and a download hook would be the wrong instrument even if it
 * existed: it fires only for artifacts *this* build fetched, so most of a working machine —
 * everything already in the cache from another project — would never be indexed. Diffing the
 * resolved set against the store catches all three cases: newly downloaded, long cached but
 * never indexed, and anything the store lost to a schema bump. It is idempotent, and needs no
 * event ordering to be true.
 */
class DependencySkillsPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = with(project) {
        val extension = extensions.create("dependencySkills", DependencySkillsExtension::class.java).apply {
            enabled.convention(
                providers.gradleProperty(ENABLED_PROPERTY).map(String::toBoolean).orElse(true),
            )
            harvester.transitive.convention(false)
        }

        val recorder = gradle.sharedServices.registerIfAbsent(SERVICE, CodexRecorder::class.java) {
            parameters.serviceUrl.set(
                extension.serviceUrl
                    .orElse(providers.gradleProperty(SERVICE_URL_PROPERTY))
                    .orElse(DEFAULT_SERVICE_URL),
            )
            parameters.projectPath.set(layout.projectDirectory.asFile.absolutePath)
            // The path, not the project's name. A name groups several checkouts into one scope, so
            // a default anyone could collide with would merge unrelated projects silently.
            parameters.projectName.set(
                extension.projectName.orElse(layout.projectDirectory.asFile.absolutePath),
            )
        }

        // Instantiated for every build, but only once the build script has been evaluated.
        //
        // Both halves matter. Gradle creates a build service lazily, so a build that resolves
        // nothing would never create this one - and never close it, and never say that it saw
        // nothing, which is the silence the report exists to break. But instantiating a service
        // resolves its PARAMETERS, and `dependencySkills { }` has not run yet at apply time, so
        // doing it here read an unconfigured extension and every project reported the default
        // name. Nothing about that failure was visible: the build passed and the scope was simply
        // filed under the wrong one.
        afterEvaluate { recorder.get() }

        val observer = Observer(
            recorder = recorder,
            enabled = extension.enabled,
            transitive = extension.harvester.transitive,
            ignored = extension.harvester.ignored,
        )

        // Ask the build for its compile classpaths; never model scope. A compile classpath
        // resolves with Usage=java-api, so what comes back is already the importable set —
        // this project's api, implementation and compileOnly, plus only the transitives its
        // dependencies chose to expose. Interpreting the configuration hierarchy by hand would
        // get compileOnlyApi, feature variants and platform constraints wrong.
        pluginManager.withPlugin("java-base") {
            extensions.findByType(SourceSetContainer::class.java)?.configureEach {
                observer.watch(project, compileClasspathConfigurationName)
            }
        }

        // KMP names the same thing per compilation. Loaded from a separate class so a project
        // without KGP never has its types touched — the dependency is compileOnly.
        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            MultiplatformCompilations.watchAll(project, observer)
        }
    }

    internal companion object {
        const val EXTENSION = "dependencySkills"
        const val SERVICE = "dependencySkillsCodex"
        const val ENABLED_PROPERTY = "dependencySkills.enabled"
        const val SERVICE_URL_PROPERTY = "dependencySkills.serviceUrl"

        /** Loopback, because the service holds one machine's dependency graph and stays on it. */
        const val DEFAULT_SERVICE_URL = "http://127.0.0.1:8310"
    }
}

/**
 * Registers the one callback this plugin has, on the configurations worth watching.
 *
 * It watches rather than resolves. `afterResolve` fires only for a configuration the build was
 * going to resolve anyway, which is what makes this fire on an IDE sync — the moment
 * dependencies actually change — without altering what the build resolves.
 */
internal class Observer(
    private val recorder: Provider<CodexRecorder>,
    private val enabled: Property<Boolean>,
    private val transitive: Property<Boolean>,
    private val ignored: SetProperty<String>,
) {

    fun watch(project: Project, configurationName: String) {
        // `matching` rather than `named`: the configuration may not exist yet, and a name that
        // never appears should be silence rather than a failure. Neither realises it, and
        // nothing here resolves anything at configuration time.
        project.configurations.matching { it.name == configurationName }.configureEach {
            // The explicit Action disambiguates from the Groovy Closure overload.
            incoming.afterResolve(Action<ResolvableDependencies> { onResolved(this) })
        }
    }

    private fun onResolved(dependencies: ResolvableDependencies) {
        // A broken index must not break a build. This is the outermost boundary: the callback
        // runs inside Gradle's resolution machinery, so anything escaping it fails the
        // resolution itself, and a project would stop compiling because its index is unwell.
        runCatching {
            if (!enabled.get()) return@runCatching
            val ignores = ignored.get()
            val coordinates: List<Coordinate> =
                Coordinates.of(dependencies.resolutionResult, transitive.get())
                    .filterNot { Coordinates.ignored(it, ignores) }
            recorder.get().record(coordinates)
        }
    }
}

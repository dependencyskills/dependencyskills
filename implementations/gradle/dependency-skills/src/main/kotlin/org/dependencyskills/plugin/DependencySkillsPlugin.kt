package org.dependencyskills.plugin

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.SourceSetContainer

/**
 * Reports which of a consuming project's dependencies the codex has never seen — and, applied to
 * a library, ships that library's own skill in its sources jar ([SkillPackaging]). Where a project
 * declares the `consumer { }` or `author { }` block, it also writes that agent skill into the
 * project ([AgentSkills]).
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
            harvester.transitive.convention(true)
            fetchSources.convention(
                providers.gradleProperty(FETCH_SOURCES_PROPERTY).map(String::toBoolean).orElse(true),
            )
            val claudeCode = rootProject.layout.projectDirectory.dir(".claude").asFile.isDirectory
            listOf(consumer, author).forEach {
                it.enabled.convention(false)
                it.refresh.convention(SkillRefresh.Always)
                it.claudeCode.convention(claudeCode)
            }
        }

        val recorder = gradle.sharedServices.registerIfAbsent(SERVICE, CodexRecorder::class.java) {
            // No default: the full codex is told only where a developer says where it listens. Most
            // projects run only the lightweight codex, which reads the SBOM, and a line about a service
            // they never installed would be noise on every build (#45).
            parameters.serviceUrl.set(
                extension.serviceUrl.orElse(providers.gradleProperty(SERVICE_URL_PROPERTY)),
            )
            parameters.projectPath.set(layout.projectDirectory.asFile.absolutePath)
            // The path, not the project's name. A name groups several checkouts into one scope, so
            // a default anyone could collide with would merge unrelated projects silently.
            parameters.projectName.set(
                extension.projectName.orElse(layout.projectDirectory.asFile.absolutePath),
            )
            // The root's build directory, because the recorder is one per build: a multi-module
            // build's resolved set is one union, and one file at the root is where a reader starting
            // anywhere in the checkout finds it.
            parameters.reportFile.set(rootProject.layout.buildDirectory.file(REPORT_FILE))
            // Every project in the build, so a module deleted from settings leaves the SBOM, while one
            // this build simply did not compile keeps what it last resolved.
            parameters.projectPaths.set(rootProject.allprojects.map { it.path })
            // What the version catalogs declare, whether or not a module uses it yet; see Catalogs.
            parameters.declared.set(provider { Catalogs.declared(project) })
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
        afterEvaluate {
            val service = recorder.get()
            // As early as the extension can be trusted, and still well before anything resolves or
            // downloads - the service loads its model while Gradle fetches rather than afterwards.
            //
            // NOT before `afterEvaluate`, however tempting. Instantiating a build service realises
            // its PARAMETERS, and `dependencySkills { }` has not run at apply time, so warming from
            // there would read an unconfigured extension and send the wrong service the wrong
            // project - silently, with a passing build.
            if (extension.enabled.getOrElse(true)) service.signalSyncing()
        }

        // Its own service rather than the recorder's: under the configuration cache a task's service
        // is a fresh instance at execution, and the recorder's would then report, on close, that
        // nothing resolved.
        val claims = gradle.sharedServices.registerIfAbsent(SourcesClaims.NAME, SourcesClaims::class.java) {}
        val fetching = extension.enabled.zip(extension.fetchSources) { on, fetch -> on && fetch }

        // The agent skills, each only where its block is declared, written before any compile task.
        val skillWriters = listOf(
            AgentSkills.register(project, "writeConsumerSkill", AgentSkills.LIBRARIAN, extension.consumer, extension.enabled, claims),
            AgentSkills.register(project, "writeAuthorSkill", AgentSkills.AUTHOR_SKILL, extension.author, extension.enabled, claims),
        )

        val observer = Observer(
            recorder = recorder,
            enabled = extension.enabled,
            transitive = extension.harvester.transitive,
            ignored = extension.harvester.ignored,
            onWatched = { configuration, compileTasks ->
                Sources.fetchBefore(project, configuration, compileTasks, fetching, claims)
                tasks.configureEach { if (name in compileTasks) dependsOn(skillWriters) }
            },
            // A composite build's `includeBuild`s, whose projects stand in for published modules.
            includedBuilds = gradle.includedBuilds.associate { ":${it.name}" to it.projectDir },
        )

        // Ask the build for its compile classpaths; never model scope. A compile classpath
        // resolves with Usage=java-api, so what comes back is already the importable set —
        // this project's api, implementation and compileOnly, plus only the transitives its
        // dependencies chose to expose. Interpreting the configuration hierarchy by hand would
        // get compileOnlyApi, feature variants and platform constraints wrong.
        pluginManager.withPlugin("java-base") {
            extensions.findByType(SourceSetContainer::class.java)?.configureEach {
                // Java's and Kotlin's compile tasks for the source set: either may be the one that runs.
                observer.watch(project, compileClasspathConfigurationName,
                    listOf(compileJavaTaskName, getCompileTaskName("kotlin")))
            }
        }

        // KMP names the same thing per compilation. Loaded from a separate class so a project
        // without KGP never has its types touched — the dependency is compileOnly.
        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            MultiplatformCompilations.watchAll(project, observer)
        }

        // The other half: a library applying this ships its own skill in its sources jar. Does
        // nothing in a project that has no skill, which is every purely consuming one.
        SkillPackaging.apply(project)
    }

    internal companion object {
        const val EXTENSION = "dependencySkills"
        const val SERVICE = "dependencySkillsCodex"
        const val ENABLED_PROPERTY = "dependencySkills.enabled"
        const val SERVICE_URL_PROPERTY = "dependencySkills.serviceUrl"
        const val FETCH_SOURCES_PROPERTY = "dependencySkills.fetchSources"

        /** The CycloneDX SBOM the lightweight codex reads, relative to the root build directory. */
        const val REPORT_FILE = "dependencyskills/bom.cdx.json"
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
    /** Called once for each configuration watched, with its compile tasks, so its dependencies' sources can be fetched. */
    private val onWatched: (Configuration, List<String>) -> Unit = { _, _ -> },
    /** Each build this one includes, by its build path, to its directory: where an included project's skill is. */
    private val includedBuilds: Map<String, java.io.File> = emptyMap(),
) {

    /** Watches one compile-dependency configuration, which the tasks named [compileTasks] compile against. */
    fun watch(project: Project, configurationName: String, compileTasks: List<String>) {
        val path = project.path
        // `matching` rather than `named`: the configuration may not exist yet, and a name that
        // never appears should be silence rather than a failure. Neither realises it, and
        // nothing here resolves anything at configuration time.
        project.configurations.matching { it.name == configurationName }.configureEach {
            // The explicit Action disambiguates from the Groovy Closure overload.
            val configuration = name
            incoming.afterResolve(Action<ResolvableDependencies> { onResolved(path, configuration, this) })
            onWatched(this, compileTasks)
        }
    }

    /** Records one resolved compile classpath against the Gradle project, by path, it belongs to. */
    private fun onResolved(projectPath: String, configuration: String, dependencies: ResolvableDependencies) {
        // A broken index must not break a build. This is the outermost boundary: the callback
        // runs inside Gradle's resolution machinery, so anything escaping it fails the
        // resolution itself, and a project would stop compiling because its index is unwell.
        runCatching {
            if (!enabled.get()) return@runCatching
            val ignores = ignored.get()
            val coordinates: List<Coordinate> =
                Coordinates.of(dependencies.resolutionResult, transitive.get(), includedBuilds)
                    .filterNot { Coordinates.ignored(it, ignores) }
            recorder.get().record(projectPath, configuration, coordinates)
        }
    }
}

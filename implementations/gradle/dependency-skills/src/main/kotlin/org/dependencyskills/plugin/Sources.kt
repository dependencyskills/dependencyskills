package org.dependencyskills.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * The sources jars of a project's dependencies, which is where a JVM library's skill travels.
 *
 * A build downloads the jars it compiles against and never their sources; an IDE fetches those when
 * it syncs, if it is set to. So whether a library's skill is on the machine used to depend on how the
 * machine had been used: on one where an IDE runs daily, 86% of cached versions had sources, and a
 * project there still lacked them for 14 of its 18 new dependencies — and on a machine that only
 * builds from the command line, which is where agents build, the lookup found nothing (RAD-0079).
 *
 * So the build fetches them, through Gradle's own repositories, credentials and cache, while it is
 * already resolving — never the lookup, inside an agent's turn.
 */
internal object Sources {

    /**
     * The sources of every module on [configuration], by Gradle's own sources variant.
     *
     * Reselecting variants from the graph the configuration already resolved asks for exactly those
     * components' sources. It covers a library published with Gradle module metadata — including a
     * multiplatform library, whose platform module carries the sources jar — and one published with
     * a POM alone, for which Gradle derives the same variant. Lenient: a library without sources, or
     * an offline build, is skipped rather than failed.
     */
    fun of(configuration: Configuration, project: Project): FileCollection =
        configuration.incoming.artifactView {
            withVariantReselection()
            lenient(true)
            componentFilter { it is ModuleComponentIdentifier }
            attributes {
                attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category::class.java, Category.DOCUMENTATION))
                attribute(DocsType.DOCS_TYPE_ATTRIBUTE, project.objects.named(DocsType::class.java, DocsType.SOURCES))
                attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling::class.java, Bundling.EXTERNAL))
            }
        }.files

    /**
     * Registers a task fetching [configuration]'s sources, and the catalog's, and runs it before each
     * of [compileTasks].
     *
     * One task per configuration, before its own compile task, so a build fetches the sources of
     * exactly the classpaths it compiles against: a build of `classes` does not resolve the test
     * classpath, and a JVM build of a multiplatform project does not resolve its iOS targets. Before
     * rather than after, so it runs when the compile task is up to date too — adding a catalog entry
     * changes nothing a compile task sees.
     */
    fun fetchBefore(
        project: Project,
        configuration: Configuration,
        compileTasks: List<String>,
        fetching: Provider<Boolean>,
        claims: Provider<SourcesClaims>,
    ) {
        val name = FetchDependencySources.NAME + configuration.name.replaceFirstChar { it.uppercase() }
        // A multiplatform JVM target's classpath is watched twice — as a Java source set and as a
        // Kotlin compilation — so the second call only adds its compile tasks.
        val fetch = if (name in project.tasks.names) project.tasks.named(name, FetchDependencySources::class.java)
        else project.tasks.register(name, FetchDependencySources::class.java) {
            description = "Fetches the sources jars of ${configuration.name}, where its dependencies' skills travel."
            this.fetching.set(fetching)
            this.claims.set(claims)
            usesService(claims)
            sources.from(of(configuration, project))
            declaredSources.from(project.provider { declared(project, Catalogs.declared(project)) })
        }
        project.tasks.configureEach { if (this.name in compileTasks) dependsOn(fetch) }
    }

    /**
     * The sources jars of libraries a version catalog declares, which no configuration resolves until
     * a module uses them. Asked for by classifier, since there is no graph to reselect from.
     */
    fun declared(project: Project, coordinates: List<String>): FileCollection {
        if (coordinates.isEmpty()) return project.files()
        val dependencies = coordinates.map { project.dependencies.create("$it:sources") }
        return project.configurations.detachedConfiguration(*dependencies.toTypedArray())
            .apply { isTransitive = false }
            .incoming.artifactView { lenient(true) }.files
    }
}

/** Once-per-build work shared by every project that applies the plugin: the first to claim a key does it. */
abstract class SourcesClaims : BuildService<BuildServiceParameters.None> {
    private val claimed = HashSet<String>()

    fun claim(key: String): Boolean = synchronized(claimed) { claimed.add(key) }

    /** Runs [work] with no other project's once-per-build work running, for files several of them write. */
    fun <T> exclusively(work: () -> T): T = synchronized(this) { work() }

    internal companion object {
        const val NAME = "dependencySkillsSourcesClaims"
    }
}

/**
 * Fetches the sources jars [Sources] names, before the compile task that uses them, so they are in
 * the cache before an agent asks. Resolving them is the whole of the work; there is nothing to write.
 *
 * Nothing here may fail a build: a failure to fetch is logged at info and forgotten, because an index
 * is an aid and a project must still compile without it.
 */
@DisableCachingByDefault(because = "Resolving the sources is the work, and it produces no output")
abstract class FetchDependencySources : DefaultTask() {

    /** The sources of what this project's compile classpaths resolved. */
    @get:Internal
    abstract val sources: ConfigurableFileCollection

    /** The sources of what the version catalogs declare; fetched once per build, by whichever project runs first. */
    @get:Internal
    abstract val declaredSources: ConfigurableFileCollection

    @get:Internal
    abstract val fetching: Property<Boolean>

    @get:Internal
    abstract val claims: Property<SourcesClaims>

    @TaskAction
    fun fetch() {
        if (!fetching.getOrElse(true)) return
        runCatching {
            val resolved = sources.files.size
            val declared = if (claims.get().claim(CATALOG_SOURCES)) declaredSources.files.size else 0
            logger.info("dependencyskills: {} sources jars on the compile classpaths, {} for declared libraries", resolved, declared)
        }.onFailure { logger.info("dependencyskills: sources not fetched: {}", it.message) }
    }

    internal companion object {
        const val NAME = "dependencySkillsSources"
        const val CATALOG_SOURCES = "catalog-sources"
    }
}

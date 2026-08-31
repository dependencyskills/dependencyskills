package org.dependencyskills.plugin

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import javax.inject.Inject

/**
 * What a consuming project says about its own dependency graph.
 *
 * ```kotlin
 * dependencySkills {
 *     projectName = "acme-platform"          // optional; several checkouts can share one
 *     serviceUrl = "http://127.0.0.1:8310"   // where the codex service is
 *     harvester {
 *         transitive = true
 *         ignore("com.example:noisy-library")
 *     }
 * }
 * ```
 *
 * The nesting is deliberate. Configuration here is per project, by the build, because what to
 * harvest is a property of the project rather than of the tool — which is a real divergence
 * from how an MCP server is usually configured, once for the agent. The two surfaces answer
 * different questions and should not be collapsed.
 */
abstract class DependencySkillsExtension @Inject constructor(objects: ObjectFactory) {

    /**
     * Master switch. Off means the plugin observes nothing and writes nothing.
     *
     * Defaults to the `dependencySkills.enabled` Gradle property when set, so a CI job or a
     * downstream build can turn it off without editing a build script:
     *
     * ```
     * ./gradlew build -PdependencySkills.enabled=false
     * ```
     */
    abstract val enabled: Property<Boolean>

    /**
     * The name this project's scope is filed under.
     *
     * **Defaults to the project directory**, and that default is load-bearing rather than lazy. A
     * name is what groups several checkouts into one scope, so anything derivable — the root
     * project's name, the directory's name — would silently merge two unrelated projects that
     * happen to share it, making one project's entries reachable from another that never depended
     * on them. A path cannot collide, so grouping stays something a developer asks for.
     *
     * Set it where several repositories are one product and an agent in any of them should see
     * what all of them depend on:
     *
     * ```kotlin
     * dependencySkills { projectName = "acme-platform" }
     * ```
     */
    abstract val projectName: Property<String>

    /**
     * Where the codex service is listening.
     *
     * A URL rather than a path to the store, deliberately: the build says what it resolved and the
     * service decides what to do about it. Nothing here knows where the store lives, which is what
     * lets the store move without touching a build script.
     *
     * Defaults to the `dependencySkills.serviceUrl` Gradle property when set.
     */
    abstract val serviceUrl: Property<String>

    /** What the out-of-band harvester is fed. */
    val harvester: HarvesterSpec = objects.newInstance(HarvesterSpec::class.java)

    fun harvester(configure: Action<in HarvesterSpec>) = configure.execute(harvester)
}

/**
 * What gets recorded as worth harvesting.
 *
 * This block exists rather than a flat set of properties because the settings that will land
 * here all follow from the same fact — that a project's own dependency graph is the trigger —
 * and they are project-specific in a way the encoder is not. Which model does the embedding is
 * emphatically **not** configurable: the store is machine-wide, and two projects choosing
 * different encoders would produce vectors that cannot share an index.
 */
abstract class HarvesterSpec {

    /**
     * Widen from what this project declared to everything the compile classpath resolved.
     *
     * Off by default, and the default is the conservative one rather than the good one.
     * RAD-0022 measured 11 of 17 real capabilities living only in the transitive tail, so this
     * is where most of the value is — but it is also where most of the volume is, and it is a
     * trade an operator should take deliberately rather than inherit.
     */
    abstract val transitive: Property<Boolean>

    /**
     * Coordinates never to record, as `group:artifact` — a library whose documentation is
     * noise, or one a team knows it never calls directly. The version is not part of the
     * match: ignoring a library means ignoring it, not ignoring one release of it.
     */
    abstract val ignored: SetProperty<String>

    fun ignore(vararg coordinates: String) = ignored.addAll(coordinates.toList())
}

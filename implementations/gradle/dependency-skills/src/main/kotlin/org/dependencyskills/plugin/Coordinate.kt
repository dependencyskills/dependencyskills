package org.dependencyskills.plugin

/**
 * A resolved dependency, as the build system named it.
 *
 * **The plugin's own type, not the codex's.** It used to import `codex.core.Coordinate`, which
 * meant a plugin whose whole job is watching a list and writing a file dragged the store — and
 * 11.4 MB of SQLite — onto every consuming project's buildscript classpath.
 *
 * What the plugin and the codex actually share is a **file format**, not a Java type. Two strings
 * and a colon is a smaller contract than a class, and it is the one the scope file already
 * expresses.
 *
 * [ecosystem] is carried separately rather than parsed out of [value] because a build resolves
 * from more than one: a Kotlin Multiplatform build pulls npm packages through Gradle, and both
 * end up here. Two coordinates from different ecosystems never collide.
 */
data class Coordinate(val ecosystem: String, val value: String) {
    /** The scope file's line format, which is the whole contract with the service. */
    override fun toString() = "$ecosystem:$value"
}

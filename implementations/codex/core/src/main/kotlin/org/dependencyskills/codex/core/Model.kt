package org.dependencyskills.codex.core

/**
 * A resolved dependency, as the build system named it.
 *
 * The [ecosystem] is carried separately rather than parsed out of [value] because the
 * codex holds whatever the build system resolved, not whatever one package manager
 * publishes: a Kotlin Multiplatform build pulls npm packages through Gradle, and both
 * end up here. Two coordinates from different ecosystems never collide.
 *
 * Scope is deliberately absent. Scope belongs to the (project, source set) -> coordinate
 * edge, not to the coordinate: the same artifact is `api` in one project and
 * `implementation` in another, and this store is shared across every project on the
 * machine. Which coordinates a query may see is computed per project, at query time.
 */
data class Coordinate(val ecosystem: String, val value: String) {
    init {
        require(ecosystem.isNotBlank()) { "ecosystem must not be blank" }
        require(value.isNotBlank()) { "coordinate must not be blank" }
    }
    override fun toString() = "$ecosystem:$value"
}

/** Where a coordinate has got to, independently of whether it produced any entries. */
enum class HarvestState {
    /** Seen in a dependency graph; nothing has looked at it yet. */
    Pending,
    /** Harvested, and its entries are in the store. */
    Indexed,
    /** Harvested, and there was nothing to index — no sources artifact. */
    NoSource,
    /** Harvesting was attempted and failed. Distinct from [NoSource]: this one may succeed later. */
    Failed,
}

/**
 * Whether an entry's prose may be shown.
 *
 * [Degraded] is not deletion. The entry keeps its place and its retrieval key, and returns
 * its signature — an entry with no key cannot be found at all, which would make the safe
 * outcome indistinguishable from silently dropping it.
 */
enum class EntryState { Whole, Degraded }

/**
 * What produced an entry, so a bad version can be invalidated selectively rather than by
 * deleting the whole store.
 *
 * [pooling] travels with [encoder] on purpose. Pooling is a per-model property — CLS beats
 * mean on one model and collapses on another — so two vectors from the *same* encoder under
 * different pooling are not comparable and must not share an index.
 */
data class Provenance(
    val extractor: String,
    val summariser: String? = null,
    val encoder: String? = null,
    val pooling: String? = null,
) {
    init {
        require(extractor.isNotBlank()) { "extractor must be recorded" }
        require(pooling == null || encoder != null) { "pooling without an encoder is meaningless" }
    }
}

/**
 * An entry as a caller sees it.
 *
 * **The raw documentation is not here, and that is the point.** It is a retrieval key: the
 * store searches on it and never hands it out. What may be displayed is [rewrite], and a
 * [Degraded] entry has none — it offers [symbol] and [signature] only. Leaving the raw text
 * out of this type makes the rule structural rather than a convention a caller can forget.
 */
data class Entry(
    val id: String,
    val symbol: String,
    val signature: String,
    val rewrite: String?,
    val lang: String,
    val docFormat: String,
    val state: EntryState,
    val provenance: Provenance,
    /** Every coordinate that carries this entry. A capability really can be in more than one. */
    val coordinates: Set<Coordinate>,
)

/** An entry on its way in, before the store gives it an identity. */
data class NewEntry(
    val symbol: String,
    val signature: String,
    /** Raw documentation. Stored as a retrieval key; never returned. */
    val doc: String,
    val lang: String,
    val docFormat: String,
    val provenance: Provenance,
    val rewrite: String? = null,
    val state: EntryState = EntryState.Whole,
) {
    init {
        require(symbol.isNotBlank()) { "symbol must not be blank" }
        require(signature.isNotBlank()) { "signature must not be blank" }
    }
}

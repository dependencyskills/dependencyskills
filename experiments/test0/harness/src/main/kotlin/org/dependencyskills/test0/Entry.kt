package org.dependencyskills.test0

/** Designed (author opted in) vs discovered (harvested from what exists) — RAD-0011. */
enum class Tier { Designed, Discovered }

/**
 * A codex entry — the two-faced, per-capability unit (RAD-0013).
 *
 * The semantic face ([capability]) is what a caller searches by; the syntactic
 * face ([symbol]/[signature]/[sample]) is how they use it. A parser arm in the
 * bake-off produces a list of these from source; the tests assert on them.
 */
data class Entry(
    val coordinate: String,
    // syntactic face
    val symbol: String,
    val signature: String,
    val sample: String? = null,
    // semantic face
    val capability: String? = null,
    val triggers: List<String> = emptyList(),
    val notFor: String? = null,
    val category: String? = null,
    // standard-tag harvest
    val since: String? = null,
    // provenance
    val tier: Tier = Tier.Discovered,
    val source: String,
)

package org.dependencyskills.codex.harvester

import org.dependencyskills.codex.core.Codex
import org.dependencyskills.codex.core.Coordinate
import org.dependencyskills.codex.core.HarvestState
import java.nio.file.Path

/**
 * Harvests one archive into the store under [coordinate], and records how it went.
 *
 * The seam is here rather than inside [SourcesJarHarvester] on purpose: the harvester stays a
 * pure function of the jar, and everything that touches prior state is this one call.
 *
 * Every outcome moves the coordinate's harvest state, including the ones that produced nothing.
 * That is the whole point of the state existing — a library with no sources and a library nobody
 * has looked at yet are indistinguishable by their entries, and only the state tells them apart.
 * Without it the sources-less one is re-queued on every build and re-fails forever.
 */
fun Codex.harvest(
    coordinate: Coordinate,
    jar: Path,
    harvester: SourcesJarHarvester = SourcesJarHarvester(),
): HarvestResult {
    seen(coordinate)
    val result = harvester.harvest(jar)
    when (result) {
        // put() moves the coordinate to Indexed itself, entries or no entries.
        is HarvestResult.Harvested -> put(coordinate, result.entries)
        is HarvestResult.NoSource -> harvestState(coordinate, HarvestState.NoSource)
        is HarvestResult.Failed -> harvestState(coordinate, HarvestState.Failed)
    }
    return result
}

/**
 * Records that a coordinate publishes no sources artifact at all, so nothing was read.
 *
 * The caller that resolves coordinates to files needs this: "the repository has no
 * `-sources.jar` for this version" is a finding, not a reason to say nothing. Left unrecorded,
 * the coordinate stays [HarvestState.Pending] and is picked up again by every build that
 * resolves it.
 */
fun Codex.noSourcesPublished(coordinate: Coordinate) {
    harvestState(coordinate, HarvestState.NoSource)
}

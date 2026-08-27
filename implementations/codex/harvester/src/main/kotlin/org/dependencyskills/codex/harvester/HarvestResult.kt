package org.dependencyskills.codex.harvester

import org.dependencyskills.codex.core.NewEntry

/**
 * What reading one sources jar produced.
 *
 * The three cases are deliberately not collapsible into "a list, possibly empty". An archive
 * with no source in it, an archive that could not be read, and an archive that yielded nothing
 * are different facts, and the one failure this project keeps re-learning is that a skip and an
 * absence look identical once they have both been flattened to zero. Checking only the newest
 * version of each module once dropped 42 artifacts without a word, `androidx.compose.ui` among
 * them, and nothing in the output said so.
 */
sealed interface HarvestResult {

    /** The archive was read. [entries] may still be empty — [report] says why. */
    data class Harvested(val entries: List<NewEntry>, val report: HarvestReport) : HarvestResult

    /**
     * There was nothing to harvest, and that is a fact about the artifact rather than a failure.
     * A library that publishes no sources jar, or publishes one holding only resources, lands
     * here. Re-running will not change it.
     */
    data class NoSource(val reason: String) : HarvestResult

    /**
     * The archive could not be read. Distinct from [NoSource] because it may succeed later — a
     * truncated download is the ordinary cause.
     */
    data class Failed(val reason: String, val cause: Throwable? = null) : HarvestResult
}

/**
 * What the walk saw, including what it threw away.
 *
 * Every count here is something a caller would otherwise have to infer from silence. The
 * discard counts are the point: a filter that quietly drops everything and a corpus that
 * genuinely contains nothing produce the same empty list, and only these numbers separate them.
 */
data class HarvestReport(
    /** Files carrying a language this harvester parses. */
    val sourceFiles: Int,
    /** Named declarations found, documented or not. */
    val declarations: Int,
    /** Declarations that came away with a doc comment — the entries returned. */
    val documented: Int,
    /**
     * Doc comments no declaration claimed. File licence headers are the bulk of these: they sit
     * above the package statement, so the whitespace-only gap rule refuses to bind them to the
     * first declaration underneath. Counted rather than dropped in silence.
     */
    val unclaimedDocs: Int,
    /** Doc comments that cleaned down to fewer than four words — too thin to retrieve on. */
    val tooShort: Int,
    /** Source files the parser reported a syntax error in. Their declarations are still taken. */
    val withParseErrors: Int,
    /** Files in the archive that could not be read at all. */
    val unreadable: Int,
    /**
     * Kotlin source-set roots found at the top of the archive — `commonMain`, `jvmMain`,
     * `appleMain`. Empty for a package-rooted jar, which is what a plain JVM library publishes.
     * This is what distinguishes a multiplatform publication, and it costs nothing: the archive
     * is already open.
     */
    val sourceSets: Set<String>,
)

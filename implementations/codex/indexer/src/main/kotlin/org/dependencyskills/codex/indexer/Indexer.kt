package org.dependencyskills.codex.indexer

import org.dependencyskills.codex.classifier.Decision
import org.dependencyskills.codex.classifier.ProseClassifier
import org.dependencyskills.codex.core.Codex
import org.dependencyskills.codex.core.Coordinate
import org.dependencyskills.codex.core.EntryState
import org.dependencyskills.codex.core.HarvestState
import org.dependencyskills.codex.harvester.BytecodeHarvester
import org.dependencyskills.codex.harvester.ClassFileVisibility
import org.dependencyskills.codex.harvester.HarvestResult
import org.dependencyskills.codex.harvester.harvestBytecode
import org.dependencyskills.codex.harvester.SourcesJarHarvester
import org.dependencyskills.codex.harvester.VisibilityOracle
import org.dependencyskills.codex.harvester.harvest
import org.dependencyskills.codex.index.TwoFacedIndex
import org.dependencyskills.codex.inference.TextEncoder
import org.dependencyskills.codex.inference.TextGenerator
import org.dependencyskills.codex.summariser.Summariser
import org.dependencyskills.codex.summariser.summarise
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Turns `Pending` into `Indexed`.
 *
 * Every stage this calls already existed and was tested; none of them was ever called outside a
 * test, so a coordinate a build recorded stayed `Pending` for ever and the server correctly
 * reported that nothing was indexed. This is the caller.
 *
 * **The order is load-bearing, not incidental.** Classification runs before summarisation so that
 * suspect prose reaches the summariser *already degraded* and its rewrite is withheld rather than
 * stored. Paraphrasing suspect prose well does not make it less suspect — it makes it more
 * persuasive, in our own voice, which is the laundering route the whole design exists to close.
 *
 * **One model, held for the whole pass.** The generator is opened by the caller and passed in,
 * because loading it costs seconds and a pass covers many coordinates. Unloading between them
 * would dominate the work.
 */
class Indexer(
    private val store: Path,
    /**
     * The summariser, or null when this machine has no generative model configured.
     *
     * Nullable because #28's bytecode path never summarises anything: a library that publishes no
     * sources yields a signature and no prose, so it can be indexed with no model at all. Making
     * this required left that path unreachable on exactly the machine it exists for.
     */
    private val generator: TextGenerator?,
    private val generatorName: String,
    private val encoder: TextEncoder,
    private val encoderName: String,
    private val vectors: Path,
    /**
     * How a coordinate becomes a file on disk, and what happens to it afterwards.
     *
     * The only part of this that touches the outside world, so a test drives every path without a
     * populated cache or a network.
     */
    private val sources: SourcesSupplier,
    /**
     * Where the COMPILED jar is, for a coordinate that publishes no sources (#28).
     *
     * Injected for the same reason [sources] is: it is the other thing that reaches outside, and
     * a test needs to route a coordinate down the bytecode path without a populated cache.
     * Returning null is ordinary — this machine simply has neither artifact.
     */
    private val classes: (Coordinate) -> Path? = SourcesInCache::classes,
    /**
     * How many coordinates are in flight. **Not** how many model loads.
     *
     * Above one this does not make summarising parallel — a generator holds a llama.cpp context
     * and nothing says it is safe to call concurrently, so calls to it are serialised. What it
     * parallelises is everything else: harvesting a jar, classifying its prose and embedding its
     * entries, all of which are I/O and CPU, and all of which can overlap another coordinate's
     * time in the model. That is where the wall-clock is, and it needs one model rather than N.
     */
    private val concurrency: Int = 1,
) {

    /**
     * The generator, one caller at a time.
     *
     * A wrapper rather than a lock at each call site, so it is impossible to add a path that
     * forgets. The cost is that the model is a queue; the alternative is a model per worker, which
     * multiplies the memory this service was just made careful about.
     */
    private class Serialised(private val delegate: TextGenerator) : TextGenerator {
        override fun generate(prompt: String, maxTokens: Int): String =
            synchronized(this) { delegate.generate(prompt, maxTokens) }
        override fun close() = Unit   // owned by the caller, closed with the pass
    }

    /** What became of one coordinate, for whoever is reporting progress. */
    data class Outcome(
        val coordinate: Coordinate,
        val state: HarvestState,
        val entries: Int = 0,
        val degraded: Int = 0,
        val indexed: Int = 0,
        val detail: String? = null,
        /** Declarations the compiled artifact said no consumer can reach, and which were dropped. */
        val notReachable: Int = 0,
        /**
         * Identifiers refused because the name itself read as prose (#28).
         *
         * Reported so a library attacking this is distinguishable from one that was merely empty.
         */
        val refusedNames: Int = 0,
        /**
         * Declarations kept because no compiled artifact was on this machine to judge them.
         *
         * Reported rather than logged once: a pass where this is large did not apply the
         * visibility rule at all, and that looks exactly like a library that is entirely public.
         */
        val visibilityUnknown: Int = 0,
    )

    /**
     * Runs every `Pending` coordinate through the pipeline, reporting each as it completes.
     *
     * [observer] is called per coordinate rather than at the end, because a pass is minutes of
     * model calls and a service that says nothing until it finishes is indistinguishable from one
     * that has hung.
     */
    fun run(observer: (Outcome) -> Unit = {}): List<Outcome> {
        val pending = Codex.open(store).use { it.coordinatesIn(HarvestState.Pending).map { r -> r.coordinate } }
        if (pending.isEmpty()) return emptyList()

        // Opened once for the pass, not per coordinate. Lucene's writer is thread-safe and holds a
        // directory lock, so a writer per coordinate would both cost more and make concurrency
        // impossible. Committed after each coordinate, so a pass killed half way keeps what it did.
        return TwoFacedIndex.open(vectors, encoderName, encoder.pooling, encoder.dimensions).use { index ->
            val serialised = generator?.let { Serialised(it) }
            val results = java.util.Collections.synchronizedList(mutableListOf<Outcome>())
            val pool = Executors.newFixedThreadPool(concurrency.coerceAtLeast(1)) { r ->
                Thread(r, "dscodex-index").apply { isDaemon = true }
            }
            try {
                pending.forEach { coordinate ->
                    pool.execute {
                        val outcome = runCatching { index(coordinate, index, serialised) }.getOrElse { failure ->
                            // Failed, not NoSource. The distinction is retryability: a jar that
                            // could not be read today may read tomorrow, and a library that
                            // publishes no sources never will.
                            Codex.open(store).use { it.harvestState(coordinate, HarvestState.Failed) }
                            Outcome(coordinate, HarvestState.Failed,
                                detail = failure.message ?: failure::class.simpleName)
                        }
                        results.add(outcome)
                        observer(outcome)
                    }
                }
                pool.shutdown()
                // No deadline. A pass is minutes of model calls by design, and a timeout here would
                // abandon work that is progressing rather than stuck.
                pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
            } finally {
                pool.shutdownNow()
            }
            results.toList()
        }
    }

    /** One coordinate, all the way through, into an index the pass owns. */
    private fun index(coordinate: Coordinate, index: TwoFacedIndex, generator: TextGenerator?): Outcome {
        // No sources anywhere: fall through to the compiled jar rather than giving up on the
        // library for good (#28). This is the set that has nothing else to offer — a private
        // repository's artifacts most of all, which a build resolves with its own credentials and
        // which very often publish no sources at all.
        val jar = sources.acquire(coordinate) ?: return fromBytecode(coordinate, index)

        // Sources exist but nothing can summarise them. Left PENDING rather than Failed or
        // NoSource: both of those are terminal in their own way, and this coordinate is neither
        // broken nor sourceless — it is waiting for a model that may be configured tomorrow.
        if (generator == null) {
            sources.release(jar)
            return Outcome(
                coordinate, HarvestState.Pending,
                detail = "waiting for a generative model to summarise its prose",
            )
        }

        return try {
            pipeline(coordinate, jar.path, index, generator)
        } finally {
            // Only what we fetched. A jar found in the build's cache is left exactly where it was.
            sources.release(jar)
        }
    }

    /**
     * Indexes a coordinate from its compiled classes, because it publishes no sources (#28).
     *
     * **Nothing is fetched.** The jar is read from the build's own cache, where it must already be
     * for the project to have compiled — which is exactly why this works for a private repository
     * whose credentials live in the build and not here.
     *
     * **The summariser is never called.** There is no prose to rewrite, and pointing a generative
     * model at a signature would hand it attacker-controlled text with nothing to paraphrase. The
     * classifier is skipped for the same reason: it scores documentation, and there is none.
     */
    private fun fromBytecode(coordinate: Coordinate, index: TwoFacedIndex): Outcome {
        val compiled = classes(coordinate)
            ?: return Codex.open(store).use {
                it.harvestState(coordinate, HarvestState.NoSource)
                Outcome(coordinate, HarvestState.NoSource, detail = "no sources and no classes on this machine")
            }

        var entries = 0
        var indexed = 0
        var refused = 0
        val result = Codex.open(store).use { codex ->
            codex.harvestBytecode(coordinate, compiled, BytecodeHarvester())
        }
        when (result) {
            is HarvestResult.Failed ->
                return Outcome(coordinate, HarvestState.Failed, detail = result.reason)
            is HarvestResult.NoSource ->
                return Outcome(coordinate, HarvestState.NoSource, detail = result.reason)
            is HarvestResult.Harvested -> {
                entries = result.entries.size
                refused = result.report.refusedNames
            }
        }

        // Embedded on the SIGNATURE, which is all a degraded entry has. The documentation face of
        // a summarised entry carries its prose; here that face carries the declaration itself, so
        // the entry is still findable rather than being an unreachable row in the store.
        Codex.open(store).use { codex ->
            codex.entriesOf(coordinate).forEach { entry ->
                if (entry.state != EntryState.Degraded) return@forEach
                index.add(
                    entry.id,
                    entry.coordinates,
                    encoder.embed(keyText(entry.symbol, entry.signature)),
                    null,          // no rewrite exists, and none is invented
                )
                indexed++
            }
        }
        index.commit()

        Codex.open(store).use { it.harvestState(coordinate, HarvestState.Indexed) }
        return Outcome(
            coordinate, HarvestState.Indexed, entries = entries, indexed = indexed,
            refusedNames = refused,
            detail = "from bytecode: no sources published",
        )
    }

    private fun pipeline(
        coordinate: Coordinate,
        jar: Path,
        index: TwoFacedIndex,
        generator: TextGenerator,
    ): Outcome {

        var entries = 0
        var degraded = 0
        var indexed = 0
        var notReachable = 0
        var visibilityUnknown = 0

        // #30: what a consumer can reach is read from the compiled artifact, which source cannot
        // answer — an entry with no visibility keyword is an implicitly-public interface member
        // or a package-private class, and in source those are identical.
        //
        // Absent on this machine is ordinary rather than an error: a coordinate whose sources
        // were fetched without its classes harvests everything, and the report says so. Silently
        // treating that as "index it all" is how the defect would return unnoticed.
        val classes = SourcesInCache.classes(coordinate)
        val visibility = classes?.let { ClassFileVisibility.of(it) } ?: VisibilityOracle.Blind

        Codex.open(store).use { codex ->
            val harvested = codex.harvest(coordinate, jar, SourcesJarHarvester(visibility = visibility))
            (harvested as? org.dependencyskills.codex.harvester.HarvestResult.Harvested)?.report?.let {
                notReachable = it.notReachable
                visibilityUnknown = it.visibilityUnknown
            }
            entries = codex.entriesOf(coordinate).size
            // Back to Pending, deliberately, and this is not bookkeeping.
            //
            // `Codex.put` marks a coordinate Indexed as soon as its ENTRIES are written, which in
            // the store's vocabulary means "harvested". This pipeline is not finished at that
            // point - nothing has been classified, summarised or embedded - and a pass killed in
            // the window between them left the coordinate marked complete while being anything
            // but. Measured: a library interrupted mid-summarise sat at 195 of 4,176 entries
            // rewritten, marked Indexed, and no later pass would ever look at it again.
            //
            // Pending until the whole pipeline finishes. Re-running is cheap by construction:
            // entries are content-addressed so a re-harvest writes nothing new, and summarise is
            // idempotent per model so the 195 are not paid for twice.
            codex.harvestState(coordinate, HarvestState.Pending)

            // -- classify, BEFORE anything paraphrases ------------------------------------------
            val classifier = ProseClassifier()
            codex.entriesOf(coordinate).forEach { entry ->
                if (entry.docFormat !in classifier.calibratedFor()) return@forEach
                val doc = codex.rawDocumentation(entry.id) ?: return@forEach
                if (classifier.classify(doc, entry.docFormat).decision == Decision.Suspect) {
                    codex.setEntryState(entry.id, EntryState.Degraded)
                    degraded++
                }
            }

            // -- summarise ----------------------------------------------------------------------
            codex.summarise(coordinate, Summariser(generator, model = generatorName))
        }

        // -- embed both faces ---------------------------------------------------------------
        Codex.open(store).use { codex ->
            codex.entriesOf(coordinate).forEach { entry ->
                val doc = codex.rawDocumentation(entry.id) ?: return@forEach
                val docVector = encoder.embed(keyText(entry.symbol, doc))
                // Only a whole entry has a rewrite; a degraded one has none, and its
                // documentation face still makes it findable. That is the point of two faces.
                val rewriteVector = entry.rewrite?.let { encoder.embed(keyText(entry.symbol, it)) }
                index.add(entry.id, entry.coordinates, docVector, rewriteVector)
                indexed++
            }
        }
        // Per coordinate, so an interrupted pass keeps everything it finished.
        index.commit()

        Codex.open(store).use { it.harvestState(coordinate, HarvestState.Indexed) }
        return Outcome(coordinate, HarvestState.Indexed, entries, degraded, indexed,
            notReachable = notReachable, visibilityUnknown = visibilityUnknown)
    }

    companion object {
        /**
         * What an entry is embedded as.
         *
         * The symbol's last segment, then the text. This has to match what the measurement was
         * taken with — the retrieval numbers this design rests on were produced with exactly this
         * key — and a query is embedded as the bare need, deliberately: a developer asks for a
         * capability, not for a symbol.
         */
        fun keyText(symbol: String, text: String): String =
            symbol.substringAfterLast('.') + ". " + text.take(MAX_CHARS)

        /**
         * The clamp the generative path already learned the hard way.
         *
         * Twenty-eight doc comments in one real corpus exceeded the context and took the process
         * down with SIGABRT part-way through a fifteen-minute run.
         */
        const val MAX_CHARS = 4000
    }
}

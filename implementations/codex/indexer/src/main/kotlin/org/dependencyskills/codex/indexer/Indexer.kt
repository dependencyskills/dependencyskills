package org.dependencyskills.codex.indexer

import org.dependencyskills.codex.classifier.Decision
import org.dependencyskills.codex.classifier.ProseClassifier
import org.dependencyskills.codex.core.Codex
import org.dependencyskills.codex.core.Coordinate
import org.dependencyskills.codex.core.EntryState
import org.dependencyskills.codex.core.HarvestState
import org.dependencyskills.codex.harvester.SourcesJarHarvester
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
    private val generator: TextGenerator,
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
            val serialised = Serialised(generator)
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
    private fun index(coordinate: Coordinate, index: TwoFacedIndex, generator: TextGenerator): Outcome {
        val jar = sources.acquire(coordinate)
            ?: return Codex.open(store).use {
                // Nothing on this machine has downloaded them. Recorded so it is not re-attempted
                // on every pass, and so #28 can find these coordinates when bytecode indexing
                // exists — it is exactly this set that has nothing else to offer.
                it.harvestState(coordinate, HarvestState.NoSource)
                Outcome(coordinate, HarvestState.NoSource, detail = "no sources jar in the cache or on Central")
            }

        return try {
            pipeline(coordinate, jar.path, index, generator)
        } finally {
            // Only what we fetched. A jar found in the build's cache is left exactly where it was.
            sources.release(jar)
        }
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

        Codex.open(store).use { codex ->
            codex.harvest(coordinate, jar, SourcesJarHarvester())
            entries = codex.entriesOf(coordinate).size

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
        return Outcome(coordinate, HarvestState.Indexed, entries, degraded, indexed)
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

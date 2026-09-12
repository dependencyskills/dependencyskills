package org.dependencyskills.codex.server

import org.dependencyskills.codex.core.Codex
import org.dependencyskills.codex.core.HarvestState
import org.dependencyskills.codex.index.PackagedEncoder
import org.dependencyskills.codex.indexer.Indexer
import org.dependencyskills.codex.indexer.SourcesSupplier
import org.dependencyskills.codex.inference.openEncoder
import org.dependencyskills.codex.inference.openGenerator
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.dependencyskills.codex.inference.TextEncoder
import org.dependencyskills.codex.inference.TextGenerator

/**
 * Decides *when* indexing runs. [Indexer] knows how.
 *
 * **On its own thread, always.** A pass is minutes of model calls, and the service has to keep
 * answering `search` and `get` throughout — an agent asking a question while its own dependencies
 * are being indexed is the ordinary case, not an edge one.
 *
 * **One pass at a time.** A second request arriving mid-pass does not start a second pass holding
 * a second model; it is dropped, because the pass already running will pick up anything newly
 * `Pending` when it reaches it. Two concurrent passes would double the memory to do the same work.
 *
 * **The model is opened when a pass starts and released when the service goes idle**, so a machine
 * that is not indexing costs a JVM and an open database rather than a generative model. Not between
 * coordinates, and not immediately at the end of a pass: a sync of several projects arrives as a
 * burst, and reloading between them would pay for the load repeatedly. `keepModelResident` holds it
 * instead, for a machine with memory to spare or a service dedicated to many machines.
 */
class IndexingService(
    private val store: Path,
    private val config: IndexingSettings,
    /**
     * Called after a pass that indexed something, so the query side can pick the index up.
     *
     * Not optional in practice: without it the service builds a vector index and keeps answering
     * lexically until it is restarted, which nothing reports and only shows up as worse answers.
     */
    private val onIndexed: () -> Unit = {},
) : AutoCloseable {

    private val logger = LoggerFactory.getLogger(IndexingService::class.java)

    // Single-threaded on purpose: it is the mechanism that makes "one pass at a time" true rather
    // than intended, and a daemon thread so a stuck pass cannot keep the process alive.
    private val passes = Executors.newSingleThreadExecutor { r ->
        Thread(r, "dscodex-indexer").apply { isDaemon = true }
    }
    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)

    /**
     * The loaded models, when they are being kept.
     *
     * Held only under `keepModelResident`, and closed by an idle timer otherwise. Guarded because
     * the idle sweeper and a starting pass both touch them.
     */
    private var heldGenerator: TextGenerator? = null
    private var heldEncoder: TextEncoder? = null
    private val models = Any()

    @Volatile private var lastPass = 0L

    /**
     * Closes the models once nothing has needed them for a while.
     *
     * Not zero delay. A sync of several projects arrives as a burst, and unloading between them
     * would pay the load again for work that was about to continue. Skipped entirely when the
     * operator has asked for the model to stay.
     */
    private val idle = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "dscodex-idle").apply { isDaemon = true }
    }.also { sweeper ->
        sweeper.scheduleWithFixedDelay({
            runCatching {
                if (config.keepModelResident || running.get()) return@runCatching
                val idleFor = (System.nanoTime() - lastPass) / 1_000_000_000
                if (lastPass != 0L && idleFor >= config.unloadAfterIdleSeconds) releaseModels()
            }
        }, 30, 30, TimeUnit.SECONDS)
    }

    /**
     * Loads the models ahead of the work, without starting a pass.
     *
     * A build signals this at the **start** of configuration, so the load overlaps the dependency
     * download instead of following it. On a cold project that download is minutes and the load is
     * not instant either; running them one after the other wastes the overlap.
     *
     * **Conditional, and that is the point.** A signal for a machine with nothing `Pending` loads
     * nothing, because otherwise every build on a fully indexed machine would page in a
     * multi-gigabyte model to do nothing with it — which is the complaint this was meant to avoid,
     * arriving through the fix for it.
     */
    fun warm(): Boolean {
        if (paused.get() || pending() == 0 || isLoaded) return false
        val generativeModel = config.model ?: return false
        val packaged = PackagedEncoder.unpack() ?: return false
        passes.execute {
            runCatching { models(generativeModel, packaged.model.toString(), packaged.pooling) }
                .onSuccess { logger.info("models warmed ahead of the next pass") }
                .onFailure { logger.warn("could not warm the models: {}", it.message) }
        }
        return true
    }

    private val isLoaded: Boolean get() = synchronized(models) { heldGenerator != null }

    /** Stops further passes. A pass already running finishes the coordinate it is on. */
    fun pause() { paused.set(true) }

    fun resume() {
        paused.set(false)
        request()
    }

    val isPaused: Boolean get() = paused.get()

    /** How many coordinates are waiting, for anything reporting status. */
    fun pending(): Int = runCatching {
        Codex.open(store).use { it.coordinatesIn(HarvestState.Pending).size }
    }.getOrDefault(0)

    val isRunning: Boolean get() = running.get()

    /**
     * Asks for a pass, and returns immediately.
     *
     * Returns false when one is already running or there is nothing to do. Neither is an error:
     * the first means the work is already in hand, and the second is the ordinary state of a
     * machine whose dependencies are all indexed.
     */
    fun request(): Boolean {
        if (paused.get()) return false
        if (pending() == 0) return false
        if (!running.compareAndSet(false, true)) return false
        passes.execute {
            try {
                pass()
            } catch (t: Throwable) {
                // A pass that dies must not take the service with it, and must not be silent.
                logger.error("the indexing pass failed", t)
            } finally {
                running.set(false)
                lastPass = System.nanoTime()
                // Released immediately unless the operator asked for them to stay, so an idle
                // service costs a JVM and an open database rather than a generative model.
                if (!config.keepModelResident && config.unloadAfterIdleSeconds <= 0) releaseModels()
                if (paused.get()) logger.info("indexing is paused; {} coordinates left", pending())
            }
        }
        return true
    }

    private fun pass() {
        val packaged = PackagedEncoder.unpack()
        if (packaged == null) {
            logger.warn("no packaged encoder on the classpath; nothing can be indexed")
            return
        }
        // No generative model is NOT a reason to skip the pass, and treating it as one was a
        // real defect: #28 indexes a library that publishes no sources straight from its
        // bytecode, and that path never summarises anything. Bailing out here left the fallback
        // unreachable on exactly the machine it exists for — one with no model set up, where a
        // signature with no prose is the only thing on offer.
        //
        // Still said plainly, because a service that quietly indexes nothing looks exactly like
        // one that is working.
        val generativeModel = config.model
        if (generativeModel == null) {
            logger.warn(
                "no generative model configured — {} coordinates cannot be summarised; any that " +
                    "publish no sources will still be indexed from bytecode. Set indexing.model " +
                    "in the config to summarise the rest",
                pending(),
            )
        }

        val started = System.nanoTime()
        var indexed = 0
        var noSource = 0
        var failed = 0

        val (generator, encoder) = models(generativeModel, packaged.model.toString(), packaged.pooling)
        try {
            logger.info("indexing {} coordinates", pending())
            val staging = store.resolveSibling(STAGING)
            SourcesSupplier(staging).let { supplier ->
                // Anything staged before this pass is an orphan: no pass is running, so nothing is
                // waiting on it, and a killed pass re-fetches from the start.
                supplier.clean()
                Indexer(
                    store = store,
                    generator = generator,
                    generatorName = generativeModel?.let { Path.of(it).fileName.toString() } ?: "none",
                    encoder = encoder,
                    encoderName = packaged.name,
                    vectors = store.resolveSibling(VECTORS),
                    sources = supplier,
                    concurrency = config.concurrency,
                ).run { outcome ->
                    when (outcome.state) {
                        HarvestState.Indexed -> {
                            indexed++
                            logger.info(
                                "indexed {} — {} entries, {} degraded, {} not reachable",
                                outcome.coordinate, outcome.entries, outcome.degraded,
                                outcome.notReachable,
                            )
                            // Said only when it happens. A library that is genuinely all public
                            // and one nothing judged look identical without this line.
                            //
                            // It used to name a missing classes jar as the reason, and that is
                            // now the less common half of it: with the artifact found, entries
                            // still go unjudged when the oracle cannot resolve their symbol —
                            // 1,410 of kotlin-stdlib's 6,303 on one real pass, mostly members
                            // nested inside another type. Two causes, one count, so the line
                            // reports the count and does not claim to know which.
                            if (outcome.visibilityUnknown > 0) {
                                logger.info(
                                    "{} entries of {} went unjudged — no compiled artifact, or a " +
                                        "symbol the oracle could not resolve in it",
                                    outcome.visibilityUnknown, outcome.coordinate,
                                )
                            }
                            // A library whose identifiers were refused must not read as a library
                            // that merely had nothing in it. Said at WARN because a name long
                            // enough to be refused is either generated code or an attempt.
                            if (outcome.refusedNames > 0) {
                                logger.warn(
                                    "{} identifiers in {} were refused as too long to be names",
                                    outcome.refusedNames, outcome.coordinate,
                                )
                            }
                        }
                        HarvestState.NoSource -> {
                            noSource++
                            logger.info("no sources for {}", outcome.coordinate)
                        }
                        else -> {
                            failed++
                            logger.warn("failed on {}: {}", outcome.coordinate, outcome.detail)
                        }
                    }
                }
            }
        } finally {
            // Left loaded on purpose. A sync of several projects arrives as a burst and the idle
            // sweeper decides when they go, so a second project does not pay the load again.
        }
        // Every pass says what it did, including a pass that did nothing. That is the difference
        // between an indexer that is idle and one that is broken.
        if (indexed > 0) runCatching { onIndexed() }
        logger.info(
            "pass finished in {}s — {} indexed, {} without sources, {} failed",
            (System.nanoTime() - started) / 1_000_000_000,
            indexed, noSource, failed,
        )
    }

    /** Opens the models, or returns the ones already held. */
    private fun models(generativeModel: String?, encoderModel: String, pooling: org.dependencyskills.codex.inference.Pooling):
        Pair<TextGenerator?, TextEncoder> = synchronized(models) {
        // Null when nothing is configured. The encoder is always opened — embedding is what makes
        // an entry findable, and a degraded entry needs it just as much as a summarised one.
        val generator = heldGenerator ?: generativeModel?.let { openGenerator(it, contextTokens = 2048) }
        val encoder = heldEncoder ?: openEncoder(encoderModel, pooling)
        // Always held once open. What differs is when they are let go: the idle sweeper releases
        // them unless `keepModelResident`, and nothing else does. One owner, one policy.
        heldGenerator = generator
        heldEncoder = encoder
        generator to encoder
    }

    private fun releaseModels() = synchronized(models) {
        heldGenerator?.let { runCatching { it.close() } }
        heldEncoder?.let { runCatching { it.close() } }
        if (heldGenerator != null) logger.info("released the models after {}s idle", config.unloadAfterIdleSeconds)
        heldGenerator = null
        heldEncoder = null
    }

    override fun close() {
        idle.shutdownNow()
        passes.shutdownNow()
        releaseModels()
    }

    private companion object {
        /** Beside the store, matching where the query side looks for it. */
        const val VECTORS = "vectors"

        /** Ours, and only ours. Sources we fetched live here and nowhere near a build's cache. */
        const val STAGING = "sources"
    }
}

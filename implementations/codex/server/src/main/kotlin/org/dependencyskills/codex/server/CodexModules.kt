package org.dependencyskills.codex.server

import org.dependencyskills.codex.core.Codex
import org.dependencyskills.codex.core.CodexLocation
import org.dependencyskills.codex.index.VectorSearch
import org.koin.core.module.Module
import org.koin.dsl.module
import java.nio.file.Path

/**
 * Where this machine's store lives, for whoever needs to say so.
 *
 * One place, because the service and the stdio transport must not disagree about it: two entry
 * points each reading their own environment variable is how a developer ends up with two stores
 * and an index that is somehow always empty.
 */
fun storeFile(): Path =
    System.getenv("DSCODEX_STORE")?.let { Path.of(it) } ?: CodexLocation.databaseFile()

/**
 * What the service holds open for its whole life.
 *
 * The store and the index are singletons; [CodexQueries] is a **factory**, taking the scope as a
 * parameter. That difference is the containment boundary expressed in the container: scope belongs
 * to one request rather than to the process, so a caller cannot be handed an instance carrying
 * somebody else's. A singleton here is exactly the mistake that would serve one project's answers
 * to another, and it would do it silently.
 *
 * `createdAtStart` on both, so the container opens them while the service is starting rather than
 * on the first request that happens to need one. A store that cannot be opened should take the
 * process down in front of whoever started it, not surface as a 500 to the first caller.
 *
 * Closing is not the container's job here. It happens on Ktor's `ApplicationStopped`, so the store
 * is released exactly when the server that was serving from it stops.
 */
fun codexRuntimeModule(store: Path = storeFile()): Module = module {
    single<Codex>(createdAtStart = true) { Codex.open(store) }

    // Registered only when one exists, and injected with `getOrNull`. A store that has been
    // harvested but not embedded is an ordinary state, not a missing dependency - answering
    // lexically is what the query layer already does when this is absent.
    VectorSearch.openIfBuilt(store)?.let { index ->
        single<VectorSearch>(createdAtStart = true) { index }
    }

    // A new one every time it is asked for, over the singletons above.
    factory { (scope: ProjectScope) -> CodexQueries(get(), scope, getOrNull()) }
}

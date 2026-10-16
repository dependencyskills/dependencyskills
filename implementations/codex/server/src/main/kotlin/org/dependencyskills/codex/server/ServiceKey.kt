package org.dependencyskills.codex.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import java.security.MessageDigest

/** The environment variable that sets the service key. */
const val KEY_ENV = "DSCODEX_KEY"

/** The one route answered without the key, so a supervisor can tell the process is up. */
internal const val OPEN_PATH = "/health"

/**
 * The service key, when one is set: [KEY_ENV], with blank counting as absent.
 *
 * **An environment variable and never a setting**, so `config.toml` holds no secret and can be read, copied and
 * compared freely. An installed service takes it from `/etc/dscodex/environment`, which the unit reads if it
 * exists.
 */
internal fun serviceKey(env: Map<String, String> = System.getenv()): String? =
    env[KEY_ENV]?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Requires [key] as `Authorization: Bearer <key>` on every route but [OPEN_PATH].
 *
 * **Optional, deliberately.** With no key the service answers anyone who can reach it, which is right for one on
 * loopback and is the default. A key is for a service shared on a network: a shared secret over plain HTTP, which
 * keeps it from answering whoever finds the port and claims nothing more than that.
 *
 * The comparison takes the same time however much of the key matches, so the answer says nothing about how
 * close a guess was.
 */
internal fun Application.requireKey(key: String) {
    val expected = key.toByteArray()
    intercept(ApplicationCallPipeline.Plugins) {
        if (call.request.path() == OPEN_PATH) return@intercept
        val presented = call.request.header(HttpHeaders.Authorization)?.trim()?.split(' ', limit = 2)
            ?.takeIf { it.size == 2 && it[0].equals("Bearer", ignoreCase = true) }
            ?.get(1)?.trim()?.toByteArray()
        if (presented != null && MessageDigest.isEqual(presented, expected)) return@intercept
        call.response.header(HttpHeaders.WWWAuthenticate, "Bearer")
        call.respondText(
            "401: this service needs its key, sent as Authorization: Bearer <key>",
            status = HttpStatusCode.Unauthorized,
        )
        finish()
    }
}

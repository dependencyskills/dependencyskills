package org.dependencyskills.codex.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.AfterEach
import org.koin.core.context.stopKoin
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The optional key: required everywhere but `/health` when set, and nowhere when not. */
class ServiceKeyTest {

    private val key = "correct-horse"

    @AfterEach fun tearDown() = stopKoin()

    private fun store(): Path = createTempDirectory("key").resolve("codex.db")

    @Test
    fun `with a key, health stays open and everything else asks for it`() = testApplication {
        val store = store()
        application { codexModule(store, CodexConfig.load(store), key) }

        assertEquals(HttpStatusCode.OK, client.get("/health").status)

        val refused = client.get("/indexing")
        assertEquals(HttpStatusCode.Unauthorized, refused.status)
        assertEquals("Bearer", refused.headers[HttpHeaders.WWWAuthenticate])
        assertTrue("Authorization: Bearer" in refused.bodyAsText())

        assertEquals(HttpStatusCode.Unauthorized, client.post("/mcp").status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/indexing") { header(HttpHeaders.Authorization, "Bearer correct-hors") }.status,
        )
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/indexing") { header(HttpHeaders.Authorization, key) }.status,
        )
    }

    @Test
    fun `the right key is accepted, whatever case the scheme is in`() = testApplication {
        val store = store()
        application { codexModule(store, CodexConfig.load(store), key) }
        assertEquals(HttpStatusCode.OK, client.get("/indexing") { header(HttpHeaders.Authorization, "Bearer $key") }.status)
        assertEquals(HttpStatusCode.OK, client.get("/indexing") { header(HttpHeaders.Authorization, "bearer $key") }.status)
    }

    @Test
    fun `a registration without the key records nothing`() = testApplication {
        val store = store()
        application { codexModule(store, CodexConfig.load(store), key) }
        val body = """{"path":"/srv/acme","coordinates":["maven:com.example.acme:acme-core:1.0.0"]}"""

        assertEquals(HttpStatusCode.Unauthorized, client.post("/projects") { setBody(body) }.status)
        assertTrue(client.get("/health").bodyAsText().contains("pending=0"))

        val accepted = client.post("/projects") {
            header(HttpHeaders.Authorization, "Bearer $key")
            setBody(body)
        }
        assertEquals("ok 1", accepted.bodyAsText())
    }

    @Test
    fun `without a key, nothing is asked for`() = testApplication {
        val store = store()
        application { codexModule(store, CodexConfig.load(store)) }
        assertEquals(HttpStatusCode.OK, client.get("/indexing").status)
    }

    @Test
    fun `the key comes from the environment, and blank is no key`() {
        assertEquals(key, serviceKey(mapOf(KEY_ENV to " $key\n")))
        assertNull(serviceKey(mapOf(KEY_ENV to "  ")))
        assertNull(serviceKey(emptyMap()))
    }
}

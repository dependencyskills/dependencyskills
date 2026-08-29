package org.dependencyskills.codex.harvester

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What survives a doc comment on its way to becoming a retrieval key.
 *
 * Every case here is shaped like documentation that exists: the Ktor footer, the Android `<p>`,
 * the escaped generic. The point of each is the same one — the store searches on these words, so
 * a word that is markup is a word a query can match by accident.
 */
class DocTextTest {

    @Test
    fun `a markdown link keeps its text and loses its address`() {
        val doc = cleanDoc(
            """
            /**
             * Customizes a request builder in the specified block.
             *
             * [Report a problem](https://example.test/feedback/?fqname=acme.Client.customize)
             */
            """.trimIndent(),
        )
        assertEquals("Customizes a request builder in the specified block. Report a problem", doc)
        assertTrue(doc!!.none { it == '[' || it == ']' })
    }

    @Test
    fun `a comment that is only a link footer is not a description`() {
        // Not a judgement about the footer - the address was most of what made this long enough
        // to keep, and three words of boilerplate retrieve nothing the symbol does not.
        assertNull(cleanDoc("/** [Report a problem](https://example.test/feedback/) */"))
    }

    @Test
    fun `a KDoc symbol reference is left alone`() {
        // It carries no address, and the text is a symbol name - the most matchable thing here.
        assertEquals(
            "Creates a raw [ClientWebSocketSession] with no ping-pong messages.",
            cleanDoc("/** Creates a raw [ClientWebSocketSession] with no ping-pong messages. */"),
        )
    }

    @Test
    fun `an HTML tag goes and the words it wrapped stay`() {
        assertEquals(
            "A primary toolbar within the activity. The action bar appears at the top.",
            cleanDoc(
                """
                /**
                 * A primary toolbar within the activity.
                 * <p>The action bar appears at the <em>top</em>.</p>
                 */
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `an anchor loses its address and keeps its label`() {
        assertEquals(
            "Consider using other common navigation patterns instead.",
            cleanDoc(
                """
                /**
                 * Consider using other
                 * <a href="http://developer.example.test/design/patterns/navigation.html">common
                 * navigation patterns</a> instead.
                 */
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `a pre block goes the way a markdown fence goes`() {
        assertEquals(
            "Builds a client. Both of those are code, not description.",
            cleanDoc(
                """
                /**
                 * Builds a client.
                 * <pre>{@code
                 * HttpClient client = HttpClient.newBuilder().build();
                 * }</pre>
                 *
                 * ```
                 * val client = HttpClient()
                 * ```
                 * Both of those are code, not description.
                 */
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `inline code keeps its content because a symbol name is worth matching`() {
        assertEquals(
            "Configures the AsyncClientConnectionManager used by the engine.",
            cleanDoc("/** Configures the <code>AsyncClientConnectionManager</code> used by the engine. */"),
        )
        assertEquals(
            "Configures the `AsyncClientConnectionManager` used by the engine.",
            cleanDoc("/** Configures the `AsyncClientConnectionManager` used by the engine. */"),
        )
    }

    @Test
    fun `entities are decoded into the characters the comment meant`() {
        assertEquals(
            "Returns a Map<String, List<Item>> keyed by name & sorted.",
            cleanDoc("/** Returns a Map&lt;String, List&lt;Item&gt;&gt; keyed by name &amp; sorted. */"),
        )
        // The numeric escapes javadoc needs so a comment can mention `*` `/` and `@` at all.
        assertEquals(
            "Matches the glob * against a / separated @path.",
            cleanDoc("/** Matches the glob &#42; against a &#47; separated &#64;path. */"),
        )
    }

    @Test
    fun `an entity this does not know is left as it was written`() {
        // The conservative half. A guessed expansion puts a word in the key the comment never
        // said, and no later pass would notice it had.
        assertEquals(
            "Sold by AT&T; and nobody else.",
            cleanDoc("/** Sold by AT&T; and nobody else. */"),
        )
    }

    @Test
    fun `a generic is prose, not a tag`() {
        // The reason the tag vocabulary is closed. `<\w+>` would eat every one of these.
        assertEquals(
            "Accepts a <T> and a <String> and a <key> placeholder.",
            cleanDoc("/** Accepts a <T> and a <String> and a <key> placeholder. */"),
        )
    }

    @Test
    fun `a bare address is dropped wherever it appears`() {
        // The preposition that pointed at it is left dangling, and that is fine. This is a key a
        // query is matched against, not prose anyone reads - "at" costs a stopword, and building
        // a rule that could tell a pointing "at" from any other one would cost far more.
        assertEquals(
            "See the release notes at for what changed.",
            cleanDoc("/** See the release notes at https://example.test/notes for what changed. */"),
        )
        assertEquals(
            "See the release notes at for what changed.",
            cleanDoc("/** See the release notes at <https://example.test/notes> for what changed. */"),
        )
    }

    @Test
    fun `a decoded entity cannot make a line look like a tag`() {
        // Decoding runs after the tag-only filter for exactly this: `&#64;param` written to
        // escape the tag would otherwise become `@param` and take the line with it.
        assertEquals(
            "@param is written like that on purpose here.",
            cleanDoc("/** &#64;param is written like that on purpose here. */"),
        )
    }

    @Test
    fun `a tag-only line is still structure`() {
        assertEquals(
            "Runs the thing and hands back what it produced.",
            cleanDoc(
                """
                /**
                 * Runs the thing and hands back what it produced.
                 *
                 * @param attempts how many times to try
                 * @return what it produced
                 */
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `a label is not a description`() {
        assertNull(cleanDoc("/** The name. */"))
        assertNull(cleanDoc("/** <p>The name.</p> */"))
    }
}

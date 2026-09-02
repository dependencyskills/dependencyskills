package org.dependencyskills.codex.harvester

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What a consumer of the library can actually reach (#30).
 *
 * Every case here is one the source could not have answered. They are asserted against two real
 * published artifacts rather than hand-written fixtures, because the traps — implicit public,
 * package-private, Kotlin `internal`, a top-level function in a synthesized file class — are
 * things a compiler produces and a test author would not think to write.
 */
class VisibilityTest {

    private fun harvestOf(sources: java.nio.file.Path, classes: java.nio.file.Path?) =
        assertIs<HarvestResult.Harvested>(
            SourcesJarHarvester(
                visibility = classes?.let { ClassFileVisibility.of(it) } ?: VisibilityOracle.Blind
            ).harvest(sources)
        )

    // -- the rule ------------------------------------------------------------------------------

    @Test
    fun `a member of a type nobody can reach is itself unreachable`() {
        // The rule is the ENCLOSING declaration's visibility, not the member's. `readResolve` is
        // declared inside a package-private class, so a consumer cannot call it however the
        // method's own flags read. Left as Unknown it would have been kept, which is the whole
        // defect: the first version of this oracle stopped recording members once it had decided
        // the type was out of reach, and every member of every package-private class came back.
        val oracle = assertNotNull(ClassFileVisibility.of(Fixtures.javaClasses))
        assertEquals(Reach.Unreachable, oracle.reach("org.slf4j.helpers.NamedLoggerBase"))
        assertEquals(Reach.Unreachable, oracle.reach("org.slf4j.helpers.NamedLoggerBase.readResolve"))
    }

    @Test
    fun `a public member of a public type is reachable`() {
        val oracle = assertNotNull(ClassFileVisibility.of(Fixtures.javaClasses))
        assertEquals(Reach.Reachable, oracle.reach("org.slf4j.ILoggerFactory"))
        assertEquals(Reach.Reachable, oracle.reach("org.slf4j.ILoggerFactory.getLogger"))
    }

    @Test
    fun `package-private and private statics are dropped from the harvest`() {
        val harvested = harvestOf(Fixtures.javaSources, Fixtures.javaClasses)
        val symbols = harvested.entries.map { it.symbol }.toSet()
        // Documented, so they were harvested before this rule existed; not callable, so they are
        // exactly the entries #30 was opened about.
        listOf(
            "org.slf4j.LoggerFactory.reset",
            "org.slf4j.LoggerFactory.getProvider",
            "org.slf4j.LoggerFactory.API_COMPATIBILITY_LIST",
            "org.slf4j.helpers.NamedLoggerBase",
        ).forEach { assertTrue(it !in symbols, "$it is not callable and should not be indexed") }
        assertTrue("org.slf4j.ILoggerFactory.getLogger" in symbols, "the public API must survive")
    }

    // -- the cases source cannot answer --------------------------------------------------------

    @Test
    fun `Kotlin internal is dropped, though it compiles to public`() {
        // `internal` is the case no access flag records: it compiles to public with the module
        // name appended, so it looks reachable in source AND in bytecode. Only the metadata says
        // otherwise.
        val oracle = assertNotNull(ClassFileVisibility.of(Fixtures.kotlinClasses))
        assertEquals(Reach.Unreachable, oracle.reach("kotlinx.serialization.UnknownFieldException"))
        assertEquals(Reach.Unreachable, oracle.reach("kotlinx.serialization.findCachedSerializer"))
    }

    @Test
    fun `a property resolves under its source name, not its accessor`() {
        // `descriptor` in source is `getDescriptor()` compiled. Matching on the JVM name leaves
        // every Kotlin property unresolved — 125 of 323 entries in this one library before the
        // metadata was consulted.
        val oracle = assertNotNull(ClassFileVisibility.of(Fixtures.kotlinClasses))
        assertEquals(Reach.Reachable, oracle.reach("kotlinx.serialization.KSerializer.descriptor"))
    }

    @Test
    fun `a top-level function resolves under its package, not its file class`() {
        // Top-level declarations compile into a class named after the file, and
        // `@JvmMultifileClass` splits that across parts — so `serializer` lives in
        // `SerializersKt__SerializersKt`. The source symbol names neither.
        val oracle = assertNotNull(ClassFileVisibility.of(Fixtures.kotlinClasses))
        assertEquals(Reach.Reachable, oracle.reach("kotlinx.serialization.serializer"))
    }

    @Test
    fun `every symbol in a Kotlin library resolves to a verdict`() {
        // The matching, not the flags, is the hard part of this rule — so this asserts the thing
        // that actually breaks: that no source symbol falls through to Unknown and gets kept by
        // default. A regression in overload, property or file-class handling shows up here first.
        val oracle = assertNotNull(ClassFileVisibility.of(Fixtures.kotlinClasses))
        val harvested = harvestOf(Fixtures.kotlinSources, Fixtures.kotlinClasses)
        assertEquals(0, harvested.report.visibilityUnknown, "every Kotlin symbol should resolve")
        assertTrue(harvested.report.notReachable > 0, "this library does have unreachable entries")
        assertEquals(Reach.Unknown, oracle.reach("com.example.NothingLikeThis"))
    }

    @Test
    fun `a private nested class is unreachable, and its public parent is not`() {
        // Nesting is read from the InnerClasses attribute, never by rewriting a `$` in the binary
        // name. That matters because a `$` is not reliably a nesting marker: gson shipped a
        // TOP-LEVEL class literally called `$Gson$Types` for over a decade, alongside genuinely
        // nested types inside it, and no rule applied to the string separates the two.
        val oracle = assertNotNull(ClassFileVisibility.of(Fixtures.javaClasses))
        assertEquals(Reach.Reachable, oracle.reach("org.slf4j.helpers.Reporter"))
        assertEquals(Reach.Unreachable, oracle.reach("org.slf4j.helpers.Reporter.Level"))
    }

    @Test
    fun `a public function taking a lambda is kept`() {
        // The guard against the rule that looks obvious and is wrong. "Skip lambdas" would throw
        // this away — RAD-0063 measured 23% of kotlin-stdlib's entries as public functions taking
        // one. A lambda inherits the visibility of the declaration that encloses it, and here
        // that declaration is public API a caller is meant to use.
        val oracle = assertNotNull(ClassFileVisibility.of(Fixtures.kotlinClasses))
        assertEquals(
            Reach.Reachable,
            oracle.reach("kotlinx.serialization.descriptors.buildClassSerialDescriptor"),
        )
        val harvested = harvestOf(Fixtures.kotlinSources, Fixtures.kotlinClasses)
        val kept = harvested.entries.first {
            it.symbol == "kotlinx.serialization.descriptors.buildClassSerialDescriptor"
        }
        assertTrue(kept.signature.contains("->"), "the fixture should be a lambda-taking function")
    }

    // -- one rule, both paths (#28) -------------------------------------------------------------

    @Test
    fun `the sources path and the bytecode path never disagree about what is reachable`() {
        // #30's Notes opened on this risk: two paths deciding visibility separately would make a
        // library's API depend on which artifact happened to be indexed. They share one oracle, and
        // this is what says so.
        //
        // The entry SETS are deliberately not compared. The sources path keeps only declarations
        // that carry a doc comment — 229 of them here — while the bytecode path keeps everything a
        // consumer can reach, 658. That difference is by design. What must never differ is the
        // reachability verdict on a symbol both paths saw.
        val oracle = assertNotNull(ClassFileVisibility.of(Fixtures.javaClasses))
        val fromSources = harvestOf(Fixtures.javaSources, Fixtures.javaClasses).entries.map { it.symbol }.toSet()
        val fromBytecode = assertIs<HarvestResult.Harvested>(BytecodeHarvester().harvest(Fixtures.javaClasses))
            .entries.map { it.symbol }.toSet()

        // Everything the sources path kept, the bytecode path also considers reachable. Symbols the
        // oracle has no opinion about are excluded: those are declarations with no compiled
        // counterpart, which is a fact about the artifacts rather than a disagreement — see #35.
        val judged = fromSources.filter { oracle.reach(it) != Reach.Unknown }
        assertTrue(judged.isNotEmpty(), "the fixture should have symbols both paths can see")
        judged.forEach {
            assertTrue(it in fromBytecode, "$it survived the sources path but not the bytecode path")
        }

        // And nothing the sources path refused as unreachable may appear from the bytecode path.
        // This is the direction that would actually leak: a private member reappearing because the
        // other extractor applied its own rule.
        val blind = harvestOf(Fixtures.javaSources, null).entries.map { it.symbol }.toSet()
        (blind - fromSources).forEach {
            assertTrue(it !in fromBytecode, "$it is not callable, yet the bytecode path indexed it")
        }
    }

    // -- no artifact ---------------------------------------------------------------------------

    @Test
    fun `with no compiled artifact nothing is dropped, and the report says so`() {
        // A missing classes jar must not silently become "index everything" with no trace. The
        // entries are kept — guessing would reintroduce the defect — and the count is what tells
        // an operator the rule is not running.
        val blind = harvestOf(Fixtures.javaSources, null)
        val filtered = harvestOf(Fixtures.javaSources, Fixtures.javaClasses)
        assertEquals(0, blind.report.notReachable)
        assertEquals(blind.entries.size, blind.report.visibilityUnknown)
        assertTrue(
            filtered.entries.size < blind.entries.size,
            "the oracle should have dropped something relative to the blind harvest",
        )
    }

    @Test
    fun `an artifact that is not a readable archive answers null rather than throwing`() {
        assertEquals(null, ClassFileVisibility.of(Fixtures.javaSources.resolveSibling("absent.jar")))
    }
}

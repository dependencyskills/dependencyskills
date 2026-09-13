package org.dependencyskills.codex.harvester

import org.dependencyskills.codex.core.NewEntry
import org.dependencyskills.codex.core.Provenance
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Turns one `-sources.jar` into entries.
 *
 * ADR-0009 settles where content comes from: the sources jar, which most libraries already
 * publish. Gradle and Maven never unpack a dependency, so this reads the archive in place
 * rather than extracting it.
 *
 * **This is a pure function of the jar.** It reads no prior state, makes no deduplication
 * decision, and returns the same entries in the same order for the same archive no matter what
 * is already stored. Duplicates collapse later, in the store, by content address — RAD-0041
 * found that deciding it here makes the store depend on which build ran first, and leaves a
 * project that depends only on the artifact which lost unable to see the entry at all.
 */
class SourcesJarHarvester(
    private val extractor: String = EXTRACTOR,
    /**
     * What a consumer of the library can reach, read from the compiled artifact (#30).
     *
     * Passed in rather than resolved here, which is what keeps the class above's promise: the
     * harvest stays a pure function of its inputs, and the decision about which classes jar
     * belongs to which sources jar stays with the caller that resolved them both.
     *
     * The default answers [Reach.Unknown] to everything, so a caller that has no compiled
     * artifact gets the previous behaviour — every documented declaration — and a report saying
     * that is what happened.
     */
    private val visibility: VisibilityOracle = VisibilityOracle.Blind,
) {

    fun harvest(jar: Path): HarvestResult {
        if (!Files.isRegularFile(jar)) {
            return HarvestResult.Failed("no readable file at ${jar.fileName}")
        }
        val zip = try {
            ZipFile(jar.toFile())
        } catch (e: ZipException) {
            return HarvestResult.Failed("not a readable archive: ${e.message}", e)
        } catch (e: java.io.IOException) {
            return HarvestResult.Failed("could not open the archive: ${e.message}", e)
        }
        return zip.use { read(it, jar) }
    }

    private fun read(zip: ZipFile, jar: Path): HarvestResult {
        // Sorted, so the entry order is a property of the archive's contents rather than of the
        // order the zip's central directory happens to list them in.
        val names = zip.entries().asSequence().filterNot { it.isDirectory }.map { it.name }.sorted().toList()
        val sources = names.mapNotNull { name -> SourceLanguage.of(name)?.let { name to it } }
        if (sources.isEmpty()) {
            return HarvestResult.NoSource(
                "${jar.fileName} holds no Kotlin or Java source (${names.size} files)"
            )
        }

        val entries = ArrayList<NewEntry>()
        var declarations = 0
        var unclaimedDocs = 0
        var tooShort = 0
        var withParseErrors = 0
        var unreadable = 0
        var notReachable = 0
        var visibilityUnknown = 0

        // One extractor per language per archive: a TSParser holds native state and is not safe
        // across threads, and creating one per file costs a grammar load each time.
        val extractors = HashMap<SourceLanguage, SourceExtractor>()
        try {
            for ((name, language) in sources) {
                val source = try {
                    zip.getInputStream(zip.getEntry(name)).use {
                        String(it.readBytes(), StandardCharsets.UTF_8)
                    }
                } catch (e: java.io.IOException) {
                    unreadable++
                    continue
                }
                val fileYield = extractors.getOrPut(language) { SourceExtractor(language) }.read(source)
                declarations += fileYield.declarations
                unclaimedDocs += fileYield.unclaimedDocs
                tooShort += fileYield.tooShort
                if (fileYield.hadParseError) withParseErrors++
                for (item in fileYield.extracted) {
                    // #30: a capability a developer cannot invoke is not a capability. The
                    // enclosing declaration decides — the oracle has already resolved a member
                    // of an unreachable type to unreachable, so this is a single lookup.
                    // The keyword first, because a keyword that is present is never ambiguous and
                    // the oracle is answering a coarser question — see [declaresNonPublic].
                    if (declaresNonPublic(item.signature)) { notReachable++; continue }
                    when (visibility.reach(item.symbol)) {
                        Reach.Unreachable -> { notReachable++; continue }
                        Reach.Unknown -> visibilityUnknown++
                        Reach.Reachable -> Unit
                    }
                    entries.add(
                        NewEntry(
                            symbol = item.symbol,
                            signature = item.signature,
                            doc = item.doc,
                            lang = language.lang,
                            docFormat = language.docFormat,
                            provenance = Provenance(extractor = extractor),
                        )
                    )
                }
            }
        } finally {
            extractors.values.forEach { it.close() }
        }

        return HarvestResult.Harvested(
            entries = entries,
            report = HarvestReport(
                sourceFiles = sources.size,
                declarations = declarations,
                documented = entries.size,
                unclaimedDocs = unclaimedDocs,
                tooShort = tooShort,
                withParseErrors = withParseErrors,
                unreadable = unreadable,
                notReachable = notReachable,
                visibilityUnknown = visibilityUnknown,
                sourceSets = sourceSetsOf(names),
            ),
        )
    }

    companion object {
        /**
         * Identifies what produced an entry, so a bad extractor can be invalidated selectively
         * rather than by deleting the store. Bump it when the extraction changes what it emits
         * for input it already read.
         */
        const val EXTRACTOR = "tree-sitter-sources-jar/1"

        /**
         * Whether a signature says, in a word, that nobody outside the library can reach it.
         *
         * **This does not reopen the argument [VisibilityOracle] settled.** That argument is about
         * the *absence* of a keyword: `T build()` in an interface is implicitly public and
         * `final class StrBuilderReader` is package-private, and no rule applied to source tells
         * them apart. Nothing here reads an absence. It reads a keyword that is present, and a
         * declaration that says `private` or `internal` out loud is unreachable from outside the
         * library in both languages, with no artifact required to know it.
         *
         * It exists because there are declarations no compiled artifact can speak for. An
         * `expect class` has no bytecode by definition, and a multiplatform sources jar carries
         * whole source sets — Kotlin/Native, JS — that no JVM classes jar covers. For those the
         * source keyword is not the weaker evidence, it is the only evidence there is. Measured on
         * a rebuild of four Kotlin libraries: 20 entries survived the oracle this way, among them
         * `internal expect class ValueTimeMarkReading` and an `internal class FileSink` taking a
         * `CPointer`.
         *
         * **It runs before the oracle, not after, and that ordering is load-bearing.** A symbol
         * omits parameter types, so an overload family shares one — and therefore one verdict.
         * `kotlin.text.split` is five declarations under that name, four public and one
         * `private`, and the family resolves to [Reach.Reachable]; consulted second, this screen
         * would never see the private one. That is #42 arriving as a visibility leak rather than
         * a ranking complaint, and reading the keyword first closes it without waiting on #42.
         *
         * `protected` is deliberately absent: a subclass outside the library can reach it.
         */
        internal fun declaresNonPublic(signature: String): Boolean {
            // Annotations first, and they may carry parenthesised arguments containing anything
            // at all — including the word `private`. Stripping them is what keeps this reading
            // the declaration's own modifiers rather than an annotation's payload.
            val declaration = LEADING_ANNOTATIONS.replace(signature.trimStart(), "")
            // Only the run of modifiers that OPENS the declaration counts, and the scan stops at
            // the first word that is not one. `public class Uuid private constructor(...)` is
            // public API whose constructor is not, and a rule that searched the whole string —
            // or allowed any word before the keyword — would delete the class. Same trap with
            // `var x: Int private set`.
            for (token in declaration.split(WHITESPACE)) {
                if (token in NON_PUBLIC) return true
                if (token !in MODIFIERS) return false
            }
            return false
        }

        private val LEADING_ANNOTATIONS = Regex("""^(?:@[\w.]+(?:\([^)]*\))?\s+)*""")
        private val WHITESPACE = Regex("""\s+""")

        /** `protected` is deliberately not here: a subclass outside the library can reach it. */
        private val NON_PUBLIC = setOf("private", "internal")

        /**
         * Words that may precede a visibility keyword in either language. Anything else ends the
         * modifier run — including `class`, `fun`, `val`, `var` and a Java return type.
         */
        private val MODIFIERS = setOf(
            "public", "protected", "abstract", "final", "open", "sealed", "data", "inline",
            "value", "expect", "actual", "override", "suspend", "external", "tailrec",
            "operator", "infix", "const", "lateinit", "inner", "companion", "annotation",
            "enum", "static", "synchronized", "native", "strictfp", "transient", "volatile",
            "default", "vararg", "crossinline", "noinline", "reified",
        )

        /**
         * A Kotlin Multiplatform sources jar is rooted on source sets — `commonMain`,
         * `jvmMain`, `appleMain` — where a plain JVM one is rooted on package directories.
         * That is the discriminator, and it costs nothing: the archive is already open.
         *
         * Considered and not used: Gradle Module Metadata, which is authoritative about what was
         * published but describes the publication rather than the archive actually read here,
         * and is only present in the cache when Gradle happened to fetch it.
         */
        internal fun sourceSetsOf(names: List<String>): Set<String> =
            names.mapNotNull { it.substringBefore('/', "").ifEmpty { null } }
                .filterNot { it == "META-INF" }
                .filter { SOURCE_SET.matches(it) }
                .toSortedSet()

        private val SOURCE_SET = Regex("^[a-z][A-Za-z0-9]*(?:Main|Test)$")
    }
}

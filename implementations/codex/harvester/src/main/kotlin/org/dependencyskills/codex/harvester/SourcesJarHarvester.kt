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
class SourcesJarHarvester(private val extractor: String = EXTRACTOR) {

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
                fileYield.extracted.mapTo(entries) {
                    NewEntry(
                        symbol = it.symbol,
                        signature = it.signature,
                        doc = it.doc,
                        lang = language.lang,
                        docFormat = language.docFormat,
                        provenance = Provenance(extractor = extractor),
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

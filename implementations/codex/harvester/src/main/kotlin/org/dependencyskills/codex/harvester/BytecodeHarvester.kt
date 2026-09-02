package org.dependencyskills.codex.harvester

import org.dependencyskills.codex.core.EntryState
import org.dependencyskills.codex.core.NewEntry
import org.dependencyskills.codex.core.Provenance
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Turns a library's compiled jar into entries, for the libraries that publish no sources.
 *
 * ADR-0009 makes the sources jar the transport, and most libraries publish one. Some do not, and
 * private repositories — which a build resolves perfectly well with its own credentials — very
 * often do not. Today those coordinates end at `NoSource` and the library is invisible to an agent
 * for the rest of its life, though the developer compiles against it daily.
 *
 * **A signature with no prose is worth much more than nothing.** It answers "does this library
 * already do the thing I am about to write", which is the question the codex exists for. It will
 * rank below a summarised entry for a plain-language need, and should.
 *
 * **This is a fallback, never a supplement.** A library with sources is harvested from them; this
 * runs only when there are none, so the two can never produce competing entries for one symbol.
 *
 * **Everything read here is third-party content.** There are no doc comments in a class file, so
 * the prose vector is gone — nothing to classify, nothing to summarise, no paragraph of an
 * author's text reaching a model. What remains is the identifier, and RAD-0027 measured that an
 * identifier is a working free-text channel: a camel-cased imperative is legal in every language
 * this project harvests, needs no escaping, and made agents act 8 of 12 times. So names are
 * screened here, on content this does not trust, rather than by hoping the publisher ran a linter.
 */
class BytecodeHarvester(
    private val extractor: String = EXTRACTOR,
    /** Refuses an identifier that reads as prose. Applied at extraction, never as a rewrite. */
    private val screen: NameScreen = NameScreen.Default,
) {

    fun harvest(jar: Path): HarvestResult {
        if (!Files.isRegularFile(jar)) {
            return HarvestResult.Failed("no readable file at ${jar.fileName}")
        }
        val zip = try {
            ZipFile(jar.toFile())
        } catch (e: ZipException) {
            // Failed rather than NoSource: the distinction is retryability, and a truncated
            // download is the ordinary cause of this.
            return HarvestResult.Failed("not a readable archive: ${e.message}", e)
        } catch (e: java.io.IOException) {
            return HarvestResult.Failed("could not open the archive: ${e.message}", e)
        }
        return zip.use { read(it, jar) }
    }

    private fun read(zip: ZipFile, jar: Path): HarvestResult {
        // One pass over the archive, shared with the visibility rule below rather than parsed
        // twice to ask two questions of the same bytes.
        val nodes = ClassFileVisibility.classesIn(zip)
        if (nodes.isEmpty()) {
            return HarvestResult.NoSource("${jar.fileName} holds no class files")
        }
        val visibility = ClassFileVisibility.from(nodes)
            ?: return HarvestResult.NoSource("${jar.fileName} holds no readable class files")
        val outerOf = ClassFileVisibility.outerNames(nodes)

        val entries = ArrayList<NewEntry>()
        var declarations = 0
        var notReachable = 0
        var refused = 0

        // Sorted by binary name so the entry order is a property of the archive's contents rather
        // than of the order its central directory happens to list them in — the same guarantee the
        // sources harvester gives.
        for (node in nodes.sortedBy { it.name }) {
            // Declared inside a method — an anonymous or local class, or a lambda the compiler
            // lifted out. `EnclosingMethod` is what says so; matching on `$$inlined$` in the name
            // would be the same string-guessing this codebase already refuses for nesting.
            if (node.outerMethod != null) continue
            if (node.access and Opcodes.ACC_SYNTHETIC != 0) continue

            val dotted = ClassFileVisibility.dottedName(node.name, outerOf) ?: continue
            val language = languageOf(node)
            declarations++
            if (visibility.reach(dotted) != Reach.Reachable) {
                notReachable++
                continue
            }
            val simple = dotted.substringAfterLast('.')
            val members = members(node, dotted, visibility)
            if (screen.verdict(simple, NameKind.Type, language) == NameVerdict.Refused) {
                // Refused, not rewritten. A paraphrased signature cannot be called, so there is no
                // sanitised middle: the entry is stored verbatim or it is not stored.
                //
                // Its members go with it, by the same rule that decides visibility — the enclosing
                // declaration governs. They are counted, because a refusal that silently takes
                // twenty entries with it reads as a library that simply had nothing in it.
                refused += 1 + members.size
                declarations += members.size
                continue
            }
            entries.add(entry(dotted, BytecodeSignatures.ofClass(node)))

            for ((name, signature, kind) in members) {
                declarations++
                when (screen.verdict(name.substringAfterLast('.'), kind, language)) {
                    NameVerdict.Refused -> refused++
                    NameVerdict.Accepted -> entries.add(entry(name, signature))
                }
            }
        }

        if (entries.isEmpty() && refused == 0) {
            return HarvestResult.NoSource("${jar.fileName} holds nothing a consumer can reach")
        }
        return HarvestResult.Harvested(
            entries = entries,
            report = HarvestReport(
                sourceFiles = nodes.size,
                declarations = declarations,
                documented = entries.size,
                unclaimedDocs = 0,
                tooShort = 0,
                withParseErrors = 0,
                unreadable = 0,
                notReachable = notReachable,
                visibilityUnknown = 0,
                refusedNames = refused,
                sourceSets = emptySet(),
            ),
        )
    }

    /** A member: its symbol, its rendered signature, and which population its name belongs to. */
    private data class Member(val symbol: String, val signature: String, val kind: NameKind)

    /**
     * Kotlin carries `@kotlin.Metadata`; a class without it was written in Java or in something
     * that compiles like it. Read per class because one jar routinely holds both.
     */
    private fun languageOf(node: ClassNode): SourceLang =
        if (node.visibleAnnotations.orEmpty().any { it.desc == "Lkotlin/Metadata;" }) {
            SourceLang.Kotlin
        } else {
            SourceLang.Java
        }

    /** The reachable members of one class, already named the way the store keys a symbol. */
    private fun members(
        node: ClassNode,
        dotted: String,
        visibility: ClassFileVisibility,
    ): List<Member> {
        val out = ArrayList<Member>()
        val simple = dotted.substringAfterLast('.')
        for (method in node.methods.orEmpty()) {
            if (method.name == "<clinit>") continue
            if (method.access and Opcodes.ACC_SYNTHETIC != 0) continue
            val name = if (method.name == "<init>") simple else method.name
            val symbol = "$dotted.$name"
            if (visibility.reach(symbol) != Reach.Reachable) continue
            out.add(Member(symbol, BytecodeSignatures.ofMethod(node, method), NameKind.Function))
        }
        for (field in node.fields.orEmpty()) {
            if (field.access and Opcodes.ACC_SYNTHETIC != 0) continue
            val symbol = "$dotted.${field.name}"
            if (visibility.reach(symbol) != Reach.Reachable) continue
            val kind = if (NameScreen.isConstantCase(field.name)) NameKind.Constant else NameKind.Field
            out.add(Member(symbol, BytecodeSignatures.ofField(field), kind))
        }
        return out
    }

    /**
     * A degraded entry: a symbol and a signature, and nothing else.
     *
     * `doc` is empty and `rewrite` is null because there is no prose and none is invented. The
     * summariser is never pointed at this — there would be nothing for it to rewrite, and doing so
     * would hand attacker-controlled text to a generative model.
     */
    private fun entry(symbol: String, signature: String) = NewEntry(
        symbol = symbol,
        signature = signature,
        doc = "",
        lang = "jvm-bytecode",
        docFormat = "none",
        provenance = Provenance(extractor = extractor),
        rewrite = null,
        state = EntryState.Degraded,
    )

    companion object {
        /**
         * Distinguishable from the sources extractor, so a bad version of either can be
         * invalidated selectively rather than by deleting the store.
         */
        const val EXTRACTOR = "asm-classes-jar/1"
    }
}

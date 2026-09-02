package org.dependencyskills.codex.harvester

import kotlin.metadata.Visibility
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.fieldSignature
import kotlin.metadata.jvm.getterSignature
import kotlin.metadata.jvm.signature
import kotlin.metadata.visibility
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * A [VisibilityOracle] backed by the library's compiled classes.
 *
 * Reads the artifact once and answers from a map, because the harvester asks about every
 * declaration in the archive and reopening a jar per question would dominate the harvest.
 *
 * **Three things here are less obvious than they look, and each is a measured trap.**
 *
 * **Nesting comes from the `InnerClasses` attribute, never from the name.** A `$` in a binary
 * name usually marks a nested class, and the tempting fix is to rewrite it to a dot. It is
 * wrong: `com.google.gson.internal.$Gson$Types` is a *top-level* class in its own file, named
 * that way deliberately since 2011, while `$Gson$Types$ParameterizedTypeImpl` inside it really
 * is nested. Both forms ship in the same jar, so no rule applied to the string can separate
 * them. The attribute can. Gson removed the name in 2.14 because, in their words, the old names
 * caused problems — but every jar already published keeps it forever.
 *
 * **Members are matched by descriptor, not by reconstructed name.** Overloads share a name, and
 * Kotlin mangles any name that touches an inline value class — `getDays-UwyO8pc` for a source
 * symbol of `days`. No string transformation recovers that.
 *
 * **Kotlin `internal` is invisible to access flags.** It compiles to `public` with the module
 * name appended, so it looks reachable in both source and bytecode. Only the Kotlin metadata
 * says otherwise, which is why that is read too.
 */
class ClassFileVisibility private constructor(
    private val verdicts: Map<String, Boolean>,
) : VisibilityOracle {

    override fun reach(symbol: String): Reach = when (verdicts[symbol]) {
        true -> Reach.Reachable
        false -> Reach.Unreachable
        null -> Reach.Unknown
    }

    /** How many declarations the artifact had an opinion about, for the harvest to report. */
    val size: Int get() = verdicts.size

    companion object {

        /**
         * Reads [jar], or returns null when it is absent or unreadable.
         *
         * Null rather than an exception: a coordinate whose classes jar is not on disk is an
         * ordinary case — the caller falls back to [VisibilityOracle.Blind] and says so.
         */
        fun of(jar: Path): ClassFileVisibility? {
            if (!Files.isRegularFile(jar)) return null
            val zip = try {
                ZipFile(jar.toFile())
            } catch (e: ZipException) {
                return null
            } catch (e: java.io.IOException) {
                return null
            }
            return zip.use { from(classesIn(it)) }
        }

        /**
         * Every readable class in the archive, parsed once.
         *
         * Exposed so a caller that also needs the classes — the bytecode harvest of #28 reads the
         * same jar for its content — can share this pass instead of parsing every class file a
         * second time to ask a different question of it.
         */
        fun classesIn(zip: ZipFile): List<ClassNode> = zip.entries().asSequence()
            .filter { !it.isDirectory && it.name.endsWith(".class") }
            .mapNotNull { entry ->
                try {
                    ClassNode().also {
                        zip.getInputStream(entry).use { input ->
                            ClassReader(input).accept(
                                it,
                                ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
                            )
                        }
                    }
                } catch (e: Exception) {                                   // noqa: BLE001
                    // A class file this ASM cannot read is one declaration unaccounted for, not
                    // a reason to abandon the artifact and lose every other answer in it.
                    null
                }
            }
            .toList()

        /**
         * Which class encloses which, from the `InnerClasses` attributes across the whole archive.
         *
         * Collected from every class rather than from each class about itself: a nested class does
         * not reliably carry its own outer name, but the outer one names it.
         */
        internal fun outerNames(nodes: List<ClassNode>): Map<String, String> {
            val outerOf = HashMap<String, String>()
            for (node in nodes) {
                for (inner in node.innerClasses.orEmpty()) {
                    // outerName is null for local and anonymous classes, which no source symbol
                    // names and which are therefore left out entirely.
                    if (inner.outerName != null && inner.innerName != null) {
                        outerOf[inner.name] = inner.outerName
                    }
                }
            }
            return outerOf
        }

        /** Builds the oracle from an already-parsed archive. */
        fun from(nodes: List<ClassNode>): ClassFileVisibility? {
            val classes = LinkedHashMap<String, Compiled>()
            // The InnerClasses attribute of ANY class may describe a nesting relation, so these
            // are collected across the whole archive before anything is resolved. A nested class
            // does not reliably carry its own outer name; the outer one names it.
            val outerOf = HashMap<String, String>()
            val nestedAccess = HashMap<String, Int>()

            for (node in nodes) {
                classes[node.name] = Compiled(node)
                for (inner in node.innerClasses.orEmpty()) {
                    // outerName is null for local and anonymous classes: they are nested, and
                    // unreachable by name whatever their flags say, so they are recorded as
                    // nested under a name nothing resolves to.
                    if (inner.outerName != null && inner.innerName != null) {
                        outerOf[inner.name] = inner.outerName
                    }
                    nestedAccess[inner.name] = inner.access
                }
            }
            if (classes.isEmpty()) return null

            val verdicts = HashMap<String, Boolean>()
            for ((binary, compiled) in classes) {
                val dotted = dottedName(binary, outerOf) ?: continue
                // The InnerClasses entry is authoritative for a nested class's own modifiers;
                // the class file's top-level access flags lose `private` and `protected` there.
                val access = nestedAccess[binary] ?: compiled.node.access
                val reachableType = visible(access) &&
                    !compiled.isInternal &&
                    enclosingReachable(binary, outerOf, classes, nestedAccess)
                verdicts[dotted] = reachableType

                // Kotlin declares in source names the JVM does not use: a property is a pair of
                // accessors, a top-level function lives in a synthesized file class, and a
                // function touching an inline value class is renamed outright. The metadata
                // carries the source name, so these are registered from it rather than guessed
                // at from the compiled member. Done before the JVM pass so a real compiled
                // member can still widen the verdict.
                compiled.sourceNamed().forEach { (name, visible) ->
                    // A file facade's declarations belong to the package, not to the class the
                    // compiler invented to hold them: `encodeToString`, not `SerializationKt`.
                    val owner = if (compiled.isFileFacade) dotted.substringBeforeLast('.', "") else dotted
                    val key = if (owner.isEmpty()) name else "$owner.$name"
                    // A top-level declaration has no enclosing declaration in the source, so the
                    // class the compiler invented to hold it must not gate it. That is not a
                    // technicality: a `@JvmMultifileClass` PART is emitted package-private —
                    // `SerializersKt__SerializersKt` — while the facade beside it is public and
                    // is what a caller actually links against. Gating on the holder marks every
                    // public top-level function in such a file unreachable.
                    val ok = visible && (compiled.isFileFacade || reachableType)
                    verdicts[key] = (verdicts[key] ?: false) || ok
                }

                val simple = dotted.substringAfterLast('.')
                for (member in compiled.members()) {
                    // A constructor is `<init>` compiled and the class's simple name in source.
                    // Left untranslated, every constructor in the archive reads as absent, which
                    // was 350 of 2,185 entries in the measurement that produced this class.
                    val name = if (member.name == "<init>") simple else member.name
                    if (name == "<clinit>") continue
                    val key = "$dotted.$name"
                    // A member of a type nobody can reach is itself unreachable, whatever its
                    // own flags say. Recorded as false rather than left absent: absent reads as
                    // Unknown, and Unknown means keep, which would let every member of every
                    // package-private class straight back in — the defect this closes.
                    val ok = reachableType &&
                        visible(member.access) &&
                        member.access and Opcodes.ACC_SYNTHETIC == 0 &&
                        !compiled.isInternalMember(member)
                    // Overloads share a key. The widest wins: if any overload is callable, a
                    // consumer can call something by that name, and the entry is about the name.
                    verdicts[key] = (verdicts[key] ?: false) || ok
                }
            }
            return ClassFileVisibility(verdicts)
        }

        /** Public or protected. Protected is reachable — a consumer subclasses to get at it. */
        private fun visible(access: Int): Boolean =
            access and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED) != 0

        /**
         * The source-style name: package dots, and a dot for each real nesting step.
         *
         * Returns null for a local or anonymous class, which no source symbol names.
         */
        internal fun dottedName(binary: String, outerOf: Map<String, String>): String? {
            var current = binary
            val parts = ArrayDeque<String>()
            val seen = HashSet<String>()
            while (true) {
                if (!seen.add(current)) return null              // a cycle; refuse rather than spin
                val outer = outerOf[current]
                if (outer == null) return (listOf(current.replace('/', '.')) + parts).joinToString(".")
                // Only the part after the outer name's prefix is this step's simple name, and it
                // is taken by length rather than by splitting on `$` — the outer name may itself
                // contain a literal one.
                val simple = current.removePrefix("$outer$")
                if (simple == current || simple.isEmpty()) return null
                parts.addFirst(simple)
                current = outer
            }
        }

        private fun enclosingReachable(
            binary: String,
            outerOf: Map<String, String>,
            classes: Map<String, Compiled>,
            nestedAccess: Map<String, Int>,
        ): Boolean {
            var outer = outerOf[binary]
            val seen = HashSet<String>()
            while (outer != null) {
                if (!seen.add(outer)) return false
                val compiled = classes[outer] ?: return false
                val access = nestedAccess[outer] ?: compiled.node.access
                if (!visible(access) || compiled.isInternal) return false
                outer = outerOf[outer]
            }
            return true
        }
    }

    /** One compiled class, with its Kotlin metadata read once. */
    private class Compiled(val node: ClassNode) {

        private val kotlin: KotlinClassMetadata? = runCatching { readKotlinMetadata(node) }.getOrNull()

        /** Kotlin `internal` on the type itself, which no access flag records. */
        val isInternal: Boolean =
            (kotlin as? KotlinClassMetadata.Class)?.kmClass?.visibility == Visibility.INTERNAL

        /**
         * Whether this class holds a package's top-level declarations rather than a real class's
         * members.
         *
         * Both kinds count. A file of top-level declarations compiles to a facade named after the
         * file, and `@JvmMultifileClass` splits one facade across several *parts* — so
         * `kotlinx.serialization.serializer` lives in `SerializersKt__SerializersKt`. Treating
         * only the single-file kind as a facade leaves every multi-file declaration keyed under
         * the invented class name, where no source symbol will ever find it.
         */
        val isFileFacade: Boolean =
            kotlin is KotlinClassMetadata.FileFacade || kotlin is KotlinClassMetadata.MultiFileClassPart

        /**
         * Declarations under the names Kotlin source gives them, with whether they are visible.
         *
         * This is what closes the gap between a source symbol and a compiled member: `descriptor`
         * rather than `getDescriptor`, `encodeToString` rather than a member of `SerializationKt`,
         * and `days` rather than the value-class-mangled `getDays-UwyO8pc`.
         */
        fun sourceNamed(): List<Pair<String, Boolean>> {
            val functions: List<Pair<String, Visibility>>
            val properties: List<Pair<String, Visibility>>
            when (val km = kotlin) {
                is KotlinClassMetadata.Class -> {
                    functions = km.kmClass.functions.map { it.name to it.visibility }
                    properties = km.kmClass.properties.map { it.name to it.visibility }
                }
                is KotlinClassMetadata.FileFacade -> {
                    functions = km.kmPackage.functions.map { it.name to it.visibility }
                    properties = km.kmPackage.properties.map { it.name to it.visibility }
                }
                is KotlinClassMetadata.MultiFileClassPart -> {
                    functions = km.kmPackage.functions.map { it.name to it.visibility }
                    properties = km.kmPackage.properties.map { it.name to it.visibility }
                }
                else -> return emptyList()
            }
            return (functions + properties).map { (name, visibility) ->
                name to (visibility == Visibility.PUBLIC || visibility == Visibility.PROTECTED)
            }
        }

        fun members(): List<Member> =
            node.methods.orEmpty().map { Member(it.name, it.desc, it.access) } +
                node.fields.orEmpty().map { Member(it.name, it.desc, it.access) }

        /**
         * Kotlin `internal` on a member.
         *
         * Matched on the JVM signature rather than the name: overloads share a name, and a
         * value-class parameter mangles the compiled name away from the source one entirely.
         */
        fun isInternalMember(member: Member): Boolean {
            val km = (kotlin as? KotlinClassMetadata.Class)?.kmClass ?: return false
            val fn = km.functions.firstOrNull {
                it.signature?.name == member.name && it.signature?.descriptor == member.descriptor
            }
            if (fn != null) return fn.visibility == Visibility.INTERNAL
            val prop = km.properties.firstOrNull {
                it.getterSignature?.name == member.name || it.fieldSignature?.name == member.name
            }
            return prop?.visibility == Visibility.INTERNAL
        }

        private companion object {
            fun readKotlinMetadata(node: ClassNode): KotlinClassMetadata? {
                val annotation = node.visibleAnnotations.orEmpty()
                    .firstOrNull { it.desc == "Lkotlin/Metadata;" } ?: return null
                var kind = 1
                var version: IntArray? = null
                var data1: Array<String>? = null
                var data2: Array<String>? = null
                var extraInt = 0
                var packageName: String? = null
                var extraString: String? = null
                val values = annotation.values.orEmpty()
                var i = 0
                while (i + 1 < values.size) {
                    val name = values[i] as? String
                    when (val value = values[i + 1]) {
                        is Int -> if (name == "k") kind = value else if (name == "xi") extraInt = value
                        is String -> if (name == "pn") packageName = value else if (name == "xs") extraString = value
                        is List<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            when (name) {
                                "mv" -> version = (value as List<Int>).toIntArray()
                                "d1" -> data1 = (value as List<String>).toTypedArray()
                                "d2" -> data2 = (value as List<String>).toTypedArray()
                            }
                        }
                    }
                    i += 2
                }
                val metadata = Metadata(
                    kind = kind,
                    metadataVersion = version ?: intArrayOf(2, 0, 0),
                    data1 = data1 ?: emptyArray(),
                    data2 = data2 ?: emptyArray(),
                    extraString = extraString ?: "",
                    packageName = packageName ?: "",
                    extraInt = extraInt,
                )
                // Lenient: a class compiled by a newer Kotlin than this metadata library knows
                // is one class without a Kotlin opinion, not a failed harvest.
                return KotlinClassMetadata.readLenient(metadata)
            }
        }
    }

    private class Member(val name: String, val descriptor: String, val access: Int)
}

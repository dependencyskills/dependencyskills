package org.dependencyskills.test0

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.nio.file.Path
import kotlin.io.path.name

/** Shared PSI plumbing for the parser arms (kotlin-compiler-embeddable, no native deps). */
internal object Psi {

    /** Run [block] with a KtPsiFactory in a short-lived compiler environment. */
    fun <T> withFactory(block: (KtPsiFactory) -> T): T {
        val disposable = Disposer.newDisposable()
        try {
            val config = CompilerConfiguration().apply {
                put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
                put(CommonConfigurationKeys.MODULE_NAME, "test0")
            }
            val env = KotlinCoreEnvironment.createForProduction(
                disposable, config, EnvironmentConfigFiles.JVM_CONFIG_FILES,
            )
            return block(KtPsiFactory(env.project))
        } finally {
            Disposer.dispose(disposable)
        }
    }

    /** Top-level declarations plus class/object members, recursively. */
    fun declarations(decls: List<KtDeclaration>): List<KtNamedDeclaration> =
        decls.flatMap { d ->
            val self = if (d is KtNamedDeclaration && d.name != null) listOf(d) else emptyList()
            val nested = if (d is KtClassOrObject) declarations(d.declarations) else emptyList()
            self + nested
        }

    /** The declaration's own leading `/** … */`, as raw text, or null. */
    fun rawKDoc(d: KtNamedDeclaration): String? =
        d.children.filterIsInstance<KDoc>().firstOrNull()?.text

    fun signatureOf(d: KtNamedDeclaration): String = when (d) {
        is KtNamedFunction -> buildString {
            append("fun ")
            d.typeParameterList?.let { append(it.text).append(" ") }
            d.receiverTypeReference?.let { append(it.text).append(".") }
            append(d.name)
            append("(").append(d.valueParameters.joinToString(", ") { it.text }).append(")")
            d.typeReference?.let { append(": ").append(it.text) }
        }
        is KtClassOrObject -> {
            val kw = when {
                d is KtObjectDeclaration -> "object"
                d is KtClass && d.isInterface() -> "interface"
                else -> "class"
            }
            "$kw ${d.name}" + (d.primaryConstructor?.valueParameterList?.text ?: "")
        }
        is KtProperty -> "val ${d.name}"
        else -> d.name ?: "?"
    }

    data class ParsedDoc(val prose: String?, val tags: Map<String, String>)

    /** Split a raw `/** … */` block into leading prose and a tag map. Raw text only. */
    fun parseKDoc(raw: String?): ParsedDoc {
        if (raw == null) return ParsedDoc(null, emptyMap())
        val body = raw.removePrefix("/**").removeSuffix("*/")
        val prose = StringBuilder()
        val tags = LinkedHashMap<String, String>()
        var current: String? = null
        for (rawLine in body.lines()) {
            val line = rawLine.trim().removePrefix("*").trim()
            when {
                line.startsWith("@") -> {
                    val sp = line.indexOf(' ')
                    current = if (sp < 0) line else line.substring(0, sp)
                    tags[current!!] = if (sp < 0) "" else line.substring(sp + 1).trim()
                }
                current != null && line.isNotEmpty() ->
                    tags[current!!] = (tags[current!!] + " " + line).trim()
                current == null && line.isNotEmpty() ->
                    prose.append(line).append(" ")
            }
        }
        return ParsedDoc(prose.toString().trim().ifEmpty { null }, tags)
    }

    private val DESIGNED_TAGS = setOf("@capability", "@triggers", "@category", "@notFor", "@similar")

    /** Assemble an [Entry] from a declaration and its parsed doc. */
    fun buildEntry(
        d: KtNamedDeclaration,
        path: Path,
        doc: ParsedDoc,
        source: String,
        sample: String?,
    ): Entry = Entry(
        coordinate = "test0:${path.parent.name}",
        symbol = d.fqName?.asString() ?: (d.name ?: "?"),
        signature = signatureOf(d),
        sample = sample,
        capability = doc.tags["@capability"] ?: doc.prose,
        triggers = doc.tags["@triggers"]
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
        notFor = doc.tags["@notFor"],
        category = doc.tags["@category"],
        since = doc.tags["@since"],
        tier = if (doc.tags.keys.any { it in DESIGNED_TAGS }) Tier.Designed else Tier.Discovered,
        source = source,
    )
}

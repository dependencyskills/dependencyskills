package org.dependencyskills.test0

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * The **enriched** arm. Stands in for Dokka: it parses the whole source set together
 * and resolves *within* it —
 *  - **inherited docs**: an undocumented `override` inherits the KDoc of the same
 *    member on a supertype that is in the parsed set;
 *  - **`@sample` expansion**: an `@sample` reference is replaced by the referenced
 *    function's body.
 *
 * Real Dokka resolves against the full type graph (binary dependencies included);
 * here resolution is limited to the parsed set, which is enough to measure the
 * enrichment *delta* on the fixture. Real-Dokka fidelity is a later step (RAD-0009).
 */
class EnrichedPsiParser : Parser {

    override val name = "enriched-psi"

    override fun parse(sources: List<Path>): List<Entry> = Psi.withFactory { factory ->
        val files = sources.map { path -> path to factory.createFile(path.name, path.readText()) }
        val all = files.flatMap { (path, kt) -> Psi.declarations(kt.declarations).map { path to it } }

        // @sample target fqName -> function body text
        val funcBodies: Map<String, String> = all.mapNotNull { (_, d) ->
            val fn = d as? KtNamedFunction ?: return@mapNotNull null
            val fq = fn.fqName?.asString() ?: return@mapNotNull null
            val body = fn.bodyBlockExpression?.text ?: fn.bodyExpression?.text ?: return@mapNotNull null
            fq to body
        }.toMap()

        // simple class name -> class, for supertype lookup
        val classesByName: Map<String, KtClassOrObject> = files
            .flatMap { (_, kt) -> Psi.declarations(kt.declarations).filterIsInstance<KtClassOrObject>() }
            .mapNotNull { c -> c.name?.let { it to c } }
            .toMap()

        all.map { (path, d) ->
            var raw = Psi.rawKDoc(d)
            var inherited = false
            if (raw == null && d is KtNamedFunction && d.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
                val inh = inheritedKDoc(d, classesByName)
                if (inh != null) {
                    raw = inh
                    inherited = true
                }
            }
            val doc = Psi.parseKDoc(raw)
            val sample = doc.tags["@sample"]?.let { funcBodies[it] ?: it }
            val source = when {
                inherited -> "kdoc(inherited)@${path.name}"
                raw != null -> "kdoc@${path.name}"
                else -> "none"
            }
            Psi.buildEntry(d, path, doc, source, sample)
        }
    }

    /** The same-named member's KDoc on a supertype present in the parsed set. */
    private fun inheritedKDoc(fn: KtNamedFunction, classesByName: Map<String, KtClassOrObject>): String? {
        val container = fn.containingClassOrObject ?: return null
        for (st in container.superTypeListEntries) {
            val name = st.typeReference?.text?.substringBefore("<")?.trim() ?: continue
            val superClass = classesByName[name] ?: continue
            val superFn = Psi.declarations(superClass.declarations)
                .firstOrNull { it is KtNamedFunction && it.name == fn.name } ?: continue
            Psi.rawKDoc(superFn)?.let { return it }
        }
        return null
    }
}

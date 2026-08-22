package org.dependencyskills.test0

import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * The **raw** arm of the bake-off: `(declaration, as-authored KDoc)` per file, via
 * PSI. No inheritance resolution, no `@sample` expansion — the honest as-shipped
 * signal the enriched arm ([EnrichedPsiParser]) is measured against.
 */
class KotlinPsiParser : Parser {

    override val name = "kotlin-psi"

    override fun parse(sources: List<Path>): List<Entry> = Psi.withFactory { factory ->
        sources.flatMap { path ->
            val ktFile = factory.createFile(path.name, path.readText())
            Psi.declarations(ktFile.declarations).map { d ->
                val raw = Psi.rawKDoc(d)
                val doc = Psi.parseKDoc(raw)
                Psi.buildEntry(
                    d = d,
                    path = path,
                    doc = doc,
                    source = if (raw != null) "kdoc@${path.name}" else "none",
                    sample = doc.tags["@sample"], // bare reference, unexpanded
                )
            }
        }
    }
}

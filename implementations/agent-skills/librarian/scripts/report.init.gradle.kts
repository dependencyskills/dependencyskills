// The dependency report the lookup reads, written by an init script instead of a plugin: nothing is added
// to the build, and nothing has to be published for a build to find it. From the project's root:
//
//   ./gradlew --init-script <this file> dependencySkillsReport --no-configuration-cache
//
// Each project resolves its own main compile classpaths, fetches their sources jars through Gradle's own
// repositories and cache — a library's skill travels in its sources jar, and a build does not download
// those by default — and writes a fragment; the root task merges them into
// build/dependencyskills/bom.cdx.json, the same CycloneDX file the build plugins write. The `librarian`
// skill carries this file and says when to run it (RAD-0081).

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType

/** The configurations a module's own code compiles against: main ones only, never tests, release over debug. */
fun watched(name: String): Boolean {
    val n = name.lowercase()
    return (n.endsWith("compileclasspath") || n.endsWith("compileklibraries")) && "test" !in n && "debug" !in n
}

fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

allprojects {
    val p = this
    val included = gradle.includedBuilds.associate { ":" + it.name to it.projectDir }
    tasks.register("dependencySkillsFragment") {
        group = "dependency skills"
        description = "Resolves this project's compile classpaths, fetches their sources jars, and records what it found."
        val out = layout.buildDirectory.file("dependencyskills/fragment.tsv")
        outputs.upToDateWhen { false }
        doLast {
            val lines = sortedSetOf<String>()
            var sources = 0
            p.configurations.filter { it.isCanBeResolved && watched(it.name) }.forEach { c ->
                val components = runCatching { c.incoming.resolutionResult.allComponents }
                    .onFailure { logger.info("dependencyskills: ${p.path}:${c.name} did not resolve: ${it.message}") }
                    .getOrNull() ?: return@forEach
                components.forEach { component ->
                    when (val id = component.id) {
                        is ModuleComponentIdentifier -> lines += "resolved\t${id.group}:${id.module}:${id.version}"
                        // A library an included build supplies resolves as that build's project, not a module:
                        // reported under its own coordinate, with the directory its skill is read from.
                        is ProjectComponentIdentifier -> {
                            val build = included[id.build.buildPath] ?: return@forEach
                            val mv = component.moduleVersion ?: return@forEach
                            val coordinate = "${mv.group}:${mv.name}:${mv.version}"
                            lines += "resolved\t$coordinate"
                            lines += "source\t$coordinate\t" + build.resolve(id.projectPath.trim(':').replace(':', '/')).path
                        }
                    }
                }
                // The build plugin's own way to the sources: reselect the documentation variant from the graph
                // just resolved. Lenient, so a library without sources, or an offline build, is skipped.
                sources += runCatching {
                    c.incoming.artifactView {
                        withVariantReselection()
                        lenient(true)
                        componentFilter { it is ModuleComponentIdentifier }
                        attributes {
                            attribute(Category.CATEGORY_ATTRIBUTE, p.objects.named(Category::class.java, Category.DOCUMENTATION))
                            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, p.objects.named(DocsType::class.java, DocsType.SOURCES))
                            attribute(Bundling.BUNDLING_ATTRIBUTE, p.objects.named(Bundling::class.java, Bundling.EXTERNAL))
                        }
                    }.files.files.size
                }.getOrDefault(0)
            }
            val file = out.get().asFile
            file.parentFile.mkdirs()
            file.writeText(lines.joinToString("\n", postfix = "\n"))
            logger.lifecycle("dependencyskills: ${p.path}: ${lines.count { it.startsWith("resolved") }} resolved, $sources sources jars")
        }
    }
}

gradle.rootProject {
    val root = this
    tasks.register("dependencySkillsReport") {
        group = "dependency skills"
        description = "Merges every project's fragment into build/dependencyskills/bom.cdx.json, the report the lookup reads."
        dependsOn(root.allprojects.map { "${it.path.removeSuffix(":")}:dependencySkillsFragment" })
        outputs.upToDateWhen { false }
        doLast {
            val platform = Regex("-(jvm|android|androidnative\\w*|ios\\w*|js|wasm-?js|wasm-?wasi|linux\\w*|macos\\w*|mingw\\w*|tvos\\w*|watchos\\w*|metadata)$")
            val projects = sortedMapOf<String, MutableSet<String>>()
            val sources = sortedMapOf<String, String>()
            root.allprojects.forEach { p ->
                val f = p.layout.buildDirectory.file("dependencyskills/fragment.tsv").get().asFile
                if (!f.isFile) return@forEach
                f.readLines().filter { it.isNotBlank() }.forEach { line ->
                    val parts = line.split("\t")
                    when (parts[0]) {
                        "resolved" -> projects.getOrPut(parts[1]) { sortedSetOf() } += p.path
                        "source" -> sources[parts[1]] = parts[2]
                    }
                }
            }
            // A multiplatform library resolves as its root and one module per platform; the root names it.
            val roots = projects.keys.map { it.substringBeforeLast(":") }.toSet()
            val kept = projects.filterKeys { c ->
                val (g, a, _) = c.split(":")
                val base = a.replace(platform, "")
                base == a || "$g:$base" !in roots
            }
            val components = kept.map { (c, ps) ->
                val (g, a, v) = c.split(":")
                val purl = "pkg:maven/$g/$a@$v"
                val props = ps.joinToString(",") { """{"name":"dependencyskills:project","value":${quote(it)}}""" } +
                    (sources[c]?.let { """,{"name":"dependencyskills:source","value":${quote(it)}}""" } ?: "")
                """    {"type":"library","bom-ref":${quote(purl)},"group":${quote(g)},"name":${quote(a)},"version":${quote(v)},"purl":${quote(purl)},"properties":[$props]}"""
            }
            val report = root.layout.buildDirectory.file("dependencyskills/bom.cdx.json").get().asFile
            report.parentFile.mkdirs()
            report.writeText(buildString {
                append("{\n  \"bomFormat\": \"CycloneDX\",\n  \"specVersion\": \"1.6\",\n  \"version\": 1,\n")
                append("  \"metadata\": {\"component\": {\"type\": \"application\", \"bom-ref\": \"root\", \"name\": ${quote(root.name)}},\n")
                append("    \"properties\": [{\"name\": \"dependencyskills:classpath\", \"value\": \"compile\"}]},\n")
                append("  \"components\": [\n")
                append(components.joinToString(",\n"))
                append("\n  ]\n}\n")
            })
            logger.lifecycle("dependencyskills: wrote ${components.size} components to $report")
        }
    }
}

package org.dependencyskills.codex.classifier

internal data class GoldenCase(
    val text: String,
    val kind: String,
    val score: Double,
    val convention: String? = null,
    val register: String? = null,
)

internal data class GoldenFixture(
    val terms: Int,
    val thresholds: Map<String, Double>,
    val cases: List<GoldenCase>,
)

/**
 * Reads the golden fixture.
 *
 * Hand-rolled rather than pulling in a JSON library, because this module ships a classifier and
 * a dependency added for a test is a dependency in the artefact's dependency graph.
 */
internal object Golden {

    fun load(): GoldenFixture {
        val text = Golden::class.java.getResourceAsStream("/golden-scores.json")
            ?.bufferedReader()?.readText()
            ?: error("golden-scores.json is missing; run tools/golden.py")
        val terms = Regex("\"terms\":\\s*(\\d+)").find(text)!!.groupValues[1].toInt()
        val thresholds = Regex("\"thresholds\":\\s*\\{([^}]*)}").find(text)!!.groupValues[1]
            .split(',').filter { it.isNotBlank() }.associate {
                val (k, v) = it.split(':', limit = 2)
                k.trim().trim('"') to v.trim().toDouble()
            }
        val cases = Regex("\\{[^{}]*\"score\"[^{}]*}").findAll(text).map { block ->
            fun field(name: String) = Regex("\"$name\":\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .find(block.value)?.groupValues?.get(1)?.unescape()
            GoldenCase(
                text = field("text") ?: "",
                kind = field("kind")!!,
                score = Regex("\"score\":\\s*(-?[\\d.eE+]+)").find(block.value)!!.groupValues[1].toDouble(),
                convention = field("convention"),
                register = field("register"),
            )
        }.toList()
        return GoldenFixture(terms, thresholds, cases)
    }

    private fun String.unescape() = replace("\\\"", "\"").replace("\\\\", "\\")
        .replace("\\n", "\n").replace("\\t", "\t").replace("\\/", "/")
        .replace(Regex("\\\\u([0-9a-fA-F]{4})")) { String(Character.toChars(it.groupValues[1].toInt(16))) }
}

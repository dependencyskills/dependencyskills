package org.dependencyskills.codex.harvester

/**
 * Whether a consumer of the library could reach a declaration.
 *
 * **This exists because source cannot answer it.** #30 found a `private` field outranking the
 * class that holds it, and the obvious fix — read the visibility keyword — does not work. Of
 * 1,447 entries harvested from one library, 165 carried no visibility keyword at all, and they
 * are not one thing: `T build()` inside an interface is implicitly public, while
 * `final class StrBuilderReader extends Reader` is package-private. In source they are
 * indistinguishable. Compiled, they differ in one bit.
 *
 * So the oracle reads the compiled artifact, and the harvester asks it rather than guessing.
 *
 * **The unit of decision is the enclosing declaration, not the member.** A public method of a
 * package-private class is not reachable; a private field of a public class is not reachable;
 * and a lambda inside a public function is reachable exactly when that function is. Any rule
 * phrased as "skip private members" or "skip lambdas" gets one of those wrong — RAD-0063
 * measured that 23% of `kotlin-stdlib`'s entries are public functions taking lambdas, which a
 * lambda-skipping rule would have thrown away.
 */
fun interface VisibilityOracle {

    /** What the compiled artifact says about [symbol], a dot-qualified name from the source. */
    fun reach(symbol: String): Reach

    companion object {

        /**
         * The answer when no compiled artifact was available.
         *
         * Deliberately not "reachable". [Reach.Unknown] is a third answer and the caller has to
         * decide what to do with it, which is the point: silently treating an unreadable jar as
         * "index everything" is how the original defect would come back without anybody
         * noticing. The harvester keeps the entry and *reports* the count.
         */
        val Blind: VisibilityOracle = VisibilityOracle { Reach.Unknown }
    }
}

/** The three answers. Unknown is not a failure — it is the honest answer with no artifact. */
enum class Reach {
    /** A consumer can name and call this. */
    Reachable,

    /** Present in the library and not callable from outside it. */
    Unreachable,

    /** No compiled artifact was read, so nothing is claimed either way. */
    Unknown,
}

package test0.inherit

// Inheritance case — a discriminator between the parsers. The interface and its
// method are documented; the override is not. Dokka can resolve the inherited
// doc onto the override; a raw tree-sitter parse sees only a bare override.

/**
 * Runs an operation, retrying it on failure.
 *
 * Implementations choose the attempt count and the delay between tries.
 */
interface Retrier {
    /** Runs [block], retrying on failure, and returns its result. */
    fun <T> run(block: () -> T): T
}

class DefaultRetrier(private val times: Int = 3) : Retrier {
    override fun <T> run(block: () -> T): T {
        repeat(times - 1) {
            try {
                return block()
            } catch (e: Exception) {
                // swallow and retry
            }
        }
        return block()
    }
}

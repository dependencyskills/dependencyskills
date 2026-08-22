package test0.l0

// L0 — no documentation. Bare signatures only.
// Transparent name (retryWithBackoff) vs opaque name (Policy.apply): at L0 the
// only signal is the symbol name itself.

fun <T> retryWithBackoff(
    times: Int = 3,
    initialDelayMs: Long = 100,
    factor: Double = 2.0,
    block: () -> T,
): T {
    var delay = initialDelayMs
    repeat(times - 1) {
        try {
            return block()
        } catch (e: Exception) {
            Thread.sleep(delay)
            delay = (delay * factor).toLong()
        }
    }
    return block()
}

class Policy(
    private val times: Int = 3,
    private val initialDelayMs: Long = 100,
    private val factor: Double = 2.0,
) {
    fun <T> apply(block: () -> T): T {
        var delay = initialDelayMs
        repeat(times - 1) {
            try {
                return block()
            } catch (e: Exception) {
                Thread.sleep(delay)
                delay = (delay * factor).toLong()
            }
        }
        return block()
    }
}

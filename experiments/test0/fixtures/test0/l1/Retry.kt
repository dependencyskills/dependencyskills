package test0.l1

// L1 — one-line KDoc. The realistic ~33%-median case.

/** Retries [block] with exponential backoff. */
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

/** Applies the configured policy around [block]. */
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

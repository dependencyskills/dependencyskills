package test0.l2

// L2 — rich KDoc: purpose, when-to-use, a trap, @sample, @see. The ceiling of
// what standard KDoc tags carry. No custom tags yet (that is L3).

/**
 * Runs [block], retrying on failure with exponential backoff.
 *
 * Use when a call can fail transiently — a flaky network request, a briefly
 * locked resource — and a short wait is likely to clear it. The delay starts at
 * [initialDelayMs] and grows by [factor] on each attempt, up to [times] tries.
 *
 * Not a substitute for rate limiting: it reacts to failures, it does not pace
 * successful calls.
 *
 * @param times total number of attempts, including the first.
 * @param block the operation to run; its result is returned on the first success.
 * @sample test0.samples.retryFlaky
 * @see Policy for a reusable, preconfigured form.
 * @since 1.2.0
 */
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

/**
 * A reusable retry policy: [apply] runs a block, retrying on failure with
 * exponential backoff.
 *
 * Construct once with the attempt count and timing, then reuse it across many
 * calls. Prefer this over [retryWithBackoff] when the same policy is applied in
 * several places.
 *
 * @sample test0.samples.retryFlaky
 * @since 1.2.0
 */
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

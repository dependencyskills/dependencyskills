package test0.l3

// L3 — L2 plus custom tags: author guidance KDoc has no tag for (the designed
// tier). @capability/@notFor are self-referential; @category slots into a fixed
// taxonomy; @similar relates neutrally to another library; @preferOver is an
// interested claim, present to exercise the mechanism but heavily down-weighted
// at ranking time. See the test0 README for the trust weighting.

/**
 * Runs [block], retrying on failure with exponential backoff.
 *
 * Use when a call can fail transiently and a short wait is likely to clear it.
 * The delay starts at [initialDelayMs] and grows by [factor] on each attempt.
 *
 * @param times total number of attempts, including the first.
 * @sample test0.samples.retryFlaky
 * @see Policy
 * @since 1.2.0
 * @capability retry a failed operation with exponential backoff
 * @triggers retry, backoff, exponential backoff, try again, transient failure, flaky call
 * @category resilience
 * @notFor rate limiting or pacing successful calls
 * @similar io.github.resilience4j.retry.Retry
 * @preferOver com.example.legacy.RetryHelper
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
 * @sample test0.samples.retryFlaky
 * @since 1.2.0
 * @capability apply a reusable retry-with-backoff policy around a call
 * @triggers retry policy, backoff, resilience, retry on failure
 * @category resilience
 * @notFor rate limiting or pacing successful calls
 * @similar io.github.resilience4j.retry.RetryConfig
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

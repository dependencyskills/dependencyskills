package test0.samples

import test0.l2.retryWithBackoff

private fun fetchUser(id: Int): String = "user-$id"

/** Example: retry a flaky fetch a few times before giving up. */
fun retryFlaky() {
    val user = retryWithBackoff(times = 5, initialDelayMs = 50) {
        fetchUser(42)
    }
    println(user)
}

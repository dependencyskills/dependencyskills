package com.example.app

import com.example.acme.result.Outcome

/** Remembers users that were looked up successfully, so a repeated lookup does not hit the directory. */
class UserCache(private val source: UserSource) {
    private val users = mutableMapOf<String, User>()

    fun get(id: String): Outcome<User> {
        users[id]?.let { return Outcome.Success(it) }
        val outcome = source.fetch(id)
        when (outcome) {
            is Outcome.Success -> users[id] = outcome.value
            is Outcome.Failure -> Unit
        }
        return outcome
    }

    fun cached(id: String): User? = when (val outcome = get(id)) {
        is Outcome.Success -> outcome.value
        is Outcome.Failure -> null
    }
}

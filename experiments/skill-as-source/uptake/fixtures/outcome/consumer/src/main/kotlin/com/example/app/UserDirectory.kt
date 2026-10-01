package com.example.app

class UserDirectory(private val source: UserSource) {

    /** The user's name, or "unknown" when the lookup fails. */
    fun displayName(id: String): String = TODO()

    /** The user's email, or null when the lookup fails or the user has none. */
    fun emailOrNull(id: String): String? = TODO()

    /** Looks up every id and returns the message of each failure, in order. */
    fun failureMessages(ids: List<String>): List<String> = TODO()

    /** The names of every user that could be looked up, skipping failures. */
    fun names(ids: List<String>): List<String> = TODO()
}

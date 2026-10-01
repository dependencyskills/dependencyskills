package com.example.app

import com.example.acme.lookup.Lookup

class UserDirectory(private val users: Lookup<User>) {

    /** The user's name, or "unknown" when no such user exists. Any other lookup failure is thrown. */
    fun displayName(id: String): String = TODO()

    /** The user's email, or null when no such user exists or the user has none. Any other lookup failure is thrown. */
    fun emailOrNull(id: String): String? = TODO()

    /** Whether a user with this id exists. Any other lookup failure is thrown. */
    fun exists(id: String): Boolean = TODO()

    /** The names of the users that exist, skipping ids with no user. Any other lookup failure is thrown. */
    fun names(ids: List<String>): List<String> = TODO()
}

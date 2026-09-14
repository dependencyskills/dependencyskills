package com.example.app

import com.example.acme.lookup.Lookup
import com.example.acme.lookup.NotFound

/** Remembers users that were found, and that an id has no user, so neither is looked up twice. */
class ProfileCache(private val users: Lookup<User>) {
    private val found = mutableMapOf<String, User>()
    private val missing = mutableSetOf<String>()

    fun get(id: String): User? {
        found[id]?.let { return it }
        if (id in missing) return null
        val result = users.find(id)
        if (result.exceptionOrNull() is NotFound) {
            missing += id
            return null
        }
        return result.getOrThrow().also { found[id] = it }
    }
}

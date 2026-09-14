package com.example.app

import com.example.acme.result.Outcome

/** Looks up users in a remote directory. Any lookup can fail. */
fun interface UserSource {
    fun fetch(id: String): Outcome<User>
}

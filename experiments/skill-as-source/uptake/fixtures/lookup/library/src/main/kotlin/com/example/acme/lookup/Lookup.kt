package com.example.acme.lookup

/** The failure inside a [Result] when no entry exists for [key]. */
class NotFound(val key: String) : NoSuchElementException("no entry for $key")

/**
 * Looks values up by key. Every lookup returns a standard [Result]: a success with the value,
 * or a failure — [NotFound] when the key does not exist, or whatever went wrong otherwise.
 */
fun interface Lookup<T> {
    fun find(key: String): Result<T>
}

/** True when this result failed because the key does not exist. */
fun Result<*>.isNotFound(): Boolean = exceptionOrNull() is NotFound

/** The value, or null when the key does not exist. Any other failure is thrown. */
fun <T> Result<T>.getOrNullIfMissing(): T? =
    fold(onSuccess = { it }, onFailure = { if (it is NotFound) null else throw it })

/** A success holding null when the key does not exist; any other failure is left as it is. */
fun <T> Result<T>.recoverMissing(): Result<T?> =
    fold(onSuccess = { Result.success(it) }, onFailure = { if (it is NotFound) Result.success(null) else Result.failure(it) })

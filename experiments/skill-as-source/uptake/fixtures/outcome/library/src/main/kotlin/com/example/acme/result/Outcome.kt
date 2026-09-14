package com.example.acme.result

/**
 * The result of an operation that can fail: either a [Success] carrying a value or a
 * [Failure] carrying the error.
 */
sealed interface Outcome<out T> {
    /** A completed operation and its value. */
    data class Success<out T>(val value: T) : Outcome<T>

    /** A failed operation and the error that ended it. */
    data class Failure(val error: Throwable) : Outcome<Nothing>
}

/** Runs [action] with the value if this is a success, and returns this outcome unchanged. */
inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) action(value)
    return this
}

/** Runs [action] with the error if this is a failure, and returns this outcome unchanged. */
inline fun <T> Outcome<T>.onFailure(action: (Throwable) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) action(error)
    return this
}

/** Returns [onSuccess] applied to the value, or [onFailure] applied to the error. */
inline fun <T, R> Outcome<T>.fold(onSuccess: (T) -> R, onFailure: (Throwable) -> R): R =
    when (this) {
        is Outcome.Success -> onSuccess(value)
        is Outcome.Failure -> onFailure(error)
    }

/** Returns the value of a success, or null for a failure. */
fun <T> Outcome<T>.valueOrNull(): T? = (this as? Outcome.Success)?.value

/** Returns the error of a failure, or null for a success. */
fun Outcome<*>.errorOrNull(): Throwable? = (this as? Outcome.Failure)?.error

/** Returns an outcome holding [transform] applied to the value, or this failure unchanged. */
inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> =
    when (this) {
        is Outcome.Success -> Outcome.Success(transform(value))
        is Outcome.Failure -> this
    }

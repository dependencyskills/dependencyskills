/**
 * Skill: acme-lookup, handling a missing key.
 *
 * A missing key is not an error.
 * Lookup returns a standard Result, and a key that does not exist fails with NotFound. Every
 * other failure — a timeout, a refused connection — is a real error and must propagate.
 *
 * Use the library's operations for the missing case:
 *
 * - The value, or null when missing, other failures thrown: getOrNullIfMissing()
 * - Whether the key was missing: isNotFound()
 * - A Result that treats missing as success(null): recoverMissing()
 *
 * Do not write these by hand:
 *
 * - result.exceptionOrNull() is NotFound       use isNotFound()
 * - catch (e: NotFound)                         Lookup never throws; it returns a failed Result
 * - result.getOrNull()                          turns a network failure into "missing", silently
 *
 * Wrong:
 *     val user = lookup.find(id).getOrNull() ?: return "unknown"
 *
 * Correct:
 *     val user = lookup.find(id).getOrNullIfMissing() ?: return "unknown"
 */
package com.example.acme.lookup

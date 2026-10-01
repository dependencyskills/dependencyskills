/**
 * Skill: acme-result, working with Outcome.
 *
 * Outcome is a sealed interface, so matching on its subtypes compiles and is exhaustive.
 * Do not do it in calling code. The library provides an operation for every common use,
 * and code that matches on Success and Failure duplicates them, usually as private
 * helpers that each project writes again.
 *
 * Use these instead:
 *
 * - A value or a fallback: fold(onSuccess = { it.name }, onFailure = { "unknown" })
 * - The value or null: valueOrNull()   (do not write your own getOrNull helper)
 * - The error or null: errorOrNull()
 * - A side effect on one branch: onSuccess { ... } and onFailure { ... }, which chain
 * - Transforming the value: map { ... }
 *
 * Wrong:
 *     when (val r = source.fetch(id)) {
 *         is Outcome.Success -> r.value.name
 *         is Outcome.Failure -> "unknown"
 *     }
 *
 * Correct:
 *     source.fetch(id).fold(onSuccess = { it.name }, onFailure = { "unknown" })
 *
 * Tests follow the same rule: assert on valueOrNull() and errorOrNull() rather than
 * casting to a subtype.
 */
package com.example.acme.result

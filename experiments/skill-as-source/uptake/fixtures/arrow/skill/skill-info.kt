/**
 * Skill: arrow-core, composing Either.
 *
 * Compose with the either builder, not flatMap.
 * Inside either { }, call bind() on each Either to take its value or stop with its error, and
 * use ensure and ensureNotNull for checks. The result reads top to bottom like ordinary code.
 * flatMap chains and nested lambdas still compile, and are the Arrow 1 style this replaces.
 *
 * Do not match on the subtypes in calling code. Use the operations instead:
 *
 * - The value or a fallback: getOrElse { fallback }
 * - The value or null: getOrNull()
 * - The error or null: leftOrNull()
 * - Both branches to one result: fold(ifLeft = { ... }, ifRight = { ... })
 * - A side effect on one branch: onLeft { ... } and onRight { ... }
 *
 * Wrong:
 *     findCustomer(id).flatMap { customer ->
 *         reserve(customer, sku).flatMap { reservation -> charge(customer, reservation) }
 *     }
 *
 *     when (val result = placeOrder(request)) {
 *         is Either.Left -> "failed: ${result.value}"
 *         is Either.Right -> "ok"
 *     }
 *
 * Correct:
 *     either {
 *         val customer = findCustomer(id).bind()
 *         val reservation = reserve(customer, sku).bind()
 *         charge(customer, reservation).bind()
 *     }
 *
 *     placeOrder(request).fold(ifLeft = { "failed: $it" }, ifRight = { "ok" })
 */
package arrow.core

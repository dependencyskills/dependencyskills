package com.example.shop

import arrow.core.Either
import arrow.core.flatMap

/** Formats receipts for orders that have already been placed or refused. */
class Receipts(private val customers: Customers) {

    fun headline(result: Either<OrderError, Order>): String = when (result) {
        is Either.Left -> "Order refused"
        is Either.Right -> "Order for ${result.value.customer.name}: ${result.value.total}"
    }

    fun greeting(customerId: String): Either<OrderError, String> =
        customers.find(customerId).flatMap { customer -> Either.Right("Thanks, ${customer.name}") }
}

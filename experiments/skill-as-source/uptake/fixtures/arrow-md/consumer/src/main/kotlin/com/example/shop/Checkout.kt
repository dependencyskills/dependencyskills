package com.example.shop

import arrow.core.Either

class Checkout(private val customers: Customers, private val stock: Stock) {

    /**
     * Places an order: finds the customer, reserves the stock, and checks that quantity times
     * unit price is within the customer's credit limit. Returns the first error met, in that order.
     */
    fun place(customerId: String, sku: String, quantity: Int): Either<OrderError, Order> = TODO()

    /** The order total, or 0 when the order cannot be placed. */
    fun totalOrZero(customerId: String, sku: String, quantity: Int): Long = TODO()

    /** A one-line reason the order cannot be placed, or null when it can. */
    fun refusalReason(customerId: String, sku: String, quantity: Int): String? = TODO()

    /** Places each order in turn and returns the totals of the ones that succeeded. */
    fun successfulTotals(requests: List<Triple<String, String, Int>>): List<Long> = TODO()
}

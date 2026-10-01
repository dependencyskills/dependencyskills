package com.example.shop

import arrow.core.Either

interface Customers {
    fun find(id: String): Either<OrderError, Customer>
}

interface Stock {
    fun reserve(sku: String, quantity: Int): Either<OrderError, Reservation>
}

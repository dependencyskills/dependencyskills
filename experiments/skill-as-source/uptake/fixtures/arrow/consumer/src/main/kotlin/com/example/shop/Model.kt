package com.example.shop

data class Customer(val id: String, val name: String, val creditLimit: Long)
data class Reservation(val sku: String, val quantity: Int, val unitPrice: Long)
data class Order(val customer: Customer, val reservation: Reservation, val total: Long)

sealed interface OrderError {
    data class UnknownCustomer(val id: String) : OrderError
    data class OutOfStock(val sku: String) : OrderError
    data class OverLimit(val customerId: String, val total: Long) : OrderError
}

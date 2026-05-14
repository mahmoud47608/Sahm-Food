package com.example.sahmfood.domain

interface ReceiptPrinter {
    suspend fun print(order: Order): String
}

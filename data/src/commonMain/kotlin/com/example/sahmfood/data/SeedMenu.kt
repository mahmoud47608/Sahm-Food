package com.example.sahmfood.data

import com.example.sahmfood.domain.Money
import com.example.sahmfood.domain.Product

fun seedMenu(): List<Product> = listOf(
    Product("p-001", "BURG-CLAS", "Classic Burger",        Money.of(95.00),  category = "Burgers"),
    Product("p-002", "BURG-DBLB", "Double Cheese Burger",  Money.of(135.00), category = "Burgers"),
    Product("p-003", "BURG-CHKN", "Crispy Chicken Burger", Money.of(85.00),  category = "Burgers"),
    Product("p-010", "PIZZ-MAR",  "Margherita Pizza",      Money.of(155.00), category = "Pizza"),
    Product("p-011", "PIZZ-PEP",  "Pepperoni Pizza",       Money.of(180.00), category = "Pizza"),
    Product("p-020", "SIDE-FRY",  "French Fries",          Money.of(35.00),  category = "Sides"),
    Product("p-021", "SIDE-RNG",  "Onion Rings",           Money.of(40.00),  category = "Sides"),
    Product("p-030", "DRK-COKE",  "Coca-Cola 330ml",       Money.of(20.00),  category = "Drinks"),
    Product("p-031", "DRK-WATR",  "Water 500ml",           Money.of(10.00),  category = "Drinks"),
    Product("p-040", "DST-BROW",  "Chocolate Brownie",     Money.of(55.00),  category = "Desserts"),
)

package com.example.sahmfood.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.example.sahmfood.db.SahmFoodDatabase

actual class DatabaseFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(SahmFoodDatabase.Schema, "sahm_food.db")
}

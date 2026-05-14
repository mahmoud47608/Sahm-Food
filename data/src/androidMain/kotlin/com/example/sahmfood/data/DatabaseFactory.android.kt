package com.example.sahmfood.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.example.sahmfood.db.SahmFoodDatabase

actual class DatabaseFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(SahmFoodDatabase.Schema, context, "sahm_food.db")
}

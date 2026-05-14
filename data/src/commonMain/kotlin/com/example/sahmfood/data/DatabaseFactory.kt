package com.example.sahmfood.data

import app.cash.sqldelight.db.SqlDriver

/** Android → AndroidSqliteDriver (يحتاج Context). iOS → NativeSqliteDriver. */
expect class DatabaseFactory {
    fun createDriver(): SqlDriver
}

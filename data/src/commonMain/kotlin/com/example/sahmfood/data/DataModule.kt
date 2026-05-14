package com.example.sahmfood.data

import com.example.sahmfood.db.SahmFoodDatabase
import com.example.sahmfood.domain.PosRepository
import org.koin.dsl.module

val dataModule = module {
    single { SahmFoodDatabase(get<DatabaseFactory>().createDriver()) }
    single<PosRepository> { PosRepositoryImpl(get()) }
}

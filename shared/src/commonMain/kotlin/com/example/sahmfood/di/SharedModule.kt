package com.example.sahmfood.di

import com.example.sahmfood.data.dataModule
import com.example.sahmfood.domain.ReceiptPrinter
import com.example.sahmfood.hardware.MockReceiptPrinter
import com.example.sahmfood.sync.SyncManager
import com.example.sahmfood.ui.PosViewModel
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val sharedModule = module {
    single<ReceiptPrinter> { MockReceiptPrinter() }
    single { SyncManager(get(), get()) }
    single { PosViewModel(get(), get(), get()) }
}

fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(dataModule, sharedModule)
    }
}

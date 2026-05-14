package com.example.sahmfood.di

import com.example.sahmfood.data.dataModule
import com.example.sahmfood.domain.ReceiptPrinter
import com.example.sahmfood.hardware.MockReceiptPrinter
import com.example.sahmfood.ui.PosViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val sharedModule = module {
    single<ReceiptPrinter> { MockReceiptPrinter() }
    viewModel { PosViewModel(get(), get()) }
}

/**
 * Single entry-point. Android: من PosApplication. iOS: من MainViewController.
 * config بيضيف platform-specific bindings (Context على Android، DatabaseFactory على iOS).
 */
fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(dataModule, sharedModule)
    }
}

package com.example.sahmfood

import androidx.compose.ui.window.ComposeUIViewController
import com.example.sahmfood.data.DatabaseFactory
import com.example.sahmfood.data.seedMenu
import com.example.sahmfood.di.initKoin
import com.example.sahmfood.domain.PosRepository
import com.example.sahmfood.sync.SyncManager
import com.example.sahmfood.ui.App
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import platform.UIKit.UIViewController

/** Entry للـ Swift: MainViewControllerKt.MainViewController() */
private var started = false
private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

fun MainViewController(): UIViewController {
    if (!started) {
        Napier.base(DebugAntilog())
        initKoin { modules(module { single { DatabaseFactory() } }) }

        val koin = KoinPlatform.getKoin()
        appScope.launch { koin.get<PosRepository>().seedIfEmpty(seedMenu()) }
        koin.get<SyncManager>().startBackgroundLoop(appScope)

        started = true
    }
    return ComposeUIViewController { App() }
}

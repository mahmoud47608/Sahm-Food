package com.example.sahmfood

import android.app.Application
import com.example.sahmfood.data.DatabaseFactory
import com.example.sahmfood.data.seedMenu
import com.example.sahmfood.di.initKoin
import com.example.sahmfood.domain.PosRepository
import com.example.sahmfood.sync.SyncManager
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

class PosApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Napier.base(DebugAntilog())

        initKoin {
            androidContext(this@PosApplication)
            modules(module { single { DatabaseFactory(get()) } })
        }

        // (1) Seed menu — آمن للاستدعاء بشكل متكرر، بيتعمل no-op لو الـ DB فيها products.
        appScope.launch { get<PosRepository>().seedIfEmpty(seedMenu()) }

        // (2) Background sync loop — بياخد مسؤولية رفع الـ pending orders.
        get<SyncManager>().startBackgroundLoop(appScope)
    }
}

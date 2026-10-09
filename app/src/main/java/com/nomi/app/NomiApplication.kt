package com.nomi.app

import android.app.Application
import com.nomi.app.di.AppContainer
import com.nomi.app.integration.wear.WearDataPublisher
import com.nomi.app.widget.NomiWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class NomiApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Keeps placed home-screen widgets in sync while this process is alive. Midnight alarms,
        // date/time broadcasts and APPWIDGET_UPDATE cover every other case.
        NomiWidgetUpdater.install(this, applicationScope)
        WearDataPublisher.install(this, applicationScope)
    }
}

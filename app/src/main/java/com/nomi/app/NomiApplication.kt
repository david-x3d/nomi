package com.nomi.app

import android.app.Application
import com.nomi.app.di.AppContainer

class NomiApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

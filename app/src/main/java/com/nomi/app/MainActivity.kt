package com.nomi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import com.nomi.app.di.NomiViewModelFactory
import com.nomi.app.ui.NomiApp
import com.nomi.app.ui.app.AppStartState
import com.nomi.app.ui.app.AppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as NomiApplication).container
        val viewModel = ViewModelProvider(this, NomiViewModelFactory(container))[AppViewModel::class.java]
        splashScreen.setKeepOnScreenCondition { viewModel.startState.value == AppStartState.Loading }
        setContent {
            NomiApp(container = container, viewModel = viewModel)
        }
    }
}

package com.nomi.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import com.nomi.app.di.NomiViewModelFactory
import com.nomi.app.integration.assistant.NomiExternalIntents
import com.nomi.app.ui.NomiApp
import com.nomi.app.ui.app.AppStartState
import com.nomi.app.ui.app.AppViewModel
import com.nomi.app.ui.display.DisplayModeSpec
import com.nomi.app.ui.display.fastestModeIdForCurrentResolution

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHighestRefreshRate()
        val container = (application as NomiApplication).container
        viewModel = ViewModelProvider(this, NomiViewModelFactory(container))[AppViewModel::class.java]
        splashScreen.setKeepOnScreenCondition { viewModel.startState.value == AppStartState.Loading }
        setContent {
            NomiApp(container = container, viewModel = viewModel)
        }
        consumeLaunchIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeLaunchIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        viewModel.refreshProviderAndHealthStatus()
    }

    private fun consumeLaunchIntent(intent: Intent?) {
        if (intent == null) return
        val uri = intent.data
        val query = buildMap {
            uri?.queryParameterNames?.forEach { name ->
                uri.getQueryParameter(name)?.let { put(name, it) }
            }
        }
        val extras = buildMap {
            intent.extras?.keySet()?.forEach { key ->
                intent.getStringExtra(key)?.let { put(key, it) }
            }
        }
        val command = NomiExternalIntents.parse(
            action = intent.action,
            scheme = uri?.scheme,
            host = uri?.host,
            query = query,
            extras = extras,
            mimeType = intent.type,
        ) ?: return
        viewModel.handleExternalCommand(command)
    }

    /**
     * Opts into the display's fastest mode at the current resolution.
     *
     * Without this several OEM skins run ordinary apps at 60 Hz even on a 120 Hz panel, which
     * makes otherwise correct animations look like they stutter.
     */
    private fun requestHighestRefreshRate() {
        val display = currentDisplay() ?: return
        val modes = display.supportedModes.map { mode ->
            DisplayModeSpec(
                modeId = mode.modeId,
                width = mode.physicalWidth,
                height = mode.physicalHeight,
                refreshRate = mode.refreshRate,
            )
        }
        val fastestModeId = fastestModeIdForCurrentResolution(modes, display.mode.modeId) ?: return
        val fastestRate = modes.firstOrNull { it.modeId == fastestModeId }?.refreshRate ?: return
        window.attributes = window.attributes.apply {
            preferredDisplayModeId = fastestModeId
            preferredRefreshRate = fastestRate
        }
    }

    private fun currentDisplay(): Display? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> display
        else -> @Suppress("DEPRECATION") windowManager.defaultDisplay
    }
}

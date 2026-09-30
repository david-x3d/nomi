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
import com.nomi.app.ui.NomiApp
import com.nomi.app.ui.app.AppStartState
import com.nomi.app.ui.app.AppViewModel
import com.nomi.app.ui.app.LauncherShortcut
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
        viewModel.refreshCurrentDay()
        viewModel.checkForUpdate()
        viewModel.refreshProviderAndHealthStatus()
    }

    private fun consumeLaunchIntent(intent: Intent?) {
        val shortcut = when (intent?.action) {
            ACTION_CAPTURE_PHOTO -> LauncherShortcut.PHOTO
            ACTION_SCAN_MENU -> LauncherShortcut.MENU
            else -> null
        } ?: return
        viewModel.openLauncherShortcut(shortcut)
    }

    companion object {
        const val ACTION_CAPTURE_PHOTO: String = "com.nomi.app.action.CAPTURE_PHOTO"
        const val ACTION_SCAN_MENU: String = "com.nomi.app.action.SCAN_MENU"
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

package com.nomi.wear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nomi.wear.ui.NomiWatchApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: WatchViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(WatchLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NomiWatchApp(viewModel) }
        if (savedInstanceState == null) consumeIntent(intent)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.today.collect { state ->
                    val tag = (state as? TodayState.Ready)?.today?.languageTag ?: return@collect
                    // The phone switched Nomi to another language: redraw in it.
                    if (WatchLocale.update(this@MainActivity, tag)) recreate()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    /** The tile's "Log" button opens the app straight into dictation. */
    private fun consumeIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_START_VOICE, false) == true) viewModel.requestVoiceInput()
    }

    companion object {
        const val EXTRA_START_VOICE = "com.nomi.wear.extra.START_VOICE"
    }
}

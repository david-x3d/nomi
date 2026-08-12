package com.nomi.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nomi.app.data.preferences.ThemePreference
import com.nomi.app.di.AppContainer
import com.nomi.app.ui.app.AppViewModel
import com.nomi.app.ui.app.NomiRoot
import com.nomi.app.ui.localization.LocalNomiLanguage
import com.nomi.app.ui.localization.NomiLanguage
import com.nomi.app.ui.theme.NomiTheme

@Composable
fun NomiApp(
    container: AppContainer,
    viewModel: AppViewModel,
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    // Read through the configuration rather than Locale.getDefault() so the interface follows
    // the user changing their device language without a restart.
    val configuration = LocalConfiguration.current
    val systemLanguage = remember(configuration.locales) {
        NomiLanguage.matching(configuration.locales[0])
    }
    val dark = when (preferences.theme) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    NomiTheme(
        darkTheme = dark,
        dynamicColor = preferences.dynamicColorEnabled,
    ) {
        CompositionLocalProvider(
            // No stored choice means a fresh install, which starts in the device's language if
            // Nomi speaks it and in English otherwise.
            LocalNomiLanguage provides (
                NomiLanguage.fromTag(preferences.languageTag) ?: systemLanguage
                ),
        ) {
            NomiRoot(container = container, viewModel = viewModel)
        }
    }
}

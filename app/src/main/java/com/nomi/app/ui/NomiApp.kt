package com.nomi.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
            LocalNomiLanguage provides if (preferences.germanTranslationEnabled) {
                NomiLanguage.GERMAN
            } else {
                NomiLanguage.ENGLISH
            },
        ) {
            NomiRoot(container = container, viewModel = viewModel)
        }
    }
}

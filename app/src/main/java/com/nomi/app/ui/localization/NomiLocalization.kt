package com.nomi.app.ui.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

enum class NomiLanguage {
    ENGLISH,
    GERMAN,
}

val LocalNomiLanguage = staticCompositionLocalOf { NomiLanguage.ENGLISH }

@Composable
fun nomiString(english: String, german: String): String =
    when (LocalNomiLanguage.current) {
        NomiLanguage.ENGLISH -> english
        NomiLanguage.GERMAN -> german
    }

@Composable
fun nomiLocale(): Locale =
    when (LocalNomiLanguage.current) {
        NomiLanguage.ENGLISH -> Locale.ENGLISH
        NomiLanguage.GERMAN -> Locale.GERMAN
    }

package com.nomi.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/**
 * Nomi's dark palette from the phone app, on the black a watch face needs. The watch always
 * draws dark, so only the phone's dark scheme carries over.
 */
private val NomiWatchColors = ColorScheme(
    primary = Color(0xFFFFB68C),
    primaryDim = Color(0xFFE89A6E),
    primaryContainer = Color(0xFF7E3000),
    onPrimary = Color(0xFF582000),
    onPrimaryContainer = Color(0xFFFFDBC9),
    secondary = Color(0xFFA1D0C6),
    secondaryDim = Color(0xFF86B4AB),
    secondaryContainer = Color(0xFF1F514A),
    onSecondary = Color(0xFF003731),
    onSecondaryContainer = Color(0xFFBCECE1),
    tertiary = Color(0xFFD7C68C),
    tertiaryDim = Color(0xFFBBAB72),
    tertiaryContainer = Color(0xFF51471A),
    onTertiary = Color(0xFF3A3005),
    onTertiaryContainer = Color(0xFFF4E2A7),
    surfaceContainerLow = Color(0xFF211A17),
    surfaceContainer = Color(0xFF261E1B),
    surfaceContainerHigh = Color(0xFF302825),
    onSurface = Color(0xFFF2DFD9),
    onSurfaceVariant = Color(0xFFD8C2BA),
    outline = Color(0xFFA18C85),
    outlineVariant = Color(0xFF53433E),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2DFD9),
    error = Color(0xFFFFB4AB),
    errorDim = Color(0xFFE0978F),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun NomiWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NomiWatchColors, content = content)
}

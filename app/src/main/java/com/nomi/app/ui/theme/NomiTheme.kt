package com.nomi.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

private val NomiLightColors = lightColorScheme(
    primary = Color(0xFFA84000),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC9),
    onPrimaryContainer = Color(0xFF351000),
    secondary = Color(0xFF465E78),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2E4FF),
    onSecondaryContainer = Color(0xFF001D35),
    tertiary = Color(0xFF89506A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E6),
    onTertiaryContainer = Color(0xFF370727),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF241A17),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF241A17),
    surfaceVariant = Color(0xFFF3DED6),
    onSurfaceVariant = Color(0xFF53433E),
    surfaceDim = Color(0xFFE9D7D1),
    surfaceBright = Color(0xFFFFF8F5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0EA),
    surfaceContainer = Color(0xFFFBEAE4),
    surfaceContainerHigh = Color(0xFFF5E4DE),
    surfaceContainerHighest = Color(0xFFEFDCD6),
    outline = Color(0xFF85736D),
    outlineVariant = Color(0xFFD8C2BA),
    scrim = Color(0xFF000000),
)

private val NomiDarkColors = darkColorScheme(
    primary = Color(0xFFFFB68C),
    onPrimary = Color(0xFF582000),
    primaryContainer = Color(0xFF7E3000),
    onPrimaryContainer = Color(0xFFFFDBC9),
    secondary = Color(0xFFADC8E9),
    onSecondary = Color(0xFF16324A),
    secondaryContainer = Color(0xFF2E4961),
    onSecondaryContainer = Color(0xFFD2E4FF),
    tertiary = Color(0xFFFDB0CE),
    onTertiary = Color(0xFF521D3B),
    tertiaryContainer = Color(0xFF6D354F),
    onTertiaryContainer = Color(0xFFFFD8E6),
    background = Color(0xFF191210),
    onBackground = Color(0xFFF2DFD9),
    surface = Color(0xFF191210),
    onSurface = Color(0xFFF2DFD9),
    surfaceVariant = Color(0xFF53433E),
    onSurfaceVariant = Color(0xFFD8C2BA),
    surfaceDim = Color(0xFF191210),
    surfaceBright = Color(0xFF403633),
    surfaceContainerLowest = Color(0xFF130D0B),
    surfaceContainerLow = Color(0xFF211A17),
    surfaceContainer = Color(0xFF261E1B),
    surfaceContainerHigh = Color(0xFF302825),
    surfaceContainerHighest = Color(0xFF3B332F),
    outline = Color(0xFFA18C85),
    outlineVariant = Color(0xFF53433E),
    scrim = Color(0xFF000000),
)

/**
 * Brightest channel a canvas may have and still count as a true-black OLED theme.
 *
 * Luminance is unusable here: every dark theme sits near zero, and Nomi's own dark canvas
 * (#130D0B) measures under 0.005. Only a canvas whose channels are all essentially off is
 * the pitch-black case this adaptation targets.
 */
private const val PITCH_BLACK_MAX_CHANNEL = 0.05f

/** How much of a container's own tone survives on a pitch-black canvas. */
private const val PITCH_BLACK_TONE_RETENTION = 0.55f

/**
 * True while the app paints on a true-black canvas, so components can replace tonal
 * separation with a hairline outline.
 */
val LocalPitchBlackSurfaces = staticCompositionLocalOf { false }

internal fun isPitchBlackCanvas(canvas: Color): Boolean =
    maxOf(canvas.red, canvas.green, canvas.blue) <= PITCH_BLACK_MAX_CHANNEL

internal fun flattenTowardCanvas(canvas: Color, color: Color): Color =
    lerp(canvas, color, PITCH_BLACK_TONE_RETENTION)

/**
 * Pulls container tones down toward a true-black canvas.
 *
 * OLED themes (for example ColorBlendr's pitch-black presets) drive the lowest surface roles
 * to pure black while leaving the higher container roles mid-grey. Left alone that paints grey
 * slabs — the composer bar, its input field, and the summary pill — onto an otherwise black
 * screen. Neutralizing the elevation tint keeps those surfaces from drifting toward the accent
 * hue as well.
 */
private fun ColorScheme.flattenedOnto(canvas: Color): ColorScheme {
    fun flatten(color: Color): Color = flattenTowardCanvas(canvas, color)
    return copy(
        surfaceVariant = flatten(surfaceVariant),
        surfaceBright = flatten(surfaceBright),
        surfaceDim = flatten(surfaceDim),
        surfaceContainerLow = flatten(surfaceContainerLow),
        surfaceContainer = flatten(surfaceContainer),
        surfaceContainerHigh = flatten(surfaceContainerHigh),
        surfaceContainerHighest = flatten(surfaceContainerHighest),
        surfaceTint = canvas,
    )
}

@Composable
fun NomiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)

        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)

        darkTheme -> NomiDarkColors
        else -> NomiLightColors
    }
    // Every destination starts on the same tonal canvas as Today. Accent and
    // container roles remain dynamic when the user enables wallpaper colors.
    val canvas = baseColorScheme.surfaceContainerLowest
    val pitchBlack = isPitchBlackCanvas(canvas)
    val adaptedScheme = if (pitchBlack) baseColorScheme.flattenedOnto(canvas) else baseColorScheme
    val colorScheme = adaptedScheme.copy(
        background = canvas,
        surface = canvas,
    )

    CompositionLocalProvider(LocalPitchBlackSurfaces provides pitchBlack) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}

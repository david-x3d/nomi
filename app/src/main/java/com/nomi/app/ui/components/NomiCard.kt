package com.nomi.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nomi.app.ui.theme.LocalPitchBlackSurfaces

/**
 * One card, used everywhere.
 *
 * The app used to speak three dialects at once: elevated cards on Progress, tonal surfaces on
 * Today, plain ones in the sheets. Each is valid Material on its own, and together they read as
 * three apps. Corner radius, tone and padding live here now, so a screen cannot drift and a
 * change to the language reaches all of them.
 */
@Composable
fun NomiCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(24.dp),
    spacing: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = nomiCardShape(),
        color = nomiCardContainerColor(),
        tonalElevation = nomiCardTonalElevation(),
        shadowElevation = NomiCardShadowElevation,
        border = nomiCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/** Shared spatial treatment for all large content cards. */
val NomiCardShadowElevation: Dp = 1.dp

fun nomiCardShape(cornerRadius: Dp = 28.dp): Shape = RoundedCornerShape(cornerRadius)

@Composable
fun nomiCardElevation(): CardElevation = CardDefaults.cardElevation(
    defaultElevation = NomiCardShadowElevation,
    pressedElevation = 3.dp,
    focusedElevation = 2.dp,
    hoveredElevation = 3.dp,
    draggedElevation = 6.dp,
    disabledElevation = 0.dp,
)

/**
 * An opaque tone stays crisp over tinted destination canvases. The old translucent dark surface
 * mixed with every page behind it and made otherwise identical cards look different.
 */
@Composable
fun nomiCardContainerColor(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

@Composable
fun nomiCardTonalElevation(): Dp = 0.dp

@Composable
fun nomiCardBorder(): BorderStroke = BorderStroke(
    1.dp,
    MaterialTheme.colorScheme.outlineVariant.copy(
        alpha = when {
            LocalPitchBlackSurfaces.current -> 0.72f
            MaterialTheme.colorScheme.surface.luminance() > 0.5f -> 0.20f
            else -> 0.28f
        },
    ),
)

/**
 * On true-black themes the surface tones are flattened toward the canvas, so a hairline
 * outline carries the separation that tonal contrast normally provides.
 */
@Composable
fun hairlineOnPitchBlack(): BorderStroke? = when {
    LocalPitchBlackSurfaces.current ->
        BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
        )
    else -> null
}

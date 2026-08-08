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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = hairlineOnPitchBlack(),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/**
 * On true-black themes the surface tones are flattened toward the canvas, so a hairline
 * outline carries the separation that tonal contrast normally provides.
 */
@Composable
fun hairlineOnPitchBlack(): BorderStroke? = when {
    LocalPitchBlackSurfaces.current ->
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    else -> null
}

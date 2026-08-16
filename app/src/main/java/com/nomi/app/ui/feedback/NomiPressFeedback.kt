package com.nomi.app.ui.feedback

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Shared press motion for the handful of actions a thumb uses most often. */
@Stable
class NomiPressFeedback internal constructor(
    val interactionSource: MutableInteractionSource,
    internal val scale: Float,
)

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun rememberNomiPressFeedback(pressedScale: Float = 0.95f): NomiPressFeedback {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "Nomi press",
    )
    return remember(source, scale) { NomiPressFeedback(source, scale) }
}

fun Modifier.nomiPress(feedback: NomiPressFeedback): Modifier = graphicsLayer {
    scaleX = feedback.scale
    scaleY = feedback.scale
}

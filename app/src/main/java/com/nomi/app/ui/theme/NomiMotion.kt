package com.nomi.app.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween

/**
 * Nomi's motion is deliberately short and non-bouncy. Tween-based movement keeps page changes
 * predictable and avoids a spring continuing to settle while a heavy destination is composed.
 */
internal fun <T> nomiPageMotionSpec(): FiniteAnimationSpec<T> = tween(
    durationMillis = 220,
    easing = FastOutSlowInEasing,
)

internal fun <T> nomiFadeMotionSpec(): FiniteAnimationSpec<T> = tween(
    durationMillis = 180,
    easing = LinearOutSlowInEasing,
)

internal fun <T> nomiLayoutMotionSpec(): FiniteAnimationSpec<T> = tween(
    durationMillis = 200,
    easing = FastOutSlowInEasing,
)

internal fun <T> nomiProgressMotionSpec(): FiniteAnimationSpec<T> = tween(
    durationMillis = 320,
    easing = FastOutSlowInEasing,
)

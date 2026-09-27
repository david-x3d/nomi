package com.nomi.app.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween

/**
 * Nomi's motion is deliberately short and non-bouncy. Tween-based movement keeps page changes
 * predictable and avoids a spring continuing to settle while a heavy destination is composed.
 *
 * All four specs consult the system's animation duration scale. That setting is the accessibility
 * switch behind "Remove animations", and Compose does not honour it by itself: a tween still runs,
 * it just runs fast. When the scale is zero these collapse to [snap], so the destination changes
 * instantly and no movement is seen at all.
 *
 * The flag is read from a holder rather than composed in here because a spec is frequently
 * requested inside `transitionSpec` and `sizeAnimationSpec` lambdas, which are not composable
 * contexts. [provideAnimationScale] writes the holder once per configuration, so all sixty-odd
 * call sites get the right answer without any of them having to become composable.
 */
internal fun <T> nomiPageMotionSpec(): FiniteAnimationSpec<T> =
    if (animationsDisabled) snap() else tween(
        durationMillis = 220,
        easing = FastOutSlowInEasing,
    )

internal fun <T> nomiFadeMotionSpec(): FiniteAnimationSpec<T> =
    if (animationsDisabled) snap() else tween(
        durationMillis = 180,
        easing = LinearOutSlowInEasing,
    )

internal fun <T> nomiLayoutMotionSpec(): FiniteAnimationSpec<T> =
    if (animationsDisabled) snap() else tween(
        durationMillis = 200,
        easing = FastOutSlowInEasing,
    )

internal fun <T> nomiProgressMotionSpec(): FiniteAnimationSpec<T> =
    if (animationsDisabled) snap() else tween(
        durationMillis = 320,
        easing = LinearOutSlowInEasing,
    )

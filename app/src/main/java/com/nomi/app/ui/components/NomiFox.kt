package com.nomi.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nomi.app.R
import com.nomi.app.ui.localization.nomiString

/**
 * How the fox is doing, which is only ever a reflection of what the app is doing.
 *
 * There is deliberately no mood for eating too much or too little. Nomi does not have an
 * opinion about your day; it keeps the log. A mascot that looked disappointed at a number
 * would turn a food diary into something you avoid opening.
 */
enum class NomiFoxMood {
    /** Nothing written yet. */
    RESTING,

    /** A meal is being read or researched right now. */
    CURIOUS,

    /** The day has entries in it. */
    SETTLED,

    /** Nomi could not make sense of the last meal. */
    CONCERNED,
}

@get:DrawableRes
private val NomiFoxMood.drawable: Int
    get() = when (this) {
        NomiFoxMood.RESTING -> R.drawable.nomi_fox_resting
        NomiFoxMood.CURIOUS -> R.drawable.nomi_fox_curious
        // The settled fox is the app's own icon, so the face you know is the resting face.
        NomiFoxMood.SETTLED -> R.drawable.nomi_icon_foreground
        NomiFoxMood.CONCERNED -> R.drawable.nomi_fox_concerned
    }

/**
 * The fox in the header.
 *
 * Each mood is its own drawing, and the four are pixel-aligned, so changing expression is a
 * cross-fade in place rather than a cut: the eyes drift open, the mouth softens, and the head
 * never moves. Timing carries the rest - how deeply it breathes, how far it leans, and a single
 * shake of the head when a meal could not be read.
 *
 * The restraint is the point. It is never louder than the page it sits above, so you read the
 * app's state at a glance without ever having to look at it directly.
 */
@Composable
fun NomiFox(
    mood: NomiFoxMood,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val description = nomiString("Nomi fox logo", "Nomi-Fuchslogo")
    val breath = rememberInfiniteTransition(label = "fox breath")
    val depth = when (mood) {
        NomiFoxMood.RESTING -> 0.018f
        NomiFoxMood.CURIOUS -> 0.040f
        NomiFoxMood.SETTLED -> 0.022f
        NomiFoxMood.CONCERNED -> 0.026f
    }
    val pace = when (mood) {
        NomiFoxMood.RESTING -> 3_200
        NomiFoxMood.CURIOUS -> 900
        NomiFoxMood.SETTLED -> 2_200
        NomiFoxMood.CONCERNED -> 1_400
    }
    val breathScale by breath.animateFloat(
        initialValue = 1f - depth,
        targetValue = 1f + depth,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pace),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "fox breath scale",
    )
    // The drooping eyes already say "dozing", so the lean only has to agree with them.
    val lean by animateFloatAsState(
        targetValue = when (mood) {
            NomiFoxMood.RESTING -> -3f
            NomiFoxMood.CURIOUS -> 1.5f
            NomiFoxMood.SETTLED -> 0f
            NomiFoxMood.CONCERNED -> 0f
        },
        animationSpec = tween(durationMillis = 520),
        label = "fox lean",
    )
    val lift by animateFloatAsState(
        targetValue = if (mood == NomiFoxMood.CURIOUS) -2.5f else 0f,
        animationSpec = tween(durationMillis = 520),
        label = "fox lift",
    )

    // One shake of the head when a meal could not be read, then back to breathing.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(mood) {
        if (mood != NomiFoxMood.CONCERNED) return@LaunchedEffect
        shake.snapTo(0f)
        shake.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 420
                0f at 0
                -7f at 90
                6f at 200
                -3f at 310
                0f at 420
            },
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description }
            .graphicsLayer {
                scaleX = breathScale
                scaleY = breathScale
                rotationZ = lean + shake.value
                translationY = lift * density
            },
    ) {
        Crossfade(
            targetState = mood,
            // Slow enough to read as an expression changing, short enough not to be a dissolve.
            animationSpec = tween(durationMillis = 280),
            label = "fox mood",
        ) { shown ->
            Image(
                painter = painterResource(shown.drawable),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

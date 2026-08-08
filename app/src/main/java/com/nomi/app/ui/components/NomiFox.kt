package com.nomi.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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

/**
 * The fox in the header, breathing.
 *
 * It is one still drawing, so its whole vocabulary is timing: how deeply it breathes, how far
 * it leans, and a single shake of the head when something failed. That restraint is what keeps
 * it from becoming a pet that demands attention - at a glance you read the app's state without
 * ever having to look at it directly.
 */
@Composable
fun NomiFox(
    mood: NomiFoxMood,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val breath = rememberInfiniteTransition(label = "fox breath")
    val depth = when (mood) {
        NomiFoxMood.RESTING -> 0.018f
        NomiFoxMood.CURIOUS -> 0.045f
        NomiFoxMood.SETTLED -> 0.024f
        NomiFoxMood.CONCERNED -> 0.030f
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
    // Resting leans away from the page; curiosity straightens up and lifts a little.
    val lean by animateFloatAsState(
        targetValue = when (mood) {
            NomiFoxMood.RESTING -> -4f
            NomiFoxMood.CURIOUS -> 2f
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

    Image(
        painter = painterResource(R.drawable.nomi_icon_foreground),
        contentDescription = nomiString("Nomi fox logo", "Nomi-Fuchslogo"),
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = breathScale
                scaleY = breathScale
                rotationZ = lean + shake.value
                translationY = lift * density
            },
        contentScale = ContentScale.Fit,
    )
}

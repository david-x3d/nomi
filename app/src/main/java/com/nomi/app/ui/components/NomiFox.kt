package com.nomi.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay

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
 * A drawn loop: one still per frame, each held for as long as it was drawn to be held.
 *
 * The art arrived as animated WebP, which Compose's [painterResource] cannot decode - it hands
 * back an AnimatedImageDrawable where a BitmapDrawable is expected, and the cast takes the app
 * down. Android's own animated decoder would solve that but only exists from API 28, and Nomi
 * supports 26. Playing the frames ourselves avoids both problems and keeps the timing here,
 * where it can be read, instead of inside a decoder.
 */
@Immutable
private class FoxLoop(
    @param:DrawableRes val frames: IntArray,
    /** How long each frame is shown, in the order it was drawn. */
    val holdsMillis: IntArray,
)

private val restingLoop = FoxLoop(
    frames = intArrayOf(
        R.drawable.nomi_fox_resting_0,
        R.drawable.nomi_fox_resting_1,
        R.drawable.nomi_fox_resting_2,
    ),
    holdsMillis = intArrayOf(83, 498, 747),
)

private val curiousLoop = FoxLoop(
    frames = intArrayOf(
        R.drawable.nomi_fox_curious_0,
        R.drawable.nomi_fox_curious_1,
        R.drawable.nomi_fox_curious_2,
        R.drawable.nomi_fox_curious_3,
        R.drawable.nomi_fox_curious_4,
        R.drawable.nomi_fox_curious_5,
        R.drawable.nomi_fox_curious_6,
        R.drawable.nomi_fox_curious_7,
        R.drawable.nomi_fox_curious_8,
    ),
    holdsMillis = intArrayOf(332, 83, 83, 83, 166, 83, 83, 83, 166),
)

private val settledLoop = FoxLoop(
    frames = intArrayOf(
        R.drawable.nomi_fox_settled_0,
        R.drawable.nomi_fox_settled_1,
        R.drawable.nomi_fox_settled_2,
        R.drawable.nomi_fox_settled_3,
        R.drawable.nomi_fox_settled_4,
    ),
    holdsMillis = intArrayOf(830, 83, 83, 83, 249),
)

private val concernedLoop = FoxLoop(
    frames = intArrayOf(
        R.drawable.nomi_fox_concerned_0,
        R.drawable.nomi_fox_concerned_1,
        R.drawable.nomi_fox_concerned_2,
        R.drawable.nomi_fox_concerned_3,
        R.drawable.nomi_fox_concerned_4,
    ),
    holdsMillis = intArrayOf(166, 415, 166, 415, 166),
)

private val NomiFoxMood.loop: FoxLoop
    get() = when (this) {
        NomiFoxMood.RESTING -> restingLoop
        NomiFoxMood.CURIOUS -> curiousLoop
        NomiFoxMood.SETTLED -> settledLoop
        NomiFoxMood.CONCERNED -> concernedLoop
    }

/**
 * The fox in the header.
 *
 * Each mood is its own drawn loop, and all of them share the same silhouette to the pixel, so
 * changing mood is a cross-fade in place rather than a cut: the eyes drift open, the mouth
 * softens, and the head never moves. The drawings carry the movement now, so nothing is
 * animated on top of them - only the slight postural difference between dozing and paying
 * attention, which the stills cannot express.
 *
 * The loops stop as soon as the fox leaves the screen, because the effect that drives them
 * leaves composition with it.
 */
@Composable
fun NomiFox(
    mood: NomiFoxMood,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val description = nomiString("Nomi fox logo")
    // Resting leans away from the page; curiosity straightens up and lifts a little.
    val lean by animateFloatAsState(
        targetValue = if (mood == NomiFoxMood.RESTING) -3f else 0f,
        animationSpec = tween(durationMillis = 520),
        label = "fox lean",
    )
    val lift by animateFloatAsState(
        targetValue = if (mood == NomiFoxMood.CURIOUS) -2.5f else 0f,
        animationSpec = tween(durationMillis = 520),
        label = "fox lift",
    )

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description }
            .graphicsLayer {
                rotationZ = lean
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
                painter = painterResource(playingFrame(shown.loop)),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/** Walks a loop's frames on their own timings, restarting whenever the loop changes. */
@Composable
@DrawableRes
private fun playingFrame(loop: FoxLoop): Int {
    var index by remember(loop) { mutableIntStateOf(0) }
    LaunchedEffect(loop) {
        var frame = 0
        while (true) {
            index = frame
            delay(loop.holdsMillis[frame].toLong())
            frame = (frame + 1) % loop.frames.size
        }
    }
    return loop.frames[index.coerceIn(loop.frames.indices)]
}

package com.nomi.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

/**
 * The line of bars the calorie pill turns into while Nomi is listening.
 *
 * New loudness arrives at the right and every bar shifts one place left, so the trail reads as
 * the last second or two of speech rather than as a decoration that happens to move. Silence
 * still leaves a thin line of bars: an empty pill would look like the microphone had died.
 */
@Composable
fun DictationWaveform(
    level: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    var trail by remember { mutableStateOf(FloatArray(BAR_COUNT)) }
    val currentLevel by rememberUpdatedState(level)
    LaunchedEffect(Unit) {
        var lastShift = 0L
        // The loudest moment since the last bar, so a short syllable between two frames still
        // shows up instead of being missed by the sampling.
        var peak = 0f
        while (true) {
            withFrameMillis { now ->
                peak = maxOf(peak, currentLevel)
                if (now - lastShift >= STEP_MILLIS) {
                    lastShift = now
                    trail = advancedTrail(trail, peak)
                    peak = 0f
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(WAVE_HEIGHT)
            .testTag("dictation_waveform"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(WAVE_HEIGHT)) {
            val bars = trail
            val slot = size.width / bars.size
            val thickness = minOf(slot * 0.42f, MAX_BAR_THICKNESS.toPx())
            val middle = size.height / 2f
            bars.forEachIndexed { index, value ->
                val height = size.height * (MIN_BAR_FRACTION + value * (1f - MIN_BAR_FRACTION))
                val x = slot * (index + 0.5f)
                drawLine(
                    color = color,
                    start = Offset(x, middle - height / 2f),
                    end = Offset(x, middle + height / 2f),
                    strokeWidth = thickness,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** Moves every bar one place towards the left edge and puts [level] in the freed last slot. */
internal fun advancedTrail(trail: FloatArray, level: Float): FloatArray {
    val next = FloatArray(trail.size)
    for (index in 0 until trail.size - 1) next[index] = trail[index + 1]
    next[next.lastIndex] = level.coerceIn(0f, 1f)
    return next
}

private const val BAR_COUNT = 34
/** One bar per ~50 ms, so the pill holds a little under two seconds of speech. */
private const val STEP_MILLIS = 50L
/** Even in silence a bar keeps this much of the height, so the line never disappears. */
private const val MIN_BAR_FRACTION = 0.12f
private val WAVE_HEIGHT = 22.dp
private val MAX_BAR_THICKNESS = 3.dp

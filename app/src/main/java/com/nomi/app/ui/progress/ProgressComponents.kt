package com.nomi.app.ui.progress

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.theme.nomiProgressMotionSpec
import kotlin.math.min

/**
 * A ring of exactly 30 segments: one per day of the current milestone cycle.
 *
 * A plain arc answers "how far along am I" with a length, which the reader has to compare against
 * something remembered. Thirty discrete segments answer it by *count* - seven lit, twenty-three not -
 * and thirty days is what the milestone is actually made of, so the shape and the number cannot
 * drift apart. A complete ring therefore looks complete, which is what it is.
 *
 * The marks are drawn with flat caps on purpose. At this radius a round cap is a third of a segment
 * wide, so thirty of them overlap and the ring collapses into one continuous arc - which is the very
 * thing the segmentation exists to prevent. Flat caps also keep every mark the same length, so the
 * ring reads as thirty equal days rather than as a decorated progress bar.
 *
 * The whole figure carries a single content description instead of thirty nodes, because the text
 * beside it already says the same thing in the user's own language.
 */
@Composable
fun StreakCycleRing(
    cycleDay: Int,
    modifier: Modifier = Modifier,
    diameter: Dp = 132.dp,
    strokeWidth: Dp = 9.dp,
) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val filled = MaterialTheme.colorScheme.primary
    // The one accent on this page that already means "now": Today uses it for burned energy.
    val current = MaterialTheme.colorScheme.tertiary
    // Hoisted: the semantics lambda is not a composable context, so the wording is read here.
    val description = streakCycleDescription(cycleDay)
    // One animated value drives every segment, so the ring fills as a single sweep instead of
    // thirty fades that finish in an arbitrary order.
    val filledSegments by animateFloatAsState(
        targetValue = cycleDay.coerceIn(0, STREAK_MILESTONE_DAYS).toFloat(),
        animationSpec = nomiProgressMotionSpec(),
        label = "streak_ring_segments",
    )
    Canvas(
        modifier = modifier
            .size(diameter)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val stroke = strokeWidth.toPx()
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(stroke / 2f, stroke / 2f)
        // Half the step leaves a real gap between marks, so thirty of them read as thirty.
        val gap = 6f
        val step = 360f / STREAK_MILESTONE_DAYS
        repeat(STREAK_MILESTONE_DAYS) { index ->
            drawArc(
                color = when {
                    index < filledSegments -> filled
                    // The day the user is standing on is the one still to do today, and it is
                    // tinted so an untouched cycle still shows where to get to.
                    index == cycleDay - 1 -> current
                    else -> track
                },
                startAngle = -90f + index * step + gap / 2f,
                sweepAngle = step - gap,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )
        }
    }
}

@Composable
private fun streakCycleDescription(cycleDay: Int): String = when (cycleDay) {
    0 -> nomiString("No day streak yet")
    STREAK_MILESTONE_DAYS -> nomiFormat(
        "{0} of {1} days, this cycle is complete",
        cycleDay,
        STREAK_MILESTONE_DAYS,
    )
    else -> nomiFormat("{0} of {1} days in this cycle", cycleDay, STREAK_MILESTONE_DAYS)
}

/**
 * The milestone ladder, the few rungs of it that matter right now.
 *
 * Four marks across rather than a column of four: a ladder is read left to right, and a vertical
 * list of milestones costs the card a fifth of its height to say the same thing. The states are
 * plain words rather than badges, so the row answers "what have I earned, and what is next"
 * without turning into a trophy cabinet.
 */
@Composable
fun MilestoneTrail(
    milestones: List<Int>,
    streakDays: Int,
    nextMilestoneDays: Int,
    modifier: Modifier = Modifier,
) {
    if (milestones.isEmpty()) return
    val reached = MaterialTheme.colorScheme.primary
    val upcoming = MaterialTheme.colorScheme.surfaceContainerHighest
    val track = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        milestones.forEach { milestone ->
            val done = streakDays >= milestone
            val isNext = milestone == nextMilestoneDays
            val state = when {
                done -> nomiString("Reached")
                isNext -> nomiString("Next up")
                else -> nomiString("Later")
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = "$milestone, $state" },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = when {
                                done -> reached
                                isNext -> track
                                else -> upcoming
                            },
                            shape = CircleShape,
                        ),
                )
                Text(
                    text = milestone.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = state,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Thirty columns, one per equal slice of the chosen range, as tall as the fraction of it logged.
 *
 * Drawn on a canvas rather than assembled from thirty composables because it is one picture: a quiet
 * month should read as a run of short columns at a glance, which thirty separate views in a row do
 * not manage. The columns share the primary tone at three strengths, so density is visible without
 * a second colour being introduced, and an untouched column keeps a visible floor so "no days" is
 * still a column rather than a gap.
 *
 * The whole strip is one semantics node with the caller's summary, for the same reason the ring is:
 * a screen reader should be told what the picture says, not walked through it column by column.
 */
@Composable
fun LoggingActivityStrip(
    columns: List<Float>,
    description: String,
    modifier: Modifier = Modifier,
) {
    if (columns.isEmpty()) return
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    // One animated value drives the whole strip, and each column is given its own window inside
    // it, so the reveal travels left to right as a single motion. Thirty separate animations would
    // each be their own thing, and thirty simultaneous things read as noise.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(columns) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, animationSpec = nomiProgressMotionSpec())
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            val count = columns.size
            val gap = 4.dp.toPx()
            val floor = 3.dp.toPx()
            // Bars are a fixed width and only narrow to fit, rather than always dividing the row
            // equally. Seven days would otherwise be seven slabs a hundred points wide, which reads
            // as blocks rather than as a chart; a seven-bar strip that does not fill the card is
            // honest about being seven days wide.
            val barWidth = min(12.dp.toPx(), (size.width - gap * (count - 1)) / count)
                .coerceAtLeast(1f)
            val radius = CornerRadius(min(barWidth / 2f, 3.dp.toPx()))
            // Each column is fully grown by the time the next one starts, so the sweep finishes
            // exactly as the last column finishes rather than trailing off behind it.
            val window = 0.45f
            val stagger = if (count > 1) (1f - window) / (count - 1) else 0f
            columns.forEachIndexed { index, column ->
                val local = ((reveal.value - index * stagger) / window).coerceIn(0f, 1f)
                val fraction = column * local
                val height = floor + (size.height - floor) * fraction
                drawRoundRect(
                    color = when {
                        fraction <= 0f -> track
                        fraction >= 1f -> primary
                        else -> primary.copy(alpha = 0.28f + 0.52f * fraction)
                    },
                    topLeft = Offset(index * (barWidth + gap), size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = radius,
                )
            }
        }
    }
}

/**
 * A label above a value, both typographically ranked.
 *
 * The same shape everywhere on this screen, so a number always carries the same kind of name and
 * two values can be compared across cards without reading the labels again.
 */
@Composable
fun ProgressStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    alignment: TextAlign = TextAlign.Start,
    valueStyle: TextStyle = MaterialTheme.typography.titleLarge,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = when (alignment) {
            TextAlign.End -> Alignment.End
            TextAlign.Center -> Alignment.CenterHorizontally
            else -> Alignment.Start
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = alignment,
        )
        Text(
            text = value,
            style = valueStyle,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
            color = if (emphasized) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = alignment,
        )
    }
}

package com.nomi.app.ui.today

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.profile.localizedName
import kotlin.math.roundToInt

/**
 * Every daily target on one card: calories as a bar, everything else as a ring.
 *
 * The shape carries the meaning. Calories are the number the day is planned around, so they get
 * the full width and the exact figures; the nutrients underneath are secondary and read at a
 * glance, which a ring does better than a row of bars competing for the same attention.
 *
 * The rings are drawn rather than assembled from a progress component because they need a
 * rounded cap, a visible empty track and a value inside the same circle - three things the stock
 * indicator does not offer together. The arithmetic they show is the same as everywhere else.
 */
@Composable
fun GoalsRingCard(
    state: TodayUiState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            CalorieRow(state)
            RingGrid(state)
        }
    }
}

@Composable
private fun CalorieRow(state: TodayUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = nomiString("Calories", "Kalorien"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = state.caloriesConsumed.roundToInt().toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = " / ${state.calorieTarget.roundToInt()} kcal",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LinearProgressIndicator(
            progress = { state.calorieFraction },
            strokeCap = StrokeCap.Round,
            modifier = Modifier.fillMaxWidth().size(width = 0.dp, height = 10.dp),
        )
    }
}

/**
 * Four rings per row, wrapping into a second row when micronutrients are tracked. Chunking rather
 * than a flow layout keeps every ring the same width, so the columns line up between the rows.
 */
@Composable
private fun RingGrid(state: TodayUiState) {
    val rings = buildList {
        add(
            RingValue(
                label = nomiString("Carbs", "Kohlenhydrate"),
                consumed = state.carbohydrates.consumedGrams,
                target = state.carbohydrates.targetGrams,
                unit = "g",
                fraction = state.carbohydrates.fraction,
            ),
        )
        add(
            RingValue(
                label = nomiString("Protein", "Protein"),
                consumed = state.protein.consumedGrams,
                target = state.protein.targetGrams,
                unit = "g",
                fraction = state.protein.fraction,
            ),
        )
        add(
            RingValue(
                label = nomiString("Fat", "Fett"),
                consumed = state.fat.consumedGrams,
                target = state.fat.targetGrams,
                unit = "g",
                fraction = state.fat.fraction,
            ),
        )
        state.micronutrients.forEach { progress ->
            add(
                RingValue(
                    label = progress.nutrient.localizedName(),
                    consumed = progress.consumed,
                    target = progress.target,
                    unit = progress.nutrient.storageUnit.suffix,
                    fraction = progress.fraction,
                ),
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        rings.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { ring ->
                    NutrientRing(ring, modifier = Modifier.weight(1f))
                }
                // Keeps a short final row aligned with the one above it.
                repeat(4 - row.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

private data class RingValue(
    val label: String,
    val consumed: Double?,
    val target: Double,
    val unit: String,
    val fraction: Float,
)

@Composable
private fun NutrientRing(ring: RingValue, modifier: Modifier = Modifier) {
    val sweep by animateFloatAsState(
        targetValue = ring.fraction,
        animationSpec = tween(durationMillis = 600),
        label = "ring-${ring.label}",
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val arcColor = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(74.dp)) {
                val stroke = 9.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                if (sweep > 0f) {
                    drawArc(
                        color = arcColor,
                        // Starts at the top and runs clockwise, the direction a dial is read.
                        startAngle = -90f,
                        sweepAngle = 360f * sweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = ring.consumed?.roundToInt()?.toString() ?: "–",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = ring.unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = ring.label,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                // The target belongs next to the value, otherwise the ring is a fraction of
                // something the reader has to remember.
                text = "${ring.target.roundToInt()} ${ring.unit}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

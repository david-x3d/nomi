package com.nomi.app.ui.progress

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nomi.app.domain.calculator.AdaptiveTdeeConfidence
import com.nomi.app.domain.calculator.AdaptiveTdeeEstimate
import com.nomi.app.ui.components.NomiCard
import com.nomi.app.ui.components.NomiShapes
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.theme.nomiProgressMotionSpec
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AdaptiveTdeeCard(
    estimate: AdaptiveTdeeEstimate,
    metric: Boolean,
    onApplyCheckIn: (AdaptiveTdeeEstimate.Ready) -> Unit,
    onDismissCheckIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NomiCard(modifier = modifier, spacing = 16.dp) {
        when (estimate) {
            is AdaptiveTdeeEstimate.InsufficientData -> InsufficientAdaptiveTdeeContent(estimate)
            is AdaptiveTdeeEstimate.Ready -> ReadyAdaptiveTdeeContent(
                estimate = estimate,
                metric = metric,
                onApplyCheckIn = { onApplyCheckIn(estimate) },
                onDismissCheckIn = onDismissCheckIn,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InsufficientAdaptiveTdeeContent(
    estimate: AdaptiveTdeeEstimate.InsufficientData,
) {
    val logProgress by animateFloatAsState(
        targetValue = (estimate.loggedDays.toFloat() / estimate.requiredLoggedDays.toFloat()).coerceIn(0f, 1f),
        animationSpec = nomiProgressMotionSpec(),
        label = "adaptive-log-progress",
    )
    val weightProgress by animateFloatAsState(
        targetValue = (estimate.weighInDays.toFloat() / estimate.requiredWeighInDays.toFloat()).coerceIn(0f, 1f),
        animationSpec = nomiProgressMotionSpec(),
        label = "adaptive-weight-progress",
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = MaterialShapes.Cookie9Sided.toShape(),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Box(modifier = Modifier.padding(11.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.AutoGraph, contentDescription = null)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = nomiString("Adaptive TDEE"),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = nomiString("Learns your real daily burn from food logs and weight trends"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        UnlockProgressBar(
            label = nomiString("Logged days (last 28d)"),
            valueText = "${estimate.loggedDays.coerceAtMost(estimate.requiredLoggedDays)} / ${estimate.requiredLoggedDays}",
            progress = logProgress,
            accent = MaterialTheme.colorScheme.primary,
        )
        UnlockProgressBar(
            label = nomiString("Weigh-ins across 10+ days"),
            valueText = "${estimate.weighInDays.coerceAtMost(estimate.requiredWeighInDays)} / ${estimate.requiredWeighInDays}",
            progress = weightProgress,
            accent = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UnlockProgressBar(
    label: String,
    valueText: String,
    progress: Float,
    accent: androidx.compose.ui.graphics.Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = accent,
            )
        }
        LinearWavyProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReadyAdaptiveTdeeContent(
    estimate: AdaptiveTdeeEstimate.Ready,
    metric: Boolean,
    onApplyCheckIn: () -> Unit,
    onDismissCheckIn: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialShapes.Cookie9Sided.toShape(),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(modifier = Modifier.padding(11.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AutoGraph, contentDescription = null)
                }
            }
            Column {
                Text(
                    text = nomiString("Adaptive TDEE"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = nomiFormat("{0}-day trend · {1} weigh-ins", estimate.windowDays, estimate.weighInDays),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val (pillBg, pillFg, pillText) = when (estimate.confidence) {
            AdaptiveTdeeConfidence.HIGH -> Triple(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
                nomiString("High"),
            )
            AdaptiveTdeeConfidence.MEDIUM -> Triple(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.onSecondaryContainer,
                nomiString("Good"),
            )
            AdaptiveTdeeConfidence.LOW -> Triple(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                MaterialTheme.colorScheme.onSurfaceVariant,
                nomiString("Early"),
            )
        }
        Surface(shape = CircleShape, color = pillBg, contentColor = pillFg) {
            Text(
                text = pillText,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = nomiString("Estimated expenditure"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = nomiFormat("{0} kcal/day", estimate.estimatedTdeeKcal),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        val delta = estimate.tdeeDeltaFromBaselineKcal
        val deltaIcon = when {
            delta > 20 -> Icons.Default.TrendingUp
            delta < -20 -> Icons.Default.TrendingDown
            else -> Icons.Default.TrendingFlat
        }
        val deltaPrefix = if (delta > 0) "+" else ""
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(deltaIcon, contentDescription = null, modifier = Modifier.size(15.dp))
                Text(
                    text = nomiFormat("{0}{1} vs formula", deltaPrefix, delta),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                )
            }
        }
    }

    TdeeDriftGauge(
        deltaKcal = estimate.tdeeDeltaFromBaselineKcal,
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp),
    )

    val weeklyTrendText = formatWeeklyWeightRate(estimate.weeklyWeightChangeKg, metric)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ProgressStat(
            label = nomiString("Avg intake"),
            value = "${estimate.averageIntakeKcal} kcal",
            modifier = Modifier.weight(1f),
        )
        ProgressStat(
            label = nomiString("Formula"),
            value = "${estimate.baselineMaintenanceKcal} kcal",
            modifier = Modifier.weight(1f),
        )
        ProgressStat(
            label = nomiString("Trend / wk"),
            value = weeklyTrendText,
            modifier = Modifier.weight(1f),
        )
    }

    AnimatedVisibility(
        visible = estimate.checkInAvailable,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        WeeklyCheckInBanner(
            estimate = estimate,
            onApply = onApplyCheckIn,
            onDismiss = onDismissCheckIn,
        )
    }

    if (!estimate.checkInAvailable) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = when {
                    estimate.daysUntilNextCheckIn > 0 ->
                        nomiFormat("Target is up to date · next check-in in {0}d", estimate.daysUntilNextCheckIn)
                    abs(estimate.suggestedDeltaKcal) < 20 ->
                        nomiString("Your current calorie target already matches your real expenditure")
                    else ->
                        nomiString("Check-in paused")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeeklyCheckInBanner(
    estimate: AdaptiveTdeeEstimate.Ready,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val deltaPrefix = if (estimate.suggestedDeltaKcal > 0) "+" else ""
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = nomiString("Weekly calorie check-in ready"),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = nomiFormat(
                    "Adjust daily target from {0} to {1} kcal ({2}{3} kcal/day) to stay aligned with your goal.",
                    estimate.currentTargetKcal,
                    estimate.suggestedTargetKcal,
                    deltaPrefix,
                    estimate.suggestedDeltaKcal,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = nomiFormat(
                    "Updated macros: {0}g P · {1}g C · {2}g F",
                    estimate.suggestedMacros.proteinGrams.roundToInt(),
                    estimate.suggestedMacros.carbohydrateGrams.roundToInt(),
                    estimate.suggestedMacros.fatGrams.roundToInt(),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onApply,
                    shape = NomiShapes.Action,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(nomiString("Apply check-in"))
                }
                TextButton(onClick = onDismiss) {
                    Text(nomiString("Keep current"))
                }
            }
        }
    }
}

@Composable
private fun TdeeDriftGauge(
    deltaKcal: Int,
    modifier: Modifier = Modifier,
) {
    // Normalize [-400, +400] kcal into [-1, +1] around center.
    val normalized by animateFloatAsState(
        targetValue = (deltaKcal.toFloat() / 400f).coerceIn(-1f, 1f),
        animationSpec = nomiProgressMotionSpec(),
        label = "tdee-drift-gauge",
    )
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val centerMarkColor = MaterialTheme.colorScheme.outline
    val activeColor = if (deltaKcal >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier) {
        val barHeight = 8.dp.toPx()
        val top = (size.height - barHeight) / 2f
        val radius = CornerRadius(barHeight / 2f, barHeight / 2f)
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, top),
            size = Size(size.width, barHeight),
            cornerRadius = radius,
        )
        val centerX = size.width / 2f
        val endX = centerX + (size.width / 2f) * normalized
        val left = minOf(centerX, endX)
        val width = abs(endX - centerX).coerceAtLeast(barHeight)
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(left, top),
            size = Size(width, barHeight),
            cornerRadius = radius,
        )
        // Formula baseline tick at center
        drawLine(
            color = centerMarkColor,
            start = Offset(centerX, 2.dp.toPx()),
            end = Offset(centerX, size.height - 2.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
        )
    }
}

private fun formatWeeklyWeightRate(kgPerWeek: Double, metric: Boolean): String {
    val value = if (metric) kgPerWeek else kgPerWeek * 2.2046226218
    val unit = if (metric) "kg" else "lb"
    val sign = if (value > 0.02) "+" else ""
    return String.format(Locale.US, "%s%.2f %s", sign, value, unit)
}

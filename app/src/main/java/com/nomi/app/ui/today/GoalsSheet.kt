package com.nomi.app.ui.today

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nomi.app.data.preferences.GoalsCardStyle
import com.nomi.app.ui.components.NomiSheet
import com.nomi.app.ui.components.nomiCardContainerColor
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.profile.localizedName
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The day's goals.
 *
 * Calories carry the headline because they are what the day is actually about; the macros sit
 * below as three equals. The old sheet gave all four the same weight and then repeated the
 * whole thing in a summary pill you had to tap to close - a workaround for an affordance a
 * bottom sheet already has. Swiping it down is the way out now, so nothing has to explain
 * itself.
 */
@Composable
internal fun GoalsSheet(state: TodayUiState, onDismiss: () -> Unit) {
    val sheetColor = lerp(
        MaterialTheme.colorScheme.surfaceContainerLow,
        MaterialTheme.colorScheme.primaryContainer,
        0.10f,
    )
    NomiSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(sheetColor),
        ) {
            GoalsSheetHeader()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.goalsCardStyle == GoalsCardStyle.RINGS) {
                    // One compact card instead of three stacked ones: calories as a bar, every
                    // other target as a ring, water underneath.
                    GoalsRingCard(state)
                } else {
                    CalorieGoalCard(state)
                    MacroGoalCard(state)
                    if (state.micronutrients.isNotEmpty()) {
                        MicronutrientGoalCard(state.micronutrients)
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalsSheetHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Box(modifier = Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(26.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = nomiString("Goals"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = nomiString("Your day at a glance"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CalorieGoalCard(state: TodayUiState) {
    val locale = nomiLocale()
    val difference = state.caloriesDifference.roundToInt()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = nomiCardContainerColor(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = abs(difference).formatted(locale),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    // Over the target is stated, never coloured as a warning. Nomi keeps the
                    // log and has no opinion about the number in it.
                    text = if (difference >= 0) {
                        nomiString("kcal left today")
                    } else {
                        nomiString("kcal over today")
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            GoalWave(fraction = state.calorieFraction, color = MaterialTheme.colorScheme.primary)
            Text(
                text = "${state.caloriesConsumed.roundToInt().formatted(locale)} / " +
                    "${state.calorieTarget.roundToInt().formatted(locale)} kcal",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ActivityTargetBreakdown(state)
            // Movement drawn against the same target as the plate above it, so the two waves can
            // be compared. Always use Nomi’s walking estimate, just like the activity pill.
            state.effectiveBurnedCaloriesKcal?.let { burned ->
                GoalWave(
                    fraction = state.burnedFraction,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    text = if (state.burnedCaloriesAreEstimated) {
                        listOfNotNull(
                            nomiString("Estimated from steps"),
                            estimatedStepCaloriesText(burned, locale),
                            state.steps?.let { nomiFormat("{0} steps", it.formatted(locale)) },
                        ).joinToString(" · ")
                    } else {
                        "${nomiString("Burned")} · " +
                            "${burned.roundToInt().formatted(locale)} kcal"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

            }
        }
    }
}

@Composable
private fun MacroGoalCard(state: TodayUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = nomiCardContainerColor(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            MacroGoalRow(
                label = nomiString("Carbs"),
                progress = state.carbohydrates,
                color = MaterialTheme.colorScheme.error,
            )
            MacroGoalRow(
                label = nomiString("Protein"),
                progress = state.protein,
                color = MaterialTheme.colorScheme.tertiary,
            )
            MacroGoalRow(
                label = nomiString("Fat"),
                progress = state.fat,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

/**
 * The nutrients the user chose to follow, shown the same way the macros are so the day reads as
 * one picture. Fiber is a target to reach; sugar, saturated fat, and sodium are ceilings, and a
 * crossed ceiling is coloured rather than merely full.
 */
@Composable
private fun MicronutrientGoalCard(progress: List<MicronutrientProgress>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = nomiCardContainerColor(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            progress.forEach { entry ->
                MicronutrientGoalRow(entry)
            }
        }
    }
}

@Composable
private fun MicronutrientGoalRow(progress: MicronutrientProgress) {
    val locale = nomiLocale()
    val suffix = progress.nutrient.storageUnit.suffix
    val color = when {
        progress.isOverLimit -> MaterialTheme.colorScheme.error
        progress.nutrient.isLimit -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = progress.nutrient.localizedName(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(progress.consumed ?: 0.0).roundToInt().formatted(locale)} / " +
                    "${progress.target.roundToInt().formatted(locale)} $suffix",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GoalWave(fraction = progress.fraction, color = color)
        if (progress.isPartial) {
            Text(
                text = nomiString("Some of today's foods didn't publish this value, so the real total is higher."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MacroGoalRow(label: String, progress: MacroProgress, color: Color) {
    val locale = nomiLocale()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${progress.consumedGrams.roundToInt().formatted(locale)} / " +
                    "${progress.targetGrams.roundToInt().formatted(locale)} g",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GoalWave(fraction = progress.fraction, color = color)
    }
}

/**
 * Material 3's wavy indicator, filling from empty every time the sheet opens rather than
 * appearing already full, so the day is something you watch arrive at its number.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GoalWave(fraction: Float, color: Color) {
    val filled = remember { Animatable(0f) }
    val progressSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(fraction) {
        filled.animateTo(targetValue = fraction, animationSpec = progressSpec)
    }
    LinearWavyProgressIndicator(
        progress = { filled.value },
        modifier = Modifier.fillMaxWidth(),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}

internal fun Int.formatted(locale: Locale): String = String.format(locale, "%,d", this)

internal fun Long.formatted(locale: Locale): String = String.format(locale, "%,d", this)

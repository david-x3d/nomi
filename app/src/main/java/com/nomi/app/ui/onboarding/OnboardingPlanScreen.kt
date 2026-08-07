package com.nomi.app.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.nomi.app.domain.model.ActivityLevel
import com.nomi.app.domain.model.EnergySex
import com.nomi.app.domain.model.GoalType
import com.nomi.app.domain.model.NutritionPlan
import com.nomi.app.domain.model.ProgressRate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun PlanRevealScreen(
    state: OnboardingUiState,
    actions: OnboardingActions,
    onComplete: () -> Unit,
) {
    val plan = state.finalPlan ?: state.calculatedPlan
    if (plan == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = state.validationMessage ?: "Complete the previous steps to calculate your plan.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    val focusManager = LocalFocusManager.current
    val animatedCalories = remember { Animatable(0f) }
    val calorieAnimation = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val expandAnimation = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val fadeAnimation = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(plan.caloriesKcal) {
        animatedCalories.animateTo(plan.caloriesKcal.toFloat(), calorieAnimation)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
            .testTag("plan_reveal"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column {
                Text(
                    text = "YOUR STARTING PLAN",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "A target built around you",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Start here, watch your real trend, and adjust when your body gives you better information.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("daily_calorie_target"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Restaurant, contentDescription = null)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Daily energy target", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = animatedCalories.value.roundToInt().toString(),
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("kcal / day", style = MaterialTheme.typography.titleMedium)
                    if (plan.isCalorieCustomized || plan.areMacrosCustomized) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Adjusted by you", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MacroCard(
                    label = "Protein",
                    grams = plan.proteinGrams,
                    modifier = Modifier.weight(1f),
                    testTag = "plan_protein",
                )
                MacroCard(
                    label = "Carbs",
                    grams = plan.carbohydrateGrams,
                    modifier = Modifier.weight(1f),
                    testTag = "plan_carbs",
                )
                MacroCard(
                    label = "Fat",
                    grams = plan.fatGrams,
                    modifier = Modifier.weight(1f),
                    testTag = "plan_fat",
                )
            }
        }

        item { TrajectoryCard(plan = plan) }

        if (plan.safetyLimitApplied) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.Info, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("A safer starting point", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "The requested rate would push calories beyond the calculator's safety boundary, so Nomi used a gentler target.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }

        item { SectionLabel("USER INPUT") }
        item {
            DetailCard {
                InputRows(state = state)
            }
        }

        item { SectionLabel("CALCULATED RESULTS") }
        item {
            DetailCard {
                ResultRow("Age today", "${plan.ageYears} years")
                plan.bmrKcal?.let { ResultRow("Resting energy", "${it.roundToInt()} kcal") }
                plan.activityMultiplier?.let { ResultRow("Activity multiplier", "× ${it.oneOrTwoDecimals()}") }
                plan.maintenanceKcal?.let { ResultRow("Estimated maintenance", "${it.roundToInt()} kcal") }
                ResultRow(
                    label = "Goal adjustment",
                    value = signedCalories(plan.goalAdjustmentKcal),
                )
                ResultRow("Rounded target", "${plan.caloriesKcal} kcal/day", emphasized = true)
            }
        }

        item {
            TextButton(
                onClick = actions::toggleCalculationBreakdown,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("calculation_breakdown_toggle"),
            ) {
                Icon(Icons.Outlined.Calculate, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("How was this calculated?")
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = if (state.isCalculationExpanded) {
                        Icons.Outlined.ExpandLess
                    } else {
                        Icons.Outlined.ExpandMore
                    },
                    contentDescription = if (state.isCalculationExpanded) "Collapse" else "Expand",
                )
            }
        }
        item {
            AnimatedVisibility(
                visible = state.isCalculationExpanded,
                enter = expandVertically(animationSpec = expandAnimation) + fadeIn(animationSpec = fadeAnimation),
                exit = shrinkVertically(animationSpec = expandAnimation) + fadeOut(animationSpec = fadeAnimation),
            ) {
                CalculationBreakdown(plan = plan)
            }
        }

        item {
            OutlinedButton(
                onClick = actions::togglePlanEditor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("adjust_plan"),
            ) {
                Icon(Icons.Outlined.Tune, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.isPlanEditorExpanded) "Close adjustments" else "Adjust plan")
            }
        }
        item {
            AnimatedVisibility(
                visible = state.isPlanEditorExpanded,
                enter = expandVertically(animationSpec = expandAnimation) + fadeIn(animationSpec = fadeAnimation),
                exit = shrinkVertically(animationSpec = expandAnimation) + fadeOut(animationSpec = fadeAnimation),
            ) {
                PlanEditor(
                    state = state,
                    actions = actions,
                    onApply = {
                        focusManager.clearFocus()
                        actions.applyPlanEdits()
                    },
                )
            }
        }

        item {
            Button(
                onClick = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("start_tracking"),
            ) {
                Text("Start tracking")
            }
        }
        item {
            Text(
                text = "This estimate is for planning, not medical advice. Adjust based on your logged trend and professional guidance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MacroCard(
    label: String,
    grams: Int,
    modifier: Modifier = Modifier,
    testTag: String,
) {
    Card(
        modifier = modifier.testTag(testTag),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "$grams g", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrajectoryCard(plan: NutritionPlan) {
    val magnitude = abs(plan.expectedWeeklyWeightChangeKg)
    val title = when (plan.goalType) {
        GoalType.LOSE -> "About ${magnitude.oneOrTwoDecimals()} kg down per week"
        GoalType.GAIN -> "About ${magnitude.oneOrTwoDecimals()} kg up per week"
        GoalType.MAINTAIN -> "A steady-weight starting point"
    }
    val detail = plan.estimatedWeeksToGoal?.let { weeks ->
        "Roughly ${weeks.roundToInt().coerceAtLeast(1)} weeks to ${plan.targetWeightKg?.oneDecimal()} kg if the trend holds."
    } ?: "Use your multi-week weight trend to decide whether this target needs a small adjustment."

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.AutoGraph,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                Text(text = detail, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun InputRows(state: OnboardingUiState) {
    val draft = state.draft
    val dateFormatter = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
    }
    ResultRow("Date of birth", draft.dateOfBirth?.format(dateFormatter).orEmpty())
    ResultRow("Energy setup", draft.energySex.energyLabel(draft.customCalorieTarget))
    ResultRow("Height", "${draft.heightCm?.oneDecimal()} cm")
    ResultRow("Starting weight", "${draft.currentWeightKg?.oneDecimal()} kg")
    ResultRow("Goal", draft.goalType.goalLabel())
    draft.targetWeightKg?.let { ResultRow("Target weight", "${it.oneDecimal()} kg") }
    ResultRow("Activity", draft.activityLevel.activityLabel())
    if (draft.goalType != GoalType.MAINTAIN) {
        ResultRow("Chosen pace", draft.progressRate.rateLabel(draft.customWeeklyChangeKg, draft.goalType))
    }
}

@Composable
private fun CalculationBreakdown(plan: NutritionPlan) {
    DetailCard {
        if (plan.isManualTarget) {
            Text(
                text = "You chose a manual calorie target, so the resting-energy and activity equations were skipped.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            ResultRow("Manual energy target", "${plan.caloriesKcal} kcal/day", emphasized = true)
        } else {
            BreakdownStep(
                icon = Icons.Outlined.Calculate,
                title = "1 · Resting energy",
                detail = "Age, sex equation, height, and weight",
                value = "${plan.bmrKcal?.roundToInt()} kcal",
            )
            BreakdownStep(
                icon = Icons.Outlined.Scale,
                title = "2 · Activity",
                detail = "Resting energy × ${plan.activityMultiplier?.oneOrTwoDecimals()}",
                value = "${plan.maintenanceKcal?.roundToInt()} kcal",
            )
            BreakdownStep(
                icon = Icons.Outlined.AutoGraph,
                title = "3 · Goal direction",
                detail = "Requested ${signedCalories(plan.requestedGoalAdjustmentKcal)}; applied ${signedCalories(plan.goalAdjustmentKcal)}",
                value = "${plan.exactCaloriesKcal.roundToInt()} kcal",
            )
            BreakdownStep(
                icon = Icons.Outlined.Restaurant,
                title = "4 · Practical target",
                detail = "Rounded for a usable daily goal",
                value = "${plan.caloriesKcal} kcal",
                last = true,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
        Text(
            text = "Macros are a balanced starting split within the calorie target. You can edit all four targets below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BreakdownStep(
    icon: ImageVector,
    title: String,
    detail: String,
    value: String,
    last: Boolean = false,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
    if (!last) HorizontalDivider(modifier = Modifier.padding(start = 48.dp, top = 12.dp, bottom = 12.dp))
}

@Composable
private fun PlanEditor(
    state: OnboardingUiState,
    actions: OnboardingActions,
    onApply: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Adjust your starting targets", style = MaterialTheme.typography.titleLarge)
            Text(
                "Changes apply immediately to the plan you save. You don't need to restart onboarding.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PlanNumberField(
                value = state.planEditor.calories,
                onValueChange = actions::updatePlanCalories,
                label = "Calories",
                suffix = "kcal",
                testTag = "edit_plan_calories",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlanNumberField(
                    value = state.planEditor.proteinGrams,
                    onValueChange = actions::updatePlanProtein,
                    label = "Protein",
                    suffix = "g",
                    testTag = "edit_plan_protein",
                    modifier = Modifier.weight(1f),
                )
                PlanNumberField(
                    value = state.planEditor.carbohydrateGrams,
                    onValueChange = actions::updatePlanCarbohydrates,
                    label = "Carbs",
                    suffix = "g",
                    testTag = "edit_plan_carbs",
                    modifier = Modifier.weight(1f),
                )
                PlanNumberField(
                    value = state.planEditor.fatGrams,
                    onValueChange = actions::updatePlanFat,
                    label = "Fat",
                    suffix = "g",
                    testTag = "edit_plan_fat",
                    modifier = Modifier.weight(1f),
                )
            }
            if (state.draft.goalType != GoalType.MAINTAIN) {
                PlanNumberField(
                    value = state.planEditor.weeklyChangeKg,
                    onValueChange = actions::updatePlanWeeklyChange,
                    label = "Weekly change",
                    suffix = "kg/week",
                    testTag = "edit_plan_rate",
                    decimal = true,
                )
            }
            state.planEditor.validationMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Button(
                onClick = onApply,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("apply_plan_adjustments"),
            ) {
                Text("Apply changes")
            }
        }
    }
}

@Composable
private fun PlanNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suffix: String,
    testTag: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions.Default,
    )
}

@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun ResultRow(
    label: String,
    value: String,
    emphasized: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

private fun EnergySex?.energyLabel(manualCalories: Int?): String = when (this) {
    EnergySex.FEMALE -> "Female equation"
    EnergySex.MALE -> "Male equation"
    EnergySex.MANUAL -> "Manual · ${manualCalories ?: 0} kcal/day"
    null -> "—"
}

private fun GoalType?.goalLabel(): String = when (this) {
    GoalType.LOSE -> "Lose weight"
    GoalType.MAINTAIN -> "Maintain weight"
    GoalType.GAIN -> "Gain weight"
    null -> "—"
}

private fun ActivityLevel?.activityLabel(): String = when (this) {
    ActivityLevel.SEDENTARY -> "Mostly seated"
    ActivityLevel.LIGHTLY_ACTIVE -> "Lightly active"
    ActivityLevel.ACTIVE -> "Active"
    ActivityLevel.VERY_ACTIVE -> "Very active"
    null -> "—"
}

private fun ProgressRate?.rateLabel(
    custom: Double?,
    goal: GoalType?,
): String = when (this) {
    ProgressRate.GENTLE -> if (goal == GoalType.GAIN) "Gentle gain" else "Gentle"
    ProgressRate.MODERATE -> if (goal == GoalType.GAIN) "Moderate gain" else "Moderate"
    ProgressRate.FASTER -> "Faster"
    ProgressRate.CUSTOM -> "Custom · ${custom?.oneOrTwoDecimals()} kg/week"
    null -> "—"
}

private fun signedCalories(value: Double): String = when {
    value > 0 -> "+${value.roundToInt()} kcal"
    value < 0 -> "${value.roundToInt()} kcal"
    else -> "0 kcal"
}

private fun Double.oneDecimal(): String = String.format(Locale.US, "%.1f", this)

private fun Double.oneOrTwoDecimals(): String = String.format(Locale.US, "%.2f", this)
    .trimEnd('0')
    .trimEnd('.')

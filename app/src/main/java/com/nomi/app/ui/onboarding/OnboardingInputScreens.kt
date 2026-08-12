package com.nomi.app.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.Image
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Female
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.Male
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingFlat
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.nomi.app.R
import com.nomi.app.data.preferences.settingFor
import com.nomi.app.domain.Micronutrient
import com.nomi.app.domain.model.ActivityLevel
import com.nomi.app.domain.model.EnergySex
import com.nomi.app.domain.model.GoalType
import com.nomi.app.domain.model.ProgressRate
import com.nomi.app.ui.components.NomiDatePickerDialog
import com.nomi.app.ui.components.NomiInlineError
import com.nomi.app.ui.components.NomiPickerField
import com.nomi.app.ui.components.NomiSelectionRow
import com.nomi.app.ui.components.NomiShapes
import com.nomi.app.ui.components.NomiTextField
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.theme.NomiTheme
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun WelcomeScreen(onContinue: () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.82f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "welcome_mark_scale",
    )
    LaunchedEffect(Unit) { entered = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier
                .size(248.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .semantics { heading() },
            shape = MaterialTheme.shapes.extraLarge,
            color = androidx.compose.ui.graphics.Color(0xFFFFF8E8),
        ) {
            Image(
                painter = painterResource(R.drawable.nomi_logo),
                contentDescription = nomiString("Nomi, smiling fox logo"),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = 1.55f, scaleY = 1.55f),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = nomiString("Nutrition that starts with you"),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = nomiString(
                "Answer a few questions and we'll create a daily energy and macro plan you can adjust at any time.",
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(44.dp))
        Button(
            onClick = onContinue,
            shape = NomiShapes.Action,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .testTag("onboarding_get_started"),
        ) {
            Text(nomiString("Get started"), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = nomiString("About 2 minutes · You stay in control"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun DateOfBirthScreen(
    dateOfBirth: LocalDate?,
    error: String?,
    onDateSelected: (Long) -> Unit,
    onContinue: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    var showPicker by remember { mutableStateOf(false) }
    val locale = nomiLocale()
    val formatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
    }

    QuestionPage(
        title = nomiString("When were you born?"),
        supportingText = nomiString(
            "We store your date of birth—not a fixed age—so your energy estimate stays accurate over time.",
        ),
        error = error,
        onContinue = onContinue,
    ) {
        NomiPickerField(
            label = nomiString("Date of birth"),
            value = dateOfBirth?.format(formatter),
            placeholder = nomiString("Choose date of birth"),
            onClick = { showPicker = true },
            leadingIcon = Icons.Outlined.CalendarMonth,
            modifier = Modifier.testTag("date_of_birth_picker"),
        )
        dateOfBirth?.let { selected ->
            Text(
                text = nomiFormat("Age {0} today", Period.between(selected, today).years),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateOfBirth?.toUtcMilliseconds(),
            yearRange = (today.year - 120)..today.year,
        )
        NomiDatePickerDialog(
            state = pickerState,
            onDismissRequest = { showPicker = false },
            onConfirm = {
                pickerState.selectedDateMillis?.let { milliseconds ->
                    val selected = Instant.ofEpochMilli(milliseconds)
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate()
                    onDateSelected(selected.toEpochDay())
                }
                showPicker = false
            },
            confirmLabel = nomiString("Save"),
            dismissLabel = nomiString("Cancel"),
            title = nomiString("Date of birth"),
        )
    }
}

@Composable
internal fun EnergySexScreen(
    state: OnboardingUiState,
    actions: OnboardingActions,
) {
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    QuestionPage(
        title = nomiString("Choose an energy calculation"),
        supportingText = nomiString(
            "Metabolic equations use sex-specific averages. Choose the equation that fits you, or set calories yourself.",
        ),
        error = state.validationMessage,
        onContinue = actions::goNext,
    ) {
        SelectionCard(
            title = nomiString("Female"),
            description = nomiString("Use the female energy equation."),
            icon = Icons.Outlined.Female,
            selected = state.draft.energySex == EnergySex.FEMALE,
            onClick = { actions.selectEnergySex(EnergySex.FEMALE) },
            testTag = "energy_female",
        )
        SelectionCard(
            title = nomiString("Male"),
            description = nomiString("Use the male energy equation."),
            icon = Icons.Outlined.Male,
            selected = state.draft.energySex == EnergySex.MALE,
            onClick = { actions.selectEnergySex(EnergySex.MALE) },
            testTag = "energy_male",
        )
        SelectionCard(
            title = nomiString("Set calories manually instead"),
            description = nomiString("Skip the equation and enter a daily calorie target you already use."),
            icon = Icons.Outlined.Edit,
            selected = state.draft.energySex == EnergySex.MANUAL,
            onClick = { actions.selectEnergySex(EnergySex.MANUAL) },
            testTag = "energy_manual",
        )
        AnimatedVisibility(
            visible = state.draft.energySex == EnergySex.MANUAL,
            enter = expandVertically(animationSpec = spatialSpec) + fadeIn(animationSpec = effectsSpec),
            exit = shrinkVertically(animationSpec = spatialSpec) + fadeOut(animationSpec = effectsSpec),
        ) {
            NomiTextField(
                value = state.manualCaloriesText,
                onValueChange = actions::updateManualCalories,
                modifier = Modifier.testTag("manual_calories"),
                label = nomiString("Daily calorie target"),
                suffix = nomiString("kcal/day"),
                supportingText = nomiString("You can fine-tune calories and macros before saving."),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { actions.goNext() }),
            )
        }
    }
}

@Composable
internal fun HeightScreen(state: OnboardingUiState, actions: OnboardingActions) {
    QuestionPage(
        title = nomiString("What's your height?"),
        supportingText = nomiString("Height helps estimate your resting energy needs."),
        error = state.validationMessage,
        onContinue = actions::goNext,
    ) {
        MeasurementSystemPicker(
            selected = state.heightSystem,
            onSelected = actions::selectHeightSystem,
            metricLabel = nomiString("Centimeters"),
            imperialLabel = nomiString("Feet & inches"),
            testTagPrefix = "height_unit",
        )
        if (state.heightSystem == MeasurementSystem.METRIC) {
            DecimalField(
                value = state.heightCentimetersText,
                onValueChange = actions::updateHeightCentimeters,
                label = nomiString("Height"),
                suffix = "cm",
                testTag = "height_cm",
                onDone = actions::goNext,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DecimalField(
                    value = state.heightFeetText,
                    onValueChange = actions::updateHeightFeet,
                    label = nomiString("Feet"),
                    suffix = "ft",
                    testTag = "height_ft",
                    onDone = {},
                    modifier = Modifier.weight(1f),
                    decimal = false,
                )
                DecimalField(
                    value = state.heightInchesText,
                    onValueChange = actions::updateHeightInches,
                    label = nomiString("Inches"),
                    suffix = "in",
                    testTag = "height_in",
                    onDone = actions::goNext,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun WeightScreen(state: OnboardingUiState, actions: OnboardingActions) {
    QuestionPage(
        title = nomiString("What's your current weight?"),
        supportingText = nomiString("Use a recent, typical measurement. Day-to-day changes are normal."),
        error = state.validationMessage,
        onContinue = actions::goNext,
    ) {
        MeasurementSystemPicker(
            selected = state.weightSystem,
            onSelected = actions::selectWeightSystem,
            metricLabel = nomiString("Kilograms"),
            imperialLabel = nomiString("Pounds"),
            testTagPrefix = "weight_unit",
        )
        if (state.weightSystem == MeasurementSystem.METRIC) {
            DecimalField(
                value = state.currentWeightKilogramsText,
                onValueChange = actions::updateCurrentWeightKilograms,
                label = nomiString("Current weight"),
                suffix = "kg",
                testTag = "current_weight_kg",
                onDone = actions::goNext,
            )
        } else {
            DecimalField(
                value = state.currentWeightPoundsText,
                onValueChange = actions::updateCurrentWeightPounds,
                label = nomiString("Current weight"),
                suffix = "lb",
                testTag = "current_weight_lb",
                onDone = actions::goNext,
            )
        }
    }
}

@Composable
internal fun GoalScreen(state: OnboardingUiState, actions: OnboardingActions) {
    QuestionPage(
        title = nomiString("What would you like to do?"),
        supportingText = nomiString("Your choice sets the direction of your daily energy plan."),
        error = state.validationMessage,
        onContinue = actions::goNext,
    ) {
        SelectionCard(
            title = nomiString("Lose weight"),
            description = nomiString("Eat below your estimated maintenance."),
            icon = Icons.Outlined.TrendingDown,
            selected = state.draft.goalType == GoalType.LOSE,
            onClick = { actions.selectGoal(GoalType.LOSE) },
            testTag = "goal_lose",
        )
        SelectionCard(
            title = nomiString("Maintain weight"),
            description = nomiString("Stay around your estimated maintenance."),
            icon = Icons.Outlined.TrendingFlat,
            selected = state.draft.goalType == GoalType.MAINTAIN,
            onClick = { actions.selectGoal(GoalType.MAINTAIN) },
            testTag = "goal_maintain",
        )
        SelectionCard(
            title = nomiString("Gain weight"),
            description = nomiString("Eat above your estimated maintenance."),
            icon = Icons.Outlined.TrendingUp,
            selected = state.draft.goalType == GoalType.GAIN,
            onClick = { actions.selectGoal(GoalType.GAIN) },
            testTag = "goal_gain",
        )
    }
}

@Composable
internal fun TargetWeightScreen(state: OnboardingUiState, actions: OnboardingActions) {
    val losing = state.draft.goalType == GoalType.LOSE
    QuestionPage(
        title = if (losing) {
            nomiString("What's your target weight?")
        } else {
            nomiString("What weight are you aiming for?")
        },
        supportingText = if (losing) {
            nomiString(
                "Choose a target below your current weight. You can update it whenever your goal changes.",
            )
        } else {
            nomiString("Choose a target above your current weight. We'll use it to estimate a trajectory.")
        },
        error = state.validationMessage,
        onContinue = actions::goNext,
    ) {
        if (state.weightSystem == MeasurementSystem.METRIC) {
            DecimalField(
                value = state.targetWeightKilogramsText,
                onValueChange = actions::updateTargetWeightKilograms,
                label = nomiString("Target weight"),
                suffix = "kg",
                testTag = "target_weight_kg",
                onDone = actions::goNext,
            )
        } else {
            DecimalField(
                value = state.targetWeightPoundsText,
                onValueChange = actions::updateTargetWeightPounds,
                label = nomiString("Target weight"),
                suffix = "lb",
                testTag = "target_weight_lb",
                onDone = actions::goNext,
            )
        }
        TextButton(onClick = {
            actions.selectWeightSystem(
                if (state.weightSystem == MeasurementSystem.METRIC) {
                    MeasurementSystem.IMPERIAL
                } else {
                    MeasurementSystem.METRIC
                },
            )
        }) {
            Text(
                if (state.weightSystem == MeasurementSystem.METRIC) {
                    nomiString("Use pounds")
                } else {
                    nomiString("Use kilograms")
                },
            )
        }
    }
}

@Composable
internal fun ActivityScreen(state: OnboardingUiState, actions: OnboardingActions) {
    QuestionPage(
        title = nomiString("How active is a normal week?"),
        supportingText = nomiString(
            "Include both planned exercise and how much you move during work and daily life.",
        ),
        error = state.validationMessage,
        onContinue = actions::goNext,
    ) {
        ActivityOption(
            level = ActivityLevel.SEDENTARY,
            title = nomiString("Mostly seated"),
            description = nomiString("Desk-based days with little planned exercise."),
            icon = Icons.Outlined.SelfImprovement,
            state = state,
            actions = actions,
        )
        ActivityOption(
            level = ActivityLevel.LIGHTLY_ACTIVE,
            title = nomiString("Lightly active"),
            description = nomiString("Light exercise 1–3 days a week or regular casual walking."),
            icon = Icons.Outlined.DirectionsWalk,
            state = state,
            actions = actions,
        )
        ActivityOption(
            level = ActivityLevel.ACTIVE,
            title = nomiString("Active"),
            description = nomiString("Moderate exercise 3–5 days a week or frequent movement through the day."),
            icon = Icons.Outlined.DirectionsRun,
            state = state,
            actions = actions,
        )
        ActivityOption(
            level = ActivityLevel.VERY_ACTIVE,
            title = nomiString("Very active"),
            description = nomiString("Hard training most days, a physical job, or both."),
            icon = Icons.Outlined.FitnessCenter,
            state = state,
            actions = actions,
        )
    }
}

@Composable
private fun ActivityOption(
    level: ActivityLevel,
    title: String,
    description: String,
    icon: ImageVector,
    state: OnboardingUiState,
    actions: OnboardingActions,
) {
    SelectionCard(
        title = title,
        description = description,
        icon = icon,
        selected = state.draft.activityLevel == level,
        onClick = { actions.selectActivity(level) },
        testTag = "activity_${level.name.lowercase()}",
    )
}

@Composable
internal fun ProgressRateScreen(state: OnboardingUiState, actions: OnboardingActions) {
    val gaining = state.draft.goalType == GoalType.GAIN
    val options = if (gaining) {
        listOf(
            RateOption(ProgressRate.GENTLE, nomiString("Gentle gain"), nomiString("About 0.15 kg per week")),
            RateOption(ProgressRate.MODERATE, nomiString("Moderate gain"), nomiString("About 0.25 kg per week")),
            RateOption(ProgressRate.CUSTOM, nomiString("Custom"), nomiString("Choose your own weekly change")),
        )
    } else {
        listOf(
            RateOption(ProgressRate.GENTLE, nomiString("Gentle"), nomiString("About 0.25 kg per week")),
            RateOption(ProgressRate.MODERATE, nomiString("Moderate"), nomiString("About 0.5 kg per week")),
            RateOption(ProgressRate.FASTER, nomiString("Faster"), nomiString("About 0.75 kg per week")),
            RateOption(ProgressRate.CUSTOM, nomiString("Custom"), nomiString("Choose your own weekly change")),
        )
    }
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    QuestionPage(
        title = if (gaining) nomiString("Choose a gain rate") else nomiString("Choose a loss rate"),
        supportingText = nomiString(
            "This is a planning estimate, not a promise. Real progress varies and you can adjust the plan anytime.",
        ),
        error = state.validationMessage,
        onContinue = actions::goNext,
    ) {
        options.forEach { option ->
            SelectionCard(
                title = option.title,
                description = option.description,
                icon = if (gaining) Icons.Outlined.TrendingUp else Icons.Outlined.TrendingDown,
                selected = state.draft.progressRate == option.rate,
                onClick = { actions.selectProgressRate(option.rate) },
                testTag = "rate_${option.rate.name.lowercase()}",
            )
        }
        AnimatedVisibility(
            visible = state.draft.progressRate == ProgressRate.CUSTOM,
            enter = expandVertically(animationSpec = spatialSpec) + fadeIn(animationSpec = effectsSpec),
            exit = shrinkVertically(animationSpec = spatialSpec) + fadeOut(animationSpec = effectsSpec),
        ) {
            DecimalField(
                value = state.customWeeklyChangeText,
                onValueChange = actions::updateCustomWeeklyChange,
                label = nomiString("Weekly change"),
                suffix = nomiString("kg/week"),
                testTag = "custom_weekly_change",
                onDone = actions::goNext,
            )
        }
    }
}

private data class RateOption(
    val rate: ProgressRate,
    val title: String,
    val description: String,
)

/**
 * Makes the micronutrient capability visible before the plan reveal.
 *
 * Nothing is preselected. The point of the step is that a new user learns Nomi can do this at
 * all - the most common reason people bounce off a tracker is assuming a feature is missing -
 * and the wording says plainly that skipping costs nothing.
 */
@Composable
internal fun MicronutrientsScreen(state: OnboardingUiState, actions: OnboardingActions) {
    QuestionPage(
        title = nomiString("Want to track anything beyond calories?"),
        supportingText = nomiString(
            "Nomi records these for every food you log. Turn on the ones you care about and they " +
                "get their own daily goal, or skip this - you can change it any time in Settings.",
        ),
        error = state.validationMessage,
        onContinue = actions::goNext,
    ) {
        MicronutrientOption(
            micronutrient = Micronutrient.FIBER,
            title = nomiString("Fiber"),
            description = nomiString("Most people get less than half the 30 g a day that's recommended."),
            icon = Icons.Outlined.Grass,
            state = state,
            actions = actions,
        )
        MicronutrientOption(
            micronutrient = Micronutrient.SUGAR,
            title = nomiString("Sugar"),
            description = nomiString("Drinks are where a day's 25 g adds up fastest, usually unnoticed."),
            icon = Icons.Outlined.Cookie,
            state = state,
            actions = actions,
        )
        MicronutrientOption(
            micronutrient = Micronutrient.SATURATED_FAT,
            title = nomiString("Saturated fat"),
            description = nomiString("About 20 g a day, roughly a tenth of a 2,000 kcal day."),
            icon = Icons.Outlined.WaterDrop,
            state = state,
            actions = actions,
        )
        MicronutrientOption(
            micronutrient = Micronutrient.SODIUM,
            title = nomiString("Sodium"),
            description = nomiString("Under 2,000 mg a day, which is about 5 g of salt."),
            icon = Icons.Outlined.Grain,
            state = state,
            actions = actions,
        )
    }
}

@Composable
private fun MicronutrientOption(
    micronutrient: Micronutrient,
    title: String,
    description: String,
    icon: ImageVector,
    state: OnboardingUiState,
    actions: OnboardingActions,
) {
    val enabled = state.micronutrients.settingFor(micronutrient).enabled
    SelectionCard(
        title = title,
        description = description,
        icon = icon,
        selected = enabled,
        onClick = { actions.toggleMicronutrient(micronutrient, !enabled) },
        testTag = "onboarding_micronutrient_${micronutrient.name.lowercase()}",
    )
}

@Composable
private fun QuestionPage(
    title: String,
    supportingText: String,
    error: String?,
    onContinue: () -> Unit,
    content: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
        }
        item {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { Spacer(Modifier.height(4.dp)) }
        item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() } }
        error?.let { message ->
            item {
                NomiInlineError(
                    message = nomiString(message),
                    modifier = Modifier
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("onboarding_error"),
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
        item {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    onContinue()
                },
                shape = NomiShapes.Action,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .testTag("onboarding_continue"),
            ) {
                Text(nomiString("Continue"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Every onboarding question offers the same kind of answer, so they all look the same. */
@Composable
private fun SelectionCard(
    title: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    NomiSelectionRow(
        title = title,
        description = description,
        icon = icon,
        selected = selected,
        onClick = onClick,
        modifier = Modifier.testTag(testTag),
    )
}

@Composable
private fun MeasurementSystemPicker(
    selected: MeasurementSystem,
    onSelected: (MeasurementSystem) -> Unit,
    metricLabel: String,
    imperialLabel: String,
    testTagPrefix: String,
) {
    val items = listOf(
        MeasurementSystem.METRIC to metricLabel,
        MeasurementSystem.IMPERIAL to imperialLabel,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().height(56.dp)) {
        items.forEachIndexed { index, item ->
            SegmentedButton(
                selected = selected == item.first,
                onClick = { onSelected(item.first) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = items.size),
                modifier = Modifier.testTag("${testTagPrefix}_${item.first.name.lowercase()}"),
                label = { Text(item.second) },
            )
        }
    }
}

@Composable
private fun DecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suffix: String,
    testTag: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = true,
) {
    NomiTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.testTag(testTag),
        label = label,
        suffix = suffix,
        leadingIcon = Icons.Outlined.MonitorWeight,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}

private fun LocalDate.toUtcMilliseconds(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WelcomeScreenPreview() {
    NomiTheme(dynamicColor = false) { WelcomeScreen(onContinue = {}) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DateOfBirthScreenPreview() {
    NomiTheme(dynamicColor = false) {
        DateOfBirthScreen(
            dateOfBirth = LocalDate.of(1994, 4, 18),
            error = null,
            onDateSelected = {},
            onContinue = {},
        )
    }
}

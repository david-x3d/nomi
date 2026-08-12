package com.nomi.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nomi.app.data.local.entity.UserProfileEntity
import com.nomi.app.domain.ActivityLevel
import com.nomi.app.domain.EnergySex
import com.nomi.app.domain.GoalType
import com.nomi.app.domain.ProgressRate
import com.nomi.app.ui.components.NomiDatePickerDialog
import com.nomi.app.ui.components.NomiInlineError
import com.nomi.app.ui.components.NomiTextField
import com.nomi.app.ui.components.nomiCardBorder
import com.nomi.app.ui.components.nomiCardElevation
import com.nomi.app.ui.components.nomiCardShape
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.localization.nomiString
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

@Composable
fun ProfileSettingsScreen(
    profile: UserProfileEntity,
    onBack: () -> Unit,
    onSave: (ProfileEdit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = nomiLocale()
    var dateOfBirth by rememberSaveable(profile.updatedAtEpochMillis) {
        mutableStateOf(profile.dateOfBirth)
    }
    var energySex by rememberSaveable(profile.updatedAtEpochMillis) {
        mutableStateOf(profile.energyCalculationSex.orEmpty())
    }
    var heightText by rememberSaveable(profile.updatedAtEpochMillis) {
        mutableStateOf(profile.heightCm?.displayNumber(locale).orEmpty())
    }
    var goalType by rememberSaveable(profile.updatedAtEpochMillis) {
        mutableStateOf(profile.goalType)
    }
    var targetWeightText by rememberSaveable(profile.updatedAtEpochMillis) {
        mutableStateOf(profile.targetWeightKg?.displayNumber(locale).orEmpty())
    }
    var activityLevel by rememberSaveable(profile.updatedAtEpochMillis) {
        mutableStateOf(profile.activityLevel)
    }
    var progressionRate by rememberSaveable(profile.updatedAtEpochMillis) {
        mutableStateOf(profile.progressionRate.orEmpty())
    }
    var keepCustomTargets by rememberSaveable(profile.updatedAtEpochMillis) { mutableStateOf(true) }
    var submitted by rememberSaveable(profile.updatedAtEpochMillis) { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val today = remember { LocalDate.now() }
    val focusManager = LocalFocusManager.current
    val heightFocusRequester = remember { FocusRequester() }
    val targetFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val goal = enumValueOrNull<GoalType>(goalType)
    val edit = ProfileEdit(
        dateOfBirth = dateOfBirth.trim(),
        energyCalculationSex = energySex,
        heightCm = heightText.toDecimalOrNull(),
        goalType = goalType,
        targetWeightKg = if (goal == GoalType.MAINTAIN) null else targetWeightText.toDecimalOrNull(),
        activityLevel = activityLevel,
        progressionRate = if (goal == GoalType.MAINTAIN) null else progressionRate.ifBlank { null },
        keepCustomTargets = keepCustomTargets,
    )
    val validation = edit.validate(currentWeightKg = profile.startingWeightKg, today = today)
    val dateOfBirthError = localizeProfileError(validation.dateOfBirthError)
    val energySexError = localizeProfileError(validation.energySexError)
    val heightError = localizeProfileError(validation.heightError)
    val goalError = localizeProfileError(validation.goalError)
    val targetWeightError = localizeProfileError(validation.targetWeightError)
    val activityError = localizeProfileError(validation.activityError)
    val progressionRateError = localizeProfileError(validation.progressionRateError)

    SettingsEditorScaffold(
        title = nomiString("Profile & goals"),
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(innerPadding)
                .navigationBarsPadding()
                .imePadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 20.dp,
                vertical = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = nomiString("Your calculation inputs"),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = nomiFormat(
                            "Current weight stays at {0} kg. Record a new weight from Progress instead.",
                            profile.startingWeightKg.displayNumber(locale),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SettingsNoticeCard(
                    title = nomiString("Saving creates a new plan"),
                    message = nomiString("Nomi recalculates from the updated profile and starts a new plan version. Previous days keep the targets they originally used."),
                )
            }
            item {
                NomiTextField(
                    value = dateOfBirth,
                    onValueChange = { dateOfBirth = it.take(10) },
                    modifier = Modifier
                        .semantics {
                            if (submitted) dateOfBirthError?.let { error(it) }
                        }
                        .testTag("profile_date_of_birth"),
                    label = nomiString("Date of birth"),
                    placeholder = nomiString("YYYY-MM-DD"),
                    supportingText = dateOfBirthError.takeIf { submitted } ?: nomiString("Stored as a date so your age updates automatically."),
                    isError = submitted && dateOfBirthError != null,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(
                                Icons.Outlined.CalendarMonth,
                                contentDescription = nomiString("Choose date"),
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { heightFocusRequester.requestFocus() },
                    ),
                )
            }
            item {
                StringSelector(
                    label = nomiString("Energy calculation"),
                    selectedValue = energySex,
                    options = listOf(
                        EnergySex.FEMALE.name to nomiString("Female equation"),
                        EnergySex.MALE.name to nomiString("Male equation"),
                        EnergySex.MANUAL.name to nomiString("Manual energy target"),
                    ),
                    onSelected = { energySex = it },
                    errorMessage = energySexError.takeIf { submitted },
                    testTag = "profile_energy_sex",
                )
            }
            item {
                NomiTextField(
                    value = heightText,
                    onValueChange = { heightText = it.decimalInput(maxIntegerDigits = 3) },
                    modifier = Modifier
                        .focusRequester(heightFocusRequester)
                        .semantics {
                            if (submitted) heightError?.let { error(it) }
                        }
                        .testTag("profile_height_cm"),
                    label = nomiString("Height"),
                    suffix = "cm",
                    supportingText = heightError.takeIf { submitted },
                    isError = submitted && heightError != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(nomiString("Goal"), style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val goals = listOf(GoalType.LOSE, GoalType.MAINTAIN, GoalType.GAIN)
                        goals.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = goal == option,
                                onClick = {
                                    goalType = option.name
                                    if (option == GoalType.GAIN && progressionRate == ProgressRate.FASTER.name) {
                                        progressionRate = ProgressRate.MODERATE.name
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, goals.size),
                                modifier = Modifier.testTag("profile_goal_${option.name.lowercase()}"),
                                label = { Text(option.toGoalLabel()) },
                            )
                        }
                    }
                    goalError.takeIf { submitted }?.let { message ->
                        FieldError(message)
                    }
                }
            }
            if (goal != GoalType.MAINTAIN) {
                item {
                    NomiTextField(
                        value = targetWeightText,
                        onValueChange = { targetWeightText = it.decimalInput(maxIntegerDigits = 3) },
                        modifier = Modifier
                            .focusRequester(targetFocusRequester)
                            .semantics {
                                if (submitted) targetWeightError?.let { error(it) }
                            }
                            .testTag("profile_target_weight_kg"),
                        label = nomiString("Target weight"),
                        suffix = "kg",
                        supportingText = targetWeightError.takeIf { submitted },
                        isError = submitted && targetWeightError != null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    )
                }
            }
            item {
                StringSelector(
                    label = nomiString("Activity level"),
                    selectedValue = activityLevel,
                    options = listOf(
                        ActivityLevel.SEDENTARY.name to nomiString("Mostly seated"),
                        ActivityLevel.LIGHTLY_ACTIVE.name to nomiString("Lightly active"),
                        ActivityLevel.ACTIVE.name to nomiString("Active"),
                        ActivityLevel.VERY_ACTIVE.name to nomiString("Very active"),
                    ),
                    onSelected = { activityLevel = it },
                    errorMessage = activityError.takeIf { submitted },
                    testTag = "profile_activity_level",
                )
            }
            if (goal != GoalType.MAINTAIN) {
                item {
                    val rateOptions = if (goal == GoalType.GAIN) {
                        listOf(
                            ProgressRate.GENTLE.name to nomiString("Gentle gain"),
                            ProgressRate.MODERATE.name to nomiString("Moderate gain"),
                        )
                    } else {
                        listOf(
                            ProgressRate.GENTLE.name to nomiString("Gentle"),
                            ProgressRate.MODERATE.name to nomiString("Moderate"),
                            ProgressRate.FASTER.name to nomiString("Faster"),
                        )
                    }
                    StringSelector(
                        label = nomiString("Progression rate"),
                        selectedValue = progressionRate,
                        options = rateOptions,
                        onSelected = { progressionRate = it },
                        errorMessage = progressionRateError.takeIf { submitted },
                        testTag = "profile_progression_rate",
                    )
                }
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = keepCustomTargets,
                            role = Role.Switch,
                            onValueChange = { keepCustomTargets = it },
                        )
                        .testTag("profile_keep_custom_targets"),
                    shape = nomiCardShape(),
                    elevation = nomiCardElevation(),
                    border = nomiCardBorder(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                nomiString("Keep custom targets, if set"),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                nomiString("Carry your custom calorie and macro targets into the new plan instead of replacing them with calculated targets."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = keepCustomTargets, onCheckedChange = null)
                    }
                }
            }
            if (submitted && !validation.isValid) {
                item {
                    Card(
                        shape = nomiCardShape(),
                        elevation = nomiCardElevation(),
                        border = nomiCardBorder(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Assertive }
                            .testTag("profile_validation_summary"),
                    ) {
                        Text(
                            text = localizeProfileError(validation.firstError)
                                ?: nomiString("Check the highlighted fields."),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        submitted = true
                        focusManager.clearFocus()
                        if (validation.isValid) onSave(edit)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("profile_save"),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(nomiString("Save and create new plan"))
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showDatePicker) {
        val initialDate = runCatching { LocalDate.parse(dateOfBirth.trim()) }.getOrNull()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate?.toUtcMilliseconds(),
            yearRange = (today.year - 120)..today.year,
        )
        NomiDatePickerDialog(
            state = datePickerState,
            onDismissRequest = { showDatePicker = false },
            onConfirm = {
                datePickerState.selectedDateMillis?.let { milliseconds ->
                    dateOfBirth = Instant.ofEpochMilli(milliseconds)
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate()
                        .toString()
                }
                showDatePicker = false
            },
            confirmLabel = nomiString("Save"),
            dismissLabel = nomiString("Cancel"),
            title = nomiString("Date of birth"),
        )
    }
}

@Composable
private fun FieldError(message: String) {
    NomiInlineError(
        message = message,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
    enumValues<T>().firstOrNull { it.name == value.trim().uppercase() }

@Composable
private fun GoalType.toGoalLabel(): String = when (this) {
    GoalType.LOSE -> nomiString("Lose")
    GoalType.MAINTAIN -> nomiString("Maintain")
    GoalType.GAIN -> nomiString("Gain")
}

private fun String.decimalInput(maxIntegerDigits: Int): String {
    val normalized = replace(',', '.')
    val beforeDecimal = normalized.substringBefore('.').filter(Char::isDigit).take(maxIntegerDigits)
    val hasDecimal = normalized.contains('.')
    val afterDecimal = normalized.substringAfter('.', "").filter(Char::isDigit).take(2)
    return buildString {
        append(beforeDecimal)
        if (hasDecimal) {
            if (isEmpty()) append('0')
            append('.')
            append(afterDecimal)
        }
    }
}

private fun String.toDecimalOrNull(): Double? = replace(',', '.').toDoubleOrNull()

private fun Double.displayNumber(locale: Locale): String =
    String.format(locale, "%.1f", this).removeSuffix(".0").removeSuffix(",0")

/**
 * The view model reports validation failures as English sentences, which are exactly the
 * catalogue keys, so every message translates through one lookup. An unrecognized message falls
 * back to itself rather than disappearing.
 */
@Composable
private fun localizeProfileError(message: String?): String? =
    message?.let { nomiString(it) }

private fun LocalDate.toUtcMilliseconds(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

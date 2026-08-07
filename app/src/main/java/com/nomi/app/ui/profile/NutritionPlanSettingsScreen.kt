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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nomi.app.data.local.entity.NutritionPlanEntity
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.localization.nomiString
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun NutritionPlanSettingsScreen(
    plan: NutritionPlanEntity,
    onBack: () -> Unit,
    onSave: (calories: Int, protein: Int, carbs: Int, fat: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = nomiLocale()
    var caloriesText by rememberSaveable(plan.id, plan.version, plan.createdAtEpochMillis) {
        mutableStateOf(plan.calorieTargetKcal.roundToInt().toString())
    }
    var proteinText by rememberSaveable(plan.id, plan.version, plan.createdAtEpochMillis) {
        mutableStateOf(plan.proteinTargetGrams.roundToInt().toString())
    }
    var carbsText by rememberSaveable(plan.id, plan.version, plan.createdAtEpochMillis) {
        mutableStateOf(plan.carbohydrateTargetGrams.roundToInt().toString())
    }
    var fatText by rememberSaveable(plan.id, plan.version, plan.createdAtEpochMillis) {
        mutableStateOf(plan.fatTargetGrams.roundToInt().toString())
    }
    var submitted by rememberSaveable(plan.id, plan.version, plan.createdAtEpochMillis) {
        mutableStateOf(false)
    }

    val calories = caloriesText.toIntOrNull()
    val protein = proteinText.toIntOrNull()
    val carbs = carbsText.toIntOrNull()
    val fat = fatText.toIntOrNull()
    val validation = NutritionTargetValidation(
        calorieError = when {
            calories == null -> nomiString("Enter a calorie target.", "Gib ein Kalorienziel ein.")
            calories !in 800..6000 -> nomiString(
                "Calories must be between 800 and 6,000 kcal.",
                "Das Kalorienziel muss zwischen 800 und 6.000 kcal liegen.",
            )
            else -> null
        },
        proteinError = when {
            protein == null -> nomiString("Enter a protein target.", "Gib ein Eiweißziel ein.")
            protein !in 0..600 -> nomiString(
                "Protein must be between 0 and 600 g.",
                "Das Eiweißziel muss zwischen 0 und 600 g liegen.",
            )
            else -> null
        },
        carbsError = when {
            carbs == null -> nomiString("Enter a carbohydrate target.", "Gib ein Kohlenhydratziel ein.")
            carbs !in 0..900 -> nomiString(
                "Carbohydrates must be between 0 and 900 g.",
                "Das Kohlenhydratziel muss zwischen 0 und 900 g liegen.",
            )
            else -> null
        },
        fatError = when {
            fat == null -> nomiString("Enter a fat target.", "Gib ein Fettziel ein.")
            fat !in 0..300 -> nomiString(
                "Fat must be between 0 and 300 g.",
                "Das Fettziel muss zwischen 0 und 300 g liegen.",
            )
            else -> null
        },
    )
    val focusManager = LocalFocusManager.current
    val proteinFocus = androidx.compose.runtime.remember { FocusRequester() }
    val carbsFocus = androidx.compose.runtime.remember { FocusRequester() }
    val fatFocus = androidx.compose.runtime.remember { FocusRequester() }
    val hasExistingCustomTargets = plan.calorieTargetCustom || plan.proteinTargetCustom ||
        plan.carbohydrateTargetCustom || plan.fatTargetCustom
    val effectiveDate = runCatching {
        LocalDate.parse(plan.effectiveFromLocalDate).format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale),
        )
    }.getOrDefault(plan.effectiveFromLocalDate)
    val calculationMethodLabel = when (plan.calculationMethod.lowercase()) {
        "manual" -> nomiString("Manual", "Manuell")
        "mifflin_st_jeor" -> "Mifflin-St Jeor"
        else -> plan.calculationMethod.toReadableLabel()
    }

    fun saveIfValid() {
        submitted = true
        focusManager.clearFocus()
        if (validation.isValid) {
            onSave(
                requireNotNull(calories),
                requireNotNull(protein),
                requireNotNull(carbs),
                requireNotNull(fat),
            )
        }
    }

    SettingsEditorScaffold(
        title = nomiString("Nutrition targets", "Ernährungsziele"),
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
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
                        text = nomiString("Set your daily targets", "Lege deine Tagesziele fest"),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = nomiString(
                            "These values become the goals used by Today, History, and progress summaries.",
                            "Diese Werte werden als Ziele für Heute, Verlauf und Fortschrittsübersichten verwendet.",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SettingsNoticeCard(
                    title = nomiString(
                        "A new target version starts when you save",
                        "Beim Speichern beginnt eine neue Zielversion",
                    ),
                    message = nomiString(
                        "Earlier days keep their original targets, so historical comparisons remain meaningful. All values saved here are marked as custom.",
                        "Frühere Tage behalten ihre ursprünglichen Ziele, damit historische Vergleiche aussagekräftig bleiben. Alle hier gespeicherten Werte werden als benutzerdefiniert markiert.",
                    ),
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Outlined.Tune, contentDescription = null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                nomiString("Plan version ${plan.version}", "Planversion ${plan.version}"),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                nomiString(
                                    "Effective $effectiveDate · $calculationMethodLabel",
                                    "Gültig ab $effectiveDate · $calculationMethodLabel",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (hasExistingCustomTargets) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ) {
                            Text(
                                text = if (hasExistingCustomTargets) {
                                    nomiString("Custom", "Benutzerdefiniert")
                                } else {
                                    nomiString("Calculated", "Berechnet")
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
            item {
                TargetTextField(
                    label = nomiString("Calories", "Kalorien"),
                    value = caloriesText,
                    onValueChanged = { caloriesText = it.digitsOnly(4) },
                    suffix = nomiString("kcal/day", "kcal/Tag"),
                    errorMessage = validation.calorieError.takeIf { submitted },
                    testTag = "plan_target_calories",
                    imeAction = ImeAction.Next,
                    onImeAction = { proteinFocus.requestFocus() },
                )
            }
            item {
                Text(
                    text = nomiString("Macronutrients", "Makronährstoffe"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
            }
            item {
                TargetTextField(
                    label = nomiString("Protein", "Eiweiß"),
                    value = proteinText,
                    onValueChanged = { proteinText = it.digitsOnly(3) },
                    suffix = nomiString("g/day", "g/Tag"),
                    errorMessage = validation.proteinError.takeIf { submitted },
                    testTag = "plan_target_protein",
                    imeAction = ImeAction.Next,
                    onImeAction = { carbsFocus.requestFocus() },
                    modifier = Modifier.focusRequester(proteinFocus),
                )
            }
            item {
                TargetTextField(
                    label = nomiString("Carbohydrates", "Kohlenhydrate"),
                    value = carbsText,
                    onValueChanged = { carbsText = it.digitsOnly(3) },
                    suffix = nomiString("g/day", "g/Tag"),
                    errorMessage = validation.carbsError.takeIf { submitted },
                    testTag = "plan_target_carbs",
                    imeAction = ImeAction.Next,
                    onImeAction = { fatFocus.requestFocus() },
                    modifier = Modifier.focusRequester(carbsFocus),
                )
            }
            item {
                TargetTextField(
                    label = nomiString("Fat", "Fett"),
                    value = fatText,
                    onValueChanged = { fatText = it.digitsOnly(3) },
                    suffix = nomiString("g/day", "g/Tag"),
                    errorMessage = validation.fatError.takeIf { submitted },
                    testTag = "plan_target_fat",
                    imeAction = ImeAction.Done,
                    onImeAction = ::saveIfValid,
                    modifier = Modifier.focusRequester(fatFocus),
                )
            }
            if (protein != null && carbs != null && fat != null && calories != null) {
                item {
                    val macroCalories = protein * 4 + carbs * 4 + fat * 9
                    val difference = macroCalories - calories
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    nomiString("Energy represented by macros", "Durch Makros abgedeckte Energie"),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    nomiString(
                                        "Protein and carbs use 4 kcal/g; fat uses 9 kcal/g.",
                                        "Eiweiß und Kohlenhydrate liefern 4 kcal/g, Fett liefert 9 kcal/g.",
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "$macroCalories kcal",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (abs(difference) > calories * 0.15) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
            if (submitted && !validation.isValid) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Assertive }
                            .testTag("plan_target_validation_summary"),
                    ) {
                        Text(
                            text = validation.firstError
                                ?: nomiString("Check the highlighted targets.", "Prüfe die markierten Ziele."),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = ::saveIfValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("plan_targets_save"),
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(nomiString("Save custom targets", "Benutzerdefinierte Ziele speichern"))
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TargetTextField(
    label: String,
    value: String,
    onValueChanged: (String) -> Unit,
    suffix: String,
    errorMessage: String?,
    testTag: String,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        modifier = modifier
            .fillMaxWidth()
            .semantics { errorMessage?.let { error(it) } }
            .testTag(testTag),
        label = { Text(label) },
        suffix = { Text(suffix) },
        supportingText = errorMessage?.let { message -> { Text(message) } },
        isError = errorMessage != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = imeAction,
        ),
        keyboardActions = if (imeAction == ImeAction.Done) {
            KeyboardActions(onDone = { onImeAction() })
        } else {
            KeyboardActions(onNext = { onImeAction() })
        },
    )
}

private data class NutritionTargetValidation(
    val calorieError: String?,
    val proteinError: String?,
    val carbsError: String?,
    val fatError: String?,
) {
    val isValid: Boolean
        get() = calorieError == null && proteinError == null && carbsError == null && fatError == null

    val firstError: String?
        get() = listOfNotNull(calorieError, proteinError, carbsError, fatError).firstOrNull()
}

private fun String.digitsOnly(maxDigits: Int): String = filter(Char::isDigit).take(maxDigits)

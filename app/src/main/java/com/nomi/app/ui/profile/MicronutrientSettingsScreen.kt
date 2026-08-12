package com.nomi.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.nomi.app.data.preferences.MicronutrientPreferences
import com.nomi.app.data.preferences.MicronutrientSetting
import com.nomi.app.data.preferences.resolvedTarget
import com.nomi.app.data.preferences.settingFor
import com.nomi.app.data.preferences.with
import com.nomi.app.domain.Micronutrient
import com.nomi.app.ui.components.NomiTextField
import com.nomi.app.ui.components.nomiCardBorder
import com.nomi.app.ui.components.nomiCardElevation
import com.nomi.app.ui.components.nomiCardShape
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiString

/**
 * Turns individual nutrients on and sets what a day of each should look like.
 *
 * Every value shown here is already being stored for each logged food, so switching one on
 * reveals history rather than starting a new record. Targets are per nutrient because the
 * healthy direction is not the same for all of them: fiber is a floor to reach, while sugar,
 * saturated fat, and sodium are ceilings to stay under.
 */
@Composable
fun MicronutrientSettingsScreen(
    preferences: MicronutrientPreferences,
    onBack: () -> Unit,
    onSave: (MicronutrientPreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = remember(preferences) {
        mutableStateMapOf<Micronutrient, Boolean>().apply {
            Micronutrient.entries.forEach { put(it, preferences.settingFor(it).enabled) }
        }
    }
    val targets = remember(preferences) {
        mutableStateMapOf<Micronutrient, String>().apply {
            Micronutrient.entries.forEach { nutrient ->
                val setting = preferences.settingFor(nutrient)
                put(nutrient, setting.resolvedTarget(nutrient).targetText())
            }
        }
    }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val missingTarget = nomiString("Enter a daily target.")
    val outOfRangeTarget = nomiString("That target is outside the supported range.")
    val errors = Micronutrient.entries.associateWith { nutrient ->
        if (enabled[nutrient] != true) return@associateWith null
        val value = targets[nutrient]?.toDoubleOrNull()
        when {
            value == null -> missingTarget
            !value.isFinite() || value <= 0.0 || value > nutrient.maximumTarget -> outOfRangeTarget
            else -> null
        }
    }
    val isValid = errors.values.all { it == null }

    fun saveIfValid() {
        submitted = true
        if (!isValid) return
        val updated = Micronutrient.entries.fold(preferences) { accumulated, nutrient ->
            val isOn = enabled[nutrient] == true
            accumulated.with(
                nutrient,
                MicronutrientSetting(
                    enabled = isOn,
                    // A nutrient that is off keeps whatever target it had, so switching it back
                    // on does not silently reset a number the user chose earlier.
                    dailyTarget = targets[nutrient]?.toDoubleOrNull()
                        ?: preferences.settingFor(nutrient).resolvedTarget(nutrient),
                ),
            )
        }
        onSave(updated)
    }

    SettingsEditorScaffold(
        title = nomiString("Micronutrients"),
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .navigationBarsPadding()
                .imePadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = nomiString("Track more than macros"),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = nomiString("Turn on only what you care about. Each one you enable appears on Today with its own daily goal."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SettingsNoticeCard(
                    title = nomiString("Your existing days count too"),
                    message = nomiString("Nomi has been storing these values alongside every food you logged, so a nutrient you enable today already has history behind it. Foods whose source never published a value are left out of the total rather than counted as zero."),
                )
            }
            for (nutrient in Micronutrient.entries) {
                item(key = nutrient.name) {
                    MicronutrientCard(
                        nutrient = nutrient,
                        enabled = enabled[nutrient] == true,
                        onEnabledChanged = { enabled[nutrient] = it },
                        target = targets[nutrient].orEmpty(),
                        onTargetChanged = { targets[nutrient] = it.decimalOnly() },
                        errorMessage = errors[nutrient].takeIf { submitted },
                    )
                }
            }
            if (submitted && !isValid) {
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
                            .testTag("micronutrient_validation_summary"),
                    ) {
                        Text(
                            text = errors.values.filterNotNull().first(),
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
                        .testTag("micronutrient_save"),
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(nomiString("Save"))
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun MicronutrientCard(
    nutrient: Micronutrient,
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    target: String,
    onTargetChanged: (String) -> Unit,
    errorMessage: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = nomiCardShape(),
        elevation = nomiCardElevation(),
        border = nomiCardBorder(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nutrient.localizedName(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = nutrient.localizedGuidance(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChanged,
                    modifier = Modifier.testTag("micronutrient_toggle_${nutrient.name}"),
                )
            }
            if (enabled) {
                NomiTextField(
                    value = target,
                    onValueChange = onTargetChanged,
                    modifier = Modifier
                        .semantics { errorMessage?.let { error(it) } }
                        .testTag("micronutrient_target_${nutrient.name}"),
                    label = nomiString("Daily target"),
                    suffix = nomiFormat("{0}/day", nutrient.storageUnit.suffix),
                    supportingText = errorMessage,
                    isError = errorMessage != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                )
            }
        }
    }
}

@Composable
internal fun Micronutrient.localizedName(): String = when (this) {
    Micronutrient.FIBER -> nomiString("Fiber")
    Micronutrient.SUGAR -> nomiString("Sugar")
    Micronutrient.SATURATED_FAT -> nomiString("Saturated fat")
    Micronutrient.SODIUM -> nomiString("Sodium")
}

/**
 * The educational half of the feature: a number is only useful next to what it should be. These
 * are general adult reference intakes, not medical advice, and the wording says so by naming
 * the direction rather than prescribing a personal goal.
 */
@Composable
private fun Micronutrient.localizedGuidance(): String {
    val amount = "${referenceDailyAmount.targetText()} ${storageUnit.suffix}"
    return when (this) {
        Micronutrient.FIBER -> nomiFormat(
            "Aim for at least {0} a day. Most people get well under half of that.",
            amount,
        )

        Micronutrient.SUGAR -> nomiFormat(
            "Keep added and free sugars under {0} a day. Drinks are where it adds up fastest.",
            amount,
        )

        Micronutrient.SATURATED_FAT -> nomiFormat(
            "Stay under about {0} a day, roughly a tenth of a 2,000 kcal day.",
            amount,
        )

        Micronutrient.SODIUM -> nomiFormat(
            "Stay under {0} a day, which is about 5 g of salt.",
            amount,
        )
    }
}

/** Whole numbers read as targets, not measurements, so a trailing ".0" is noise. */
internal fun Double.targetText(): String =
    if (this == toLong().toDouble()) toLong().toString() else toString()

private fun String.decimalOnly(): String {
    val filtered = filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
    val firstDot = filtered.indexOf('.')
    if (firstDot < 0) return filtered.take(7)
    return (filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).filter(Char::isDigit))
        .take(7)
}

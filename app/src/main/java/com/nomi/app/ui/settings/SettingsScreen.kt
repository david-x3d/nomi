package com.nomi.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Text
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.nomi.app.data.preferences.CalorieEstimateBias
import com.nomi.app.data.preferences.GoalsCardStyle
import com.nomi.app.domain.calculator.CalorieBiasAdjuster
import com.nomi.app.integration.health.HealthConnectPermissionStatus
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.profile.localizedName
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onGermanTranslationChanged: (Boolean) -> Unit,
    onUnitSystemChanged: (UnitSystem) -> Unit,
    onActivityTargetAdjustmentChanged: (Boolean) -> Unit,
    onProfile: () -> Unit,
    onNutrition: () -> Unit,
    onMicronutrients: () -> Unit,
    onAiProvider: (Int) -> Unit,
    onAiRequestTimeoutDisabledChanged: (Boolean) -> Unit,
    onHealthConnect: () -> Unit,
    onReminderChanged: (Int, Boolean) -> Unit,
    onCalorieEstimateBiasChanged: (CalorieEstimateBias) -> Unit,
    onGoalsCardStyleChanged: (GoalsCardStyle) -> Unit,
    onReminderTimeChanged: (index: Int, hour: Int, minute: Int) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDeveloper: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var picker by remember { mutableStateOf<SettingPicker?>(null) }
    var editingReminder by remember { mutableStateOf<Int?>(null) }
    // The title collapses into the bar as the list scrolls, the same way it does on Progress
    // and History, so the three top-level screens behave alike.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(nomiString("Settings", "Einstellungen")) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = innerPadding) {
            item { SectionTitle(nomiString("You", "Du")) }
            item {
                SettingsLink(
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    title = nomiString("Profile & goal", "Profil & Ziel"),
                    supporting = nomiString(
                        "Birthday, height, weight, activity and goal",
                        "Geburtstag, Größe, Gewicht, Aktivität und Ziel",
                    ),
                    onClick = onProfile,
                )
            }
            item {
                SettingsLink(
                    icon = { Icon(Icons.Default.RestaurantMenu, contentDescription = null) },
                    title = nomiString("Nutrition plan", "Ernährungsplan"),
                    supporting = if (state.nutritionTargets.isCustom) {
                        nomiString("Custom targets", "Benutzerdefinierte Ziele")
                    } else {
                        nomiString("Recommended targets", "Empfohlene Ziele")
                    },
                    onClick = onNutrition,
                )
            }
            item {
                SettingsLink(
                    icon = { Icon(Icons.Default.Science, contentDescription = null) },
                    title = nomiString("Micronutrients", "Mikronährstoffe"),
                    supporting = if (state.trackedMicronutrients.isEmpty()) {
                        nomiString(
                            "Track fiber, sugar, saturated fat or sodium",
                            "Ballaststoffe, Zucker, gesättigte Fettsäuren oder Natrium verfolgen",
                        )
                    } else {
                        state.trackedMicronutrients
                            .map { nutrient -> nutrient.localizedName() }
                            .joinToString(" · ")
                    },
                    onClick = onMicronutrients,
                )
            }
            item { SectionTitle(nomiString("Appearance & units", "Darstellung & Einheiten")) }
            item {
                SettingsLink(
                    icon = { Icon(Icons.Default.ColorLens, contentDescription = null) },
                    title = nomiString("Theme", "Design"),
                    supporting = state.themeMode.localizedDisplayName(),
                    onClick = { picker = SettingPicker.Theme },
                )
            }
            item {
                ToggleSetting(
                    icon = { Icon(Icons.Default.ColorLens, contentDescription = null) },
                    title = nomiString("Dynamic colors", "Dynamische Farben"),
                    supporting = nomiString(
                        "Use colors from your Android wallpaper",
                        "Farben deines Android-Hintergrundbilds verwenden",
                    ),
                    checked = state.dynamicColor,
                    onCheckedChange = onDynamicColorChanged,
                )
            }
            item {
                ToggleSetting(
                    icon = { Icon(Icons.Default.Translate, contentDescription = null) },
                    title = nomiString("German translation", "Deutsch"),
                    supporting = nomiString("Translate Nomi’s interface into German", "Nomis Oberfläche auf Deutsch anzeigen"),
                    checked = state.germanTranslationEnabled,
                    onCheckedChange = onGermanTranslationChanged,
                )
            }
            item {
                SettingsLink(
                    icon = { Icon(Icons.Default.Straighten, contentDescription = null) },
                    title = nomiString("Units", "Einheiten"),
                    supporting = state.unitSystem.localizedDisplayName(),
                    onClick = { picker = SettingPicker.Units },
                )
            }
            item {
                SettingsLink(
                    icon = { Icon(Icons.Default.ColorLens, contentDescription = null) },
                    title = nomiString("Goals view", "Ziele-Ansicht"),
                    supporting = state.goalsCardStyle.localizedDisplayName(),
                    onClick = { picker = SettingPicker.GoalsStyle },
                )
            }
            item { SectionTitle(nomiString("AI providers", "KI-Anbieter")) }
            item {
                ListItem(
                    headlineContent = {
                        Text(nomiString("One key for everything", "Ein Schlüssel für alles"))
                    },
                    supportingContent = {
                        Text(
                            nomiString(
                                "Nomi is preconfigured for OpenRouter. Enter your OpenRouter API " +
                                    "key in any pipeline below and all of them use it.",
                                "Nomi ist für OpenRouter vorkonfiguriert. Gib deinen OpenRouter-" +
                                    "API-Schlüssel unten bei einem Bereich ein, alle nutzen ihn.",
                            ),
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Key, contentDescription = null) },
                )
            }
            if (state.aiProviders.isEmpty()) {
                item {
                    ListItem(
                        headlineContent = { Text(nomiString("No provider configured", "Kein Anbieter eingerichtet")) },
                        supportingContent = { Text(nomiString("Configure a provider to analyze text, photos and portions.", "Richte einen Anbieter ein, um Text, Fotos und Portionen zu analysieren.")) },
                        leadingContent = { Icon(Icons.Default.Key, contentDescription = null) },
                    )
                }
            } else {
                state.aiProviders.forEachIndexed { index, provider ->
                    item(key = "provider-$index") {
                        SettingsLink(
                            icon = { Icon(Icons.Default.Key, contentDescription = null) },
                            title = provider.purpose.localizedPurpose(),
                            supporting = "${provider.provider.localizedDisplayName()} · ${provider.model}",
                            onClick = { onAiProvider(index) },
                        )
                    }
                }
            }
            item {
                ToggleSetting(
                    icon = { Icon(Icons.Default.HourglassEmpty, contentDescription = null) },
                    title = nomiString("Never time out", "Kein Zeitlimit"),
                    supporting = nomiString(
                        "Wait as long as the provider needs instead of giving up after 45 seconds",
                        "So lange warten, wie der Anbieter braucht, statt nach 45 Sekunden abzubrechen",
                    ),
                    checked = state.aiRequestTimeoutDisabled,
                    onCheckedChange = onAiRequestTimeoutDisabledChanged,
                )
            }
            item { SectionTitle(nomiString("Health & activity", "Gesundheit & Aktivität")) }
            item {
                SettingsLink(
                    icon = { Icon(Icons.Default.HealthAndSafety, contentDescription = null) },
                    title = "Health Connect",
                    supporting = when (state.healthConnect.status) {
                        HealthConnectPermissionStatus.UNAVAILABLE ->
                            nomiString("Not available on this device", "Auf diesem Gerät nicht verfügbar")
                        HealthConnectPermissionStatus.UPDATE_REQUIRED ->
                            nomiString("Update required", "Aktualisierung erforderlich")
                        HealthConnectPermissionStatus.PARTIAL ->
                            nomiString("Permissions missing", "Berechtigungen fehlen")
                        HealthConnectPermissionStatus.CONNECTED -> nomiString("Connected", "Verbunden")
                        HealthConnectPermissionStatus.DISCONNECTED -> nomiString("Optional", "Optional")
                    },
                    enabled = state.healthConnect.status != HealthConnectPermissionStatus.UNAVAILABLE,
                    onClick = onHealthConnect,
                )
            }
            item {
                ToggleSetting(
                    icon = { Icon(Icons.Default.CloudSync, contentDescription = null) },
                    title = nomiString("Adjust target from activity", "Ziel an Aktivität anpassen"),
                    supporting = nomiString(
                        "Off by default. When on, changes are shown transparently.",
                        "Standardmäßig aus. Änderungen werden transparent angezeigt.",
                    ),
                    checked = state.activityTargetAdjustment,
                    onCheckedChange = onActivityTargetAdjustmentChanged,
                )
            }
            item {
                CalorieBiasSetting(
                    bias = state.calorieEstimateBias,
                    onBiasChanged = onCalorieEstimateBiasChanged,
                )
            }
            item { SectionTitle(nomiString("Reminders", "Erinnerungen")) }
            state.reminders.forEachIndexed { index, reminder ->
                item(key = "reminder-$index") {
                    ToggleSetting(
                        icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                        title = reminder.name.localizedReminderName(),
                        // Tapping the row edits the time; the switch stays for on and off.
                        supporting = reminder.timeText + " · " +
                            nomiString("tap to change", "tippen zum Ändern"),
                        checked = reminder.enabled,
                        onCheckedChange = { onReminderChanged(index, it) },
                        onClick = { editingReminder = index },
                    )
                }
            }
            item { SectionTitle(nomiString("Your data", "Deine Daten")) }
            item {
                SettingsLink(
                    icon = { Icon(Icons.Default.Upload, contentDescription = null) },
                    title = nomiString("Export backup", "Sicherung exportieren"),
                    supporting = nomiString("Versioned JSON without API keys", "Versioniertes JSON ohne API-Schlüssel"),
                    onClick = onExport,
                )
            }
            item {
                SettingsLink(
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                    title = nomiString("Import backup", "Sicherung importieren"),
                    supporting = nomiString("Validated before existing data changes", "Wird vor Änderungen an vorhandenen Daten geprüft"),
                    onClick = onImport,
                )
            }
            item { SectionTitle(nomiString("Developer", "Entwicklung")) }
            item {
                SettingsLink(
                    icon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                    title = nomiString("AI debug", "KI-Debug"),
                    supporting = nomiString("Provider, timing, source and validation — never keys", "Anbieter, Dauer, Quelle und Prüfung – niemals Schlüssel"),
                    onClick = onDeveloper,
                )
            }
            item {
                Text(
                    "Nomi ${state.appVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    editingReminder?.let { index ->
        val reminder = state.reminders.getOrNull(index)
        if (reminder == null) {
            editingReminder = null
        } else {
            ReminderTimeDialog(
                title = reminder.name.localizedReminderName(),
                currentTime = reminder.timeText,
                onDismiss = { editingReminder = null },
                onConfirm = { hour, minute ->
                    onReminderTimeChanged(index, hour, minute)
                    editingReminder = null
                },
            )
        }
    }

    when (picker) {
        SettingPicker.Theme -> ChoiceSheet(
            title = nomiString("Theme", "Design"),
            choices = ThemeMode.entries.map { it.localizedDisplayName() },
            selectedIndex = ThemeMode.entries.indexOf(state.themeMode),
            onSelect = { onThemeModeChanged(ThemeMode.entries[it]); picker = null },
            onDismiss = { picker = null },
        )

        SettingPicker.Units -> ChoiceSheet(
            title = nomiString("Units", "Einheiten"),
            choices = UnitSystem.entries.map { it.localizedDisplayName() },
            selectedIndex = UnitSystem.entries.indexOf(state.unitSystem),
            onSelect = { onUnitSystemChanged(UnitSystem.entries[it]); picker = null },
            onDismiss = { picker = null },
        )

        SettingPicker.GoalsStyle -> ChoiceSheet(
            title = nomiString("Goals view", "Ziele-Ansicht"),
            choices = GoalsCardStyle.entries.map { it.localizedDisplayName() },
            selectedIndex = GoalsCardStyle.entries.indexOf(state.goalsCardStyle),
            onSelect = { onGoalsCardStyleChanged(GoalsCardStyle.entries[it]); picker = null },
            onDismiss = { picker = null },
        )

        null -> Unit
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun SettingsLink(
    icon: @Composable () -> Unit,
    title: String,
    supporting: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(supporting) },
        leadingContent = icon,
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        },
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun ToggleSetting(
    icon: @Composable () -> Unit,
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(supporting) },
        leadingContent = icon,
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        modifier = Modifier.fillMaxWidth().clickable {
            onClick?.invoke() ?: onCheckedChange(!checked)
        },
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
}

/** The official Material time picker, prefilled with the time the reminder currently uses. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(
    title: String,
    currentTime: String,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val parts = currentTime.split(':')
    val state = rememberTimePickerState(
        initialHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8,
        initialMinute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text(nomiString("Save", "Speichern"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(nomiString("Cancel", "Abbrechen")) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChoiceSheet(
    title: String,
    choices: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp))
        choices.forEachIndexed { index, choice ->
            ListItem(
                headlineContent = { Text(choice) },
                leadingContent = {
                    RadioButton(selected = index == selectedIndex, onClick = { onSelect(index) })
                },
                // A ListItem paints colorScheme.surface by default, but a bottom sheet is
                // surfaceContainerLow, so an opaque row draws a visible seam across the sheet.
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth().clickable { onSelect(index) },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

private sealed interface SettingPicker {
    data object Theme : SettingPicker
    data object Units : SettingPicker
    data object GoalsStyle : SettingPicker
}

@Composable
private fun ThemeMode.localizedDisplayName(): String = when (this) {
    ThemeMode.SYSTEM -> nomiString("System", "System")
    ThemeMode.LIGHT -> nomiString("Light", "Hell")
    ThemeMode.DARK -> nomiString("Dark", "Dunkel")
}

@Composable
private fun UnitSystem.localizedDisplayName(): String = when (this) {
    UnitSystem.METRIC -> nomiString("Metric", "Metrisch")
    UnitSystem.IMPERIAL -> nomiString("Imperial", "Imperial")
}

/**
 * Five discrete stops on one official Material slider.
 *
 * The setting is a scale with a natural middle, not five unrelated options, so a slider says what
 * a list cannot: that "no bias" is the centre and each step moves the same distance away from it.
 * The example underneath updates as the thumb moves, so the effect is visible before release.
 * The preference is only written on release; dragging must not fire a DataStore write per pixel.
 */
@Composable
private fun CalorieBiasSetting(
    bias: CalorieEstimateBias,
    onBiasChanged: (CalorieEstimateBias) -> Unit,
) {
    val entries = CalorieEstimateBias.entries
    var position by remember(bias) { mutableFloatStateOf(entries.indexOf(bias).toFloat()) }
    val selected = entries[position.roundToInt().coerceIn(0, entries.lastIndex)]
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Straighten, contentDescription = null)
            Column {
                Text(nomiString("Calorie estimate bias", "Kalorienschätzung"))
                Text(
                    text = selected.localizedDisplayName(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Slider(
            value = position,
            onValueChange = { position = it },
            onValueChangeFinished = { onBiasChanged(selected) },
            valueRange = 0f..entries.lastIndex.toFloat(),
            steps = entries.size - 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = nomiString("Lower", "Niedriger"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = nomiString("Higher", "Höher"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = selected.localizedSupportingText(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
}

/**
 * Says what the setting does to a real number rather than naming it again, because "Overestimate"
 * on its own does not tell anyone how much.
 */
@Composable
private fun CalorieEstimateBias.localizedSupportingText(): String {
    val example = CalorieBiasAdjuster.scaleFor(uncertaintyPercent = 16.7, bias = this) * 600.0
    val rounded = example.roundToInt()
    return when (this) {
        CalorieEstimateBias.NONE -> nomiString(
            "Estimates are logged as given. A 500-700 kcal meal counts as 600.",
            "Schätzungen werden unverändert übernommen. 500-700 kcal zählen als 600.",
        )
        else -> nomiString(
            "${localizedDisplayName()} - a 500-700 kcal meal counts as $rounded.",
            "${localizedDisplayName()} - 500-700 kcal zählen als $rounded.",
        )
    }
}

@Composable
private fun GoalsCardStyle.localizedDisplayName(): String = when (this) {
    GoalsCardStyle.BARS -> nomiString("Calories and bars", "Kalorien und Balken")
    GoalsCardStyle.RINGS -> nomiString("One card with rings", "Eine Karte mit Ringen")
}

@Composable
private fun CalorieEstimateBias.localizedDisplayName(): String = when (this) {
    CalorieEstimateBias.STRONGLY_UNDERESTIMATE ->
        nomiString("Underestimate more", "Stärker unterschätzen")
    CalorieEstimateBias.UNDERESTIMATE -> nomiString("Underestimate", "Unterschätzen")
    CalorieEstimateBias.NONE -> nomiString("No bias", "Keine Verzerrung")
    CalorieEstimateBias.OVERESTIMATE -> nomiString("Overestimate", "Überschätzen")
    CalorieEstimateBias.STRONGLY_OVERESTIMATE ->
        nomiString("Overestimate more", "Stärker überschätzen")
}

@Composable
private fun com.nomi.app.ai.model.AiProviderKind.localizedDisplayName(): String = when (this) {
    com.nomi.app.ai.model.AiProviderKind.PERPLEXITY -> "Perplexity"
    com.nomi.app.ai.model.AiProviderKind.OPEN_ROUTER -> "OpenRouter"
    com.nomi.app.ai.model.AiProviderKind.OPEN_AI -> "OpenAI"
    com.nomi.app.ai.model.AiProviderKind.CODEX_EASY -> "Codex Easy"
    com.nomi.app.ai.model.AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE -> nomiString("Custom endpoint", "Benutzerdefinierter Endpunkt")
}

@Composable
private fun String.localizedPurpose(): String = when (this) {
    "Food research" -> nomiString("Food research", "Lebensmittelrecherche")
    "Food interpretation" -> nomiString("Food interpretation", "Lebensmittelinterpretation")
    "Portion changes" -> nomiString("Portion changes", "Portionsänderungen")
    "Photo recognition" -> nomiString("Photo recognition", "Fotoerkennung")
    "Fallback" -> nomiString("Fallback", "Intelligenter Fallback")
    else -> this
}

@Composable
private fun String.localizedReminderName(): String = when (this) {
    "Breakfast" -> nomiString("Breakfast", "Frühstück")
    "Lunch" -> nomiString("Lunch", "Mittagessen")
    "Dinner" -> nomiString("Dinner", "Abendessen")
    "Daily summary" -> nomiString("Daily summary", "Tagesübersicht")
    "Weight" -> nomiString("Weight", "Gewicht")
    else -> this
}

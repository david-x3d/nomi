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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.nomi.app.integration.health.HealthConnectPermissionStatus
import com.nomi.app.ui.localization.nomiString

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
    onAiProvider: (Int) -> Unit,
    onAiRequestTimeoutDisabledChanged: (Boolean) -> Unit,
    onHealthConnect: () -> Unit,
    onReminderChanged: (Int, Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDeveloper: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var picker by remember { mutableStateOf<SettingPicker?>(null) }
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
            item { SectionTitle(nomiString("AI providers", "KI-Anbieter")) }
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
            item { SectionTitle(nomiString("Reminders", "Erinnerungen")) }
            state.reminders.forEachIndexed { index, reminder ->
                item(key = "reminder-$index") {
                    ToggleSetting(
                        icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                        title = reminder.name.localizedReminderName(),
                        supporting = reminder.timeText,
                        checked = reminder.enabled,
                        onCheckedChange = { onReminderChanged(index, it) },
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
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(supporting) },
        leadingContent = icon,
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
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
                modifier = Modifier.fillMaxWidth().clickable { onSelect(index) },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

private sealed interface SettingPicker {
    data object Theme : SettingPicker
    data object Units : SettingPicker
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

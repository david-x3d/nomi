package com.nomi.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.nomi.app.data.preferences.GoalsCardStyle
import com.nomi.app.integration.health.HealthConnectPermissionStatus
import com.nomi.app.ui.components.NomiDialog
import com.nomi.app.ui.components.NomiSelectionRow
import com.nomi.app.ui.components.NomiSheet
import com.nomi.app.ui.components.NomiSheetHeader
import com.nomi.app.ui.localization.NomiLanguage
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.profile.localizedName

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onLanguageChanged: (NomiLanguage) -> Unit,
    onUnitSystemChanged: (UnitSystem) -> Unit,
    onActivityTargetAdjustmentChanged: (Boolean) -> Unit,
    onProfile: () -> Unit,
    onNutrition: () -> Unit,
    onMicronutrients: () -> Unit,
    onAi: () -> Unit,
    onHealthConnect: () -> Unit,
    onReminderChanged: (Int, Boolean) -> Unit,
    onGoalsCardStyleChanged: (GoalsCardStyle) -> Unit,
    onReminderTimeChanged: (index: Int, hour: Int, minute: Int) -> Unit,
    onExport: () -> Unit,
    onExportDiary: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var picker by remember { mutableStateOf<SettingPicker?>(null) }
    var editingReminder by remember { mutableStateOf<Int?>(null) }
    // The title collapses into the bar as the list scrolls, the same way it does on Progress
    // and History, so the three top-level screens behave alike.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val pageContainerColor = settingsPageColor()
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(nomiString("Settings")) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = pageContainerColor,
                    scrolledContainerColor = pageContainerColor,
                ),
            )
        },
        containerColor = pageContainerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(pageContainerColor),
            contentPadding = innerPadding,
        ) {
            // The one thing that stops Nomi working is said first, not found in a section.
            if (state.aiSetupNeeded) {
                item(key = "ai-setup") {
                    SettingsLink(
                        icon = { Icon(Icons.Default.Key, contentDescription = null) },
                        title = nomiString("Add your AI key"),
                        supporting = nomiString("Nomi can't look up food until a key is saved."),
                        onClick = onAi,
                        iconColor = MaterialTheme.colorScheme.error,
                    )
                }
            }
            item { SectionTitle(nomiString("You")) }
            item {
                SettingsLink(
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    title = nomiString("Profile & goal"),
                    supporting = nomiString("Birthday, height, weight, activity and goal"),
                    onClick = onProfile,
                    iconColor = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                SettingsLink(
                    icon = { Icon(Icons.Default.RestaurantMenu, contentDescription = null) },
                    title = nomiString("Nutrition plan"),
                    supporting = if (state.nutritionTargets.isCustom) {
                        nomiString("Custom targets")
                    } else {
                        nomiString("Recommended targets")
                    },
                    onClick = onNutrition,
                    iconColor = MaterialTheme.colorScheme.tertiary,
                )
            }
            item {
                SettingsLink(
                    icon = { Icon(Icons.Default.Science, contentDescription = null) },
                    title = nomiString("Micronutrients"),
                    supporting = if (state.trackedMicronutrients.isEmpty()) {
                        nomiString("Track fiber, sugar, saturated fat or sodium")
                    } else {
                        state.trackedMicronutrients
                            .map { nutrient -> nutrient.localizedName() }
                            .joinToString(" · ")
                    },
                    onClick = onMicronutrients,
                    iconColor = MaterialTheme.colorScheme.secondary,
                )
            }
            // One row for everything AI. The provider of each task, the estimate bias, the
            // timeout and the debug log are all a page down, where the people who want them look.
            item { SectionTitle(nomiString("AI")) }
            item {
                SettingsLink(
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    title = nomiString("AI provider"),
                    supporting = state.aiSummary(),
                    supportingColor = if (state.aiSetupNeeded) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color.Unspecified
                    },
                    onClick = onAi,
                    iconColor = MaterialTheme.colorScheme.secondary,
                )
            }
            item { SectionTitle(nomiString("Appearance & units")) }
            item {
                SettingsLink(
                    icon = { Icon(Icons.Default.ColorLens, contentDescription = null) },
                    title = nomiString("Theme"),
                    supporting = state.themeMode.localizedDisplayName(),
                    onClick = { picker = SettingPicker.Theme },
                    iconColor = MaterialTheme.colorScheme.tertiary,
                )
            }
            item {
                ToggleSetting(
                    icon = { Icon(Icons.Default.Wallpaper, contentDescription = null) },
                    title = nomiString("Dynamic colors"),
                    supporting = nomiString("Use colors from your Android wallpaper"),
                    checked = state.dynamicColor,
                    onCheckedChange = onDynamicColorChanged,
                    iconColor = MaterialTheme.colorScheme.tertiary,
                )
            }
            item {
                SettingsLink(
                    icon = { Icon(Icons.Default.Translate, contentDescription = null) },
                    title = nomiString("Language"),
                    // The current language names itself, so it stays recognizable to someone who
                    // has landed in a language they do not read and is looking for the way back.
                    supporting = state.language.nativeName,
                    onClick = { picker = SettingPicker.Language },
                    iconColor = MaterialTheme.colorScheme.secondary,
                )
            }
            item {
                SettingsLink(
                    icon = { Icon(Icons.Default.Straighten, contentDescription = null) },
                    title = nomiString("Units"),
                    supporting = state.unitSystem.localizedDisplayName(),
                    onClick = { picker = SettingPicker.Units },
                    iconColor = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                SettingsLink(
                    icon = { Icon(Icons.Default.DonutLarge, contentDescription = null) },
                    title = nomiString("Goals view"),
                    supporting = state.goalsCardStyle.localizedDisplayName(),
                    onClick = { picker = SettingPicker.GoalsStyle },
                    iconColor = MaterialTheme.colorScheme.primary,
                )
            }
            item { SectionTitle(nomiString("Health & activity")) }
            item {
                SettingsLink(
                    icon = { Icon(Icons.Default.HealthAndSafety, contentDescription = null) },
                    title = "Health Connect",
                    supporting = when (state.healthConnect.status) {
                        HealthConnectPermissionStatus.UNAVAILABLE ->
                            nomiString("Not available on this device")
                        HealthConnectPermissionStatus.UPDATE_REQUIRED ->
                            nomiString("Update required")
                        HealthConnectPermissionStatus.PARTIAL ->
                            nomiString("Permissions missing")
                        HealthConnectPermissionStatus.CONNECTED -> nomiString("Connected")
                        HealthConnectPermissionStatus.DISCONNECTED -> nomiString("Optional")
                    },
                    enabled = state.healthConnect.status != HealthConnectPermissionStatus.UNAVAILABLE,
                    onClick = onHealthConnect,
                    iconColor = MaterialTheme.colorScheme.tertiary,
                )
            }
            item {
                ToggleSetting(
                    icon = { Icon(Icons.Default.CloudSync, contentDescription = null) },
                    title = nomiString("Adjust target from activity"),
                    supporting = nomiString("Off by default. When on, changes are shown transparently."),
                    checked = state.activityTargetAdjustment,
                    onCheckedChange = onActivityTargetAdjustmentChanged,
                    iconColor = MaterialTheme.colorScheme.primary,
                )
            }
            item { SectionTitle(nomiString("Reminders")) }
            state.reminders.forEachIndexed { index, reminder ->
                item(key = "reminder-$index") {
                    ToggleSetting(
                        icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                        title = reminder.name.localizedReminderName(),
                        // Tapping the row edits the time; the switch stays for on and off.
                        supporting = reminder.timeText + " · " +
                            nomiString("tap to change"),
                        checked = reminder.enabled,
                        onCheckedChange = { onReminderChanged(index, it) },
                        onClick = { editingReminder = index },
                        iconColor = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            item { SectionTitle(nomiString("Your data")) }
            item {
                SettingsLink(
                    icon = { Icon(Icons.Default.Description, contentDescription = null) },
                    title = nomiString("Export diary"),
                    supporting = nomiString("JSON of each day's foods, calories, protein and carbs"),
                    onClick = onExportDiary,
                    iconColor = MaterialTheme.colorScheme.tertiary,
                )
            }
            item {
                SettingsLink(
                    icon = { Icon(Icons.Default.Upload, contentDescription = null) },
                    title = nomiString("Export backup"),
                    supporting = nomiString("Versioned JSON without API keys"),
                    onClick = onExport,
                    iconColor = MaterialTheme.colorScheme.secondary,
                )
            }
            item {
                SettingsLink(
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                    title = nomiString("Import backup"),
                    supporting = nomiString("Validated before existing data changes"),
                    onClick = onImport,
                    iconColor = MaterialTheme.colorScheme.primary,
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
            title = nomiString("Theme"),
            choices = ThemeMode.entries.map { it.localizedDisplayName() },
            selectedIndex = ThemeMode.entries.indexOf(state.themeMode),
            onSelect = { onThemeModeChanged(ThemeMode.entries[it]); picker = null },
            onDismiss = { picker = null },
        )

        SettingPicker.Units -> ChoiceSheet(
            title = nomiString("Units"),
            choices = UnitSystem.entries.map { it.localizedDisplayName() },
            selectedIndex = UnitSystem.entries.indexOf(state.unitSystem),
            onSelect = { onUnitSystemChanged(UnitSystem.entries[it]); picker = null },
            onDismiss = { picker = null },
        )

        SettingPicker.GoalsStyle -> ChoiceSheet(
            title = nomiString("Goals view"),
            choices = GoalsCardStyle.entries.map { it.localizedDisplayName() },
            selectedIndex = GoalsCardStyle.entries.indexOf(state.goalsCardStyle),
            onSelect = { onGoalsCardStyleChanged(GoalsCardStyle.entries[it]); picker = null },
            onDismiss = { picker = null },
        )

        // Each language is listed in its own name and never translated, which is how someone
        // finds their language in a list they cannot otherwise read.
        SettingPicker.Language -> ChoiceSheet(
            title = nomiString("Language"),
            choices = NomiLanguage.entries.map { it.nativeName },
            selectedIndex = NomiLanguage.entries.indexOf(state.language),
            onSelect = { onLanguageChanged(NomiLanguage.entries[it]); picker = null },
            onDismiss = { picker = null },
        )

        null -> Unit
    }
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
        // Follows the device's 12/24-hour setting, which a US or UK user has set deliberately.
        is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current),
    )
    NomiDialog(
        onDismissRequest = onDismiss,
        title = title,
        icon = Icons.Default.Schedule,
        confirmLabel = nomiString("Save"),
        onConfirm = { onConfirm(state.hour, state.minute) },
        dismissLabel = nomiString("Cancel"),
    ) {
        // The picker is wider than the dialog's text column, so it centres in the body
        // instead of hanging off the left edge.
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TimePicker(state = state)
        }
    }
}

/**
 * A short list of mutually exclusive settings.
 *
 * Radio buttons in a bottom sheet ask you to read four labels and then hunt for the one filled
 * circle. The expressive selection rows carry the answer in the shape and tone of the whole row,
 * so the current setting is the first thing the sheet says.
 */
@Composable
private fun ChoiceSheet(
    title: String,
    choices: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    NomiSheet(onDismissRequest = onDismiss) {
        NomiSheetHeader(title = title)
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            choices.forEachIndexed { index, choice ->
                NomiSelectionRow(
                    title = choice,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}

private sealed interface SettingPicker {
    data object Theme : SettingPicker
    data object Units : SettingPicker
    data object GoalsStyle : SettingPicker
    data object Language : SettingPicker
}

@Composable
private fun ThemeMode.localizedDisplayName(): String = when (this) {
    ThemeMode.SYSTEM -> nomiString("System")
    ThemeMode.LIGHT -> nomiString("Light")
    ThemeMode.DARK -> nomiString("Dark")
}

@Composable
private fun UnitSystem.localizedDisplayName(): String = when (this) {
    UnitSystem.METRIC -> nomiString("Metric")
    UnitSystem.IMPERIAL -> nomiString("Imperial")
}

@Composable
private fun GoalsCardStyle.localizedDisplayName(): String = when (this) {
    GoalsCardStyle.BARS -> nomiString("Calories and bars")
    GoalsCardStyle.RINGS -> nomiString("One card with rings")
}

/** The AI row's second line: which provider, and whether it has what it needs. */
@Composable
private fun SettingsUiState.aiSummary(): String {
    val status = if (aiSetupNeeded) nomiString("API key missing") else nomiString("Ready")
    val shared = sharedAiProvider
    return when {
        // Not loaded yet, which is not the same as not set up.
        aiProviders.isEmpty() -> ""
        shared != null -> "${shared.provider.localizedDisplayName()} · $status"
        else -> "${nomiString("A provider per task")} · $status"
    }
}

@Composable
private fun String.localizedReminderName(): String = when (this) {
    "Breakfast" -> nomiString("Breakfast")
    "Lunch" -> nomiString("Lunch")
    "Dinner" -> nomiString("Dinner")
    "Daily summary" -> nomiString("Daily summary")
    "Weight" -> nomiString("Weight")
    else -> this
}

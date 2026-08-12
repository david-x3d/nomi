package com.nomi.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.nomi.app.data.preferences.CalorieEstimateBias
import com.nomi.app.data.preferences.GoalsCardStyle
import com.nomi.app.domain.calculator.CalorieBiasAdjuster
import com.nomi.app.integration.health.HealthConnectPermissionStatus
import com.nomi.app.ui.components.NomiDialog
import com.nomi.app.ui.components.NomiSelectionRow
import com.nomi.app.ui.components.NomiSheet
import com.nomi.app.ui.components.NomiSheetHeader
import com.nomi.app.ui.localization.NomiLanguage
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.profile.localizedName
import kotlin.math.roundToInt

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
    val pageContainerColor = lerp(
        MaterialTheme.colorScheme.surfaceContainerLow,
        MaterialTheme.colorScheme.primaryContainer,
        0.12f,
    )
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
                    icon = { Icon(Icons.Default.ColorLens, contentDescription = null) },
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
                    icon = { Icon(Icons.Default.ColorLens, contentDescription = null) },
                    title = nomiString("Goals view"),
                    supporting = state.goalsCardStyle.localizedDisplayName(),
                    onClick = { picker = SettingPicker.GoalsStyle },
                    iconColor = MaterialTheme.colorScheme.onSurface,
                )
            }
            item { SectionTitle(nomiString("AI providers")) }
            item {
                SettingsInfo(
                    icon = { Icon(Icons.Default.Key, contentDescription = null) },
                    title = nomiString("One key for everything"),
                    supporting = nomiString(
                        "Nomi is preconfigured for OpenRouter. Enter your OpenRouter API key " +
                            "in any pipeline below and all of them use it.",
                    ),
                    iconColor = MaterialTheme.colorScheme.secondary,
                )
            }
            if (state.aiProviders.isEmpty()) {
                item {
                    SettingsInfo(
                        icon = { Icon(Icons.Default.Key, contentDescription = null) },
                        title = nomiString("No provider configured"),
                        supporting = nomiString("Configure a provider to analyze text, photos and portions."),
                        iconColor = MaterialTheme.colorScheme.error,
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
                            iconColor = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
            item {
                ToggleSetting(
                    icon = { Icon(Icons.Default.HourglassEmpty, contentDescription = null) },
                    title = nomiString("Never time out"),
                    supporting = nomiString("Wait as long as the provider needs instead of giving up after 45 seconds"),
                    checked = state.aiRequestTimeoutDisabled,
                    onCheckedChange = onAiRequestTimeoutDisabledChanged,
                    iconColor = MaterialTheme.colorScheme.secondary,
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
            item {
                CalorieBiasSetting(
                    bias = state.calorieEstimateBias,
                    onBiasChanged = onCalorieEstimateBiasChanged,
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
            item { SectionTitle(nomiString("Developer")) }
            item {
                SettingsLink(
                    icon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                    title = nomiString("AI debug"),
                    supporting = nomiString("Provider, timing, source and validation — never keys"),
                    onClick = onDeveloper,
                    iconColor = MaterialTheme.colorScheme.error,
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

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingsLink(
    icon: @Composable () -> Unit,
    title: String,
    supporting: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    iconColor: Color = Color.Unspecified,
) {
    val resolvedIconColor = if (iconColor == Color.Unspecified) {
        MaterialTheme.colorScheme.primary
    } else {
        iconColor
    }
    GlassSettingSurface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick,
        enabled = enabled,
    ) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(supporting) },
            leadingContent = { SettingsIconTile(resolvedIconColor, icon) },
            trailingContent = {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun ToggleSetting(
    icon: @Composable () -> Unit,
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: (() -> Unit)? = null,
    iconColor: Color = Color.Unspecified,
) {
    val resolvedIconColor = if (iconColor == Color.Unspecified) {
        MaterialTheme.colorScheme.primary
    } else {
        iconColor
    }
    GlassSettingSurface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = { onClick?.invoke() ?: onCheckedChange(!checked) },
    ) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(supporting) },
            leadingContent = { SettingsIconTile(resolvedIconColor, icon) },
            trailingContent = {
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun SettingsInfo(
    icon: @Composable () -> Unit,
    title: String,
    supporting: String,
    iconColor: Color,
) {
    val lightMode = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(22.dp),
        color = if (lightMode) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
        },
    ) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(supporting) },
            leadingContent = { SettingsIconTile(iconColor, icon) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun GlassSettingSurface(
    modifier: Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val lightMode = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = if (lightMode) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f)
        },
        content = content,
    )
}

@Composable
private fun SettingsIconTile(
    color: Color,
    icon: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier,
        shape = RoundedCornerShape(13.dp),
        color = color.copy(alpha = 0.14f),
        contentColor = color,
    ) {
        Box(
            modifier = Modifier.padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
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
        is24Hour = true,
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
            modifier = Modifier.padding(horizontal = 20.dp),
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
    val lightMode = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(22.dp),
        color = if (lightMode) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f)
        },
        tonalElevation = if (lightMode) 0.dp else 2.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsIconTile(MaterialTheme.colorScheme.error) {
                    Icon(Icons.Default.Straighten, contentDescription = null)
                }
                Column {
                    Text(
                        nomiString("Calorie estimate bias"),
                        style = MaterialTheme.typography.titleMedium,
                    )
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
                    text = nomiString("Lower"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = nomiString("Higher"),
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
    }
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
        CalorieEstimateBias.NONE -> nomiString("Estimates are logged as given. A 500-700 kcal meal counts as 600.")
        else -> nomiFormat(
            "{0} - a 500-700 kcal meal counts as {1}.",
            localizedDisplayName(),
            rounded,
        )
    }
}

@Composable
private fun GoalsCardStyle.localizedDisplayName(): String = when (this) {
    GoalsCardStyle.BARS -> nomiString("Calories and bars")
    GoalsCardStyle.RINGS -> nomiString("One card with rings")
}

@Composable
private fun CalorieEstimateBias.localizedDisplayName(): String = when (this) {
    CalorieEstimateBias.STRONGLY_UNDERESTIMATE ->
        nomiString("Underestimate more")
    CalorieEstimateBias.UNDERESTIMATE -> nomiString("Underestimate")
    CalorieEstimateBias.NONE -> nomiString("No bias")
    CalorieEstimateBias.OVERESTIMATE -> nomiString("Overestimate")
    CalorieEstimateBias.STRONGLY_OVERESTIMATE ->
        nomiString("Overestimate more")
}

@Composable
private fun com.nomi.app.ai.model.AiProviderKind.localizedDisplayName(): String = when (this) {
    com.nomi.app.ai.model.AiProviderKind.PERPLEXITY -> "Perplexity"
    com.nomi.app.ai.model.AiProviderKind.OPEN_ROUTER -> "OpenRouter"
    com.nomi.app.ai.model.AiProviderKind.OPEN_AI -> "OpenAI"
    com.nomi.app.ai.model.AiProviderKind.EXA_GEMINI -> "Exa + Gemini"
    com.nomi.app.ai.model.AiProviderKind.CODEX_EASY -> "Codex Easy"
    com.nomi.app.ai.model.AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE -> nomiString("Custom endpoint")
}

@Composable
private fun String.localizedPurpose(): String = when (this) {
    "Food research" -> nomiString("Food research")
    "Food interpretation" -> nomiString("Food interpretation")
    "Portion changes" -> nomiString("Portion changes")
    "Photo recognition" -> nomiString("Photo recognition")
    "Fallback" -> nomiString("Fallback")
    else -> this
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

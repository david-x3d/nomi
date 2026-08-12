package com.nomi.app.ui.settings

import com.nomi.app.ai.model.AiProviderKind
import com.nomi.app.data.preferences.CalorieEstimateBias
import com.nomi.app.data.preferences.GoalsCardStyle
import com.nomi.app.domain.Micronutrient
import com.nomi.app.integration.health.HealthConnectPermissionStatus
import com.nomi.app.ui.localization.NomiLanguage

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class UnitSystem { METRIC, IMPERIAL }

data class NutritionTargetSetting(
    val calories: Int,
    val proteinGrams: Int,
    val carbohydrateGrams: Int,
    val fatGrams: Int,
    val isCustom: Boolean,
)

data class AiProviderSetting(
    val purpose: String,
    val provider: AiProviderKind,
    val model: String,
    val endpoint: String,
    val hasApiKey: Boolean,
    val hasPrimaryApiKey: Boolean = hasApiKey,
    val hasSearchApiKey: Boolean = false,
    val connectionStatus: String? = null,
)

data class ReminderSetting(
    val name: String,
    val enabled: Boolean = false,
    val timeText: String,
)

data class HealthConnectUiState(
    val status: HealthConnectPermissionStatus = HealthConnectPermissionStatus.UNAVAILABLE,
    val isSyncing: Boolean = false,
    val todaySteps: Long? = null,
    val todayActiveCaloriesKcal: Double? = null,
    val lastSyncEpochMillis: Long? = null,
    val importedWeightCount: Int = 0,
    val message: String? = null,
)

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val language: NomiLanguage = NomiLanguage.Default,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val activityTargetAdjustment: Boolean = false,
    val calorieEstimateBias: CalorieEstimateBias = CalorieEstimateBias.NONE,
    val goalsCardStyle: GoalsCardStyle = GoalsCardStyle.BARS,
    val healthConnectAvailable: Boolean = false,
    val healthConnectEnabled: Boolean = false,
    val healthConnect: HealthConnectUiState = HealthConnectUiState(),
    val nutritionTargets: NutritionTargetSetting = NutritionTargetSetting(2_000, 130, 240, 65, false),
    /** The micronutrients currently being tracked, in presentation order. */
    val trackedMicronutrients: List<Micronutrient> = emptyList(),
    val aiProviders: List<AiProviderSetting> = emptyList(),
    val aiRequestTimeoutDisabled: Boolean = false,
    val reminders: List<ReminderSetting> = listOf(
        ReminderSetting("Breakfast", timeText = "08:00"),
        ReminderSetting("Lunch", timeText = "12:30"),
        ReminderSetting("Dinner", timeText = "19:00"),
        ReminderSetting("Daily summary", timeText = "21:00"),
        ReminderSetting("Weight", timeText = "08:00"),
    ),
    val appVersion: String = "1.0.0",
)

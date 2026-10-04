package com.nomi.app.ui.app

import com.nomi.app.BuildConfig
import com.nomi.app.data.local.entity.NutritionPlanEntity
import com.nomi.app.data.preferences.AppPreferences
import com.nomi.app.data.preferences.ProviderPipeline
import com.nomi.app.data.preferences.providerSelection
import com.nomi.app.data.preferences.ThemePreference
import com.nomi.app.data.preferences.WeightUnitPreference
import com.nomi.app.data.preferences.enabledMicronutrients
import com.nomi.app.domain.StepCalorieEstimate
import com.nomi.app.integration.health.HealthConnectPermissionStatus
import com.nomi.app.ui.localization.NomiLanguage
import com.nomi.app.ui.settings.AiProviderSetting
import com.nomi.app.ui.settings.HealthConnectUiState
import com.nomi.app.ui.settings.NutritionTargetSetting
import com.nomi.app.ui.settings.ReminderSetting
import com.nomi.app.ui.settings.SettingsUiState
import com.nomi.app.ui.settings.ThemeMode
import com.nomi.app.ui.settings.UnitSystem
import kotlin.math.roundToInt

/** Whether a pipeline's provider has every key it needs: one for most, two for Exa + Gemini. */
internal data class ProviderKeyPresence(
    val primary: Boolean,
    val search: Boolean,
) {
    val complete: Boolean get() = primary && search
}

/**
 * Whether typing a meal would fail for want of a key: interpretation and research are the two
 * pipelines every entry passes through. An empty map means the key store has not been read yet,
 * which is not the same as the keys being absent.
 */
internal fun Map<ProviderPipeline, ProviderKeyPresence>.needsAiSetup(): Boolean =
    isNotEmpty() && listOf(
        ProviderPipeline.FOOD_INTERPRETATION,
        ProviderPipeline.FOOD_RESEARCH,
    ).any { this[it]?.complete != true }

internal fun mapSettings(
    prefs: AppPreferences,
    plan: NutritionPlanEntity?,
    keys: Map<ProviderPipeline, ProviderKeyPresence>,
    health: HealthConnectUiState,
    stepEstimate: StepCalorieEstimate?,
    language: NomiLanguage,
): SettingsUiState {
    val providers = ProviderPipeline.entries.map { pipeline ->
        val selected = prefs.providerSelection(pipeline)
        AiProviderSetting(
            purpose = pipeline.displayName(),
            provider = selected.providerId.toProviderKind(),
            model = selected.model,
            endpoint = runCatching { selected.toRuntimeConfig().endpoint }
                .getOrElse { selected.endpoint.orEmpty() },
            hasApiKey = keys[pipeline]?.complete == true,
            hasPrimaryApiKey = keys[pipeline]?.primary == true,
            hasSearchApiKey = keys[pipeline]?.search == true &&
                selected.usesExaSearch,
        )
    }
    val reminders = prefs.reminders
    return SettingsUiState(
        themeMode = prefs.theme.toThemeMode(),
        dynamicColor = prefs.dynamicColorEnabled,
        language = language,
        unitSystem = if (prefs.weightUnit == WeightUnitPreference.KILOGRAMS) UnitSystem.METRIC else UnitSystem.IMPERIAL,
        activityTargetAdjustment = prefs.adjustTargetFromActivity,
        calorieEstimateBias = prefs.calorieEstimateBias,
        goalsCardStyle = prefs.goalsCardStyle,
        healthConnectAvailable = health.status != HealthConnectPermissionStatus.UNAVAILABLE,
        healthConnectEnabled = health.status == HealthConnectPermissionStatus.CONNECTED ||
            health.status == HealthConnectPermissionStatus.PARTIAL,
        healthConnect = health.copy(
            estimatedStepCaloriesKcal = stepEstimate?.activeCaloriesKcal,
            stepEstimateUsesProfileHeight = stepEstimate?.usesProfileHeight == true,
        ),
        nutritionTargets = NutritionTargetSetting(
            calories = plan?.calorieTargetKcal?.roundToInt() ?: 2_000,
            proteinGrams = plan?.proteinTargetGrams?.roundToInt() ?: 130,
            carbohydrateGrams = plan?.carbohydrateTargetGrams?.roundToInt() ?: 240,
            fatGrams = plan?.fatTargetGrams?.roundToInt() ?: 65,
            isCustom = plan?.let {
                it.calorieTargetCustom || it.proteinTargetCustom ||
                    it.carbohydrateTargetCustom || it.fatTargetCustom
            } ?: false,
        ),
        trackedMicronutrients = prefs.micronutrients.enabledMicronutrients(),
        aiProviders = providers,
        aiSetupNeeded = keys.needsAiSetup(),
        aiRequestTimeoutDisabled = prefs.aiRequestTimeoutDisabled,
        exaFullPageText = prefs.exaFullPageText,
        openRouterPreferredProvider = prefs.openRouterPreferredProvider,
        aiDebugEnabled = prefs.aiDebugEnabled,
        reminders = listOf(
            ReminderSetting("Breakfast", reminders.breakfast.enabled, reminders.breakfast.localTime),
            ReminderSetting("Lunch", reminders.lunch.enabled, reminders.lunch.localTime),
            ReminderSetting("Dinner", reminders.dinner.enabled, reminders.dinner.localTime),
            ReminderSetting("Daily summary", reminders.dailySummary.enabled, reminders.dailySummary.localTime),
            ReminderSetting("Weight", reminders.weight.enabled, reminders.weight.localTime),
        ),
        appVersion = BuildConfig.VERSION_NAME,
    )
}

internal fun ThemePreference.toThemeMode(): ThemeMode = when (this) {
    ThemePreference.SYSTEM -> ThemeMode.SYSTEM
    ThemePreference.LIGHT -> ThemeMode.LIGHT
    ThemePreference.DARK -> ThemeMode.DARK
}

internal fun ThemeMode.toPreference(): ThemePreference = when (this) {
    ThemeMode.SYSTEM -> ThemePreference.SYSTEM
    ThemeMode.LIGHT -> ThemePreference.LIGHT
    ThemeMode.DARK -> ThemePreference.DARK
}

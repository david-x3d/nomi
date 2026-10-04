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
    /** Local date the activity values belong to; prevents yesterday being shown after midnight. */
    val activityLocalDate: String? = null,
    /** Nomi's net walking-energy estimate, calculated locally from steps and profile data. */
    val estimatedStepCaloriesKcal: Double? = null,
    val stepEstimateUsesProfileHeight: Boolean = false,
    /** Food entries Health Connect currently holds from Nomi, or null before the first sync. */
    val sharedNutritionEntryCount: Int? = null,
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
    /** True once the key store has been read and logging a meal would fail for want of a key. */
    val aiSetupNeeded: Boolean = false,
    val aiRequestTimeoutDisabled: Boolean = false,
    val exaFullPageText: Boolean = false,
    val openRouterPreferredProvider: String = "",
    val aiDebugEnabled: Boolean = false,
    val reminders: List<ReminderSetting> = listOf(
        ReminderSetting("Breakfast", timeText = "08:00"),
        ReminderSetting("Lunch", timeText = "12:30"),
        ReminderSetting("Dinner", timeText = "19:00"),
        ReminderSetting("Daily summary", timeText = "21:00"),
        ReminderSetting("Weight", timeText = "08:00"),
    ),
    val appVersion: String = "1.0.0",
) {
    /**
     * Which keys the AI page can ask for outright, read from the four tasks every entry or photo
     * passes through. Fallback is left out: it is optional and usually sits on another provider.
     */
    /** Whether any task researches through Exa, the only provider that reads source pages. */
    val usesExaOpenRouter: Boolean
        get() = aiProviders.any { it.provider == AiProviderKind.EXA_OPEN_ROUTER }

    val usesExaSearch: Boolean
        get() = aiProviders.any {
            it.provider == AiProviderKind.EXA_GEMINI || it.provider == AiProviderKind.EXA_OPEN_ROUTER
        }

    val aiKeySetup: AiKeySetup
        get() {
            val tasks = aiProviders.take(ESSENTIAL_AI_TASKS)
            val research = tasks.firstOrNull() ?: return AiKeySetup.PerTask
            val readers = tasks.drop(1)
            return when {
                research.provider == AiProviderKind.EXA_GEMINI &&
                    readers.all { it.provider == AiProviderKind.GEMINI } ->
                    AiKeySetup.GeminiWithExa(
                        hasGeminiKey = research.hasPrimaryApiKey,
                        hasExaKey = research.hasSearchApiKey,
                    )
                research.provider != AiProviderKind.EXA_GEMINI &&
                    readers.all {
                        it.provider == research.provider && it.endpoint == research.endpoint
                    } -> AiKeySetup.Single(research)
                else -> AiKeySetup.PerTask
            }
        }
}

/** Research, interpretation, portion changes and photos, in the order the settings list them. */
private const val ESSENTIAL_AI_TASKS = 4

/** The shape of the AI setup, as far as asking for keys goes. */
sealed interface AiKeySetup {
    /** The recommended pair: Exa + Gemini researches, Gemini reads. One Google key and one Exa key. */
    data class GeminiWithExa(val hasGeminiKey: Boolean, val hasExaKey: Boolean) : AiKeySetup

    /** Every task on one provider, which takes one key. */
    data class Single(val provider: AiProviderSetting) : AiKeySetup

    /** Tasks spread over providers in some other way; each is set up on its own page. */
    data object PerTask : AiKeySetup
}

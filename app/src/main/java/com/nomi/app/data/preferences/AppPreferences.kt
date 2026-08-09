package com.nomi.app.data.preferences

import kotlinx.serialization.Serializable

@Serializable
enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

@Serializable
enum class WeightUnitPreference {
    KILOGRAMS,
    POUNDS,
}

@Serializable
enum class HeightUnitPreference {
    CENTIMETERS,
    FEET_AND_INCHES,
}

@Serializable
enum class ProviderPipeline {
    FOOD_RESEARCH,
    FOOD_INTERPRETATION,
    PORTION_CHANGE,
    VISION,
    SMART_FALLBACK,
}

/** Provider configuration deliberately excludes credentials, which belong in SecureSecretStore. */
@Serializable
data class ProviderSelection(
    val providerId: String = "",
    val model: String = "",
    val endpoint: String? = null,
    val advancedParametersJson: String? = null,
)

@Serializable
data class ReminderSetting(
    val enabled: Boolean = false,
    /** Local wall-clock time in HH:mm form. */
    val localTime: String,
    /** ISO day numbers: Monday=1 through Sunday=7. */
    val daysOfWeek: Set<Int> = (1..7).toSet(),
)

@Serializable
data class ReminderPreferences(
    val breakfast: ReminderSetting = ReminderSetting(localTime = "08:00"),
    val lunch: ReminderSetting = ReminderSetting(localTime = "12:30"),
    val dinner: ReminderSetting = ReminderSetting(localTime = "19:00"),
    val dailySummary: ReminderSetting = ReminderSetting(localTime = "20:30"),
    val weight: ReminderSetting = ReminderSetting(
        localTime = "08:00",
        daysOfWeek = setOf(1),
    ),
)

/**
 * Small resumable draft only; calculated plans and completed profiles are persisted in Room.
 * Strings intentionally mirror stable domain codes rather than UI display labels.
 */
@Serializable
data class PersistedOnboardingDraft(
    val currentStep: Int = 0,
    val dateOfBirth: String? = null,
    val energyCalculationSex: String? = null,
    val heightCm: Double? = null,
    val currentWeightKg: Double? = null,
    val goalType: String? = null,
    val targetWeightKg: Double? = null,
    val activityLevel: String? = null,
    val progressionRate: String? = null,
    val manualCalorieTargetKcal: Double? = null,
    val updatedAtEpochMillis: Long = 0,
)

@Serializable
data class AppPreferences(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val dynamicColorEnabled: Boolean = false,
    val germanTranslationEnabled: Boolean = false,
    val weightUnit: WeightUnitPreference = WeightUnitPreference.KILOGRAMS,
    val heightUnit: HeightUnitPreference = HeightUnitPreference.CENTIMETERS,
    val foodResearchProvider: ProviderSelection = ProviderSelection(
        providerId = "openrouter",
        model = DEFAULT_OPENROUTER_MODEL,
    ),
    val foodInterpretationProvider: ProviderSelection = ProviderSelection(
        providerId = "openrouter",
        model = DEFAULT_OPENROUTER_MODEL,
    ),
    val portionChangeProvider: ProviderSelection = ProviderSelection(
        providerId = "openrouter",
        model = DEFAULT_OPENROUTER_MODEL,
    ),
    val visionProvider: ProviderSelection = ProviderSelection(),
    val smartFallbackProvider: ProviderSelection = ProviderSelection(
        providerId = "openrouter",
        model = DEFAULT_OPENROUTER_MODEL,
    ),
    val reminders: ReminderPreferences = ReminderPreferences(),
    val onboardingDraft: PersistedOnboardingDraft? = null,
    val onboardingCompleted: Boolean = false,
    val aiDebugEnabled: Boolean = false,
    val adjustTargetFromActivity: Boolean = false,
    /** When on, AI requests wait for the provider instead of failing at the built-in limit. */
    val aiRequestTimeoutDisabled: Boolean = false,
)

internal const val DEFAULT_OPENROUTER_MODEL = "openai/gpt-5.6-sol"
internal const val RETIRED_OPENROUTER_MODEL = "deepseek/deepseek-v4"
internal const val PREVIOUS_OPENROUTER_MODEL = "deepseek/deepseek-v4-flash"
private val OPENROUTER_GPT_5_6_SOL_ALIASES = setOf(
    "gpt5.6sol",
    "gpt-5.6-sol",
    "openai/gpt5.6sol",
)

/** Keeps existing provider keys usable while replacing retired defaults and common shorthand. */
internal fun ProviderSelection.withSupportedModel(): ProviderSelection =
    if (providerId.equals("openrouter", ignoreCase = true) &&
        (model.equals(RETIRED_OPENROUTER_MODEL, ignoreCase = true) ||
            model.equals(PREVIOUS_OPENROUTER_MODEL, ignoreCase = true) ||
            model.trim().lowercase() in OPENROUTER_GPT_5_6_SOL_ALIASES)
    ) {
        copy(model = DEFAULT_OPENROUTER_MODEL)
    } else {
        this
    }

fun AppPreferences.providerSelection(pipeline: ProviderPipeline): ProviderSelection =
    when (pipeline) {
        ProviderPipeline.FOOD_RESEARCH -> foodResearchProvider
        ProviderPipeline.FOOD_INTERPRETATION -> foodInterpretationProvider
        ProviderPipeline.PORTION_CHANGE -> portionChangeProvider
        ProviderPipeline.VISION -> visionProvider
        ProviderPipeline.SMART_FALLBACK -> smartFallbackProvider
    }

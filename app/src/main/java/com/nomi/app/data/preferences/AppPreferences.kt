package com.nomi.app.data.preferences

import com.nomi.app.domain.Micronutrient
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

/** How the daily goals are drawn on Today. */
@Serializable
enum class GoalsCardStyle {
    /** Nomi's original: a calorie hero with three macro bars beneath it. */
    BARS,

    /** One card: calories as a bar, every other target as a ring. */
    RINGS,
}

/**
 * Where inside an estimate's plausible range the logged value should land.
 *
 * Only estimated foods are affected: a researched manufacturer table has no range to move in.
 */
@Serializable
enum class CalorieEstimateBias {
    STRONGLY_UNDERESTIMATE,
    UNDERESTIMATE,
    NONE,
    OVERESTIMATE,
    STRONGLY_OVERESTIMATE,
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
 * One tracked nutrient beyond the macros. [dailyTarget] is in the nutrient's own storage unit -
 * grams for fiber, sugar, and saturated fat, milligrams for sodium - and keeps its value while
 * tracking is off so turning a nutrient back on restores the number the user chose.
 */
@Serializable
data class MicronutrientSetting(
    val enabled: Boolean = false,
    val dailyTarget: Double,
)

/**
 * Defaults mirror [com.nomi.app.domain.Micronutrient.referenceDailyAmount]. They are repeated as
 * literals because a stored preference must deserialize to a stable value rather than to whatever
 * the current build believes the reference intake to be; a unit test pins the two together so the
 * duplication cannot drift unnoticed.
 */
@Serializable
data class MicronutrientPreferences(
    val fiber: MicronutrientSetting = MicronutrientSetting(dailyTarget = 30.0),
    val sugar: MicronutrientSetting = MicronutrientSetting(dailyTarget = 25.0),
    val saturatedFat: MicronutrientSetting = MicronutrientSetting(dailyTarget = 20.0),
    val sodium: MicronutrientSetting = MicronutrientSetting(dailyTarget = 2_000.0),
) {
    val anyEnabled: Boolean
        get() = fiber.enabled || sugar.enabled || saturatedFat.enabled || sodium.enabled
}

/**
 * What the food log last handed to Health Connect, as day -> (food log id -> written version).
 *
 * The version is the log row's update timestamp, so an unchanged entry is never rewritten and a
 * corrected one always is. Deleted entries leave no row to compare against, which is exactly why
 * the ids are remembered here: without them their Health Connect records could never be found
 * again and would outlive the food they describe.
 */
@Serializable
data class HealthNutritionSyncState(
    val syncedVersions: Map<String, Map<String, Long>> = emptyMap(),
    /** Start timestamps let a later local delete use an idempotent owned-data time range. */
    val syncedStartEpochMillis: Map<String, Map<String, Long>> = emptyMap(),
    /** Set before an owned-record rebuild and cleared only after every record was written. */
    val needsFullRewrite: Boolean = false,
) {
    val entryCount: Int get() = syncedVersions.values.sumOf(Map<String, Long>::size)
    val isEmpty: Boolean get() = syncedVersions.isEmpty() &&
        syncedStartEpochMillis.isEmpty() &&
        !needsFullRewrite
}

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
    /**
     * BCP-47 tag of the interface language, or blank to follow the device locale.
     *
     * A tag rather than an enum because the supported set lives in the UI layer, and an unknown
     * tag from a newer build must degrade to the device locale instead of failing to decode.
     */
    val languageTag: String = "",
    val weightUnit: WeightUnitPreference = WeightUnitPreference.KILOGRAMS,
    val heightUnit: HeightUnitPreference = HeightUnitPreference.CENTIMETERS,
    val foodResearchProvider: ProviderSelection =
        ProviderPipeline.FOOD_RESEARCH.recommendedProvider(),
    val foodInterpretationProvider: ProviderSelection =
        ProviderPipeline.FOOD_INTERPRETATION.recommendedProvider(),
    val portionChangeProvider: ProviderSelection =
        ProviderPipeline.PORTION_CHANGE.recommendedProvider(),
    val visionProvider: ProviderSelection =
        ProviderPipeline.VISION.recommendedProvider(),
    val smartFallbackProvider: ProviderSelection =
        ProviderPipeline.SMART_FALLBACK.recommendedProvider(),
    val reminders: ReminderPreferences = ReminderPreferences(),
    val micronutrients: MicronutrientPreferences = MicronutrientPreferences(),
    val onboardingDraft: PersistedOnboardingDraft? = null,
    val onboardingCompleted: Boolean = false,
    val aiDebugEnabled: Boolean = false,
    val adjustTargetFromActivity: Boolean = false,
    val calorieEstimateBias: CalorieEstimateBias = CalorieEstimateBias.NONE,
    val goalsCardStyle: GoalsCardStyle = GoalsCardStyle.BARS,
    /** When on, AI requests wait for the provider instead of failing at the built-in limit. */
    val aiRequestTimeoutDisabled: Boolean = false,
    /**
     * When on, Exa returns each source page's own text beside its excerpts. Grounding checks
     * then see the table heading and serving sentence an excerpt can leave out, at the cost of
     * Exa billing page text separately and the reading model reading more.
     */
    val exaFullPageText: Boolean = false,
    /**
     * The OpenRouter provider slug Exa + OpenRouter asks for first, such as "baseten". Blank
     * leaves the choice to OpenRouter. Others still serve the request when it is unavailable.
     */
    val openRouterPreferredProvider: String = "",
    val healthNutritionSync: HealthNutritionSyncState = HealthNutritionSyncState(),
)

/**
 * On OpenRouter every pipeline runs on one key. Research uses Sonar because it searches natively
 * and returns its own citations; the remaining pipelines only interpret text or images, so they
 * use the cheap fast model.
 */
internal const val DEFAULT_OPENROUTER_RESEARCH_MODEL = "perplexity/sonar"
internal const val DEFAULT_OPENROUTER_MODEL = "google/gemini-3.5-flash-lite"
internal const val RETIRED_OPENROUTER_MODEL = "deepseek/deepseek-v4"
internal const val PREVIOUS_OPENROUTER_MODEL = "deepseek/deepseek-v4-flash"
internal const val PREVIOUS_OPENROUTER_DEFAULT_MODEL = "openai/gpt-5.6-sol"
internal const val PREVIOUS_OPENROUTER_GEMINI_NUTRITION_MODEL = "google/gemini-3.6-flash"
internal const val DEFAULT_DIRECT_GEMINI_NUTRITION_MODEL = "gemini-2.5-flash"
/** Reading a sentence or a photo needs no research model, so Gemini on its own runs the light one. */
internal const val DEFAULT_DIRECT_GEMINI_MODEL = "gemini-2.5-flash-lite"

/**
 * What Exa + OpenRouter reads with unless the user picks another model: about as capable as
 * Gemini 3.8 Flash on general benchmarks at a tenth of its token price, with structured output.
 */
internal const val DEFAULT_EXA_OPENROUTER_MODEL = "z-ai/glm-5.3-flash"
private val RETIRED_OPENROUTER_MODELS = setOf(
    RETIRED_OPENROUTER_MODEL,
    PREVIOUS_OPENROUTER_MODEL,
    PREVIOUS_OPENROUTER_DEFAULT_MODEL,
    "gpt5.6sol",
    "gpt-5.6-sol",
    "openai/gpt5.6sol",
)

internal fun ProviderPipeline.defaultOpenRouterModel(): String =
    if (this == ProviderPipeline.FOOD_RESEARCH) {
        DEFAULT_OPENROUTER_RESEARCH_MODEL
    } else {
        DEFAULT_OPENROUTER_MODEL
    }

/**
 * What a fresh install runs on, and what Settings marks as recommended.
 *
 * Research goes to Exa + Gemini: Exa finds the pages and Gemini reads them. Everything that only
 * reads - a sentence, a portion change, a photo - goes to Gemini on its own, on the same Google
 * key, so two keys cover all four. Fallback has to search the web, which Gemini alone cannot, and
 * nothing depends on it, so it stays where it was and is simply unused until someone gives it a
 * key.
 */
internal fun ProviderPipeline.recommendedProvider(): ProviderSelection = when (this) {
    ProviderPipeline.FOOD_RESEARCH -> ProviderSelection(
        providerId = "exa-gemini",
        model = DEFAULT_DIRECT_GEMINI_NUTRITION_MODEL,
    )
    ProviderPipeline.FOOD_INTERPRETATION,
    ProviderPipeline.PORTION_CHANGE,
    ProviderPipeline.VISION,
    -> ProviderSelection(providerId = "gemini", model = DEFAULT_DIRECT_GEMINI_MODEL)
    ProviderPipeline.SMART_FALLBACK -> legacyProvider()
}

/** What an install set up before Gemini became the default runs on: OpenRouter, on one key. */
internal fun ProviderPipeline.legacyProvider(): ProviderSelection = ProviderSelection(
    providerId = "openrouter",
    model = defaultOpenRouterModel(),
)

/** Stamped on an install that was already set up when this build first ran. */
internal const val LEGACY_PROVIDER_DEFAULTS = 1

/** Stamped when onboarding finishes on a build whose defaults are [recommendedProvider]. */
internal const val RECOMMENDED_PROVIDER_DEFAULTS = 2

/**
 * Whether a pipeline with no saved choice should still mean OpenRouter.
 *
 * A provider is only written to storage when someone saves it, so most installs hold nothing for
 * most pipelines and run on whatever the default is. Changing the default would therefore move
 * every existing user onto a provider they have no key for. An install that was already past
 * onboarding when this build first ran is stamped as legacy and keeps the old default; every
 * other install gets the new one.
 */
internal fun usesLegacyProviderDefaults(providerDefaults: Int?): Boolean =
    providerDefaults == LEGACY_PROVIDER_DEFAULTS

/**
 * Keeps a deliberately chosen provider untouched while filling in anything unconfigured and
 * replacing models that are no longer the default.
 */
internal fun ProviderSelection.withSupportedModel(
    pipeline: ProviderPipeline = ProviderPipeline.FOOD_INTERPRETATION,
): ProviderSelection {
    if (providerId.isBlank()) {
        return ProviderSelection(
            providerId = "openrouter",
            model = pipeline.defaultOpenRouterModel(),
        )
    }
    if (providerId.equals("exa-gemini", ignoreCase = true)) {
        val slug = model.trim().lowercase()
        return if (
            slug.isEmpty() ||
            slug == PREVIOUS_OPENROUTER_GEMINI_NUTRITION_MODEL
        ) {
            copy(model = DEFAULT_DIRECT_GEMINI_NUTRITION_MODEL)
        } else {
            this
        }
    }
    if (providerId.equals("gemini", ignoreCase = true)) {
        return if (model.isBlank()) copy(model = DEFAULT_DIRECT_GEMINI_MODEL) else this
    }
    if (providerId.equals("exa-openrouter", ignoreCase = true)) {
        return if (model.isBlank()) copy(model = DEFAULT_EXA_OPENROUTER_MODEL) else this
    }
    if (!providerId.equals("openrouter", ignoreCase = true)) return this
    val slug = model.trim().lowercase()
    return if (slug.isEmpty() || slug in RETIRED_OPENROUTER_MODELS) {
        copy(model = pipeline.defaultOpenRouterModel())
    } else {
        this
    }
}

fun MicronutrientPreferences.settingFor(micronutrient: Micronutrient): MicronutrientSetting =
    when (micronutrient) {
        Micronutrient.FIBER -> fiber
        Micronutrient.SUGAR -> sugar
        Micronutrient.SATURATED_FAT -> saturatedFat
        Micronutrient.SODIUM -> sodium
    }

fun MicronutrientPreferences.with(
    micronutrient: Micronutrient,
    setting: MicronutrientSetting,
): MicronutrientPreferences = when (micronutrient) {
    Micronutrient.FIBER -> copy(fiber = setting)
    Micronutrient.SUGAR -> copy(sugar = setting)
    Micronutrient.SATURATED_FAT -> copy(saturatedFat = setting)
    Micronutrient.SODIUM -> copy(sodium = setting)
}

/** The nutrients the user is currently tracking, in the order they are presented. */
fun MicronutrientPreferences.enabledMicronutrients(): List<Micronutrient> =
    Micronutrient.entries.filter { settingFor(it).enabled }

/**
 * Guards the stored target against a typo or a value from an older build whose reference has
 * since changed. Anything unusable falls back to the nutrient's reference intake rather than
 * leaving a progress bar dividing by zero.
 */
fun MicronutrientSetting.resolvedTarget(micronutrient: Micronutrient): Double =
    dailyTarget.takeIf { it.isFinite() && it > 0.0 && it <= micronutrient.maximumTarget }
        ?: micronutrient.referenceDailyAmount

fun AppPreferences.providerSelection(pipeline: ProviderPipeline): ProviderSelection =
    when (pipeline) {
        ProviderPipeline.FOOD_RESEARCH -> foodResearchProvider
        ProviderPipeline.FOOD_INTERPRETATION -> foodInterpretationProvider
        ProviderPipeline.PORTION_CHANGE -> portionChangeProvider
        ProviderPipeline.VISION -> visionProvider
        ProviderPipeline.SMART_FALLBACK -> smartFallbackProvider
    }

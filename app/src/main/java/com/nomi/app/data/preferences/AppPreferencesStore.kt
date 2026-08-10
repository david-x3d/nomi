package com.nomi.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.nomiPreferencesDataStore by preferencesDataStore(name = "nomi_preferences")

interface AppPreferencesStore {
    val preferences: Flow<AppPreferences>

    suspend fun setAppearance(theme: ThemePreference, dynamicColorEnabled: Boolean)
    suspend fun setGermanTranslationEnabled(enabled: Boolean)
    suspend fun setUnits(weight: WeightUnitPreference, height: HeightUnitPreference)
    suspend fun setProvider(pipeline: ProviderPipeline, selection: ProviderSelection)
    suspend fun setReminders(reminders: ReminderPreferences)
    suspend fun setMicronutrients(micronutrients: MicronutrientPreferences)
    suspend fun setOnboardingDraft(draft: PersistedOnboardingDraft?)
    suspend fun markOnboardingCompleted(completed: Boolean, clearDraft: Boolean = completed)
    suspend fun setAiDebugEnabled(enabled: Boolean)
    suspend fun setAdjustTargetFromActivity(enabled: Boolean)
    suspend fun setCalorieEstimateBias(bias: CalorieEstimateBias)
    suspend fun setGoalsCardStyle(style: GoalsCardStyle)
    suspend fun setAiRequestTimeoutDisabled(disabled: Boolean)
}

class DataStoreAppPreferencesStore(
    context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    },
) : AppPreferencesStore {
    private val dataStore = context.applicationContext.nomiPreferencesDataStore

    override val preferences: Flow<AppPreferences> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map(::decodePreferences)

    override suspend fun setAppearance(
        theme: ThemePreference,
        dynamicColorEnabled: Boolean,
    ) {
        dataStore.edit { values ->
            values[Keys.THEME] = theme.name
            values[Keys.DYNAMIC_COLOR] = dynamicColorEnabled
        }
    }

    override suspend fun setGermanTranslationEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.GERMAN_TRANSLATION_ENABLED] = enabled }
    }

    override suspend fun setUnits(
        weight: WeightUnitPreference,
        height: HeightUnitPreference,
    ) {
        dataStore.edit { values ->
            values[Keys.WEIGHT_UNIT] = weight.name
            values[Keys.HEIGHT_UNIT] = height.name
        }
    }

    override suspend fun setProvider(
        pipeline: ProviderPipeline,
        selection: ProviderSelection,
    ) {
        dataStore.edit { values ->
            values[Keys.provider(pipeline)] =
                json.encodeToString(selection.withSupportedModel(pipeline))
        }
    }

    override suspend fun setReminders(reminders: ReminderPreferences) {
        dataStore.edit { values ->
            values[Keys.REMINDERS] = json.encodeToString(reminders)
        }
    }

    override suspend fun setMicronutrients(micronutrients: MicronutrientPreferences) {
        dataStore.edit { values ->
            values[Keys.MICRONUTRIENTS] = json.encodeToString(micronutrients)
        }
    }

    override suspend fun setOnboardingDraft(draft: PersistedOnboardingDraft?) {
        dataStore.edit { values ->
            if (draft == null) {
                values.remove(Keys.ONBOARDING_DRAFT)
            } else {
                values[Keys.ONBOARDING_DRAFT] = json.encodeToString(draft)
            }
        }
    }

    override suspend fun markOnboardingCompleted(completed: Boolean, clearDraft: Boolean) {
        dataStore.edit { values ->
            values[Keys.ONBOARDING_COMPLETED] = completed
            if (clearDraft) values.remove(Keys.ONBOARDING_DRAFT)
        }
    }

    override suspend fun setAiDebugEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.AI_DEBUG_ENABLED] = enabled }
    }

    override suspend fun setAdjustTargetFromActivity(enabled: Boolean) {
        dataStore.edit { values -> values[Keys.ADJUST_TARGET_FROM_ACTIVITY] = enabled }
    }

    override suspend fun setCalorieEstimateBias(bias: CalorieEstimateBias) {
        dataStore.edit { values -> values[Keys.CALORIE_ESTIMATE_BIAS] = bias.name }
    }

    override suspend fun setGoalsCardStyle(style: GoalsCardStyle) {
        dataStore.edit { values -> values[Keys.GOALS_CARD_STYLE] = style.name }
    }

    override suspend fun setAiRequestTimeoutDisabled(disabled: Boolean) {
        dataStore.edit { values -> values[Keys.AI_REQUEST_TIMEOUT_DISABLED] = disabled }
    }

    private fun decodePreferences(values: Preferences): AppPreferences {
        val defaults = AppPreferences()
        return AppPreferences(
            theme = values[Keys.THEME]
                ?.let { encoded -> enumValues<ThemePreference>().firstOrNull { it.name == encoded } }
                ?: defaults.theme,
            dynamicColorEnabled = values[Keys.DYNAMIC_COLOR] ?: defaults.dynamicColorEnabled,
            germanTranslationEnabled = values[Keys.GERMAN_TRANSLATION_ENABLED]
                ?: defaults.germanTranslationEnabled,
            weightUnit = values[Keys.WEIGHT_UNIT]
                ?.let { encoded -> enumValues<WeightUnitPreference>().firstOrNull { it.name == encoded } }
                ?: defaults.weightUnit,
            heightUnit = values[Keys.HEIGHT_UNIT]
                ?.let { encoded -> enumValues<HeightUnitPreference>().firstOrNull { it.name == encoded } }
                ?: defaults.heightUnit,
            foodResearchProvider = decode(
                values[Keys.FOOD_RESEARCH_PROVIDER],
                defaults.foodResearchProvider,
            ).withSupportedModel(ProviderPipeline.FOOD_RESEARCH),
            foodInterpretationProvider = decode(
                values[Keys.FOOD_INTERPRETATION_PROVIDER],
                defaults.foodInterpretationProvider,
            ).withSupportedModel(ProviderPipeline.FOOD_INTERPRETATION),
            portionChangeProvider = decode(
                values[Keys.PORTION_CHANGE_PROVIDER],
                defaults.portionChangeProvider,
            ).withSupportedModel(ProviderPipeline.PORTION_CHANGE),
            visionProvider = decode(
                values[Keys.VISION_PROVIDER],
                defaults.visionProvider,
            ).withSupportedModel(ProviderPipeline.VISION),
            smartFallbackProvider = decode(
                values[Keys.SMART_FALLBACK_PROVIDER],
                defaults.smartFallbackProvider,
            ).withSupportedModel(ProviderPipeline.SMART_FALLBACK),
            reminders = decode(values[Keys.REMINDERS], defaults.reminders),
            micronutrients = decode(values[Keys.MICRONUTRIENTS], defaults.micronutrients),
            onboardingDraft = decodeOrNull(values[Keys.ONBOARDING_DRAFT]),
            onboardingCompleted = values[Keys.ONBOARDING_COMPLETED] ?: false,
            aiDebugEnabled = values[Keys.AI_DEBUG_ENABLED] ?: false,
            adjustTargetFromActivity = values[Keys.ADJUST_TARGET_FROM_ACTIVITY] ?: false,
            calorieEstimateBias = values[Keys.CALORIE_ESTIMATE_BIAS]
                ?.let { encoded ->
                    enumValues<CalorieEstimateBias>().firstOrNull { it.name == encoded }
                }
                ?: defaults.calorieEstimateBias,
            goalsCardStyle = values[Keys.GOALS_CARD_STYLE]
                ?.let { encoded -> enumValues<GoalsCardStyle>().firstOrNull { it.name == encoded } }
                ?: defaults.goalsCardStyle,
            aiRequestTimeoutDisabled = values[Keys.AI_REQUEST_TIMEOUT_DISABLED]
                ?: defaults.aiRequestTimeoutDisabled,
        )
    }

    private inline fun <reified T> decode(encoded: String?, fallback: T): T =
        encoded?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() } ?: fallback

    private inline fun <reified T> decodeOrNull(encoded: String?): T? =
        encoded?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    private object Keys {
        val THEME = stringPreferencesKey("appearance.theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("appearance.dynamic_color")
        val GERMAN_TRANSLATION_ENABLED = booleanPreferencesKey("language.german_translation_enabled")
        val WEIGHT_UNIT = stringPreferencesKey("units.weight")
        val HEIGHT_UNIT = stringPreferencesKey("units.height")
        val FOOD_RESEARCH_PROVIDER = stringPreferencesKey("providers.food_research")
        val FOOD_INTERPRETATION_PROVIDER = stringPreferencesKey("providers.food_interpretation")
        val PORTION_CHANGE_PROVIDER = stringPreferencesKey("providers.portion_change")
        val VISION_PROVIDER = stringPreferencesKey("providers.vision")
        val SMART_FALLBACK_PROVIDER = stringPreferencesKey("providers.smart_fallback")
        val REMINDERS = stringPreferencesKey("reminders")
        val MICRONUTRIENTS = stringPreferencesKey("nutrition.micronutrients")
        val ONBOARDING_DRAFT = stringPreferencesKey("onboarding.draft")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding.completed")
        val AI_DEBUG_ENABLED = booleanPreferencesKey("developer.ai_debug")
        val ADJUST_TARGET_FROM_ACTIVITY = booleanPreferencesKey("nutrition.adjust_from_activity")
        val CALORIE_ESTIMATE_BIAS = stringPreferencesKey("nutrition.calorie_estimate_bias")
        val GOALS_CARD_STYLE = stringPreferencesKey("appearance.goals_card_style")
        val AI_REQUEST_TIMEOUT_DISABLED = booleanPreferencesKey("ai.request_timeout_disabled")

        fun provider(pipeline: ProviderPipeline) = when (pipeline) {
            ProviderPipeline.FOOD_RESEARCH -> FOOD_RESEARCH_PROVIDER
            ProviderPipeline.FOOD_INTERPRETATION -> FOOD_INTERPRETATION_PROVIDER
            ProviderPipeline.PORTION_CHANGE -> PORTION_CHANGE_PROVIDER
            ProviderPipeline.VISION -> VISION_PROVIDER
            ProviderPipeline.SMART_FALLBACK -> SMART_FALLBACK_PROVIDER
        }
    }
}

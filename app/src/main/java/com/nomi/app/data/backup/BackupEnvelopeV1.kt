package com.nomi.app.data.backup

import com.nomi.app.data.preferences.HeightUnitPreference
import com.nomi.app.data.preferences.ReminderPreferences
import com.nomi.app.data.preferences.ThemePreference
import com.nomi.app.data.preferences.WeightUnitPreference
import kotlinx.serialization.Serializable

/**
 * Stable, portable Nomi backup format.
 *
 * Credentials, provider advanced-parameter JSON, onboarding drafts, AI debug events, and other
 * transient state are intentionally not representable in this schema. API keys remain only in
 * [com.nomi.app.data.security.SecureSecretStore] on the device that owns them.
 */
@Serializable
data class BackupEnvelopeV1(
    val format: String = FORMAT,
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAtEpochMillis: Long,
    val appVersionName: String,
    val payload: BackupPayloadV1,
) {
    companion object {
        const val FORMAT: String = "nomi-backup"
        const val SCHEMA_VERSION: Int = 1
    }
}

@Serializable
data class BackupPayloadV1(
    val preferences: BackupPreferencesV1,
    val userProfile: BackupUserProfileV1?,
    val nutritionPlans: List<BackupNutritionPlanV1>,
    val nutritionSources: List<BackupNutritionSourceV1>,
    val foods: List<BackupFoodV1>,
    val foodServings: List<BackupFoodServingV1>,
    val foodAliases: List<BackupFoodAliasV1>,
    val favoriteFoods: List<BackupFavoriteFoodV1>,
    val foodLogs: List<BackupFoodLogV1>,
    val savedMeals: List<BackupSavedMealV1>,
    val savedMealItems: List<BackupSavedMealItemV1>,
    val weightEntries: List<BackupWeightEntryV1>,
)

/** Safe settings only: provider credentials and arbitrary advanced JSON are excluded by design. */
@Serializable
data class BackupPreferencesV1(
    val theme: ThemePreference,
    val dynamicColorEnabled: Boolean,
    /**
     * Kept so a backup written before the language picker still restores its language: the old
     * boolean is the only record such a file has, and [languageTag] wins when both are present.
     */
    val germanTranslationEnabled: Boolean = false,
    val languageTag: String? = null,
    val weightUnit: WeightUnitPreference,
    val heightUnit: HeightUnitPreference,
    val foodResearchProvider: BackupProviderSelectionV1,
    val foodInterpretationProvider: BackupProviderSelectionV1,
    val portionChangeProvider: BackupProviderSelectionV1,
    val visionProvider: BackupProviderSelectionV1,
    val reminders: ReminderPreferences,
    val onboardingCompleted: Boolean,
    val adjustTargetFromActivity: Boolean,
)

@Serializable
data class BackupProviderSelectionV1(
    val providerId: String,
    val model: String,
    /** Endpoint only; URI user-info, query parameters, and fragments are rejected as secret-prone. */
    val endpoint: String? = null,
)

@Serializable
data class BackupNutritionValuesV1(
    val caloriesKcal: Double,
    val proteinGrams: Double,
    val carbohydrateGrams: Double,
    val fatGrams: Double,
    val fiberGrams: Double? = null,
    val sugarGrams: Double? = null,
    val saturatedFatGrams: Double? = null,
    val sodiumMilligrams: Double? = null,
)

@Serializable
data class BackupNutritionSourceSnapshotV1(
    val kind: String,
    val providerName: String? = null,
    val displayName: String? = null,
    val externalId: String? = null,
    val url: String? = null,
    /** Added after the first backups shipped, so it stays optional when reading an older file. */
    val citedUrls: String? = null,
    val confidence: Double? = null,
    val productName: String? = null,
    val servingQuantity: Double? = null,
    val servingUnit: String? = null,
    val calorieExplanation: String? = null,
    val retrievedAtEpochMillis: Long? = null,
    val verifiedAtEpochMillis: Long? = null,
)

@Serializable
data class BackupUserProfileV1(
    val id: Int,
    val dateOfBirth: String,
    val energyCalculationSex: String? = null,
    val heightCm: Double? = null,
    val startingWeightKg: Double,
    val goalType: String,
    val targetWeightKg: Double? = null,
    val activityLevel: String,
    val progressionRate: String? = null,
    val onboardingCompleted: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class BackupNutritionPlanV1(
    val id: Long,
    val profileId: Int,
    val version: Int,
    val effectiveFromLocalDate: String,
    val calculationMethod: String,
    val activityMultiplier: Double? = null,
    val bmrKcal: Double? = null,
    val maintenanceKcal: Double? = null,
    val goalAdjustmentKcal: Double? = null,
    val calorieTargetKcal: Double,
    val proteinTargetGrams: Double,
    val carbohydrateTargetGrams: Double,
    val fatTargetGrams: Double,
    val calorieTargetCustom: Boolean,
    val proteinTargetCustom: Boolean,
    val carbohydrateTargetCustom: Boolean,
    val fatTargetCustom: Boolean,
    val changeReason: String? = null,
    val createdAtEpochMillis: Long,
)

@Serializable
data class BackupNutritionSourceV1(
    val id: Long,
    val kind: String,
    val providerName: String? = null,
    val displayName: String,
    val externalId: String? = null,
    val url: String? = null,
    val license: String? = null,
    val retrievedAtEpochMillis: Long,
    val verifiedAtEpochMillis: Long? = null,
)

@Serializable
data class BackupFoodV1(
    val id: Long,
    val canonicalName: String,
    val normalizedName: String,
    val brand: String? = null,
    val barcode: String? = null,
    val nutritionPer100g: BackupNutritionValuesV1,
    val nutritionSourceId: Long? = null,
    val isUserCreated: Boolean,
    val isEstimated: Boolean,
    val lastVerifiedAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class BackupFoodServingV1(
    val id: Long,
    val foodId: Long,
    val name: String,
    val normalizedName: String,
    val amount: Double,
    val unit: String,
    val grams: Double,
    val isDefault: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class BackupFoodAliasV1(
    val id: Long,
    val foodId: Long,
    val alias: String,
    val normalizedAlias: String,
    val locale: String? = null,
    val createdAtEpochMillis: Long,
)

@Serializable
data class BackupFavoriteFoodV1(
    val id: Long,
    val foodId: Long,
    val foodServingId: Long? = null,
    val typicalAmount: Double,
    val typicalUnit: String,
    val typicalGrams: Double? = null,
    val createdAtEpochMillis: Long,
    val lastUsedAtEpochMillis: Long? = null,
)

@Serializable
data class BackupFoodLogV1(
    val id: Long,
    val foodId: Long? = null,
    val foodServingId: Long? = null,
    val nutritionSourceId: Long? = null,
    val entryGroupId: String? = null,
    val originalInput: String? = null,
    val mealCategory: String,
    val displayNameSnapshot: String,
    val brandSnapshot: String? = null,
    val amount: Double,
    val unit: String,
    val grams: Double? = null,
    val nutritionSnapshot: BackupNutritionValuesV1,
    val sourceSnapshot: BackupNutritionSourceSnapshotV1,
    val isEstimated: Boolean,
    val inputMethod: String,
    val notes: String? = null,
    val localDate: String,
    val loggedAtEpochMillis: Long,
    val zoneId: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class BackupSavedMealV1(
    val id: Long,
    val name: String,
    val normalizedName: String,
    val notes: String? = null,
    val defaultMealCategory: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val lastUsedAtEpochMillis: Long? = null,
)

@Serializable
data class BackupSavedMealItemV1(
    val id: Long,
    val savedMealId: Long,
    val foodId: Long? = null,
    val foodServingId: Long? = null,
    val sortOrder: Int,
    val displayNameSnapshot: String,
    val brandSnapshot: String? = null,
    val amount: Double,
    val unit: String,
    val grams: Double? = null,
    val nutritionSnapshot: BackupNutritionValuesV1,
    val sourceSnapshot: BackupNutritionSourceSnapshotV1,
    val isEstimated: Boolean,
)

@Serializable
data class BackupWeightEntryV1(
    val id: Long,
    val weightKg: Double,
    val localDate: String,
    val measuredAtEpochMillis: Long,
    val zoneId: String,
    val note: String? = null,
    val source: String,
    val externalId: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

package com.nomi.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** The app has exactly one local profile; DAO/repository entry points always use [SINGLETON_ID]. */
@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "date_of_birth") val dateOfBirth: String,
    @ColumnInfo(name = "energy_calculation_sex") val energyCalculationSex: String? = null,
    @ColumnInfo(name = "height_cm") val heightCm: Double? = null,
    @ColumnInfo(name = "starting_weight_kg") val startingWeightKg: Double,
    @ColumnInfo(name = "goal_type") val goalType: String,
    @ColumnInfo(name = "target_weight_kg") val targetWeightKg: Double? = null,
    @ColumnInfo(name = "activity_level") val activityLevel: String,
    @ColumnInfo(name = "progression_rate") val progressionRate: String? = null,
    @ColumnInfo(name = "onboarding_completed", defaultValue = "0")
    val onboardingCompleted: Boolean = false,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
) {
    init {
        require(id == SINGLETON_ID) { "Only the singleton Nomi profile can be stored" }
    }

    companion object {
        const val SINGLETON_ID: Int = 1
    }
}

/**
 * Plans are append-only versions. Editing goals creates the next version rather than mutating
 * the values used to interpret older days.
 */
@Entity(
    tableName = "nutrition_plans",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["profile_id", "version"], unique = true),
        Index(value = ["profile_id", "effective_from_local_date"]),
        Index(value = ["created_at_epoch_millis"]),
    ],
)
data class NutritionPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "profile_id") val profileId: Int = UserProfileEntity.SINGLETON_ID,
    val version: Int,
    @ColumnInfo(name = "effective_from_local_date") val effectiveFromLocalDate: String,
    @ColumnInfo(name = "calculation_method") val calculationMethod: String,
    @ColumnInfo(name = "activity_multiplier") val activityMultiplier: Double? = null,
    @ColumnInfo(name = "bmr_kcal") val bmrKcal: Double? = null,
    @ColumnInfo(name = "maintenance_kcal") val maintenanceKcal: Double? = null,
    @ColumnInfo(name = "goal_adjustment_kcal") val goalAdjustmentKcal: Double? = null,
    @ColumnInfo(name = "calorie_target_kcal") val calorieTargetKcal: Double,
    @ColumnInfo(name = "protein_target_grams") val proteinTargetGrams: Double,
    @ColumnInfo(name = "carbohydrate_target_grams") val carbohydrateTargetGrams: Double,
    @ColumnInfo(name = "fat_target_grams") val fatTargetGrams: Double,
    @ColumnInfo(name = "calorie_target_custom", defaultValue = "0")
    val calorieTargetCustom: Boolean = false,
    @ColumnInfo(name = "protein_target_custom", defaultValue = "0")
    val proteinTargetCustom: Boolean = false,
    @ColumnInfo(name = "carbohydrate_target_custom", defaultValue = "0")
    val carbohydrateTargetCustom: Boolean = false,
    @ColumnInfo(name = "fat_target_custom", defaultValue = "0")
    val fatTargetCustom: Boolean = false,
    @ColumnInfo(name = "change_reason") val changeReason: String? = null,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
)

package com.nomi.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.nomi.app.data.local.entity.NutritionPlanEntity
import com.nomi.app.data.local.entity.UserProfileEntity
import com.nomi.app.data.local.entity.WeightEntryEntity
import com.nomi.app.data.local.model.OnboardingPersistenceResult
import com.nomi.app.data.local.model.ProfileWithPlanHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfilePlanDao {
    @Query("SELECT * FROM user_profiles WHERE id = 1 LIMIT 1")
    fun observeProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE id = 1 LIMIT 1")
    suspend fun profileNow(): UserProfileEntity?

    @Transaction
    @Query("SELECT * FROM user_profiles WHERE id = 1 LIMIT 1")
    fun observeProfileWithPlanHistory(): Flow<ProfileWithPlanHistory?>

    @Upsert
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Update
    suspend fun updateProfile(profile: UserProfileEntity): Int

    @Query("DELETE FROM user_profiles WHERE id = 1")
    suspend fun deleteProfile(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlan(plan: NutritionPlanEntity): Long

    @Update
    suspend fun updatePlan(plan: NutritionPlanEntity): Int

    @Delete
    suspend fun deletePlan(plan: NutritionPlanEntity): Int

    @Query(
        """
        SELECT * FROM nutrition_plans
        WHERE profile_id = :profileId
        ORDER BY version DESC
        LIMIT 1
        """,
    )
    fun observeCurrentPlan(
        profileId: Int = UserProfileEntity.SINGLETON_ID,
    ): Flow<NutritionPlanEntity?>

    @Query(
        """
        SELECT * FROM nutrition_plans
        WHERE profile_id = :profileId
        ORDER BY version DESC
        LIMIT 1
        """,
    )
    suspend fun currentPlanNow(
        profileId: Int = UserProfileEntity.SINGLETON_ID,
    ): NutritionPlanEntity?

    @Query(
        """
        SELECT * FROM nutrition_plans
        WHERE profile_id = :profileId
          AND effective_from_local_date <= :localDate
        ORDER BY effective_from_local_date DESC, version DESC
        LIMIT 1
        """,
    )
    suspend fun planEffectiveOn(
        localDate: String,
        profileId: Int = UserProfileEntity.SINGLETON_ID,
    ): NutritionPlanEntity?

    @Query(
        """
        SELECT * FROM nutrition_plans
        WHERE profile_id = :profileId
        ORDER BY version DESC
        """,
    )
    fun observePlanHistory(
        profileId: Int = UserProfileEntity.SINGLETON_ID,
    ): Flow<List<NutritionPlanEntity>>

    @Query(
        """
        SELECT COALESCE(MAX(version), 0) + 1
        FROM nutrition_plans
        WHERE profile_id = :profileId
        """,
    )
    suspend fun nextPlanVersion(
        profileId: Int = UserProfileEntity.SINGLETON_ID,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInitialWeight(entry: WeightEntryEntity): Long

    @Query("SELECT id FROM weight_entries ORDER BY measured_at_epoch_millis ASC, id ASC LIMIT 1")
    suspend fun firstWeightEntryId(): Long?

    /**
     * Commits the profile, first plan version, and starting weight atomically. It is idempotent
     * after a successful commit, which lets a caller safely retry if the subsequent preference
     * update was interrupted.
     */
    @Transaction
    suspend fun completeOnboarding(
        profile: UserProfileEntity,
        plan: NutritionPlanEntity,
        initialWeight: WeightEntryEntity,
    ): OnboardingPersistenceResult {
        val wasAlreadyComplete = profileNow()?.onboardingCompleted == true
        val completedProfile = profile.copy(
            id = UserProfileEntity.SINGLETON_ID,
            onboardingCompleted = true,
        )
        upsertProfile(completedProfile)

        if (wasAlreadyComplete) {
            val existingPlan = checkNotNull(currentPlanNow()) {
                "Completed profile is missing its nutrition plan"
            }
            val existingWeightId = checkNotNull(firstWeightEntryId()) {
                "Completed profile is missing its starting weight"
            }
            return OnboardingPersistenceResult(
                profile = completedProfile,
                planId = existingPlan.id,
                weightEntryId = existingWeightId,
            )
        }

        val planId = insertPlan(
            plan.copy(
                id = 0,
                profileId = UserProfileEntity.SINGLETON_ID,
                version = nextPlanVersion(),
            ),
        )
        val weightId = insertInitialWeight(initialWeight.copy(id = 0))
        return OnboardingPersistenceResult(completedProfile, planId, weightId)
    }
}

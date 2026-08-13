package com.nomi.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.nomi.app.data.local.entity.FoodLogEntity
import com.nomi.app.data.local.model.DailyNutritionTotals
import com.nomi.app.data.local.model.FoodLogWithCatalogReference
import com.nomi.app.data.local.model.MealNutritionTotals
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface FoodLogDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLog(log: FoodLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLogs(logs: List<FoodLogEntity>): List<Long>

    @Update
    suspend fun updateLog(log: FoodLogEntity): Int

    @Delete
    suspend fun deleteLog(log: FoodLogEntity): Int

    @Query("DELETE FROM food_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long): Int

    @Query("SELECT * FROM food_logs WHERE id = :id LIMIT 1")
    suspend fun log(id: Long): FoodLogEntity?

    @Query(
        """
        SELECT * FROM food_logs
        WHERE entry_group_id = :entryGroupId
        ORDER BY logged_at_epoch_millis, id
        """,
    )
    suspend fun logsByEntryGroupId(entryGroupId: String): List<FoodLogEntity>

    @Transaction
    @Query("SELECT * FROM food_logs WHERE id = :id LIMIT 1")
    suspend fun logWithCatalogReference(id: Long): FoodLogWithCatalogReference?

    @Query(
        """
        SELECT * FROM food_logs
        WHERE local_date = :localDate
        ORDER BY
            CASE meal_category
                WHEN 'breakfast' THEN 0
                WHEN 'lunch' THEN 1
                WHEN 'dinner' THEN 2
                WHEN 'snack' THEN 3
                ELSE 4
            END,
            logged_at_epoch_millis,
            id
        """,
    )
    fun observeDayLogs(localDate: String): Flow<List<FoodLogEntity>>

    @Query(
        """
        SELECT * FROM food_logs
        WHERE local_date = :localDate
        ORDER BY logged_at_epoch_millis, id
        """,
    )
    suspend fun dayLogs(localDate: String): List<FoodLogEntity>

    @Query(
        """
        SELECT * FROM food_logs
        WHERE local_date = :localDate AND meal_category = :mealCategory
        ORDER BY logged_at_epoch_millis, id
        """,
    )
    suspend fun mealLogs(localDate: String, mealCategory: String): List<FoodLogEntity>

    @Query(
        """
        SELECT
            :localDate AS localDate,
            COALESCE(SUM(nutrition_snapshot_calories_kcal), 0.0) AS caloriesKcal,
            COALESCE(SUM(nutrition_snapshot_protein_grams), 0.0) AS proteinGrams,
            COALESCE(SUM(nutrition_snapshot_carbohydrate_grams), 0.0) AS carbohydrateGrams,
            COALESCE(SUM(nutrition_snapshot_fat_grams), 0.0) AS fatGrams
        FROM food_logs
        WHERE local_date = :localDate
        """,
    )
    fun observeDayTotals(localDate: String): Flow<DailyNutritionTotals>

    @Query(
        """
        SELECT
            local_date AS localDate,
            COALESCE(SUM(nutrition_snapshot_calories_kcal), 0.0) AS caloriesKcal,
            COALESCE(SUM(nutrition_snapshot_protein_grams), 0.0) AS proteinGrams,
            COALESCE(SUM(nutrition_snapshot_carbohydrate_grams), 0.0) AS carbohydrateGrams,
            COALESCE(SUM(nutrition_snapshot_fat_grams), 0.0) AS fatGrams
        FROM food_logs
        WHERE local_date BETWEEN :startLocalDate AND :endLocalDate
        GROUP BY local_date
        ORDER BY local_date
        """,
    )
    fun observeDailyTotalsInRange(
        startLocalDate: String,
        endLocalDate: String,
    ): Flow<List<DailyNutritionTotals>>

    @Query(
        """
        SELECT
            meal_category AS mealCategory,
            COALESCE(SUM(nutrition_snapshot_calories_kcal), 0.0) AS caloriesKcal,
            COALESCE(SUM(nutrition_snapshot_protein_grams), 0.0) AS proteinGrams,
            COALESCE(SUM(nutrition_snapshot_carbohydrate_grams), 0.0) AS carbohydrateGrams,
            COALESCE(SUM(nutrition_snapshot_fat_grams), 0.0) AS fatGrams
        FROM food_logs
        WHERE local_date = :localDate
        GROUP BY meal_category
        ORDER BY MIN(logged_at_epoch_millis)
        """,
    )
    fun observeMealTotals(localDate: String): Flow<List<MealNutritionTotals>>

    @Query(
        """
        SELECT * FROM food_logs
        WHERE local_date BETWEEN :startLocalDate AND :endLocalDate
        ORDER BY local_date, logged_at_epoch_millis, id
        """,
    )
    suspend fun logsInRange(startLocalDate: String, endLocalDate: String): List<FoodLogEntity>

    @Query(
        """
        SELECT * FROM food_logs
        WHERE local_date BETWEEN :startLocalDate AND :endLocalDate
        ORDER BY local_date DESC, logged_at_epoch_millis DESC, id DESC
        """,
    )
    fun observeHistoryRange(
        startLocalDate: String,
        endLocalDate: String,
    ): Flow<List<FoodLogEntity>>

    @Query(
        """
        SELECT * FROM food_logs
        WHERE LOWER(display_name_snapshot) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(COALESCE(brand_snapshot, '')) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(COALESCE(notes, '')) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(meal_category) LIKE '%' || LOWER(:query) || '%'
        ORDER BY local_date DESC, logged_at_epoch_millis DESC
        LIMIT :limit
        """,
    )
    fun searchHistory(query: String, limit: Int = 100): Flow<List<FoodLogEntity>>

    @Query(
        """
        SELECT * FROM food_logs
        WHERE local_date BETWEEN :startLocalDate AND :endLocalDate
          AND (
              LOWER(display_name_snapshot) LIKE '%' || LOWER(:query) || '%'
              OR LOWER(COALESCE(brand_snapshot, '')) LIKE '%' || LOWER(:query) || '%'
              OR LOWER(COALESCE(notes, '')) LIKE '%' || LOWER(:query) || '%'
              OR LOWER(meal_category) LIKE '%' || LOWER(:query) || '%'
          )
        ORDER BY local_date DESC, logged_at_epoch_millis DESC
        LIMIT :limit
        """,
    )
    fun searchHistoryInRange(
        query: String,
        startLocalDate: String,
        endLocalDate: String,
        limit: Int = 100,
    ): Flow<List<FoodLogEntity>>

    @Transaction
    suspend fun copyMeal(
        sourceLocalDate: String,
        sourceMealCategory: String,
        targetLocalDate: String,
        targetMealCategory: String = sourceMealCategory,
        targetStartEpochMillis: Long,
        targetZoneId: String,
    ): List<Long> {
        val source = mealLogs(sourceLocalDate, sourceMealCategory)
        if (source.isEmpty()) return emptyList()
        val groupId = UUID.randomUUID().toString()
        return insertLogs(
            source.mapIndexed { index, log ->
                log.copy(
                    id = 0,
                    entryGroupId = groupId,
                    mealCategory = targetMealCategory,
                    localDate = targetLocalDate,
                    loggedAtEpochMillis = targetStartEpochMillis + index,
                    zoneId = targetZoneId,
                    inputMethod = "copied_meal",
                    createdAtEpochMillis = targetStartEpochMillis,
                    updatedAtEpochMillis = targetStartEpochMillis,
                )
            },
        )
    }

    @Transaction
    suspend fun copyDay(
        sourceLocalDate: String,
        targetLocalDate: String,
        targetStartEpochMillis: Long,
        targetZoneId: String,
    ): List<Long> {
        val source = dayLogs(sourceLocalDate)
        if (source.isEmpty()) return emptyList()
        val copiedGroupIds = mutableMapOf<String, String>()
        return insertLogs(
            source.mapIndexed { index, log ->
                val sourceGroup = log.entryGroupId ?: "single:${log.id}"
                val targetGroup = copiedGroupIds.getOrPut(sourceGroup) {
                    UUID.randomUUID().toString()
                }
                log.copy(
                    id = 0,
                    entryGroupId = targetGroup,
                    localDate = targetLocalDate,
                    loggedAtEpochMillis = targetStartEpochMillis + index,
                    zoneId = targetZoneId,
                    inputMethod = "copied_day",
                    createdAtEpochMillis = targetStartEpochMillis,
                    updatedAtEpochMillis = targetStartEpochMillis,
                )
            },
        )
    }
}

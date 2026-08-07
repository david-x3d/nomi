package com.nomi.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.nomi.app.data.local.entity.SavedMealEntity
import com.nomi.app.data.local.entity.SavedMealItemEntity
import com.nomi.app.data.local.model.SavedMealWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedMealDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMeal(meal: SavedMealEntity): Long

    @Update
    suspend fun updateMeal(meal: SavedMealEntity): Int

    @Delete
    suspend fun deleteMeal(meal: SavedMealEntity): Int

    @Query("DELETE FROM saved_meals WHERE id = :id")
    suspend fun deleteMealById(id: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItems(items: List<SavedMealItemEntity>): List<Long>

    @Update
    suspend fun updateItem(item: SavedMealItemEntity): Int

    @Delete
    suspend fun deleteItem(item: SavedMealItemEntity): Int

    @Query("DELETE FROM saved_meal_items WHERE saved_meal_id = :mealId")
    suspend fun deleteItems(mealId: Long): Int

    @Transaction
    @Query("SELECT * FROM saved_meals WHERE id = :id LIMIT 1")
    suspend fun mealWithItems(id: Long): SavedMealWithItems?

    @Transaction
    @Query(
        """
        SELECT * FROM saved_meals
        ORDER BY COALESCE(last_used_at_epoch_millis, updated_at_epoch_millis) DESC, name COLLATE NOCASE
        """,
    )
    fun observeMeals(): Flow<List<SavedMealWithItems>>

    @Transaction
    @Query(
        """
        SELECT * FROM saved_meals
        WHERE normalized_name LIKE '%' || :normalizedQuery || '%'
           OR LOWER(COALESCE(notes, '')) LIKE '%' || :normalizedQuery || '%'
        ORDER BY COALESCE(last_used_at_epoch_millis, updated_at_epoch_millis) DESC
        LIMIT :limit
        """,
    )
    fun searchMeals(
        normalizedQuery: String,
        limit: Int = 50,
    ): Flow<List<SavedMealWithItems>>

    @Query(
        """
        UPDATE saved_meals
        SET last_used_at_epoch_millis = :usedAtEpochMillis
        WHERE id = :mealId
        """,
    )
    suspend fun markMealUsed(mealId: Long, usedAtEpochMillis: Long): Int

    @Transaction
    suspend fun saveMeal(
        meal: SavedMealEntity,
        items: List<SavedMealItemEntity>,
    ): Long {
        require(items.isNotEmpty()) { "A saved meal must contain at least one item" }
        val mealId = insertMeal(meal.copy(id = 0))
        insertItems(
            items.mapIndexed { index, item ->
                item.copy(id = 0, savedMealId = mealId, sortOrder = index)
            },
        )
        return mealId
    }

    @Transaction
    suspend fun replaceMeal(
        meal: SavedMealEntity,
        items: List<SavedMealItemEntity>,
    ) {
        require(meal.id > 0) { "The saved meal must already exist" }
        require(items.isNotEmpty()) { "A saved meal must contain at least one item" }
        check(updateMeal(meal) == 1) { "Saved meal ${meal.id} does not exist" }
        deleteItems(meal.id)
        insertItems(
            items.mapIndexed { index, item ->
                item.copy(id = 0, savedMealId = meal.id, sortOrder = index)
            },
        )
    }
}

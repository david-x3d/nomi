package com.nomi.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.nomi.app.data.local.entity.FavoriteFoodEntity
import com.nomi.app.data.local.entity.FoodAliasEntity
import com.nomi.app.data.local.entity.FoodEntity
import com.nomi.app.data.local.entity.FoodResearchCacheEntity
import com.nomi.app.data.local.entity.FoodServingEntity
import com.nomi.app.data.local.entity.NutritionSourceEntity
import com.nomi.app.data.local.model.FavoriteFoodWithCatalog
import com.nomi.app.data.local.model.FoodWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodCatalogDao {
    @Query(
        "SELECT * FROM food_research_cache " +
            "WHERE cache_key = :cacheKey AND expires_at_epoch_millis > :nowEpochMillis LIMIT 1",
    )
    suspend fun freshResearchCache(
        cacheKey: String,
        nowEpochMillis: Long,
    ): FoodResearchCacheEntity?

    @Upsert
    suspend fun upsertResearchCache(entry: FoodResearchCacheEntity)

    @Query("DELETE FROM food_research_cache WHERE cache_key = :cacheKey")
    suspend fun deleteResearchCache(cacheKey: String): Int

    @Query("DELETE FROM food_research_cache WHERE expires_at_epoch_millis <= :nowEpochMillis")
    suspend fun deleteExpiredResearchCache(nowEpochMillis: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNutritionSource(source: NutritionSourceEntity): Long

    @Update
    suspend fun updateNutritionSource(source: NutritionSourceEntity): Int

    @Delete
    suspend fun deleteNutritionSource(source: NutritionSourceEntity): Int

    @Query("SELECT * FROM nutrition_sources WHERE id = :id LIMIT 1")
    suspend fun nutritionSource(id: Long): NutritionSourceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFood(food: FoodEntity): Long

    @Upsert
    suspend fun upsertFoods(foods: List<FoodEntity>): List<Long>

    @Update
    suspend fun updateFood(food: FoodEntity): Int

    @Delete
    suspend fun deleteFood(food: FoodEntity): Int

    @Query("SELECT * FROM foods WHERE id = :id LIMIT 1")
    suspend fun food(id: Long): FoodEntity?

    @Query("SELECT * FROM foods WHERE barcode = :barcode LIMIT 1")
    suspend fun foodByBarcode(barcode: String): FoodEntity?

    @Query(
        """
        SELECT * FROM foods
        WHERE normalized_name = :normalizedName
          AND (
              (:normalizedBrand IS NULL AND (brand IS NULL OR TRIM(brand) = ''))
              OR LOWER(TRIM(COALESCE(brand, ''))) = COALESCE(:normalizedBrand, '')
          )
        LIMIT 1
        """,
    )
    suspend fun foodByIdentity(normalizedName: String, normalizedBrand: String?): FoodEntity?

    @Transaction
    @Query("SELECT * FROM foods WHERE id = :id LIMIT 1")
    suspend fun foodWithDetails(id: Long): FoodWithDetails?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertServing(serving: FoodServingEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertServings(servings: List<FoodServingEntity>): List<Long>

    @Update
    suspend fun updateServing(serving: FoodServingEntity): Int

    @Delete
    suspend fun deleteServing(serving: FoodServingEntity): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlias(alias: FoodAliasEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAliases(aliases: List<FoodAliasEntity>): List<Long>

    @Delete
    suspend fun deleteAlias(alias: FoodAliasEntity): Int

    @Query("DELETE FROM food_aliases WHERE food_id = :foodId")
    suspend fun deleteAliasesForFood(foodId: Long): Int

    @Query(
        """
        SELECT f.* FROM foods AS f
        WHERE f.normalized_name LIKE '%' || :normalizedQuery || '%'
           OR LOWER(COALESCE(f.brand, '')) LIKE '%' || :normalizedQuery || '%'
           OR EXISTS (
               SELECT 1 FROM food_aliases AS a
               WHERE a.food_id = f.id
                 AND a.normalized_alias LIKE '%' || :normalizedQuery || '%'
           )
        ORDER BY
            CASE WHEN f.normalized_name = :normalizedQuery THEN 0 ELSE 1 END,
            f.canonical_name COLLATE NOCASE
        LIMIT :limit
        """,
    )
    fun searchFoods(normalizedQuery: String, limit: Int = 50): Flow<List<FoodEntity>>

    @Query(
        """
        SELECT f.* FROM foods AS f
        WHERE f.id IN (
            SELECT l.food_id FROM food_logs AS l
            WHERE l.food_id IS NOT NULL
            GROUP BY l.food_id
            ORDER BY MAX(l.logged_at_epoch_millis) DESC
            LIMIT :limit
        )
        ORDER BY (
            SELECT MAX(recent.logged_at_epoch_millis)
            FROM food_logs AS recent
            WHERE recent.food_id = f.id
        ) DESC
        """,
    )
    fun observeRecentFoods(limit: Int = 25): Flow<List<FoodEntity>>

    @Upsert
    suspend fun upsertFavorite(favorite: FavoriteFoodEntity): Long

    @Delete
    suspend fun deleteFavorite(favorite: FavoriteFoodEntity): Int

    @Query("DELETE FROM favorite_foods WHERE food_id = :foodId")
    suspend fun deleteFavoriteForFood(foodId: Long): Int

    @Query(
        """
        UPDATE favorite_foods
        SET last_used_at_epoch_millis = :usedAtEpochMillis
        WHERE id = :favoriteId
        """,
    )
    suspend fun markFavoriteUsed(favoriteId: Long, usedAtEpochMillis: Long): Int

    @Transaction
    @Query(
        """
        SELECT * FROM favorite_foods
        ORDER BY COALESCE(last_used_at_epoch_millis, created_at_epoch_millis) DESC
        """,
    )
    fun observeFavorites(): Flow<List<FavoriteFoodWithCatalog>>

    /** Inserts one catalog food and rewrites child ids inside the same transaction. */
    @Transaction
    suspend fun insertFoodWithDetails(
        food: FoodEntity,
        servings: List<FoodServingEntity>,
        aliases: List<FoodAliasEntity>,
    ): Long {
        val foodId = insertFood(food.copy(id = 0))
        if (servings.isNotEmpty()) {
            insertServings(servings.map { it.copy(id = 0, foodId = foodId) })
        }
        if (aliases.isNotEmpty()) {
            insertAliases(aliases.map { it.copy(id = 0, foodId = foodId) })
        }
        return foodId
    }
}

package com.nomi.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nomi.app.data.local.dao.AiDebugEventDao
import com.nomi.app.data.local.dao.FoodCatalogDao
import com.nomi.app.data.local.dao.FoodLogDao
import com.nomi.app.data.local.dao.ProfilePlanDao
import com.nomi.app.data.local.dao.SavedMealDao
import com.nomi.app.data.local.dao.WeightDao
import com.nomi.app.data.local.entity.AiDebugEventEntity
import com.nomi.app.data.local.entity.FavoriteFoodEntity
import com.nomi.app.data.local.entity.FoodAliasEntity
import com.nomi.app.data.local.entity.FoodEntity
import com.nomi.app.data.local.entity.FoodLogEntity
import com.nomi.app.data.local.entity.FoodServingEntity
import com.nomi.app.data.local.entity.NutritionPlanEntity
import com.nomi.app.data.local.entity.NutritionSourceEntity
import com.nomi.app.data.local.entity.SavedMealEntity
import com.nomi.app.data.local.entity.SavedMealItemEntity
import com.nomi.app.data.local.entity.UserProfileEntity
import com.nomi.app.data.local.entity.WeightEntryEntity

@Database(
    entities = [
        UserProfileEntity::class,
        NutritionPlanEntity::class,
        NutritionSourceEntity::class,
        FoodEntity::class,
        FoodServingEntity::class,
        FoodAliasEntity::class,
        FoodLogEntity::class,
        FavoriteFoodEntity::class,
        SavedMealEntity::class,
        SavedMealItemEntity::class,
        WeightEntryEntity::class,
        AiDebugEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class NomiDatabase : RoomDatabase() {
    abstract fun profilePlanDao(): ProfilePlanDao
    abstract fun foodCatalogDao(): FoodCatalogDao
    abstract fun foodLogDao(): FoodLogDao
    abstract fun savedMealDao(): SavedMealDao
    abstract fun weightDao(): WeightDao
    abstract fun aiDebugEventDao(): AiDebugEventDao

    companion object {
        private const val DATABASE_NAME = "nomi.db"

        @Volatile
        private var instance: NomiDatabase? = null

        /** Creates an independent database instance, primarily useful for tests and tools. */
        fun create(context: Context, name: String = DATABASE_NAME): NomiDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                NomiDatabase::class.java,
                name,
            ).build()

        /**
         * Process singleton. Intentionally has no destructive-migration fallback: unknown schema
         * upgrades fail closed instead of erasing food and weight history.
         */
        fun getInstance(context: Context): NomiDatabase =
            instance ?: synchronized(this) {
                instance ?: create(context).also { instance = it }
            }
    }
}

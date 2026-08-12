package com.nomi.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
import com.nomi.app.data.local.entity.FoodResearchCacheEntity
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
        FoodResearchCacheEntity::class,
    ],
    version = 5,
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

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `food_research_cache` (
                        `cache_key` TEXT NOT NULL,
                        `analysis_json` TEXT NOT NULL,
                        `stored_at_epoch_millis` INTEGER NOT NULL,
                        `expires_at_epoch_millis` INTEGER NOT NULL,
                        PRIMARY KEY(`cache_key`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_food_research_cache_expires_at_epoch_millis` " +
                        "ON `food_research_cache` (`expires_at_epoch_millis`)",
                )
            }
        }

        /**
         * Adds the citation list and confidence score to both tables that embed a source
         * snapshot. Existing rows keep NULL, which reads as "this entry predates the detail
         * view" rather than as an empty source list.
         */
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("food_logs", "saved_meal_items").forEach { table ->
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `source_snapshot_cited_urls` TEXT")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `source_snapshot_confidence` REAL")
                }
            }
        }

        /**
         * Records what the cited page itself said: its product title and the serving its values
         * describe. Older rows keep NULL, which the detail view reads as "this entry predates
         * the source breakdown" rather than as a source that published nothing.
         */
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("food_logs", "saved_meal_items").forEach { table ->
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `source_snapshot_product_name` TEXT")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `source_snapshot_serving_quantity` REAL")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `source_snapshot_serving_unit` TEXT")
                }
            }
        }

        /** Stores the short AI calorie explanation alongside the historical source snapshot. */
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("food_logs", "saved_meal_items").forEach { table ->
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `source_snapshot_calorie_explanation` TEXT")
                }
            }
        }

        /** Creates an independent database instance, primarily useful for tests and tools. */
        fun create(context: Context, name: String = DATABASE_NAME): NomiDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                NomiDatabase::class.java,
                name,
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()

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

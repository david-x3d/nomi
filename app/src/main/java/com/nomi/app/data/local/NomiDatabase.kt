package com.nomi.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nomi.app.data.local.dao.AiDebugEventDao
import com.nomi.app.data.local.dao.AiUsageEventDao
import com.nomi.app.data.local.dao.FoodCatalogDao
import com.nomi.app.data.local.dao.FoodLogDao
import com.nomi.app.data.local.dao.PendingFoodLogDao
import com.nomi.app.data.local.dao.ProfilePlanDao
import com.nomi.app.data.local.dao.SavedMealDao
import com.nomi.app.data.local.dao.WeightDao
import com.nomi.app.data.local.entity.AiDebugEventEntity
import com.nomi.app.data.local.entity.AiUsageEventEntity
import com.nomi.app.data.local.entity.FavoriteFoodEntity
import com.nomi.app.data.local.entity.FoodAliasEntity
import com.nomi.app.data.local.entity.FoodEntity
import com.nomi.app.data.local.entity.FoodLogEntity
import com.nomi.app.data.local.entity.FoodResearchCacheEntity
import com.nomi.app.data.local.entity.FoodServingEntity
import com.nomi.app.data.local.entity.NutritionPlanEntity
import com.nomi.app.data.local.entity.NutritionSourceEntity
import com.nomi.app.data.local.entity.PendingFoodLogEntity
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
        AiUsageEventEntity::class,
        PendingFoodLogEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class NomiDatabase : RoomDatabase() {
    abstract fun profilePlanDao(): ProfilePlanDao
    abstract fun foodCatalogDao(): FoodCatalogDao
    abstract fun foodLogDao(): FoodLogDao
    abstract fun savedMealDao(): SavedMealDao
    abstract fun weightDao(): WeightDao
    abstract fun aiDebugEventDao(): AiDebugEventDao
    abstract fun aiUsageEventDao(): AiUsageEventDao
    abstract fun pendingFoodLogDao(): PendingFoodLogDao

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

        /** Keeps the sentence behind a log so grouped meals can be reopened and rewritten. */
        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `food_logs` ADD COLUMN `original_input` TEXT")
            }
        }

        /** Optional conversion metadata; existing counts and gram amounts are unchanged. */
        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("food_logs", "saved_meal_items").forEach { table ->
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `resolved_volume_ml` REAL")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `resolution_source` TEXT")
                }
            }
        }

        /** Adds local AI usage/cost tracking and the persistent offline/background logging queue. */
        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ai_usage_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `pipeline` TEXT NOT NULL,
                        `resolution_kind` TEXT NOT NULL,
                        `provider_id` TEXT NOT NULL,
                        `model` TEXT NOT NULL,
                        `prompt_tokens` INTEGER NOT NULL,
                        `completion_tokens` INTEGER NOT NULL,
                        `thoughts_tokens` INTEGER NOT NULL,
                        `exa_searches` INTEGER NOT NULL,
                        `estimated_cost_usd` REAL NOT NULL,
                        `cost_source` TEXT NOT NULL,
                        `created_at_epoch_millis` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ai_usage_events_created_at_epoch_millis` " +
                        "ON `ai_usage_events` (`created_at_epoch_millis`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ai_usage_events_resolution_kind_created_at_epoch_millis` " +
                        "ON `ai_usage_events` (`resolution_kind`, `created_at_epoch_millis`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ai_usage_events_provider_id_model` " +
                        "ON `ai_usage_events` (`provider_id`, `model`)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_food_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `local_date` TEXT NOT NULL,
                        `input_text` TEXT NOT NULL,
                        `input_method` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `stage_label` TEXT,
                        `error_message` TEXT,
                        `attempt_count` INTEGER NOT NULL,
                        `created_at_epoch_millis` INTEGER NOT NULL,
                        `updated_at_epoch_millis` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pending_food_logs_local_date_created_at_epoch_millis` " +
                        "ON `pending_food_logs` (`local_date`, `created_at_epoch_millis`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pending_food_logs_status_created_at_epoch_millis` " +
                        "ON `pending_food_logs` (`status`, `created_at_epoch_millis`)",
                )
            }
        }

        /**
         * Repairs and runs the historical AI usage backfill from pre-update `food_logs`.
         * Earlier `MIGRATION_7_8` filtered on uppercase `AddFoodMethod` enum names instead of the
         * actual lowercase `food_logs.input_method` values (`ai`, `recent`, `favorite`, etc.),
         * leaving migrated databases with zero backfilled usage rows.
         */
        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                backfillHistoricalAiUsage(db)
            }
        }

        /**
         * Backfills estimated historical usage & costs from pre-update `food_logs` so 7-day,
         * 30-day, and all-time Usage & Cost views reflect prior activity immediately.
         *
         * Guards against double-counting:
         * - Skips if pre-update backfill rows already exist.
         * - Only considers `food_logs` created strictly before the earliest live usage event
         *   (with a 60-second buffer so the meal that produced the first live event is not
         *   counted twice).
         */
        internal fun backfillHistoricalAiUsage(db: SupportSQLiteDatabase) {
            val alreadyBackfilled = db.query(
                "SELECT 1 FROM `ai_usage_events` WHERE `model` LIKE 'pre-update-%' LIMIT 1",
            ).use { cursor -> cursor.moveToFirst() }
            if (alreadyBackfilled) return

            val cutoffEpochMillis = db.query(
                "SELECT MIN(`created_at_epoch_millis`) FROM `ai_usage_events` WHERE `model` NOT LIKE 'pre-update-%'",
            ).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) {
                    cursor.getLong(0) - 60_000L
                } else {
                    Long.MAX_VALUE
                }
            }

            val sourceNameExpr =
                "LOWER(TRIM(COALESCE(NULLIF(TRIM(`source_snapshot_display_name`), ''), NULLIF(TRIM(`source_snapshot_provider_name`), ''), '')))"
            val inputMethodExpr = "LOWER(TRIM(COALESCE(`input_method`, '')))"
            val sourceKindExpr = "LOWER(TRIM(COALESCE(`source_snapshot_kind`, '')))"

            val freeSourcesSql = """
                (
                    'open food facts',
                    'nomi food library',
                    'nomi favorite',
                    'local barcode cache',
                    'local barcode estimate',
                    'nomi local food cache'
                )
            """.trimIndent()

            val freeInputMethodsSql = """
                (
                    'recent', 'favorite', 'favorites', 'saved_meal', 'saved_meals',
                    'barcode', 'copied_meal', 'copied_day', 'copied_items', 'shared'
                )
            """.trimIndent()

            val freeSourceKindsSql = "('local_catalog', 'barcode', 'open_food_facts', 'shared')"

            val labelPhotoSourcesSql = """
                (
                    'nutrition label photo',
                    'foto der nährwerttabelle',
                    'foto de la etiqueta nutricional',
                    'photo de l’étiquette nutritionnelle',
                    'photo de l''étiquette nutritionnelle',
                    'foto della tabella nutrizionale',
                    'foto van de voedingswaardetabel',
                    'foto do rótulo nutricional',
                    'foto e etiketës ushqyese',
                    'foto av näringsdeklarationen',
                    'besin etiketi fotoğrafı'
                )
            """.trimIndent()

            val isManualCondition = """
                ($inputMethodExpr = 'manual'
                 OR $sourceKindExpr = 'user_manual'
                 OR $sourceNameExpr = 'manual entry')
            """.trimIndent()

            val isLabelPhotoCondition = """
                ($inputMethodExpr = 'label'
                 OR $sourceNameExpr IN $labelPhotoSourcesSql)
            """.trimIndent()

            val isFreeReusedCondition = """
                ($inputMethodExpr IN $freeInputMethodsSql
                 OR $sourceKindExpr IN $freeSourceKindsSql
                 OR $sourceNameExpr IN $freeSourcesSql)
            """.trimIndent()

            val isAiResearchCondition = """
                ($inputMethodExpr IN ('ai', 'type', 'voice', 'photo', 'menu')
                 AND NOT $isManualCondition
                 AND NOT $isLabelPhotoCondition
                 AND NOT $isFreeReusedCondition)
            """.trimIndent()

            // 1) Exa search calls for AI-researched meals (grouped by meal entry_group_id)
            db.execSQL(
                """
                INSERT INTO `ai_usage_events` (
                    `pipeline`, `resolution_kind`, `provider_id`, `model`,
                    `prompt_tokens`, `completion_tokens`, `thoughts_tokens`, `exa_searches`,
                    `estimated_cost_usd`, `cost_source`, `created_at_epoch_millis`
                )
                SELECT
                    'FOOD_RESEARCH', 'AI_CALL', 'exa', 'exa-search',
                    0, 0, 0, 1,
                    0.005, 'ESTIMATED', MIN(`created_at_epoch_millis`)
                FROM `food_logs`
                WHERE $isAiResearchCondition
                GROUP BY COALESCE(`entry_group_id`, 'row-' || `id`)
                HAVING MIN(`created_at_epoch_millis`) < $cutoffEpochMillis
                """.trimIndent(),
            )
            // 2) Model extraction calls for AI-researched meals
            db.execSQL(
                """
                INSERT INTO `ai_usage_events` (
                    `pipeline`, `resolution_kind`, `provider_id`, `model`,
                    `prompt_tokens`, `completion_tokens`, `thoughts_tokens`, `exa_searches`,
                    `estimated_cost_usd`, `cost_source`, `created_at_epoch_millis`
                )
                SELECT
                    'FOOD_RESEARCH', 'AI_CALL',
                    CASE
                        WHEN LOWER(COALESCE(MAX(`source_snapshot_provider_name`), '')) LIKE '%openrouter%' THEN 'openrouter'
                        WHEN LOWER(COALESCE(MAX(`source_snapshot_provider_name`), '')) LIKE '%openai%' THEN 'openai'
                        WHEN LOWER(COALESCE(MAX(`source_snapshot_provider_name`), '')) LIKE '%perplexity%'
                          OR LOWER(COALESCE(MAX(`source_snapshot_provider_name`), '')) LIKE '%sonar%' THEN 'perplexity'
                        ELSE 'gemini'
                    END,
                    'pre-update-estimate',
                    3200, 580, 0, 0,
                    0.00055, 'ESTIMATED', MIN(`created_at_epoch_millis`)
                FROM `food_logs`
                WHERE $isAiResearchCondition
                GROUP BY COALESCE(`entry_group_id`, 'row-' || `id`)
                HAVING MIN(`created_at_epoch_millis`) < $cutoffEpochMillis
                """.trimIndent(),
            )
            // 3) Vision calls for nutrition label, photo, and menu scans
            db.execSQL(
                """
                INSERT INTO `ai_usage_events` (
                    `pipeline`, `resolution_kind`, `provider_id`, `model`,
                    `prompt_tokens`, `completion_tokens`, `thoughts_tokens`, `exa_searches`,
                    `estimated_cost_usd`, `cost_source`, `created_at_epoch_millis`
                )
                SELECT
                    'VISION', 'AI_CALL', 'gemini', 'pre-update-vision',
                    5400, 640, 0, 0,
                    0.0024, 'ESTIMATED', MIN(`created_at_epoch_millis`)
                FROM `food_logs`
                WHERE ($isLabelPhotoCondition OR $inputMethodExpr IN ('photo', 'menu'))
                  AND NOT $isManualCondition
                GROUP BY COALESCE(`entry_group_id`, 'row-' || `id`)
                HAVING MIN(`created_at_epoch_millis`) < $cutoffEpochMillis
                """.trimIndent(),
            )
            // 4) Free local/cached/barcode/reused meals
            db.execSQL(
                """
                INSERT INTO `ai_usage_events` (
                    `pipeline`, `resolution_kind`, `provider_id`, `model`,
                    `prompt_tokens`, `completion_tokens`, `thoughts_tokens`, `exa_searches`,
                    `estimated_cost_usd`, `cost_source`, `created_at_epoch_millis`
                )
                SELECT
                    'FOOD_RESEARCH', 'CACHE_HIT', 'cache', 'pre-update-reused',
                    0, 0, 0, 0,
                    0.0, 'FREE', MIN(`created_at_epoch_millis`)
                FROM `food_logs`
                WHERE $isFreeReusedCondition
                  AND NOT $isManualCondition
                  AND NOT $isLabelPhotoCondition
                GROUP BY COALESCE(`entry_group_id`, 'row-' || `id`)
                HAVING MIN(`created_at_epoch_millis`) < $cutoffEpochMillis
                """.trimIndent(),
            )
        }

        /** Creates an independent database instance, primarily useful for tests and tools. */
        fun create(context: Context, name: String = DATABASE_NAME): NomiDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                NomiDatabase::class.java,
                name,
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
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

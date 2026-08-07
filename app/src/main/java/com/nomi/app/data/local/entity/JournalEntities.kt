package com.nomi.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "food_logs",
    foreignKeys = [
        ForeignKey(
            entity = FoodEntity::class,
            parentColumns = ["id"],
            childColumns = ["food_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = FoodServingEntity::class,
            parentColumns = ["id"],
            childColumns = ["food_serving_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = NutritionSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["nutrition_source_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["local_date", "logged_at_epoch_millis"]),
        Index(value = ["local_date", "meal_category"]),
        Index(value = ["entry_group_id"]),
        Index(value = ["food_id"]),
        Index(value = ["food_serving_id"]),
        Index(value = ["nutrition_source_id"]),
        Index(value = ["display_name_snapshot"]),
        Index(value = ["brand_snapshot"]),
    ],
)
data class FoodLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "food_id") val foodId: Long? = null,
    @ColumnInfo(name = "food_serving_id") val foodServingId: Long? = null,
    @ColumnInfo(name = "nutrition_source_id") val nutritionSourceId: Long? = null,
    @ColumnInfo(name = "entry_group_id") val entryGroupId: String? = null,
    @ColumnInfo(name = "meal_category") val mealCategory: String,
    @ColumnInfo(name = "display_name_snapshot") val displayNameSnapshot: String,
    @ColumnInfo(name = "brand_snapshot") val brandSnapshot: String? = null,
    val amount: Double,
    val unit: String,
    val grams: Double? = null,
    /** Values for this exact logged portion, never dynamically joined from [FoodEntity]. */
    @Embedded(prefix = "nutrition_snapshot_") val nutritionSnapshot: NutritionValues,
    /** Source metadata captured at logging time and safe if the source row changes or disappears. */
    @Embedded(prefix = "source_snapshot_") val sourceSnapshot: NutritionSourceSnapshot,
    @ColumnInfo(name = "is_estimated", defaultValue = "0") val isEstimated: Boolean = false,
    @ColumnInfo(name = "input_method") val inputMethod: String,
    val notes: String? = null,
    /** ISO-8601 date as perceived where the entry was logged, e.g. 2026-08-07. */
    @ColumnInfo(name = "local_date") val localDate: String,
    /** Absolute point on the timeline; epoch milliseconds encode an Instant without locale loss. */
    @ColumnInfo(name = "logged_at_epoch_millis") val loggedAtEpochMillis: Long,
    /** IANA zone id used to derive [localDate], e.g. Europe/Berlin. */
    @ColumnInfo(name = "zone_id") val zoneId: String,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "saved_meals",
    indices = [
        Index(value = ["normalized_name"]),
        Index(value = ["last_used_at_epoch_millis"]),
        Index(value = ["created_at_epoch_millis"]),
    ],
)
data class SavedMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    val notes: String? = null,
    @ColumnInfo(name = "default_meal_category") val defaultMealCategory: String? = null,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "last_used_at_epoch_millis") val lastUsedAtEpochMillis: Long? = null,
)

@Entity(
    tableName = "saved_meal_items",
    foreignKeys = [
        ForeignKey(
            entity = SavedMealEntity::class,
            parentColumns = ["id"],
            childColumns = ["saved_meal_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FoodEntity::class,
            parentColumns = ["id"],
            childColumns = ["food_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = FoodServingEntity::class,
            parentColumns = ["id"],
            childColumns = ["food_serving_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["saved_meal_id", "sort_order"], unique = true),
        Index(value = ["food_id"]),
        Index(value = ["food_serving_id"]),
    ],
)
data class SavedMealItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "saved_meal_id") val savedMealId: Long,
    @ColumnInfo(name = "food_id") val foodId: Long? = null,
    @ColumnInfo(name = "food_serving_id") val foodServingId: Long? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "display_name_snapshot") val displayNameSnapshot: String,
    @ColumnInfo(name = "brand_snapshot") val brandSnapshot: String? = null,
    val amount: Double,
    val unit: String,
    val grams: Double? = null,
    /** Concrete portion values keep a saved meal usable even after catalog eviction. */
    @Embedded(prefix = "nutrition_snapshot_") val nutritionSnapshot: NutritionValues,
    @Embedded(prefix = "source_snapshot_") val sourceSnapshot: NutritionSourceSnapshot,
    @ColumnInfo(name = "is_estimated", defaultValue = "0") val isEstimated: Boolean = false,
)

@Entity(
    tableName = "weight_entries",
    indices = [
        Index(value = ["local_date", "measured_at_epoch_millis"]),
        Index(value = ["measured_at_epoch_millis"]),
        Index(value = ["source"]),
    ],
)
data class WeightEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "measured_at_epoch_millis") val measuredAtEpochMillis: Long,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    val note: String? = null,
    val source: String = "manual",
    @ColumnInfo(name = "external_id") val externalId: String? = null,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "ai_debug_events",
    indices = [
        Index(value = ["created_at_epoch_millis"]),
        Index(value = ["pipeline", "created_at_epoch_millis"]),
        Index(value = ["validation_status"]),
    ],
)
data class AiDebugEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pipeline: String,
    @ColumnInfo(name = "provider_id") val providerId: String,
    val model: String,
    @ColumnInfo(name = "duration_millis") val durationMillis: Long,
    @ColumnInfo(name = "cache_hit") val cacheHit: Boolean,
    @ColumnInfo(name = "source_summary") val sourceSummary: String? = null,
    @ColumnInfo(name = "parsed_result_json") val parsedResultJson: String? = null,
    @ColumnInfo(name = "validation_status") val validationStatus: String,
    @ColumnInfo(name = "failure_category") val failureCategory: String? = null,
    /** Never store request headers, credentials, or raw provider authorization errors here. */
    @ColumnInfo(name = "safe_message") val safeMessage: String? = null,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
)

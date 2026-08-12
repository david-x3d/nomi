package com.nomi.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "nutrition_sources",
    indices = [
        Index(value = ["kind", "provider_name"]),
        Index(value = ["provider_name", "external_id"]),
        Index(value = ["verified_at_epoch_millis"]),
    ],
)
data class NutritionSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    @ColumnInfo(name = "provider_name") val providerName: String? = null,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "external_id") val externalId: String? = null,
    val url: String? = null,
    val license: String? = null,
    @ColumnInfo(name = "retrieved_at_epoch_millis") val retrievedAtEpochMillis: Long,
    @ColumnInfo(name = "verified_at_epoch_millis") val verifiedAtEpochMillis: Long? = null,
)

@Entity(
    tableName = "foods",
    foreignKeys = [
        ForeignKey(
            entity = NutritionSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["nutrition_source_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["normalized_name"]),
        Index(value = ["brand"]),
        Index(value = ["barcode"], unique = true),
        Index(value = ["nutrition_source_id"]),
        Index(value = ["last_verified_at_epoch_millis"]),
    ],
)
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "canonical_name") val canonicalName: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    val brand: String? = null,
    val barcode: String? = null,
    @Embedded(prefix = "per_100g_") val nutritionPer100g: NutritionValues,
    @ColumnInfo(name = "nutrition_source_id") val nutritionSourceId: Long? = null,
    @ColumnInfo(name = "is_user_created", defaultValue = "0")
    val isUserCreated: Boolean = false,
    @ColumnInfo(name = "is_estimated", defaultValue = "0")
    val isEstimated: Boolean = false,
    @ColumnInfo(name = "last_verified_at_epoch_millis") val lastVerifiedAtEpochMillis: Long? = null,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "food_servings",
    foreignKeys = [
        ForeignKey(
            entity = FoodEntity::class,
            parentColumns = ["id"],
            childColumns = ["food_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["food_id"]),
        Index(value = ["food_id", "normalized_name", "amount", "unit"], unique = true),
    ],
)
data class FoodServingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "food_id") val foodId: Long,
    val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    val amount: Double,
    val unit: String,
    val grams: Double,
    @ColumnInfo(name = "is_default", defaultValue = "0") val isDefault: Boolean = false,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "food_aliases",
    foreignKeys = [
        ForeignKey(
            entity = FoodEntity::class,
            parentColumns = ["id"],
            childColumns = ["food_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["normalized_alias"]),
        Index(value = ["food_id", "normalized_alias"], unique = true),
    ],
)
data class FoodAliasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "food_id") val foodId: Long,
    val alias: String,
    @ColumnInfo(name = "normalized_alias") val normalizedAlias: String,
    val locale: String? = null,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "favorite_foods",
    foreignKeys = [
        ForeignKey(
            entity = FoodEntity::class,
            parentColumns = ["id"],
            childColumns = ["food_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FoodServingEntity::class,
            parentColumns = ["id"],
            childColumns = ["food_serving_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["food_id"], unique = true),
        Index(value = ["food_serving_id"]),
        Index(value = ["last_used_at_epoch_millis"]),
    ],
)
data class FavoriteFoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "food_id") val foodId: Long,
    @ColumnInfo(name = "food_serving_id") val foodServingId: Long? = null,
    @ColumnInfo(name = "typical_amount") val typicalAmount: Double,
    @ColumnInfo(name = "typical_unit") val typicalUnit: String,
    @ColumnInfo(name = "typical_grams") val typicalGrams: Double? = null,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "last_used_at_epoch_millis") val lastUsedAtEpochMillis: Long? = null,
)

/** A validated web-research result that can be reused until its deliberate refresh date. */
@Entity(
    tableName = "food_research_cache",
    indices = [Index(value = ["expires_at_epoch_millis"])],
)
data class FoodResearchCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "cache_key") val cacheKey: String,
    @ColumnInfo(name = "analysis_json") val analysisJson: String,
    @ColumnInfo(name = "stored_at_epoch_millis") val storedAtEpochMillis: Long,
    @ColumnInfo(name = "expires_at_epoch_millis") val expiresAtEpochMillis: Long,
)

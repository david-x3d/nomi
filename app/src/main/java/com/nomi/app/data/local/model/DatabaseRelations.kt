package com.nomi.app.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
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

data class FoodWithDetails(
    @Embedded val food: FoodEntity,
    @Relation(parentColumn = "id", entityColumn = "food_id")
    val servings: List<FoodServingEntity>,
    @Relation(parentColumn = "id", entityColumn = "food_id")
    val aliases: List<FoodAliasEntity>,
    /** A list models the nullable foreign key without fabricating an empty source object. */
    @Relation(parentColumn = "nutrition_source_id", entityColumn = "id")
    val sources: List<NutritionSourceEntity>,
)

data class SavedMealWithItems(
    @Embedded val meal: SavedMealEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "saved_meal_id",
        entity = SavedMealItemEntity::class,
    )
    val items: List<SavedMealItemEntity>,
)

data class ProfileWithPlanHistory(
    @Embedded val profile: UserProfileEntity,
    @Relation(parentColumn = "id", entityColumn = "profile_id")
    val plans: List<NutritionPlanEntity>,
)

data class FoodLogWithCatalogReference(
    @Embedded val log: FoodLogEntity,
    @Relation(parentColumn = "food_id", entityColumn = "id")
    val foods: List<FoodEntity>,
)

data class OnboardingPersistenceResult(
    val profile: UserProfileEntity,
    val planId: Long,
    val weightEntryId: Long,
)

data class DailyNutritionTotals(
    val localDate: String,
    val caloriesKcal: Double,
    val proteinGrams: Double,
    val carbohydrateGrams: Double,
    val fatGrams: Double,
)

data class MealNutritionTotals(
    val mealCategory: String,
    val caloriesKcal: Double,
    val proteinGrams: Double,
    val carbohydrateGrams: Double,
    val fatGrams: Double,
)

data class WeightRange(
    val oldest: WeightEntryEntity?,
    val newest: WeightEntryEntity?,
)

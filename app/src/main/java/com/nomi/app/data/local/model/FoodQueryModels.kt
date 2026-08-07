package com.nomi.app.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.nomi.app.data.local.entity.FavoriteFoodEntity
import com.nomi.app.data.local.entity.FoodEntity
import com.nomi.app.data.local.entity.FoodServingEntity

data class FavoriteFoodWithCatalog(
    @Embedded val favorite: FavoriteFoodEntity,
    @Relation(parentColumn = "food_id", entityColumn = "id")
    val food: FoodEntity,
    @Relation(parentColumn = "food_serving_id", entityColumn = "id")
    val selectedServings: List<FoodServingEntity>,
)

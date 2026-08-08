package com.nomi.app.domain.usecase

import com.nomi.app.data.local.entity.FoodEntity

/** Only data with a durable, non-model provenance may bypass fresh nutrition research. */
internal fun FoodEntity.isTrustedForNutritionReuse(): Boolean =
    isUserCreated || !barcode.isNullOrBlank() || nutritionSourceId != null

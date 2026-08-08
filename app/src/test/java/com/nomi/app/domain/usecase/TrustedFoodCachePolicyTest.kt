package com.nomi.app.domain.usecase

import com.nomi.app.data.local.entity.FoodEntity
import com.nomi.app.data.local.entity.NutritionValues
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedFoodCachePolicyTest {
    @Test
    fun `manual barcode and sourced foods are trusted`() {
        assertTrue(food(isUserCreated = true).isTrustedForNutritionReuse())
        assertTrue(food(barcode = "4000000000000").isTrustedForNutritionReuse())
        assertTrue(food(nutritionSourceId = 7L).isTrustedForNutritionReuse())
    }

    @Test
    fun `legacy model cache never bypasses research even when not marked estimated`() {
        assertFalse(food(isEstimated = false).isTrustedForNutritionReuse())
        assertFalse(food(isEstimated = true).isTrustedForNutritionReuse())
    }

    private fun food(
        isUserCreated: Boolean = false,
        barcode: String? = null,
        nutritionSourceId: Long? = null,
        isEstimated: Boolean = false,
    ) = FoodEntity(
        canonicalName = "Test food",
        normalizedName = "test food",
        barcode = barcode,
        nutritionPer100g = NutritionValues(caloriesKcal = 100.0),
        nutritionSourceId = nutritionSourceId,
        isUserCreated = isUserCreated,
        isEstimated = isEstimated,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )
}

package com.nomi.app.domain.usecase

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.data.local.entity.FoodEntity
import com.nomi.app.data.local.entity.NutritionValues
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalogue is what a later log of the same food reuses, so a weak first answer must not
 * outlive a better one, and a good row must not be degraded by a worse later lookup.
 */
class FoodCatalogUpgradeTest {

    @Test
    fun `verified package data replaces a stored generic estimate`() {
        assertTrue(food(isEstimated = true).acceptsVerifiedUpgradeFrom(researchIsEstimate = false))
    }

    @Test
    fun `a fresh estimate never overwrites an already verified row`() {
        assertFalse(food(isEstimated = false).acceptsVerifiedUpgradeFrom(researchIsEstimate = true))
        assertFalse(food(isEstimated = true).acceptsVerifiedUpgradeFrom(researchIsEstimate = true))
    }

    @Test
    fun `a food the user created is never rewritten by research`() {
        assertFalse(
            food(isEstimated = true, isUserCreated = true)
                .acceptsVerifiedUpgradeFrom(researchIsEstimate = false),
        )
    }

    @Test
    fun `a failed lookup leaves nothing behind that a later lookup could reuse`() {
        // Only a whole successful analysis reaches the 21-day cache, and only when every item
        // carries a real citation. An estimate, and therefore anything a failed-then-estimated
        // item produced, is refused.
        assertTrue(analysis(sourceUrl = "https://example.test/x", estimate = false).canPersistForResearchReuse())
        assertFalse(analysis(sourceUrl = "https://example.test/x", estimate = true).canPersistForResearchReuse())
        assertFalse(analysis(sourceUrl = null, estimate = true).canPersistForResearchReuse())
        assertFalse(FoodAnalysis(items = emptyList()).canPersistForResearchReuse())
    }

    @Test
    fun `a mixed meal is not cached because one item was only estimated`() {
        val mixed = FoodAnalysis(
            items = listOf(
                item(sourceUrl = "https://example.test/verified", estimate = false),
                item(sourceUrl = null, estimate = true),
            ),
        )
        assertFalse(mixed.canPersistForResearchReuse())
    }

    private fun food(isEstimated: Boolean, isUserCreated: Boolean = false) = FoodEntity(
        canonicalName = "Catalogue row",
        normalizedName = "catalogue row",
        nutritionPer100g = NutritionValues(caloriesKcal = 100.0),
        isUserCreated = isUserCreated,
        isEstimated = isEstimated,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )

    private fun analysis(sourceUrl: String?, estimate: Boolean) =
        FoodAnalysis(items = listOf(item(sourceUrl, estimate)))

    private fun item(sourceUrl: String?, estimate: Boolean) = AnalyzedFoodItem(
        name = "Catalogue row",
        quantity = 100.0,
        unit = "g",
        calories = 100.0,
        proteinGrams = 5.0,
        carbohydrateGrams = 10.0,
        fatGrams = 4.0,
        sourceUrl = sourceUrl,
        isEstimate = estimate,
    )
}

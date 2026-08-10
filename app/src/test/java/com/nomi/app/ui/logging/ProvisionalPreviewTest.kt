package com.nomi.app.ui.logging

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ui.today.MealCategory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisionalPreviewTest {
    @Test
    fun `a preview is final unless it was built from the fast estimate`() {
        assertFalse(preview().isProvisional)
        assertTrue(preview(isProvisional = true).isProvisional)
    }

    @Test
    fun `a provisional preview is still complete enough to save`() {
        val provisional = preview(isProvisional = true)

        // Nothing about being provisional may weaken the entry: the values are real, they are
        // simply unsourced, which is what the Estimated label on the row says.
        assertTrue(provisional.analysis.items.isNotEmpty())
        assertTrue(provisional.analysis.items.single().calories > 0.0)
        assertTrue(provisional.analysis.items.single().isEstimate)
    }

    private fun preview(isProvisional: Boolean = false) = FoodLoggingUiState.Preview(
        analysis = FoodAnalysis(
            items = listOf(
                AnalyzedFoodItem(
                    name = "Chicken Teriyaki Footlong",
                    brand = "Subway",
                    quantity = 1.0,
                    unit = "piece",
                    gramsEquivalent = 480.0,
                    calories = 739.2,
                    proteinGrams = 49.9,
                    carbohydrateGrams = 116.2,
                    fatGrams = 8.2,
                    isEstimate = true,
                ),
            ),
        ),
        mealCategory = MealCategory.LUNCH,
        originalText = "Subway Chicken Teriyaki Footlong",
        isProvisional = isProvisional,
    )
}

package com.nomi.app.ai.validation

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class AiResponseValidatorTest {
    @Test
    fun `failure messages in source fields are rejected`() {
        val error = assertThrows(AiValidationException::class.java) {
            AiResponseValidator.validate(
                FoodAnalysis(
                    items = listOf(item(sourceName = "No sufficient live web evidence found")),
                ),
            )
        }

        assertTrue(error.message!!.contains("source name"))
    }

    @Test
    fun `failure messages in the product name are rejected`() {
        assertThrows(AiValidationException::class.java) {
            AiResponseValidator.validate(
                FoodAnalysis(items = listOf(item(name = "Produkt nicht gefunden"))),
            )
        }
    }

    @Test
    fun `real product and source names pass`() {
        AiResponseValidator.validate(
            FoodAnalysis(
                items = listOf(
                    item(name = "MAGGI Ravioli pikant", sourceName = "maggi.de"),
                ),
            ),
        )
    }

    private fun item(
        name: String = "Raspberry jam",
        sourceName: String? = "Manufacturer",
    ): AnalyzedFoodItem = AnalyzedFoodItem(
        name = name,
        quantity = 100.0,
        unit = "g",
        gramsEquivalent = 100.0,
        calories = 250.0,
        proteinGrams = 0.5,
        carbohydrateGrams = 60.0,
        fatGrams = 0.2,
        sourceName = sourceName,
        sourceServingQuantity = 100.0,
        sourceServingUnit = "g",
        sourceServingGramsEquivalent = 100.0,
        isEstimate = false,
    )
}

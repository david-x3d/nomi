package com.nomi.app.data.remote.ai

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.validation.AiValidationException
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class OpenAiCompatibleProvidersTest {
    @Test
    fun `matching provider citation grounds nutrition result`() {
        val analysis = analysis("https://EXAMPLE.com/product/#nutrition")

        val validated = validateWebSearchEvidence(
            analysis,
            evidenceUrls = setOf("https://example.com/product/"),
        )

        assertSame(analysis, validated)
    }

    @Test
    fun `missing mismatched or empty search evidence is rejected`() {
        assertThrows(AiValidationException::class.java) {
            validateWebSearchEvidence(analysis(null), setOf("https://example.com/product"))
        }
        assertThrows(AiValidationException::class.java) {
            validateWebSearchEvidence(
                analysis("https://invented.example/product"),
                setOf("https://example.com/product"),
            )
        }
        assertThrows(AiValidationException::class.java) {
            validateWebSearchEvidence(analysis("https://example.com/product"), emptySet())
        }
    }

    private fun analysis(sourceUrl: String?): FoodAnalysis = FoodAnalysis(
        items = listOf(
            AnalyzedFoodItem(
                name = "Raspberry jam",
                quantity = 100.0,
                unit = "g",
                gramsEquivalent = 100.0,
                calories = 250.0,
                proteinGrams = 0.5,
                carbohydrateGrams = 60.0,
                fatGrams = 0.2,
                sourceName = "Manufacturer",
                sourceUrl = sourceUrl,
                sourceServingQuantity = 100.0,
                sourceServingUnit = "g",
                sourceServingGramsEquivalent = 100.0,
                isEstimate = false,
            ),
        ),
    )
}

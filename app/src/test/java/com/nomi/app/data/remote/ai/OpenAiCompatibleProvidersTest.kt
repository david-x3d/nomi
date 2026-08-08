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
        val analysis = analysis(
            "https://manufacturer.example/product/#nutrition",
            listOf("https://supermarket.example/product"),
        )

        val validated = validateWebSearchEvidence(
            analysis,
            evidenceUrls = setOf(
                "https://manufacturer.example/product/",
                "https://supermarket.example/product",
            ),
        )

        assertSame(analysis, validated)
    }

    @Test
    fun `missing mismatched or empty search evidence is rejected`() {
        assertThrows(AiValidationException::class.java) {
            validateWebSearchEvidence(
                analysis(null, listOf("https://retailer.example/product")),
                setOf("https://example.com/product", "https://retailer.example/product"),
            )
        }
        assertThrows(AiValidationException::class.java) {
            validateWebSearchEvidence(
                analysis(
                    "https://invented.example/product",
                    listOf("https://retailer.example/product"),
                ),
                setOf("https://example.com/product", "https://retailer.example/product"),
            )
        }
        assertThrows(AiValidationException::class.java) {
            validateWebSearchEvidence(
                analysis("https://example.com/product", listOf("https://retailer.example/product")),
                emptySet(),
            )
        }
    }

    @Test
    fun `two pages from one website are not independent evidence`() {
        assertThrows(AiValidationException::class.java) {
            validateWebSearchEvidence(
                analysis(
                    "https://shop.example.com/product",
                    listOf("https://shop.example.com/nutrition"),
                ),
                setOf(
                    "https://shop.example.com/product",
                    "https://shop.example.com/nutrition",
                ),
            )
        }
    }

    private fun analysis(
        sourceUrl: String?,
        supportingSourceUrls: List<String>,
    ): FoodAnalysis = FoodAnalysis(
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
                supportingSourceUrls = supportingSourceUrls,
                sourceServingQuantity = 100.0,
                sourceServingUnit = "g",
                sourceServingGramsEquivalent = 100.0,
                isEstimate = false,
            ),
        ),
    )
}

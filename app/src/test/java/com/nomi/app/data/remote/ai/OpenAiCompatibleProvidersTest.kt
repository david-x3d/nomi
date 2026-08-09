package com.nomi.app.data.remote.ai

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.validation.AiValidationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleProvidersTest {
    @Test
    fun `provider evidence replaces model-authored urls`() {
        val analysis = analysis(
            sourceName = "Manufacturer label",
            sourceUrl = "https://invented.example/product",
            supportingSourceUrls = listOf("https://invented.example/citation"),
        )

        val grounded = groundWithWebSearchEvidence(
            analysis,
            evidenceUrls = linkedSetOf(
                "https://manufacturer.example/product/#nutrition",
                "https://supermarket.example/product",
                "https://manufacturer.example/another-page",
            ),
        )

        val item = grounded.items.single()
        assertEquals("Manufacturer label", item.sourceName)
        assertEquals("https://manufacturer.example/product", item.sourceUrl)
        assertEquals(listOf("https://supermarket.example/product"), item.supportingSourceUrls)
        assertFalse(item.supportingSourceUrls.contains("https://invented.example/citation"))
    }

    @Test
    fun `two independent provider hosts are required`() {
        val error = assertThrows(AiValidationException::class.java) {
            groundWithWebSearchEvidence(
                analysis(),
                evidenceUrls = linkedSetOf(
                    "https://www.shop.example.com/product",
                    "https://shop.example.com/nutrition",
                    "not-a-url",
                ),
            )
        }

        assertEquals(
            "Food research needs citations from at least two independent websites.",
            error.message,
        )
    }

    @Test
    fun `provider host fills null or blank source names but preserves supplied label`() {
        val baseItem = analyzedItem(sourceName = null)
        val analysis = FoodAnalysis(
            items = listOf(
                baseItem,
                baseItem.copy(name = "Raspberry preserve", sourceName = "   "),
                baseItem.copy(name = "Strawberry jam", sourceName = "Official product label"),
            ),
        )

        val grounded = groundWithWebSearchEvidence(
            analysis,
            evidenceUrls = linkedSetOf(
                "https://www.manufacturer.example/product",
                "https://retailer.example/product",
            ),
        )

        assertEquals(
            listOf("manufacturer.example", "manufacturer.example", "Official product label"),
            grounded.items.map { it.sourceName },
        )
    }

    @Test
    fun `missing valid provider evidence is rejected`() {
        val error = assertThrows(AiValidationException::class.java) {
            groundWithWebSearchEvidence(
                analysis(),
                evidenceUrls = linkedSetOf("", "not-a-url", "file:///tmp/product"),
            )
        }

        assertEquals(
            "The food research provider returned no web-search citations. Try again or " +
                "configure Perplexity, OpenRouter, or OpenAI for Food research.",
            error.message,
        )
    }

    @Test
    fun `research refusal envelope becomes a validation error`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val error = assertThrows(AiValidationException::class.java) {
            throwIfResearchRefusal(json, """{"error": "no reliable source found"}""")
        }
        assertTrue(error.message!!.contains("no reliable source found"))
        assertTrue(error.message!!.contains("describe the food more precisely"))

        throwIfResearchRefusal(json, """{"items": [], "error": null}""")
        throwIfResearchRefusal(json, "not even json")
    }

    @Test
    fun `all-zero nutrition for a branded food is rejected as a placeholder`() {
        val placeholder = analyzedItem(sourceName = "iglo.at").copy(
            name = "MAGGI Ravioli pikant",
            brand = "MAGGI",
            calories = 0.0,
            proteinGrams = 0.0,
            carbohydrateGrams = 0.0,
            fatGrams = 0.0,
        )

        val error = assertThrows(AiValidationException::class.java) {
            rejectPlaceholderNutrition(FoodAnalysis(items = listOf(placeholder)))
        }

        assertTrue(error.message!!.contains("MAGGI Ravioli pikant"))
    }

    @Test
    fun `all-zero nutrition is allowed for plausibly calorie-free foods`() {
        val water = analyzedItem(sourceName = "Manufacturer").copy(
            name = "Mineralwasser still",
            brand = null,
            calories = 0.0,
            proteinGrams = 0.0,
            carbohydrateGrams = 0.0,
            fatGrams = 0.0,
        )

        val resolved = rejectPlaceholderNutrition(FoodAnalysis(items = listOf(water)))

        assertEquals("Mineralwasser still", resolved.items.single().name)
    }

    @Test
    fun `piece logged against mass source triggers targeted amount research`() {
        val milkSnack = analyzedItem(sourceName = "Manufacturer").copy(
            name = "Milk snack",
            brand = "Example brand",
            quantity = 1.0,
            unit = "piece",
            gramsEquivalent = null,
            sourceServingQuantity = 100.0,
            sourceServingUnit = "g",
            sourceServingGramsEquivalent = null,
        )

        assertEquals(
            listOf(0),
            unresolvedWebAmountItemIndexes(FoodAnalysis(items = listOf(milkSnack))),
        )
        assertTrue(
            unresolvedWebAmountItemIndexes(
                FoodAnalysis(items = listOf(milkSnack.copy(gramsEquivalent = 28.0))),
            ).isEmpty(),
        )
    }

    @Test
    fun `amount resolution adds weight evidence without replacing nutrition`() {
        val primaryItem = analyzedItem(
            sourceName = "Official nutrition",
            sourceUrl = "https://manufacturer.example/nutrition",
            supportingSourceUrls = listOf("https://nutrition-shop.example/product"),
        ).copy(
            name = "Milk snack",
            quantity = 1.0,
            unit = "piece",
            gramsEquivalent = null,
            sourceServingGramsEquivalent = null,
            calories = 420.0,
        )
        val resolvedItem = primaryItem.copy(
            gramsEquivalent = 28.0,
            calories = 999.0,
            sourceName = "Pack listing",
            sourceUrl = "https://manufacturer.example/pack",
            supportingSourceUrls = listOf("https://retailer.example/pack"),
            sourcePackageQuantity = 140.0,
            sourcePackageUnit = "g",
            assumptions = listOf("140 g / 5 pieces = 28 g per piece"),
        )

        val merged = mergeWebAmountResolution(
            primary = FoodAnalysis(items = listOf(primaryItem)),
            amountResolution = FoodAnalysis(items = listOf(resolvedItem)),
            unresolvedItemIndexes = setOf(0),
        ).items.single()

        assertEquals(28.0, merged.gramsEquivalent!!, 0.0)
        assertEquals(420.0, merged.calories, 0.0)
        assertEquals("Official nutrition", merged.sourceName)
        assertEquals("https://manufacturer.example/nutrition", merged.sourceUrl)
        assertEquals(140.0, merged.sourcePackageQuantity!!, 0.0)
        assertTrue(merged.supportingSourceUrls.contains("https://manufacturer.example/pack"))
        assertTrue(merged.supportingSourceUrls.contains("https://retailer.example/pack"))
        assertTrue(merged.assumptions.contains("140 g / 5 pieces = 28 g per piece"))
    }

    private fun analysis(
        sourceName: String? = "Manufacturer",
        sourceUrl: String? = null,
        supportingSourceUrls: List<String> = emptyList(),
    ): FoodAnalysis = FoodAnalysis(
        items = listOf(analyzedItem(sourceName, sourceUrl, supportingSourceUrls)),
    )

    private fun analyzedItem(
        sourceName: String?,
        sourceUrl: String? = null,
        supportingSourceUrls: List<String> = emptyList(),
    ): AnalyzedFoodItem = AnalyzedFoodItem(
        name = "Raspberry jam",
        quantity = 100.0,
        unit = "g",
        gramsEquivalent = 100.0,
        calories = 250.0,
        proteinGrams = 0.5,
        carbohydrateGrams = 60.0,
        fatGrams = 0.2,
        sourceName = sourceName,
        sourceUrl = sourceUrl,
        supportingSourceUrls = supportingSourceUrls,
        sourceServingQuantity = 100.0,
        sourceServingUnit = "g",
        sourceServingGramsEquivalent = 100.0,
        isEstimate = false,
    )
}

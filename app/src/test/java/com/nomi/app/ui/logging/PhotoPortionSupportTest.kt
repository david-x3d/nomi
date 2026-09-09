package com.nomi.app.ui.logging

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.VisionFoodItem
import com.nomi.app.ai.model.VisionFoodResult
import com.nomi.app.ai.parsing.LocalFoodIntentParser
import com.nomi.app.ai.validation.AiResponseValidator
import com.nomi.app.ai.validation.ServingNutritionNormalizer
import com.nomi.app.ai.validation.UserQuantityResolver
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PhotoPortionSupportTest {
    @Test
    fun `plate based mass is editable and scales researched per hundred gram values`() {
        val photo = VisionFoodItem(
            name = "Reis", estimatedQuantity = 1.0, unit = "serving", estimatedGrams = 180.0,
            weightEstimationBasis = "Plate assumed to be 26 cm; shallow mound covers a quarter.",
        ).toPhotoParsedItem()
        val description = listOf(photo).toPhotoMealDescription()
        assertEquals("180 g Reis", description)
        assertTrue(photo.assumptions.any { it.contains("26 cm") })
        assertTrue(photo.assumptions.any { it.contains("not a measured weight") })
        val intent = UserQuantityResolver.reconcileIntent(
            ParsedFoodIntent(originalText = description, items = listOf(photo)), "DE",
        )
        // Even if research proposes a standard 100 g portion, the photo's 180 g must win.
        val source = AnalyzedFoodItem(
            name = "Reis", quantity = 100.0, unit = "g", gramsEquivalent = 100.0,
            calories = 130.0, proteinGrams = 2.7, carbohydrateGrams = 28.0, fatGrams = 0.3,
            sourceServingQuantity = 100.0, sourceServingUnit = "g", isEstimate = false,
        )
        val reconciled = UserQuantityResolver.reconcileAnalysis(intent, FoodAnalysis(listOf(source)))
        val result = ServingNutritionNormalizer.normalize(intent, reconciled).items.single()
        assertEquals(180.0, result.quantity, 0.0)
        assertEquals(234.0, result.calories, 0.001)
    }

    @Test
    fun `piece weights describe the total rather than multiplying it again`() {
        val item = VisionFoodItem(
            name = "Sashimi", estimatedQuantity = 8.0, unit = "pieces", estimatedGrams = 160.0,
        ).toPhotoParsedItem()
        assertEquals("160 g Sashimi", listOf(item).toPhotoMealDescription())
        assertEquals(160.0, item.gramsEquivalent!!, 0.0)
        assertTrue(item.assumptions.any { it.contains("8 pieces") })
    }

    @Test
    fun `a weight without a serving amount is shown and forwarded`() {
        val item = VisionFoodItem(name = "Kartoffeln", estimatedGrams = 220.0).toPhotoParsedItem()
        assertEquals("220 g Kartoffeln", listOf(item).toPhotoMealDescription())
    }

    @Test
    fun `drink volume is never replaced with its mass`() {
        val item = VisionFoodItem(
            name = "Saft", estimatedQuantity = 200.0, unit = "ml", estimatedGrams = 210.0,
        ).toPhotoParsedItem()
        assertEquals("200 ml Saft", listOf(item).toPhotoMealDescription())
        assertEquals(210.0, item.gramsEquivalent!!, 0.0)
    }

    @Test
    fun `unknown weights do not invent grams or a portion`() {
        val item = VisionFoodItem(name = "Suppe").toPhotoParsedItem()
        assertNull(item.quantity)
        assertNull(item.gramsEquivalent)
        assertEquals("Suppe", listOf(item).toPhotoMealDescription())
        val counted = VisionFoodItem(name = "Apfel", estimatedQuantity = 2.0, unit = "pieces")
            .toPhotoParsedItem()
        assertEquals("2 pieces Apfel", listOf(counted).toPhotoMealDescription())
    }

    @Test
    fun `editing the shown grams uses the user's amount`() {
        val description = "125 g Reis"
        val parsed = requireNotNull(LocalFoodIntentParser.parseOrNull(description))
        val item = UserQuantityResolver.reconcileParsedIntent(description, parsed, "DE").items.single()
        assertEquals(125.0, item.quantity!!, 0.0)
        assertEquals(125.0, item.gramsEquivalent!!, 0.0)
    }

    @Test
    fun `new scale explanation decodes and old vision responses remain compatible`() {
        val old = Json.decodeFromString<VisionFoodResult>(
            """{"items":[{"name":"Rice","estimatedGrams":180}]}""",
        )
        assertNull(old.items.single().weightEstimationBasis)
        val current = Json.decodeFromString<VisionFoodResult>(
            """{"items":[{"name":"Rice","estimatedGrams":180,"weightEstimationBasis":"Fork for scale"}]}""",
        )
        assertEquals("Fork for scale", AiResponseValidator.validate(current).items.single().weightEstimationBasis)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative photo weights are rejected`() {
        AiResponseValidator.validate(VisionFoodResult(listOf(VisionFoodItem(name = "Rice", estimatedGrams = -2.0))))
    }
}

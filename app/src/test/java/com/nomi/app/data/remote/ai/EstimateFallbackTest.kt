package com.nomi.app.data.remote.ai

import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.NutritionVerificationStatus
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import com.nomi.app.ai.validation.AiValidationException
import com.nomi.app.ai.validation.ServingNutritionNormalizer
import com.nomi.app.ai.validation.SourceIntegrityVerifier
import com.nomi.app.ai.validation.UserQuantityResolver
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The deterministic half of the estimate fallback: everything that happens to an unsourced
 * provider answer between decoding it and showing it. Only the HTTP call is left out.
 *
 * The fixture is the reference the fallback exists for - a restaurant item that sourced
 * research regularly refuses because no fetchable official table exists for it.
 */
class EstimateFallbackTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val intent = ParsedFoodIntent(
        originalText = "Subway Chicken Teriyaki Footlong",
        items = listOf(
            ParsedFoodItem(
                name = "Chicken Teriyaki Footlong",
                brand = "Subway",
                quantity = 1.0,
                unit = "piece",
                gramsEquivalent = 480.0,
            ),
        ),
    )

    /** What the estimate prompt asks for: per-100 g values plus the logged amount in grams. */
    private val estimatePayload = """
        {
          "items": [{
            "name": "Chicken Teriyaki Footlong",
            "brand": "Subway",
            "quantity": 1,
            "unit": "piece",
            "gramsEquivalent": 480,
            "calories": 154,
            "proteinGrams": 10.4,
            "carbohydrateGrams": 24.2,
            "fatGrams": 1.7,
            "sourceName": "Estimate",
            "sourceServingQuantity": 100,
            "sourceServingUnit": "g",
            "isEstimate": true,
            "assumptions": ["Standard footlong on Italian white, no cheese"]
          }]
        }
    """.trimIndent()

    @Test
    fun `an uncited estimate is scaled to the logged amount and labeled estimated`() {
        val item = runEstimatePipeline(estimatePayload).items.single()

        // 480 g of a 154 kcal/100 g sandwich, within a rounding step of Amy's 740 kcal entry.
        assertEquals(739.2, item.calories, 0.5)
        assertEquals(49.9, item.proteinGrams, 0.5)
        assertEquals(116.2, item.carbohydrateGrams, 0.5)
        assertEquals(8.2, item.fatGrams, 0.5)
        assertEquals(1.0, item.quantity, 0.0)
        assertEquals("piece", item.unit)
        assertTrue(item.isEstimate)
        assertEquals(NutritionVerificationStatus.UNKNOWN, item.verificationStatus)
        assertNull(item.sourceUrl)
    }

    @Test
    fun `an estimate still cannot claim impossible nutrition`() {
        val impossible = estimatePayload.replace("\"calories\": 154", "\"calories\": 9000")

        assertThrows(AiValidationException::class.java) { runEstimatePipeline(impossible) }
    }

    @Test
    fun `an estimate cannot pre-scale its values to the logged amount`() {
        // Per-100 values that are really the whole sandwich would double-count once scaled.
        val preScaled = estimatePayload
            .replace("\"calories\": 154", "\"calories\": 740")
            .replace("\"proteinGrams\": 10.4", "\"proteinGrams\": 50")
            .replace("\"carbohydrateGrams\": 24.2", "\"carbohydrateGrams\": 116")

        assertThrows(AiValidationException::class.java) { runEstimatePipeline(preScaled) }
    }

    /**
     * The pages a refused research attempt had already opened are what the user watched appear
     * as site icons while the meal was being looked up. Dropping them made the saved entry claim
     * it had no sources at all, contradicting what the screen had just shown.
     */
    @Test
    fun `an estimate keeps the pages the refused research had already opened`() {
        val consulted = listOf(
            "https://www.subway.com/en-us/menunutrition",
            "https://fddb.info/db/de/lebensmittel/subway_chicken_teriyaki/index.html",
        )

        val item = runEstimatePipeline(estimatePayload, consulted).items.single()

        assertEquals(consulted, item.supportingSourceUrls)
        // Still not a cited result: no page published these numbers.
        assertNull(item.sourceUrl)
        assertNull(item.sourceDomain)
        assertTrue(item.isEstimate)
    }

    @Test
    fun `consulted pages are capped and de-duplicated before they reach validation`() {
        val many = (1..9).map { "https://example$it.test/page" } + "https://example1.test/page"

        val item = runEstimatePipeline(estimatePayload, many).items.single()

        assertEquals(5, item.supportingSourceUrls.size)
        assertEquals(item.supportingSourceUrls.distinct(), item.supportingSourceUrls)
    }

    private fun runEstimatePipeline(
        payload: String,
        consultedUrls: List<String> = emptyList(),
    ): FoodAnalysis {
        val decoded: FoodAnalysis = json.decodeFromString(payload)
        val labeled = decoded.copy(
            items = decoded.items.map {
                it.copy(
                    isEstimate = true,
                    sourceUrl = null,
                    supportingSourceUrls = consultedUrls.distinct().take(5),
                    sourceDomain = null,
                )
            },
        )
        val reconciled = UserQuantityResolver.reconcileAnalysis(intent, labeled)
        val normalized = ServingNutritionNormalizer.normalize(intent, reconciled)
        return SourceIntegrityVerifier.resolve(rejectPlaceholderNutrition(normalized))
    }
}

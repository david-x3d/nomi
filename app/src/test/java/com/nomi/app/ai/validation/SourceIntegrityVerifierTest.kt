package com.nomi.app.ai.validation

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.NutritionVerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceIntegrityVerifierTest {
    @Test
    fun `matching domain and exact product name verify the item`() {
        val resolved = SourceIntegrityVerifier.resolveItem(
            item(
                sourceUrl = "https://www.iglo.de/produkte/chicken-nuggets-im-backteig",
                sourceDomain = "iglo.de",
                sourceProductName = "iglo Chicken Nuggets im Backteig",
            ),
        )

        assertEquals(NutritionVerificationStatus.VERIFIED, resolved.verificationStatus)
        assertEquals("iglo.de", resolved.sourceDomain)
        assertFalse(resolved.isEstimate)
    }

    @Test
    fun `provider-claimed domain that contradicts the cited page downgrades the item`() {
        val resolved = SourceIntegrityVerifier.resolveItem(
            item(
                sourceUrl = "https://fitnessblog.example/nuggets",
                sourceDomain = "iglo.de",
                sourceProductName = "iglo Chicken Nuggets im Backteig",
            ),
        )

        assertEquals(NutritionVerificationStatus.ESTIMATED, resolved.verificationStatus)
        assertEquals("fitnessblog.example", resolved.sourceDomain)
        assertTrue(resolved.isEstimate)
        assertTrue(resolved.assumptions.any { it.contains("does not match the cited page") })
    }

    @Test
    fun `missing cited url yields unknown status`() {
        val resolved = SourceIntegrityVerifier.resolveItem(
            item(sourceUrl = null, sourceDomain = null, sourceProductName = "Some product"),
        )

        assertEquals(NutritionVerificationStatus.UNKNOWN, resolved.verificationStatus)
        assertEquals(null, resolved.sourceDomain)
        assertTrue(resolved.isEstimate)
    }

    @Test
    fun `missing exact source product name keeps the item an estimate`() {
        val resolved = SourceIntegrityVerifier.resolveItem(
            item(
                sourceUrl = "https://www.iglo.de/produkte/chicken-nuggets-im-backteig",
                sourceDomain = null,
                sourceProductName = null,
            ),
        )

        assertEquals(NutritionVerificationStatus.ESTIMATED, resolved.verificationStatus)
        assertTrue(resolved.isEstimate)
    }

    @Test
    fun `provider cannot assert verified status about an estimate`() {
        val resolved = SourceIntegrityVerifier.resolve(
            FoodAnalysis(
                items = listOf(
                    item(
                        sourceUrl = "https://www.iglo.de/produkte/chicken-nuggets-im-backteig",
                        sourceDomain = "iglo.de",
                        sourceProductName = "iglo Chicken Nuggets im Backteig",
                        isEstimate = true,
                        verificationStatus = NutritionVerificationStatus.VERIFIED,
                    ),
                ),
            ),
        ).items.single()

        assertEquals(NutritionVerificationStatus.ESTIMATED, resolved.verificationStatus)
    }

    private fun item(
        sourceUrl: String?,
        sourceDomain: String?,
        sourceProductName: String?,
        isEstimate: Boolean = false,
        verificationStatus: NutritionVerificationStatus = NutritionVerificationStatus.UNKNOWN,
    ): AnalyzedFoodItem = AnalyzedFoodItem(
        name = "Chicken Nuggets im Backteig",
        brand = "iglo",
        quantity = 110.0,
        unit = "g",
        gramsEquivalent = 110.0,
        calories = 309.1,
        proteinGrams = 15.4,
        carbohydrateGrams = 18.7,
        fatGrams = 18.4,
        sourceName = "iglo.de",
        sourceUrl = sourceUrl,
        sourceDomain = sourceDomain,
        sourceProductName = sourceProductName,
        verificationStatus = verificationStatus,
        sourceServingQuantity = 100.0,
        sourceServingUnit = "g",
        sourceServingGramsEquivalent = 100.0,
        isEstimate = isEstimate,
    )
}

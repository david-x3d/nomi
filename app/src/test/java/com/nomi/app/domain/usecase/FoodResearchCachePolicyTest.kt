package com.nomi.app.domain.usecase

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodResearchCachePolicyTest {
    @Test
    fun `research expires exactly twenty one days after storage`() {
        val storedAt = 10_000L
        assertEquals(storedAt + 21L * 24L * 60L * 60L * 1_000L, foodResearchExpiry(storedAt))
    }

    @Test
    fun `only sourced non estimate results can persist`() {
        assertTrue(analysis(sourceUrl = "https://example.com/red-bull", estimated = false).canPersistForResearchReuse())
        assertFalse(analysis(sourceUrl = null, estimated = false).canPersistForResearchReuse())
        assertFalse(analysis(sourceUrl = "https://example.com/red-bull", estimated = true).canPersistForResearchReuse())
    }

    @Test
    fun `cache key normalizes input but separates provider changes`() {
        val first = FoodAnalysisCacheKey.create("  RED   Bull ", "de", "parser", "research-a")
        val same = FoodAnalysisCacheKey.create("red bull", "DE", "parser", "research-a")
        val changedProvider = FoodAnalysisCacheKey.create("red bull", "DE", "parser", "research-b")

        assertEquals(first.storageKey(), same.storageKey())
        assertNotEquals(first.storageKey(), changedProvider.storageKey())
    }

    private fun analysis(sourceUrl: String?, estimated: Boolean) = FoodAnalysis(
        items = listOf(
            AnalyzedFoodItem(
                name = "Red Bull",
                quantity = 250.0,
                unit = "ml",
                calories = 115.0,
                proteinGrams = 0.0,
                carbohydrateGrams = 27.5,
                fatGrams = 0.0,
                sourceUrl = sourceUrl,
                isEstimate = estimated,
            ),
        ),
    )
}

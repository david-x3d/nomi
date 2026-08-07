package com.nomi.app.domain.usecase

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test

class RecentFoodAnalysisCacheTest {
    @Test
    fun `exact normalized input and configuration gets an immutable copy`() {
        var now = 1_000L
        val cache = RecentFoodAnalysisCache(nowMillis = { now })
        val key = key(" Apple ")
        val analysis = analysis("Apple")

        cache.put(key, analysis)
        val cached = cache.get(key("apple"))!!

        assertEquals(analysis, cached)
        assertNotSame(analysis, cached)
        assertNotSame(analysis.items, cached.items)
    }

    @Test
    fun `provider locale changes and expiry miss cache`() {
        var now = 0L
        val cache = RecentFoodAnalysisCache(ttlMillis = 100L, nowMillis = { now })
        cache.put(key("apple"), analysis("Apple"))

        assertNull(cache.get(key("apple").copy(localeCountry = "US")))
        assertNull(cache.get(key("apple").copy(researchProviderIdentity = "other")))
        now = 100L
        assertNull(cache.get(key("apple")))
    }

    @Test
    fun `least recently used entry is evicted at capacity`() {
        val cache = RecentFoodAnalysisCache(maxEntries = 2)
        cache.put(key("apple"), analysis("Apple"))
        cache.put(key("pear"), analysis("Pear"))
        cache.get(key("apple"))
        cache.put(key("banana"), analysis("Banana"))

        assertNull(cache.get(key("pear")))
        assertEquals("Apple", cache.get(key("apple"))!!.items.single().name)
    }

    private fun key(input: String) = FoodAnalysisCacheKey.create(
        input = input,
        localeCountry = "DE",
        interpretationProviderIdentity = "openrouter|parser|https://openrouter.ai/api/v1|",
        researchProviderIdentity = "perplexity|sonar|https://api.perplexity.ai|",
    )

    private fun analysis(name: String) = FoodAnalysis(
        items = listOf(
            AnalyzedFoodItem(
                name = name,
                quantity = 100.0,
                unit = "g",
                gramsEquivalent = 100.0,
                calories = 52.0,
                proteinGrams = 0.3,
                carbohydrateGrams = 14.0,
                fatGrams = 0.2,
                isEstimate = false,
                assumptions = listOf("fixture"),
            ),
        ),
    )
}

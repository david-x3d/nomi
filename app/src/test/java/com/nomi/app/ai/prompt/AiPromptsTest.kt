package com.nomi.app.ai.prompt

import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPromptsTest {
    @Test
    fun `nutrition research prompt requires live cited web evidence`() {
        val prompt = AiPrompts.researchNutrition(
            intent = ParsedFoodIntent(
                originalText = "100 g raspberry jam",
                items = listOf(
                    ParsedFoodItem(name = "raspberry jam", quantity = 100.0, unit = "g"),
                ),
            ),
            json = Json,
            localeCountry = "DE",
        )

        assertTrue(prompt.contains("MUST perform a live web search"))
        assertTrue(prompt.contains("at least two independent websites"))
        assertTrue(prompt.contains("Supermarket, grocery, retailer, and reseller"))
        assertTrue(prompt.contains("never return a model-only guess"))
        assertTrue(prompt.contains("supportingSourceUrls"))
        assertTrue(prompt.contains("fail instead of guessing"))
    }
}

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

    @Test
    fun `nutrition research prompt demands exact product and source integrity`() {
        val prompt = AiPrompts.researchNutrition(
            intent = ParsedFoodIntent(
                originalText = "110 g iglo Chicken Nuggets im Backteig",
                items = listOf(
                    ParsedFoodItem(
                        name = "Chicken Nuggets im Backteig",
                        brand = "iglo",
                        quantity = 110.0,
                        unit = "g",
                    ),
                ),
            ),
            json = Json,
            localeCountry = "DE",
        )

        assertTrue(prompt.contains("IDENTIFY THE EXACT PRODUCT FIRST"))
        assertTrue(prompt.contains("Never silently substitute a similar product"))
        assertTrue(prompt.contains("SOURCE AND DATA MUST MATCH"))
        assertTrue(prompt.contains("sourceProductName"))
        assertTrue(prompt.contains("sourceDomain"))
        assertTrue(prompt.contains("protein*4 + carbohydrates*4 + fat*9"))
    }

    @Test
    fun `nutrition research prompt demands manufacturer per-100 basis and forbids model arithmetic`() {
        val prompt = AiPrompts.researchNutrition(
            intent = ParsedFoodIntent(
                originalText = "110 g iglo Chicken Nuggets im Backteig",
                items = listOf(
                    ParsedFoodItem(
                        name = "Chicken Nuggets im Backteig",
                        brand = "iglo",
                        quantity = 110.0,
                        unit = "g",
                    ),
                ),
            ),
            json = Json,
            localeCountry = "DE",
        )

        assertTrue(prompt.contains("ALWAYS search the manufacturer's official website"))
        assertTrue(prompt.contains("never return an estimate"))
        assertTrue(prompt.contains("sourceServingQuantity=100"))
        assertTrue(prompt.contains("NEVER calculate the consumed amount's"))
        assertTrue(prompt.contains("""{"error": "<short reason>"}"""))
        assertTrue(prompt.contains("NEVER return zero calories and zero macros"))
    }

    @Test
    fun `nutrition research prompt keeps verified values exact and biases estimates high`() {
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

        assertTrue(prompt.contains("VERIFIED VALUES ARE EXACT"))
        assertTrue(prompt.contains("digit for digit"))
        assertTrue(prompt.contains("slightly higher plausible calorie"))
        assertTrue(prompt.contains("rather than underestimating"))
    }

    @Test
    fun `nutrition research prompt forbids pre-scaling the source serving basis`() {
        val prompt = AiPrompts.researchNutrition(
            intent = ParsedFoodIntent(
                originalText = "329 g Steak",
                items = listOf(ParsedFoodItem(name = "Steak", quantity = 329.0, unit = "g")),
            ),
            json = Json,
            localeCountry = "DE",
        )

        assertTrue(prompt.contains("GENERIC FOODS ARE DIFFERENT AND MUST STILL BE ANSWERED"))
        assertTrue(prompt.contains("THE ERROR IS A LAST RESORT, NOT A DEFAULT"))
        assertTrue(prompt.contains("error for a common generic food is wrong."))
        assertTrue(prompt.contains("CRITICAL SERVING-BASIS RULE"))
        assertTrue(prompt.contains("MUST NEVER describe the user's"))
        assertTrue(prompt.contains("pre-scale them to the consumed amount"))
        assertTrue(prompt.contains("calories=172"))
        assertTrue(prompt.contains("WRONG: calories=566"))
        assertTrue(prompt.contains("scale them a second time"))
    }

    @Test
    fun `amount resolution retry searches exact product piece weight`() {
        val prompt = AiPrompts.researchNutritionAmountResolution(
            intent = ParsedFoodIntent(
                originalText = "one milk snack",
                items = listOf(
                    ParsedFoodItem(
                        name = "Milk snack",
                        brand = "Example brand",
                        quantity = 1.0,
                        unit = "piece",
                    ),
                ),
            ),
            json = Json,
            localeCountry = "DE",
            unresolvedItemIndexes = listOf(0),
        )

        assertTrue(prompt.contains("AMOUNT-RESOLUTION RETRY"))
        assertTrue(prompt.contains("official manufacturer page first"))
        assertTrue(prompt.contains("pack net grams / pack piece count"))
        assertTrue(prompt.contains("5 x 28 g"))
        assertTrue(prompt.contains("gramsEquivalent=28"))
        assertTrue(prompt.contains("DO NOT replace them with 28 g"))
        assertTrue(prompt.contains("never invent or estimate a piece weight"))
    }
}

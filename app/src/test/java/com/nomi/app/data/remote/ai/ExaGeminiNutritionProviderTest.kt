package com.nomi.app.data.remote.ai

import com.nomi.app.ai.model.AiProviderConfig
import com.nomi.app.ai.model.AiProviderKind
import com.nomi.app.ai.model.AiRuntimeCredential
import com.nomi.app.ai.model.NutritionVerificationStatus
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import com.nomi.app.ai.model.QuantityOrigin
import com.nomi.app.ai.model.QuantityResolutionMetadata
import com.nomi.app.ai.model.QuantitySemantic
import kotlinx.coroutines.runBlocking
import com.nomi.app.ai.validation.AiValidationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExaGeminiNutritionProviderTest {
    private val credential = AiRuntimeCredential.from("test-key")
    private val config = AiProviderConfig(
        kind = AiProviderKind.EXA_GEMINI,
        endpoint = OPENROUTER_GEMINI_ENDPOINT,
        model = DEFAULT_GEMINI_NUTRITION_MODEL,
        timeoutMillis = 5_000,
    )

    @Test
    fun `realistic products preserve intent and normalize the selected source basis`() = runBlocking {
        val cases = listOf(
            SuccessCase(
                text = "80 g R?genwalder M?hle Veganer Schinken Spicker Mortadella",
                name = "Veganer Schinken Spicker Mortadella",
                brand = "R?genwalder M?hle",
                quantity = 80.0,
                sourceAmount = 100.0,
                calories = 117.0,
                protein = 2.2,
                carbs = 2.9,
                fat = 9.0,
                expectedCalories = 93.6,
                country = "DE",
            ),
            SuccessCase(
                text = "one 40 g box Pocky Matcha Green Tea US",
                name = "Pocky Matcha Green Tea",
                brand = "Pocky",
                quantity = 40.0,
                sourceAmount = 40.0,
                calories = 200.0,
                protein = 3.0,
                carbs = 27.0,
                fat = 9.0,
                expectedCalories = 200.0,
                country = "US",
            ),
            SuccessCase(
                text = "1 Big Mac McDonald's Australia",
                name = "Big Mac",
                brand = "McDonald's",
                quantity = 1.0,
                unit = "item",
                grams = null,
                sourceAmount = 1.0,
                sourceUnit = "item",
                calories = 557.0,
                protein = 24.6,
                carbs = 44.9,
                fat = 29.4,
                expectedCalories = 557.0,
                country = "AU",
            ),
            SuccessCase(
                text = "20g nutela",
                name = "Nutella",
                brand = "Ferrero",
                quantity = 20.0,
                sourceAmount = 100.0,
                calories = 539.0,
                protein = 6.3,
                carbs = 57.5,
                fat = 30.9,
                expectedCalories = 107.8,
                country = "DE",
            ),
        )

        cases.forEach { case ->
            var query = ""
            val result = provider(
                sources = listOf(
                    source(
                        title = "${case.brand.orEmpty()} ${case.name} official ${case.country}",
                        content = evidence(case),
                    ),
                ),
                extraction = extraction(item(case)),
                localeCountry = case.country,
                onQuery = { query = it },
            ).researchNutrition(case.intent())

            assertEquals("nutrition calories macros ${case.text}", query)
            assertEquals(case.quantity, result.items.single().quantity, 0.001)
            assertEquals(case.expectedCalories, result.items.single().calories, 0.001)
            assertEquals(case.country, result.items.single().sourceCountry)
            assertEquals(NutritionVerificationStatus.VERIFIED, result.items.single().verificationStatus)
        }
    }

    @Test
    fun `fractional package and labelled serving math stay deterministic`() = runBlocking {
        val packageCase = SuccessCase(
            text = "55% of a 320 g package Nomi Test Granola",
            name = "Nomi Test Granola",
            quantity = 176.0,
            sourceAmount = 100.0,
            calories = 450.0,
            protein = 10.0,
            carbs = 60.0,
            fat = 18.0,
            expectedCalories = 792.0,
        )
        val packageResult = provider(
            listOf(source(packageCase.name, content = evidence(packageCase))),
            extraction(item(packageCase, packageQuantity = 320.0)),
        ).researchNutrition(packageCase.intent())
        assertEquals(792.0, packageResult.items.single().calories, 0.001)
        assertEquals(320.0, packageResult.items.single().sourcePackageQuantity!!, 0.001)

        val servingCase = SuccessCase(
            text = "30 g Nutella, two labelled servings",
            name = "Nutella",
            brand = "Ferrero",
            quantity = 30.0,
            sourceAmount = 15.0,
            calories = 80.0,
            protein = 0.9,
            carbs = 8.6,
            fat = 4.6,
            expectedCalories = 160.0,
        )
        val servingResult = provider(
            listOf(source("Ferrero Nutella labelled serving", content = evidence(servingCase))),
            extraction(item(servingCase)),
        ).researchNutrition(servingCase.intent())
        assertEquals(160.0, servingResult.items.single().calories, 0.001)
        assertEquals(17.2, servingResult.items.single().carbohydrateGrams, 0.001)
    }

    @Test
    fun `conflicting sources use only the selected Exa source id`() = runBlocking {
        val case = SuccessCase(
            text = "100 g Exact Product",
            name = "Exact Product",
            quantity = 100.0,
            sourceAmount = 100.0,
            calories = 100.0,
            protein = 10.0,
            carbs = 20.0,
            fat = 3.0,
            expectedCalories = 100.0,
        )
        val officialUrl = "https://manufacturer.test/exact-product"
        val result = provider(
            sources = listOf(
                source(case.name, officialUrl, evidence(case)),
                source(case.name, "https://blog.test/exact-product", "Nutrition Exact Product: 180 kcal, protein 3 g, carbs 30 g, fat 8 g"),
            ),
            extraction = extraction(item(case)),
        ).researchNutrition(case.intent())

        assertEquals(officialUrl, result.items.single().sourceUrl)
        assertEquals(100.0, result.items.single().calories, 0.001)
    }

    @Test
    fun `no usable source rejects before Gemini is called`() {
        var geminiCalled = false
        val provider = ExaGeminiNutritionProvider(
            exaSearch = ExaNutritionSearchGateway { _, _, _ -> ExaSearchResponse() },
            geminiExtractor = GeminiNutritionExtractionGateway { _, _, _, _ ->
                geminiCalled = true
                GeminiNutritionExtraction()
            },
            exaCredential = { credential },
            geminiConfig = config,
            geminiCredential = { credential },
        )
        assertThrows(AiValidationException::class.java) {
            runBlocking { provider.researchNutrition(basicIntent("100 g Anything", "Anything")) }
        }
        assertFalse(geminiCalled)
    }

    @Test
    fun `invented or unsupported citations fail even when macros are close`() {
        val case = SuccessCase(
            text = "100 g Claimed Product",
            name = "Claimed Product",
            quantity = 100.0,
            sourceAmount = 100.0,
            calories = 100.0,
            protein = 10.0,
            carbs = 20.0,
            fat = 3.0,
            expectedCalories = 100.0,
        )
        val invented = provider(
            listOf(source(case.name, content = evidence(case))),
            extraction(item(case, sourceId = "exa-999")),
        )
        assertThrows(AiValidationException::class.java) {
            runBlocking { invented.researchNutrition(case.intent()) }
        }

        val unsupported = provider(
            listOf(source("Different Product", content = "Nutrition Different Product: 100 kcal, protein 10 g, carbs 20 g, fat 3 g")),
            extraction(item(case)),
        )
        assertThrows(AiValidationException::class.java) {
            runBlocking { unsupported.researchNutrition(case.intent()) }
        }
    }

    @Test
    fun `legitimate zero calories require explicit retrieved zero calorie evidence`() {
        val zeroCase = SuccessCase(
            text = "500 ml Coca-Cola Zero Sugar",
            name = "Coca-Cola Zero Sugar",
            brand = "Coca-Cola",
            quantity = 500.0,
            unit = "ml",
            grams = null,
            sourceAmount = 100.0,
            sourceUnit = "ml",
            calories = 0.0,
            protein = 0.0,
            carbs = 0.0,
            fat = 0.0,
            expectedCalories = 0.0,
        )
        val hallucinated = provider(
            listOf(source(zeroCase.name, content = "Nutrition Coca-Cola Zero Sugar: protein 0 g, carbs 0 g, fat 0 g")),
            extraction(item(zeroCase)),
        )
        assertThrows(AiValidationException::class.java) {
            runBlocking { hallucinated.researchNutrition(zeroCase.intent()) }
        }

        val grounded = provider(
            listOf(source(zeroCase.name, content = evidence(zeroCase))),
            extraction(item(zeroCase)),
        )
        val result = runBlocking { grounded.researchNutrition(zeroCase.intent()) }
        assertEquals(0.0, result.items.single().calories, 0.0)
        assertEquals(NutritionVerificationStatus.VERIFIED, result.items.single().verificationStatus)
    }

    private fun provider(
        sources: List<ExaSearchResult>,
        extraction: GeminiNutritionExtraction,
        localeCountry: String = "DE",
        onQuery: (String) -> Unit = {},
    ) = ExaGeminiNutritionProvider(
        exaSearch = ExaNutritionSearchGateway { query, _, _ ->
            onQuery(query)
            ExaSearchResponse(requestId = "test", results = sources)
        },
        geminiExtractor = GeminiNutritionExtractionGateway { _, _, _, prompt ->
            assertTrue(prompt.contains("source IDs are authoritative"))
            extraction
        },
        exaCredential = { credential },
        geminiConfig = config,
        geminiCredential = { credential },
        localeCountryProvider = { localeCountry },
    )

    private fun SuccessCase.intent() = ParsedFoodIntent(
        originalText = text,
        language = "en",
        items = listOf(
            ParsedFoodItem(
                name = name,
                brand = brand,
                quantity = quantity,
                unit = unit,
                gramsEquivalent = grams,
                quantityResolution = QuantityResolutionMetadata(
                    origin = QuantityOrigin.USER_EXPLICIT,
                    semantic = QuantitySemantic.DIRECT_AMOUNT,
                    canonicalQuantity = quantity,
                    canonicalUnit = unit,
                    enteredQuantity = quantity,
                    enteredUnit = unit,
                ),
            ),
        ),
    )

    private fun basicIntent(text: String, name: String) = ParsedFoodIntent(
        originalText = text,
        items = listOf(ParsedFoodItem(name, quantity = 100.0, unit = "g", gramsEquivalent = 100.0)),
    )

    private fun source(
        title: String,
        url: String = "https://manufacturer.test/product",
        content: String,
    ) = ExaSearchResult(title = title, url = url, highlights = listOf(content))

    private fun evidence(case: SuccessCase) =
        "Official nutrition ${case.name} per ${case.sourceAmount} ${case.sourceUnit}: " +
            "${case.calories} kcal, protein ${case.protein} g, carbs ${case.carbs} g, fat ${case.fat} g"

    private fun extraction(item: GeminiNutritionItem) =
        GeminiNutritionExtraction(items = listOf(item), overallConfidence = 0.98)

    private fun item(
        case: SuccessCase,
        sourceId: String = "exa-1",
        packageQuantity: Double? = null,
    ) = GeminiNutritionItem(
        name = case.name,
        brand = case.brand,
        calories = case.calories,
        proteinGrams = case.protein,
        carbohydrateGrams = case.carbs,
        fatGrams = case.fat,
        sourceId = sourceId,
        sourceProductName = case.name,
        sourceServingQuantity = case.sourceAmount,
        sourceServingUnit = case.sourceUnit,
        sourceServingGramsEquivalent = case.sourceAmount.takeIf { case.sourceUnit == "g" },
        sourceCountry = case.country,
        sourcePackageQuantity = packageQuantity,
        sourcePackageUnit = packageQuantity?.let { "g" },
        isEstimate = false,
        confidence = 0.98,
    )

    private data class SuccessCase(
        val text: String,
        val name: String,
        val brand: String? = null,
        val quantity: Double,
        val unit: String = "g",
        val grams: Double? = quantity.takeIf { unit == "g" },
        val sourceAmount: Double,
        val sourceUnit: String = unit,
        val calories: Double,
        val protein: Double,
        val carbs: Double,
        val fat: Double,
        val expectedCalories: Double,
        val country: String = "DE",
    )
}

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
        endpoint = GEMINI_API_ENDPOINT,
        model = DEFAULT_GEMINI_NUTRITION_MODEL,
        timeoutMillis = 5_000,
    )

    @Test
    fun `retrieved source URLs are published before Gemini extraction completes`() = runBlocking {
        val case = SuccessCase(
            text = "100 g Test Food",
            name = "Test Food",
            quantity = 100.0,
            sourceAmount = 100.0,
            calories = 100.0,
            protein = 5.0,
            carbs = 10.0,
            fat = 4.0,
            expectedCalories = 100.0,
            country = "DE",
        )
        var published = emptyList<String>()

        provider(
            sources = listOf(
                source(
                    title = "Official Test Food",
                    url = "https://brand.test/nutrition",
                    content = evidence(case),
                ),
            ),
            extraction = extraction(item(case)),
            onSources = { published = it },
            beforeExtraction = {
                assertEquals(listOf("https://brand.test/nutrition"), published)
            },
        ).researchNutrition(case.intent())

        assertEquals(listOf("https://brand.test/nutrition"), published)
    }

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

            assertTrue(query.startsWith("nutrition calories macros ${case.text}"))
            if (case.unit == "item") assertTrue(query.contains("weight per piece"))
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
    fun `one Duplo researches its bar weight and scales per 100 gram nutrition`() = runBlocking {
        val intent = requireNotNull(
            com.nomi.app.ai.parsing.LocalFoodIntentParser.parseOrNull("ein Duplo"),
        )
        var query = ""
        val result = provider(
            sources = listOf(
                source(
                    title = "Ferrero Duplo Deutschland",
                    content = "Ferrero Duplo: ein Riegel wiegt 18,2 g. Nährwerte pro 100 g: " +
                        "555 kcal, Protein 8 g, Kohlenhydrate 55 g, Fett 33 g",
                ),
            ),
            extraction = extraction(
                GeminiNutritionItem(
                    name = "Duplo",
                    brand = "Ferrero",
                    calories = 555.0,
                    proteinGrams = 8.0,
                    carbohydrateGrams = 55.0,
                    fatGrams = 33.0,
                    sourceId = "exa-1",
                    sourceProductName = "Ferrero Duplo",
                    sourceServingQuantity = 100.0,
                    sourceServingUnit = "g",
                    sourceServingGramsEquivalent = 100.0,
                    loggedServingGramsEquivalent = 18.2,
                    sourceCountry = "DE",
                    sourcePackageQuantity = 182.0,
                    sourcePackageUnit = "g",
                    isEstimate = false,
                    confidence = 0.98,
                ),
            ),
            onQuery = { query = it },
        ).researchNutrition(intent)

        assertTrue(query.contains("weight per piece"))
        assertEquals(1.0, result.items.single().quantity, 0.0)
        assertEquals("piece", result.items.single().unit)
        assertEquals(18.2, result.items.single().gramsEquivalent!!, 0.0)
        assertEquals(101.01, result.items.single().calories, 0.001)
    }

    @Test
    fun `multiple bars use the sourced per bar weight for deterministic total`() = runBlocking {
        val case = SuccessCase(
            text = "2 Duplo",
            name = "Duplo",
            brand = "Ferrero",
            quantity = 2.0,
            unit = "pieces",
            grams = null,
            sourceAmount = 100.0,
            sourceUnit = "g",
            calories = 555.0,
            protein = 8.0,
            carbs = 55.0,
            fat = 33.0,
            expectedCalories = 202.02,
        )
        val result = provider(
            sources = listOf(
                source(
                    title = "Ferrero Duplo Deutschland",
                    content = "Ferrero Duplo: ein Riegel wiegt 18,2 g. Nährwerte pro 100 g: " +
                        "555 kcal, Protein 8 g, Kohlenhydrate 55 g, Fett 33 g",
                ),
            ),
            extraction = extraction(item(case).copy(loggedServingGramsEquivalent = 36.4)),
        ).researchNutrition(case.intent())

        assertEquals(36.4, result.items.single().gramsEquivalent!!, 0.0)
        assertEquals(202.02, result.items.single().calories, 0.001)
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
            exaSearch = ExaNutritionSearchGateway { _, _, _, _ -> ExaSearchResponse() },
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
        val estimated = runBlocking { unsupported.researchNutrition(case.intent()) }.items.single()
        assertTrue(estimated.isEstimate)
        assertEquals(null, estimated.sourceUrl)
    }

    @Test
    fun `unverified restaurant size keeps the logged serving basis`() = runBlocking {
        val case = SuccessCase(
            text = "eine mittlere Pommes",
            name = "Pommes mittel",
            brand = "McDonald's",
            quantity = 1.0,
            unit = "medium",
            grams = null,
            sourceAmount = 100.0,
            sourceUnit = "g",
            calories = 337.0,
            protein = 4.0,
            carbs = 42.0,
            fat = 16.0,
            expectedCalories = 337.0,
        )
        val result = provider(
            sources = listOf(
                source(
                    title = "McDonald's Pommes mittel",
                    content = "McDonald's Pommes mittel nutrition page without a readable values table",
                ),
            ),
            extraction = extraction(item(case)),
        ).researchNutrition(case.intent()).items.single()

        assertTrue(result.isEstimate)
        assertEquals(1.0, result.sourceServingQuantity!!, 0.0)
        assertEquals("medium", result.sourceServingUnit)
        assertEquals(337.0, result.calories, 0.0)
    }

    @Test
    fun `adjacent Extra Sauce source id is corrected to grounded Cheeseburger source`() = runBlocking {
        val case = SuccessCase(
            text = "einen McDonald's Cheeseburger",
            name = "Cheeseburger",
            brand = "McDonald's",
            quantity = 1.0,
            unit = "item",
            grams = null,
            sourceAmount = 1.0,
            sourceUnit = "item",
            calories = 304.0,
            protein = 15.0,
            carbs = 31.0,
            fat = 13.0,
            expectedCalories = 304.0,
        )
        val burgerUrl = "https://mcdonalds.test/de-de/product/cheeseburger"
        val result = provider(
            sources = listOf(
                source(
                    title = "McDonald's Extra Sauce",
                    url = "https://mcdonalds.test/de-de/product/extra-sauce",
                    content = "McDonald's Extra Sauce: 45 kcal, Protein 0 g, Kohlenhydrate 5 g, Fett 2 g",
                ),
                source(
                    title = "McDonald's Cheeseburger",
                    url = burgerUrl,
                    content = evidence(case),
                ),
            ),
            extraction = extraction(item(case, sourceId = "exa-1")),
        ).researchNutrition(case.intent())

        assertEquals(burgerUrl, result.items.single().sourceUrl)
        assertEquals(304.0, result.items.single().calories, 0.0)
    }

    @Test
    fun `multi item meal receives a focused Exa query per item`() {
        val intent = ParsedFoodIntent(
            originalText = "McDonald's Cheeseburger mit mittleren Pommes und mittlerer Coca-Cola",
            language = "de",
            items = listOf(
                ParsedFoodItem("Cheeseburger", brand = "McDonald's", quantity = 1.0, unit = "item"),
                ParsedFoodItem("Pommes", brand = "McDonald's", quantity = 1.0, unit = "medium"),
                ParsedFoodItem("Coca-Cola", brand = "Coca-Cola", quantity = 1.0, unit = "medium"),
            ),
        )

        val queries = nutritionSearchQueries(intent)

        assertEquals(3, queries.size)
        assertTrue(queries[0].startsWith("nutrition calories macros exact item McDonald's Cheeseburger"))
        assertTrue(queries[1].startsWith("nutrition calories macros exact item McDonald's Pommes"))
        assertTrue(queries[2].startsWith("nutrition calories macros exact item Coca-Cola"))
        assertFalse("Pommes" in queries[0])
        assertFalse("Cheeseburger" in queries[1])
        assertEquals(4, exaResultsPerItemQuery(1))
        assertEquals(3, exaResultsPerItemQuery(3))
    }

    @Test
    fun `McDonalds order searches every product separately before one extraction`() = runBlocking {
        val intent = ParsedFoodIntent(
            originalText = "einen McDonald's Cheeseburger eine mittlere Pommes und eine mittlere Coca-Cola",
            language = "de",
            items = listOf(
                ParsedFoodItem("Cheeseburger", brand = "McDonald's", quantity = 1.0, unit = "serving"),
                ParsedFoodItem(
                    "Pommes",
                    brand = "McDonald's",
                    quantity = 1.0,
                    unit = "serving",
                    assumptions = listOf("mittlere Portion"),
                ),
                ParsedFoodItem(
                    "Coca-Cola",
                    brand = "Coca-Cola",
                    quantity = 1.0,
                    unit = "serving",
                    assumptions = listOf("mittlere Größe"),
                ),
            ),
        )
        val calls = mutableListOf<Pair<String, Int>>()
        val provider = ExaGeminiNutritionProvider(
            exaSearch = ExaNutritionSearchGateway { query, _, _, limit ->
                calls += query to limit
                val result = when {
                    "Cheeseburger" in query -> source(
                        "McDonald's Cheeseburger",
                        "https://mcdonalds.test/cheeseburger",
                        "Official nutrition Cheeseburger per 1 serving: 304 kcal, protein 15 g, carbs 31 g, fat 13 g",
                    )
                    "Pommes" in query -> source(
                        "McDonald's mittlere Pommes",
                        "https://mcdonalds.test/pommes-mittel",
                        "Official nutrition Pommes mittel per 1 serving: 337 kcal, protein 4 g, carbs 42 g, fat 16 g",
                    )
                    else -> source(
                        "McDonald's Coca-Cola mittel",
                        "https://mcdonalds.test/coca-cola-mittel",
                        "Official nutrition Coca-Cola mittel per 1 serving: 170 kcal, protein 0 g, carbs 42 g, fat 0 g",
                    )
                }
                ExaSearchResponse(results = listOf(result))
            },
            geminiExtractor = GeminiNutritionExtractionGateway { _, _, _, _ ->
                GeminiNutritionExtraction(
                    items = listOf(
                        restaurantItem("Cheeseburger", "McDonald's", 304.0, 15.0, 31.0, 13.0, "exa-1"),
                        restaurantItem("Pommes", "McDonald's", 337.0, 4.0, 42.0, 16.0, "exa-2"),
                        restaurantItem("Coca-Cola", "Coca-Cola", 170.0, 0.0, 42.0, 0.0, "exa-3"),
                    ),
                    overallConfidence = 0.98,
                )
            },
            exaCredential = { credential },
            geminiConfig = config,
            geminiCredential = { credential },
            localeCountryProvider = { "DE" },
        )

        val result = provider.researchNutrition(intent)

        assertEquals(3, calls.size)
        assertTrue(calls.all { it.second == 3 })
        assertTrue(calls.any { "Cheeseburger" in it.first })
        assertTrue(calls.any { "Pommes" in it.first })
        assertTrue(calls.any { "Coca-Cola" in it.first })
        assertEquals(listOf(304.0, 337.0, 170.0), result.items.map { it.calories })
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
        onSources: (List<String>) -> Unit = {},
        beforeExtraction: () -> Unit = {},
    ) = ExaGeminiNutritionProvider(
        exaSearch = ExaNutritionSearchGateway { query, _, _, _ ->
            onQuery(query)
            ExaSearchResponse(requestId = "test", results = sources)
        },
        geminiExtractor = GeminiNutritionExtractionGateway { _, _, _, prompt ->
            beforeExtraction()
            assertTrue(prompt.contains("source IDs are authoritative"))
            extraction
        },
        exaCredential = { credential },
        geminiConfig = config,
        geminiCredential = { credential },
        localeCountryProvider = { localeCountry },
        searchProgressSink = { onSources(it) },
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

    private fun restaurantItem(
        name: String,
        brand: String,
        calories: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        sourceId: String,
    ) = GeminiNutritionItem(
        name = name,
        brand = brand,
        calories = calories,
        proteinGrams = protein,
        carbohydrateGrams = carbs,
        fatGrams = fat,
        sourceId = sourceId,
        sourceProductName = name,
        sourceServingQuantity = 1.0,
        sourceServingUnit = "serving",
        sourceCountry = "DE",
        isEstimate = false,
        confidence = 0.98,
    )

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

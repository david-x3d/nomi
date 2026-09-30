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
import com.nomi.app.ai.model.ResearchNutritionBasis
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
    fun `manufacturer can volume scales per hundred ml without inventing grams`() = runBlocking {
        val case = SuccessCase(
            text = "2 cans Coke", name = "Coke", brand = "Coke", quantity = 2.0,
            unit = "can", grams = null, sourceAmount = 100.0, sourceUnit = "ml",
            calories = 42.0, protein = 0.0, carbs = 10.5, fat = 0.0,
            expectedCalories = 277.2,
        )
        val result = provider(
            sources = listOf(source("Official Coke", "https://coke.test/original",
                evidence(case) + "; one can contains 330 ml")),
            extraction = extraction(item(case).copy(sourceUnit = "can", sourceUnitVolumeMl = 330.0)),
        ).researchNutrition(case.intent()).items.single()
        assertEquals(2.0, result.quantity, 0.0)
        assertEquals("can", result.unit)
        assertEquals(660.0, result.resolvedVolumeMl!!, 1e-10)
        assertEquals(null, result.resolvedWeightGrams)
        assertEquals(277.2, result.calories, 1e-10)
    }

    @Test
    fun `manufacturer unit weight logs one Oreo without user grams`() = runBlocking {
        val case = SuccessCase(
            text = "1 Oreo", name = "Oreo", brand = "Oreo", quantity = 1.0,
            unit = "piece", grams = null, sourceAmount = 100.0, sourceUnit = "g",
            calories = 474.0, protein = 5.0, carbs = 68.0, fat = 19.0,
            expectedCalories = 53.562,
        )
        val result = provider(
            sources = listOf(source("Official Oreo", "https://oreo.test/original",
                evidence(case) + "; one Oreo piece weighs 11.3 g")),
            extraction = extraction(item(case).copy(
                sourceUnit = "piece", sourceUnitWeightGrams = 11.3,
            )),
        ).researchNutrition(case.intent()).items.single()
        assertEquals(1.0, result.quantity, 0.0)
        assertEquals("piece", result.unit)
        assertEquals(11.3, result.resolvedWeightGrams!!, 1e-10)
        assertEquals(53.562, result.calories, 1e-10)
        assertFalse(result.isEstimate)
    }

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
    fun `grounded per-100 basis overrides a provider field that repeats the logged 400 grams`() = runBlocking {
        val case = SuccessCase(
            text = "400 g Puszta-Hütte Gulaschsuppe",
            name = "Puszta-Hütte Gulaschsuppe",
            quantity = 400.0,
            sourceAmount = 100.0,
            calories = 56.0,
            protein = 4.4,
            carbs = 5.1,
            fat = 1.7,
            expectedCalories = 224.0,
        )
        val providerResponse = item(case).copy(
            // The old contract trusted these two fields and therefore stored 56 kcal for 400 g.
            sourceServingQuantity = 400.0,
            sourceServingGramsEquivalent = 400.0,
            nutritionBasis = ResearchNutritionBasis.PER_100_G,
            sourceBasisText = "per 100 g",
        )

        val result = provider(
            sources = listOf(source(case.name, content = evidence(case))),
            extraction = extraction(providerResponse),
        ).researchNutrition(case.intent()).items.single()

        assertEquals(400.0, result.quantity, 0.0)
        assertEquals(100.0, result.sourceServingQuantity!!, 0.0)
        assertEquals(224.0, result.calories, 1e-12)
        assertEquals(17.6, result.proteinGrams, 1e-12)
        assertEquals(4.0, result.servingValidation!!.scaleFactor, 0.0)
    }

    @Test
    fun `per-100 source text cannot be labeled as a complete portion`() {
        val case = SuccessCase(
            text = "400 g soup",
            name = "Soup",
            quantity = 400.0,
            sourceAmount = 100.0,
            calories = 56.0,
            protein = 4.4,
            carbs = 5.1,
            fat = 1.7,
            expectedCalories = 224.0,
        )
        val mislabeled = item(case).copy(
            sourceServingQuantity = 400.0,
            nutritionBasis = ResearchNutritionBasis.SOURCE_SERVING,
            sourceBasisText = "per 100 g",
        )

        val error = assertThrows(AiValidationException::class.java) {
            runBlocking {
                provider(
                    sources = listOf(source(case.name, content = evidence(case))),
                    extraction = extraction(mislabeled),
                ).researchNutrition(case.intent())
            }
        }
        assertTrue(error.message!!.contains("mislabeled"))
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
                    nutritionBasis = ResearchNutritionBasis.PER_100_G,
                    sourceBasisText = "Nährwerte pro 100 g",
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
            extraction = extraction(
                item(case).copy(
                    loggedServingGramsEquivalent = 36.4,
                    sourceBasisText = "Nährwerte pro 100 g",
                ),
            ),
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
    fun `generic values cannot replace a conflicting manufacturer nutrition table`() {
        val case = SuccessCase(
            text = "100 g Exact Product",
            name = "Exact Product",
            brand = "Example Brand",
            quantity = 100.0,
            sourceAmount = 100.0,
            calories = 180.0,
            protein = 3.0,
            carbs = 30.0,
            fat = 8.0,
            expectedCalories = 180.0,
        )
        val research = provider(
            sources = listOf(
                source(
                    case.name,
                    "https://nutrition-database.test/exact-product",
                    evidence(case),
                ),
                source(
                    "Example Brand Exact Product",
                    "https://example-brand.test/exact-product",
                    "Example Brand Exact Product nutrition per 100 g: " +
                        "120 kcal, protein 9 g, carbs 12 g, fat 4 g",
                ),
            ),
            extraction = extraction(item(case, sourceId = "exa-1")),
        )

        assertThrows(AiValidationException::class.java) {
            runBlocking { research.researchNutrition(case.intent()) }
        }
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
        assertThrows(AiValidationException::class.java) {
            runBlocking { unsupported.researchNutrition(case.intent()) }
        }
    }

    @Test
    fun `unverified restaurant size keeps the logged serving basis`() = runBlocking {
        val case = SuccessCase(
            text = "eine mittlere Pommes",
            name = "Pommes mittel",
            brand = "McDonald's",
            quantity = 1.0,
            unit = "piece",
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
            extraction = extraction(
                item(case).copy(
                    sourceServingQuantity = case.quantity,
                    sourceServingUnit = case.unit,
                    sourceServingGramsEquivalent = case.grams,
                    nutritionBasis = ResearchNutritionBasis.SOURCE_SERVING,
                    sourceBasisText = null,
                    isEstimate = true,
                    uncertaintyPercent = 20.0,
                ),
            ),
        ).researchNutrition(case.intent()).items.single()

        assertTrue(result.isEstimate)
        assertEquals(1.0, result.sourceServingQuantity!!, 0.0)
        assertEquals("piece", result.sourceServingUnit)
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
                ParsedFoodItem("Cheeseburger", brand = "McDonald's", quantity = 1.0, unit = "piece"),
                ParsedFoodItem(
                    "Pommes",
                    brand = "McDonald's",
                    quantity = 1.0,
                    unit = "piece",
                    assumptions = listOf("mittlere Portion"),
                ),
                ParsedFoodItem(
                    "Coca-Cola",
                    brand = "Coca-Cola",
                    quantity = 1.0,
                    unit = "piece",
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
                        "Official nutrition Cheeseburger per 1 piece: 304 kcal, protein 15 g, carbs 31 g, fat 13 g",
                    )
                    "Pommes" in query -> source(
                        "McDonald's mittlere Pommes",
                        "https://mcdonalds.test/pommes-mittel",
                        "Official nutrition Pommes mittel per 1 piece: 337 kcal, protein 4 g, carbs 42 g, fat 16 g",
                    )
                    else -> source(
                        "McDonald's Coca-Cola mittel",
                        "https://mcdonalds.test/coca-cola-mittel",
                        "Official nutrition Coca-Cola mittel per 1 piece: 170 kcal, protein 0 g, carbs 42 g, fat 0 g",
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
    fun `a restaurant table heading both columns still grounds a per-serving reading`() = runBlocking {
        // Restaurant pages print "pro 100 g" and "pro Portion" side by side, and extracted text
        // turns the table into pipes. Quoting that heading used to reject a correct serving.
        val result = restaurantProvider(
            table = RESTAURANT_TABLE,
            basisText = "pro 100 g | pro Portion (119 g)",
        ).researchNutrition(cheeseburgerIntent()).items.single()

        assertEquals(300.0, result.calories, 1e-9)
        assertEquals(NutritionVerificationStatus.VERIFIED, result.verificationStatus)
    }

    @Test
    fun `a basis quote survives the page's own spacing and line breaks`() = runBlocking {
        // The page prints "Portion(119g)" in a table cell; the model writes it back with spaces.
        val result = restaurantProvider(
            table = RESTAURANT_TABLE.replace("pro Portion (119 g)", "pro\nPortion(119g)"),
            basisText = "pro Portion (119 g)",
        ).researchNutrition(cheeseburgerIntent()).items.single()

        assertEquals(300.0, result.calories, 1e-9)
    }

    @Test
    fun `a serving quote that is only per 100 g is still rejected`() {
        assertThrows(AiValidationException::class.java) {
            runBlocking {
                restaurantProvider(table = RESTAURANT_TABLE, basisText = "pro 100 g")
                    .researchNutrition(cheeseburgerIntent())
            }
        }
    }

    @Test
    fun `a quote that is not on the page is still rejected`() {
        assertThrows(AiValidationException::class.java) {
            runBlocking {
                restaurantProvider(table = RESTAURANT_TABLE, basisText = "pro Menü (350 g)")
                    .researchNutrition(cheeseburgerIntent())
            }
        }
    }

    private fun cheeseburgerIntent() = ParsedFoodIntent(
        originalText = "1 McDonald's Cheeseburger",
        items = listOf(
            ParsedFoodItem("Cheeseburger", brand = "McDonald's", quantity = 1.0, unit = "piece"),
        ),
    )

    private fun restaurantProvider(table: String, basisText: String) = provider(
        sources = listOf(
            source("McDonald's Cheeseburger Nährwerte", "https://mcdonalds.test/cheeseburger", table),
        ),
        extraction = extraction(
            restaurantItem("Cheeseburger", "McDonald's", 300.0, 15.5, 31.0, 12.5, "exa-1")
                .copy(sourceBasisText = basisText),
        ),
    )

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

    // region generic-food fallback
    //
    // A generic food ("steak", "rice", "banana") has no manufacturer to verify against. Research
    // legitimately returns a reputable generic per-100 reading whose exact digits need not appear
    // in any one retrieved excerpt. These cases pin that such an item still resolves, scaled to
    // the logged amount, while a branded product keeps the strict single-source requirement.

    /** Two generic pages that disagree, so no single document verifies the model's reading. */
    private fun genericSteakSources() = listOf(
        source(
            title = "Beef steak nutrition facts",
            url = "https://generic-nutrition.test/beef-steak",
            content = "Beef steak, cooked. Nutrition per 100 g: 271 kcal, protein 25 g, " +
                "carbohydrates 0 g, fat 19 g",
        ),
        source(
            title = "Steak nutrition overview",
            url = "https://food-database.test/steak",
            content = "Steak (average cut). Per 100 g: 210 kcal, protein 29 g, " +
                "carbohydrates 0 g, fat 10 g",
        ),
    )

    /** A coherent generic per-100 reading that no single retrieved page reproduces exactly. */
    private fun genericSteakEstimate(sourceId: String = "exa-1") = GeminiNutritionItem(
        name = "Steak",
        calories = 250.0,
        proteinGrams = 26.0,
        carbohydrateGrams = 0.0,
        fatGrams = 16.0,
        sourceId = sourceId,
        sourceProductName = "Steak",
        sourceServingQuantity = 100.0,
        sourceServingUnit = "g",
        sourceServingGramsEquivalent = 100.0,
        nutritionBasis = ResearchNutritionBasis.PER_100_G,
        sourceBasisText = "per 100 g",
        sourceCountry = "DE",
        isEstimate = true,
        confidence = 0.7,
    )

    private fun genericIntent(text: String, name: String, quantity: Double, unit: String = "g") =
        ParsedFoodIntent(
            originalText = text,
            language = "en",
            items = listOf(
                ParsedFoodItem(
                    name = name,
                    quantity = quantity,
                    unit = unit,
                    gramsEquivalent = quantity.takeIf { unit == "g" },
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

    @Test
    fun `a generic food resolves to a scaled estimate when no single source verifies it`() =
        runBlocking {
            val loggedGrams = 276.0
            val result = provider(
                sources = genericSteakSources(),
                extraction = extraction(genericSteakEstimate()),
            ).researchNutrition(genericIntent("276 g steak", "steak", loggedGrams))

            val item = result.items.single()
            assertEquals(loggedGrams, item.quantity, 1e-9)
            assertEquals("g", item.unit)
            // Per-100 values scaled by the logged amount, by the deterministic normalizer.
            val factor = loggedGrams / 100.0
            assertEquals(250.0 * factor, item.calories, 1e-6)
            assertEquals(26.0 * factor, item.proteinGrams, 1e-6)
            assertEquals(0.0, item.carbohydrateGrams, 1e-6)
            assertEquals(16.0 * factor, item.fatGrams, 1e-6)
            // An ungrounded generic reading is offered as an estimate, never as verified. It
            // cites nothing, but it is still a labeled estimate rather than an unknown value.
            assertTrue(item.isEstimate)
            assertEquals(NutritionVerificationStatus.ESTIMATED, item.verificationStatus)
        }

    @Test
    fun `a generic food keeps its per-100 basis instead of becoming a whole serving`() =
        runBlocking {
            val loggedGrams = 276.0
            val item = provider(
                sources = genericSteakSources(),
                extraction = extraction(genericSteakEstimate()),
            ).researchNutrition(genericIntent("276 g steak", "steak", loggedGrams))
                .items
                .single()

            // The regression this guards: relabeling a per-100 reading as a whole logged serving
            // would make 250 kcal the total for 276 g instead of the per-100 basis.
            assertEquals(ResearchNutritionBasis.PER_100_G, item.nutritionBasis)
            assertEquals(100.0, item.sourceServingQuantity!!, 1e-9)
            assertEquals("g", item.sourceServingUnit)
            assertTrue(item.calories > 250.0)
        }

    @Test
    fun `generic foods scale linearly across arbitrary gram quantities`() = runBlocking {
        listOf(1.0, 37.0, 99.0, 100.0, 276.0, 501.5, 1234.0).forEach { loggedGrams ->
            val item = provider(
                sources = genericSteakSources(),
                extraction = extraction(genericSteakEstimate()),
            ).researchNutrition(genericIntent("$loggedGrams g steak", "steak", loggedGrams))
                .items
                .single()

            val factor = loggedGrams / 100.0
            assertEquals(loggedGrams, item.quantity, 1e-9)
            assertEquals("$loggedGrams g", 250.0 * factor, item.calories, 1e-6)
            assertEquals("$loggedGrams g", 26.0 * factor, item.proteinGrams, 1e-6)
            assertEquals("$loggedGrams g", 16.0 * factor, item.fatGrams, 1e-6)
        }
    }

    @Test
    fun `an ungrounded generic result cites no source it could not verify`() = runBlocking {
        val item = provider(
            sources = genericSteakSources(),
            extraction = extraction(genericSteakEstimate()),
        ).researchNutrition(genericIntent("276 g steak", "steak", 276.0))
            .items
            .single()

        // One coherent reading, never a merge of the two disagreeing pages.
        assertEquals(null, item.sourceUrl)
        assertEquals(emptyList<String>(), item.supportingSourceUrls)
    }

    @Test
    fun `a branded product is still rejected when no single source verifies it`() {
        val brandedEstimate = genericSteakEstimate().copy(
            name = "Test Brand Ribeye",
            brand = "Test Brand",
            sourceProductName = "Test Brand Ribeye",
        )
        val intent = genericIntent("276 g Test Brand Ribeye", "Test Brand Ribeye", 276.0)
        val brandedIntent = intent.copy(
            items = listOf(intent.items.single().copy(brand = "Test Brand")),
        )

        assertThrows(AiValidationException::class.java) {
            runBlocking {
                provider(
                    sources = genericSteakSources(),
                    extraction = extraction(brandedEstimate),
                ).researchNutrition(brandedIntent)
            }
        }
    }

    @Test
    fun `a package-specific claim is still rejected when no single source verifies it`() {
        assertThrows(AiValidationException::class.java) {
            runBlocking {
                provider(
                    sources = genericSteakSources(),
                    extraction = extraction(
                        genericSteakEstimate().copy(
                            sourcePackageQuantity = 380.0,
                            sourcePackageUnit = "g",
                        ),
                    ),
                ).researchNutrition(genericIntent("276 g steak", "steak", 276.0))
            }
        }
    }

    @Test
    fun `a generic estimate still cannot claim physically impossible nutrition`() {
        assertThrows(AiValidationException::class.java) {
            runBlocking {
                provider(
                    sources = genericSteakSources(),
                    extraction = extraction(genericSteakEstimate().copy(calories = 9000.0)),
                ).researchNutrition(genericIntent("276 g steak", "steak", 276.0))
            }
        }
    }

    @Test
    fun `a generic food still verifies normally when one source supports it`() = runBlocking {
        val case = SuccessCase(
            text = "276 g steak",
            name = "steak",
            quantity = 276.0,
            sourceAmount = 100.0,
            calories = 271.0,
            protein = 25.0,
            carbs = 0.0,
            fat = 19.0,
            expectedCalories = 271.0 * 2.76,
        )
        val item = provider(
            sources = listOf(source(case.name, content = evidence(case))),
            extraction = extraction(item(case).copy(isEstimate = false)),
        ).researchNutrition(case.intent()).items.single()

        assertEquals(case.expectedCalories, item.calories, 1e-6)
        assertFalse(item.isEstimate)
        assertEquals(NutritionVerificationStatus.VERIFIED, item.verificationStatus)
    }
    // endregion

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
        sourceServingUnit = "piece",
        sourceBasisText = "per 1 piece",
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
        nutritionBasis = when {
            case.sourceAmount == 100.0 && case.sourceUnit == "g" -> ResearchNutritionBasis.PER_100_G
            case.sourceAmount == 100.0 && case.sourceUnit == "ml" -> ResearchNutritionBasis.PER_100_ML
            else -> ResearchNutritionBasis.SOURCE_SERVING
        },
        sourceBasisText = "per ${case.sourceAmount} ${case.sourceUnit}",
        sourceCountry = case.country,
        sourcePackageQuantity = packageQuantity,
        sourcePackageUnit = packageQuantity?.let { "g" },
        isEstimate = false,
        confidence = 0.98,
    )

    private companion object {
        const val RESTAURANT_TABLE =
            "McDonald's Cheeseburger Nährwerte\n" +
                "| Nährwert | pro 100 g | pro Portion (119 g) |\n" +
                "| --- | --- | --- |\n" +
                "| Energie | 252 kcal | 300 kcal |\n" +
                "| Eiweiß | 13 g | 15.5 g |\n" +
                "| Kohlenhydrate | 26 g | 31 g |\n" +
                "| Fett | 10.5 g | 12.5 g |"
    }

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

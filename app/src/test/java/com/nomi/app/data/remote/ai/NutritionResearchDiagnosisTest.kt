package com.nomi.app.data.remote.ai

import com.nomi.app.ai.model.AiProviderConfig
import com.nomi.app.ai.model.AiProviderKind
import com.nomi.app.ai.model.AiRuntimeCredential
import com.nomi.app.ai.model.NutritionVerificationStatus
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import com.nomi.app.ai.model.ResearchNutritionBasis
import com.nomi.app.ai.validation.NutritionFailureReason
import com.nomi.app.ai.validation.NutritionResearchException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the ordinary meals that used to fail with one shared "could not verify nutrition"
 * message, and the diagnosis that replaced it.
 *
 * The scenarios are written the way research actually behaves: retrieved excerpts are partial,
 * generic foods have no manufacturer to prove, and one item on a plate can be worse supported
 * than the rest. No food name or calorie figure here has any meaning to production code - the
 * same fixtures would work with any other food.
 */
class NutritionResearchDiagnosisTest {
    private val credential = AiRuntimeCredential.from("test-key")
    private val config = AiProviderConfig(
        kind = AiProviderKind.EXA_GEMINI,
        endpoint = GEMINI_API_ENDPOINT,
        model = DEFAULT_GEMINI_NUTRITION_MODEL,
        timeoutMillis = 5_000,
    )

    // region generic foods

    @Test
    fun `a generic food scales its per-100 basis to any logged amount`() = runBlocking {
        // The retrieved excerpt prints the calories but not the macros, which is the ordinary
        // shape of a generic result and the case that used to fail outright. 90 g is deliberately
        // not 100 g: at exactly 100 g the per-100 table is already the answer, so no scaling is
        // needed and the failure stayed hidden.
        val item = provider(
            sources = listOf(genericPartialPage),
            extraction = extraction(genericPer100(kcal = 213.0, p = 21.2, c = 0.0, f = 14.2)),
        ).researchNutrition(genericIntent("90 g steak", "steak", 90.0)).items.single()

        assertEquals(90.0, item.quantity, 1e-9)
        assertEquals("g", item.unit)
        assertEquals(213.0 * 0.9, item.calories, 1e-9)
        assertEquals(21.2 * 0.9, item.proteinGrams, 1e-9)
        assertEquals(0.0, item.carbohydrateGrams, 1e-9)
        assertEquals(14.2 * 0.9, item.fatGrams, 1e-9)
        assertTrue(item.isEstimate)
        assertEquals(NutritionVerificationStatus.ESTIMATED, item.verificationStatus)
    }

    @Test
    fun `every nutrient of a generic food scales by the same factor at any quantity`() =
        runBlocking {
            listOf(1.0, 7.5, 42.0, 137.0, 250.0, 999.0).forEach { grams ->
                val item = provider(
                    sources = listOf(genericPage),
                    extraction = extraction(
                        genericPer100(kcal = 213.0, p = 21.2, c = 3.5, f = 14.2).copy(
                            fiberGrams = 1.4,
                            sugarGrams = 2.1,
                            saturatedFatGrams = 5.6,
                            sodiumMilligrams = 62.0,
                        ),
                    ),
                ).researchNutrition(genericIntent("$grams g steak", "steak", grams)).items.single()

                val factor = grams / 100.0
                assertEquals("kcal at $grams g", 213.0 * factor, item.calories, 1e-9)
                assertEquals("protein at $grams g", 21.2 * factor, item.proteinGrams, 1e-9)
                assertEquals("carbs at $grams g", 3.5 * factor, item.carbohydrateGrams, 1e-9)
                assertEquals("fat at $grams g", 14.2 * factor, item.fatGrams, 1e-9)
                assertEquals("fiber at $grams g", 1.4 * factor, item.fiberGrams!!, 1e-9)
                assertEquals("sugar at $grams g", 2.1 * factor, item.sugarGrams!!, 1e-9)
                assertEquals(
                    "saturated fat at $grams g",
                    5.6 * factor,
                    item.saturatedFatGrams!!,
                    1e-9,
                )
                assertEquals("sodium at $grams g", 62.0 * factor, item.sodiumMilligrams!!, 1e-9)
                assertEquals("logged amount at $grams g", grams, item.quantity, 1e-9)
            }
        }

    @Test
    fun `a per-100 ml generic drink is never scaled as if millilitres were grams`() = runBlocking {
        val item = provider(
            sources = listOf(
                page(
                    "Apfelsaft Naehrwerte",
                    "https://example.test/apfelsaft",
                    "Apfelsaft. Naehrwerte pro 100 ml: 46 kcal, Eiweiss 0,1 g, " +
                        "Kohlenhydrate 11,2 g, Fett 0,1 g.",
                ),
            ),
            extraction = extraction(
                genericPer100(kcal = 46.0, p = 0.1, c = 11.2, f = 0.1).copy(
                    name = "Apfelsaft",
                    sourceProductName = "Apfelsaft",
                    nutritionBasis = ResearchNutritionBasis.PER_100_ML,
                    sourceServingUnit = "ml",
                    sourceBasisText = "pro 100 ml",
                ),
            ),
        ).researchNutrition(
            ParsedFoodIntent(
                originalText = "250 ml Apfelsaft",
                items = listOf(ParsedFoodItem("Apfelsaft", quantity = 250.0, unit = "ml")),
            ),
        ).items.single()

        assertEquals(46.0 * 2.5, item.calories, 1e-9)
        assertEquals("ml", item.unit)
        assertEquals("volume_ml", item.servingValidation?.dimension)
        // A volume basis must not acquire a gram equivalent it was never given.
        assertNull(item.sourceServingGramsEquivalent)
    }

    @Test
    fun `a generic food is not blocked by the model writing a word into the brand field`() =
        runBlocking {
            val item = provider(
                sources = listOf(genericPage),
                extraction = extraction(
                    genericPer100(kcal = 213.0, p = 21.2, c = 0.0, f = 14.2)
                        .copy(brand = "Generic", sourceBasisText = "per 100 g"),
                ),
            ).researchNutrition(genericIntent("150 g steak", "steak", 150.0)).items.single()

            assertEquals(213.0 * 1.5, item.calories, 1e-9)
            assertTrue(item.isEstimate)
            // The unverified reading is published without the identity it could not prove.
            assertNull(item.brand)
            assertNull(item.sourceUrl)
            assertNull(item.sourceProductName)
        }

    @Test
    fun `a preparation word in the request does not reject a page about the same food`() =
        runBlocking {
            val item = provider(
                sources = listOf(
                    page(
                        "Haehnchenbrustfilet Naehrwerte",
                        "https://example.test/haehnchenbrustfilet",
                        "Haehnchenbrustfilet. Naehrwerte pro 100 g: 100 kcal, Eiweiss 23,1 g, " +
                            "Kohlenhydrate 0,0 g, Fett 0,9 g.",
                    ),
                ),
                extraction = extraction(
                    genericPer100(kcal = 100.0, p = 23.1, c = 0.0, f = 0.9).copy(
                        name = "Haehnchenbrustfilet",
                        sourceProductName = "Haehnchenbrustfilet",
                    ),
                ),
            ).researchNutrition(
                genericIntent("180 g Haehnchenbrust", "Haehnchenbrustfilet gebraten", 180.0),
            ).items.single()

            assertEquals(180.0, item.calories, 1e-9)
            assertFalse(item.isEstimate)
            assertEquals(NutritionVerificationStatus.VERIFIED, item.verificationStatus)
        }

    @Test
    fun `a preparation word longer than the food noun is still not required to match`() =
        runBlocking {
            // "gekocht" is the longer word here, but "Reis" is the food. A rule that leaned on
            // the longest token would reject this page; a plain majority accepts it.
            val item = provider(
                sources = listOf(ricePage),
                extraction = extraction(
                    genericPer100(kcal = 130.0, p = 2.7, c = 28.2, f = 0.3).copy(
                        name = "Reis gekocht",
                        sourceProductName = "Reis gekocht",
                    ),
                ),
            ).researchNutrition(genericIntent("200 g Reis", "Reis gekocht", 200.0)).items.single()

            assertEquals(130.0 * 2.0, item.calories, 1e-9)
            assertEquals(NutritionVerificationStatus.VERIFIED, item.verificationStatus)
        }

    @Test
    fun `an unrelated food is still rejected rather than answered generically`() {
        val error = assertThrows(NutritionResearchException::class.java) {
            runBlocking {
                provider(
                    sources = listOf(genericPage),
                    extraction = extraction(
                        genericPer100(kcal = 89.0, p = 1.1, c = 22.8, f = 0.3).copy(
                            name = "Banane",
                            sourceProductName = "Banane",
                        ),
                    ),
                ).researchNutrition(genericIntent("120 g banana", "banana", 120.0))
            }
        }
        assertEquals(NutritionFailureReason.SOURCE_IDENTITY_MISMATCH, error.reason)
        assertEquals("banana", error.itemName)
    }

    // endregion

    // region multi-item meals

    @Test
    fun `every item of a generic multi-item meal keeps its own amount and source`() = runBlocking {
        val analysis = provider(
            sources = listOf(genericPage, ricePage, appleSaucePage),
            extraction = GeminiNutritionExtraction(
                items = listOf(
                    genericPer100(kcal = 213.0, p = 21.2, c = 0.0, f = 14.2, sourceId = "exa-1"),
                    genericPer100(kcal = 130.0, p = 2.7, c = 28.2, f = 0.3, sourceId = "exa-2")
                        .copy(name = "Reis gekocht", sourceProductName = "Reis gekocht"),
                    genericPer100(kcal = 42.0, p = 0.2, c = 10.1, f = 0.1, sourceId = "exa-3")
                        .copy(name = "Apfelmus", sourceProductName = "Apfelmus"),
                ),
                overallConfidence = 0.9,
            ),
        ).researchNutrition(
            ParsedFoodIntent(
                originalText = "200 g Steak, 150 g Reis und 80 g Apfelmus",
                items = listOf(
                    ParsedFoodItem("Steak", quantity = 200.0, unit = "g", gramsEquivalent = 200.0),
                    ParsedFoodItem("Reis", quantity = 150.0, unit = "g", gramsEquivalent = 150.0),
                    ParsedFoodItem(
                        "Apfelmus",
                        quantity = 80.0,
                        unit = "g",
                        gramsEquivalent = 80.0,
                    ),
                ),
            ),
        )

        assertEquals(3, analysis.items.size)
        assertEquals(213.0 * 2.0, analysis.items[0].calories, 1e-9)
        assertEquals(130.0 * 1.5, analysis.items[1].calories, 1e-9)
        assertEquals(42.0 * 0.8, analysis.items[2].calories, 1e-9)
        assertEquals(listOf(200.0, 150.0, 80.0), analysis.items.map { it.quantity })
        assertEquals(
            listOf(
                "https://example.test/rindersteak",
                "https://example.test/reis",
                "https://example.test/apfelmus",
            ),
            analysis.items.map { it.sourceUrl },
        )
    }

    @Test
    fun `one unusable item names itself instead of failing the whole meal anonymously`() {
        val error = assertThrows(NutritionResearchException::class.java) {
            runBlocking {
                provider(
                    sources = listOf(genericPage, ricePage, appleSaucePage),
                    extraction = GeminiNutritionExtraction(
                        items = listOf(
                            genericPer100(213.0, 21.2, 0.0, 14.2, sourceId = "exa-1"),
                            genericPer100(130.0, 2.7, 28.2, 0.3, sourceId = "exa-2")
                                .copy(name = "Reis gekocht", sourceProductName = "Reis gekocht"),
                            // A per-100 g reading cannot be scaled to a counted amount without a
                            // weight, and this one supplies none.
                            genericPer100(42.0, 0.2, 10.1, 0.1, sourceId = "exa-3")
                                .copy(name = "Apfelmus", sourceProductName = "Apfelmus"),
                        ),
                    ),
                ).researchNutrition(
                    ParsedFoodIntent(
                        originalText = "200 g Steak, 150 g Reis und 1 Portion Apfelmus",
                        items = listOf(
                            ParsedFoodItem("Steak", quantity = 200.0, unit = "g", gramsEquivalent = 200.0),
                            ParsedFoodItem("Reis", quantity = 150.0, unit = "g", gramsEquivalent = 150.0),
                            ParsedFoodItem("Apfelmus", quantity = 1.0, unit = "piece"),
                        ),
                    ),
                )
            }
        }

        assertEquals(NutritionFailureReason.MISSING_PORTION_WEIGHT, error.reason)
        assertEquals("Apfelmus", error.itemName)
        assertEquals(2, error.itemIndex)
        // The other two items are not mentioned: they resolved, and only the failure is reported.
        assertFalse(error.message.orEmpty().contains("Steak"))
        assertFalse(error.message.orEmpty().contains("Reis"))
    }

    @Test
    fun `a failed item is retried on its own while resolved items are kept`() = runBlocking {
        var extractionCalls = 0
        val analysis = ExaGeminiNutritionProvider(
            exaSearch = ExaNutritionSearchGateway { _, _, _, _ ->
                ExaSearchResponse(results = listOf(genericPage, ricePage))
            },
            geminiExtractor = GeminiNutritionExtractionGateway { _, _, _, prompt ->
                extractionCalls++
                if (extractionCalls == 1) {
                    assertFalse(prompt.contains("second, narrowed attempt"))
                    GeminiNutritionExtraction(
                        items = listOf(
                            genericPer100(213.0, 21.2, 0.0, 14.2, sourceId = "exa-1"),
                            // First pass: no weight for the counted amount.
                            genericPer100(130.0, 2.7, 28.2, 0.3, sourceId = "exa-2")
                                .copy(name = "Reis gekocht", sourceProductName = "Reis gekocht"),
                        ),
                    )
                } else {
                    // The narrowed retry asks only about the item that failed.
                    assertTrue(prompt.contains("second, narrowed attempt"))
                    GeminiNutritionExtraction(
                        items = listOf(
                            genericPer100(130.0, 2.7, 28.2, 0.3, sourceId = "exa-2").copy(
                                name = "Reis gekocht",
                                sourceProductName = "Reis gekocht",
                                loggedServingGramsEquivalent = 180.0,
                            ),
                        ),
                    )
                }
            },
            exaCredential = { credential },
            geminiConfig = config,
            geminiCredential = { credential },
            localeCountryProvider = { "DE" },
        ).researchNutrition(
            ParsedFoodIntent(
                originalText = "200 g Steak und 1 Portion Reis",
                items = listOf(
                    ParsedFoodItem("Steak", quantity = 200.0, unit = "g", gramsEquivalent = 200.0),
                    ParsedFoodItem("Reis", quantity = 1.0, unit = "piece"),
                ),
            ),
        )

        assertEquals(2, extractionCalls)
        assertEquals(2, analysis.items.size)
        // The item that already worked kept its verified reading from the first pass.
        assertEquals(213.0 * 2.0, analysis.items[0].calories, 1e-9)
        assertEquals(NutritionVerificationStatus.VERIFIED, analysis.items[0].verificationStatus)
        // The retried item scaled through the weight the second attempt supplied.
        assertEquals(130.0 * 1.8, analysis.items[1].calories, 1e-9)
        assertEquals(1.0, analysis.items[1].quantity, 1e-9)
        assertEquals("serving", analysis.items[1].unit)
    }

    // endregion

    // region branded products

    @Test
    fun `a branded product with manufacturer evidence stays verified`() = runBlocking {
        val item = provider(
            sources = listOf(
                page(
                    "Ruegenwalder Muehle Vegane Muehlen Frikadellen",
                    "https://www.ruegenwalder.de/produkte/muehlen-frikadellen",
                    "Vegane Muehlen Frikadellen. Naehrwerte pro 100 g: 213 kcal, " +
                        "Eiweiss 17,0 g, Kohlenhydrate 6,4 g, Fett 13,0 g.",
                ),
            ),
            extraction = extraction(
                brandedItem(
                    name = "Vegane Muehlen Frikadellen",
                    brand = "Ruegenwalder Muehle",
                    kcal = 213.0,
                    p = 17.0,
                    c = 6.4,
                    f = 13.0,
                ),
            ),
        ).researchNutrition(
            ParsedFoodIntent(
                originalText = "160 g Ruegenwalder Muehle Vegane Muehlen Frikadellen",
                items = listOf(
                    ParsedFoodItem(
                        name = "Vegane Muehlen Frikadellen",
                        brand = "Ruegenwalder Muehle",
                        quantity = 160.0,
                        unit = "g",
                        gramsEquivalent = 160.0,
                    ),
                ),
            ),
        ).items.single()

        assertEquals(213.0 * 1.6, item.calories, 1e-9)
        assertFalse(item.isEstimate)
        assertEquals(NutritionVerificationStatus.VERIFIED, item.verificationStatus)
        assertEquals("ruegenwalder.de", item.sourceDomain)
    }

    @Test
    fun `a branded product whose evidence is about something else is never downgraded`() {
        val error = assertThrows(NutritionResearchException::class.java) {
            runBlocking {
                provider(
                    // A real nutrition page, but for a different manufacturer's product.
                    sources = listOf(
                        page(
                            "Andere Marke Frikadellen",
                            "https://www.anderemarke.test/frikadellen",
                            "Andere Marke Frikadellen. Naehrwerte pro 100 g: 240 kcal, " +
                                "Eiweiss 14,0 g, Kohlenhydrate 9,0 g, Fett 16,0 g.",
                        ),
                    ),
                    extraction = extraction(
                        brandedItem(
                            name = "Vegane Muehlen Frikadellen",
                            brand = "Ruegenwalder Muehle",
                            kcal = 213.0,
                            p = 17.0,
                            c = 6.4,
                            f = 13.0,
                        ),
                    ),
                ).researchNutrition(
                    ParsedFoodIntent(
                        originalText = "160 g Ruegenwalder Muehle Vegane Muehlen Frikadellen",
                        items = listOf(
                            ParsedFoodItem(
                                name = "Vegane Muehlen Frikadellen",
                                brand = "Ruegenwalder Muehle",
                                quantity = 160.0,
                                unit = "g",
                                gramsEquivalent = 160.0,
                            ),
                        ),
                    ),
                )
            }
        }

        assertEquals(NutritionFailureReason.SOURCE_IDENTITY_MISMATCH, error.reason)
        assertEquals("Vegane Muehlen Frikadellen", error.itemName)
    }

    @Test
    fun `a package size claim keeps a reading out of the generic estimate path`() {
        val error = assertThrows(NutritionResearchException::class.java) {
            runBlocking {
                provider(
                    sources = listOf(genericPage),
                    extraction = extraction(
                        genericPer100(kcal = 271.0, p = 25.0, c = 0.0, f = 19.0).copy(
                            sourceBasisText = null,
                            sourcePackageQuantity = 380.0,
                            sourcePackageUnit = "g",
                        ),
                    ),
                ).researchNutrition(genericIntent("276 g steak", "steak", 276.0))
            }
        }
        assertNotNull(error.reason)
    }

    // endregion

    // region provider failures

    @Test
    fun `a search timeout is reported as a timeout, not as unverifiable nutrition`() {
        val error = assertThrows(HttpRequestTimeoutException::class.java) {
            runBlocking {
                ExaGeminiNutritionProvider(
                    exaSearch = ExaNutritionSearchGateway { _, _, _, _ ->
                        throw HttpRequestTimeoutException("https://api.exa.ai/search", 5_000)
                    },
                    geminiExtractor = GeminiNutritionExtractionGateway { _, _, _, _ ->
                        error("extraction must not run after a search timeout")
                    },
                    exaCredential = { credential },
                    geminiConfig = config,
                    geminiCredential = { credential },
                    localeCountryProvider = { "DE" },
                ).researchNutrition(genericIntent("150 g steak", "steak", 150.0))
            }
        }
        assertTrue(error.message.orEmpty().contains("timeout", ignoreCase = true))
    }

    @Test
    fun `an extraction timeout is reported as a timeout, not as unverifiable nutrition`() {
        assertThrows(HttpRequestTimeoutException::class.java) {
            runBlocking {
                ExaGeminiNutritionProvider(
                    exaSearch = ExaNutritionSearchGateway { _, _, _, _ ->
                        ExaSearchResponse(results = listOf(genericPage))
                    },
                    geminiExtractor = GeminiNutritionExtractionGateway { _, _, _, _ ->
                        throw HttpRequestTimeoutException(GEMINI_API_ENDPOINT, 5_000)
                    },
                    exaCredential = { credential },
                    geminiConfig = config,
                    geminiCredential = { credential },
                    localeCountryProvider = { "DE" },
                ).researchNutrition(genericIntent("150 g steak", "steak", 150.0))
            }
        }
    }

    @Test
    fun `retrieval that returns nothing usable is reported as a missing source`() {
        val error = assertThrows(NutritionResearchException::class.java) {
            runBlocking {
                provider(
                    sources = emptyList(),
                    extraction = extraction(genericPer100(213.0, 21.2, 0.0, 14.2)),
                ).researchNutrition(genericIntent("150 g steak", "steak", 150.0))
            }
        }
        assertEquals(NutritionFailureReason.NO_SUITABLE_SOURCE, error.reason)
    }

    // endregion

    private fun provider(
        sources: List<ExaSearchResult>,
        extraction: GeminiNutritionExtraction,
    ) = ExaGeminiNutritionProvider(
        exaSearch = ExaNutritionSearchGateway { _, _, _, _ ->
            ExaSearchResponse(requestId = "test", results = sources)
        },
        geminiExtractor = GeminiNutritionExtractionGateway { _, _, _, _ -> extraction },
        exaCredential = { credential },
        geminiConfig = config,
        geminiCredential = { credential },
        localeCountryProvider = { "DE" },
    )

    private fun page(title: String, url: String, text: String) =
        ExaSearchResult(title = title, url = url, highlights = listOf(text))

    private val genericPage = page(
        "Rindersteak Kalorien und Naehrwerte",
        "https://example.test/rindersteak",
        "Rindersteak. Naehrwerte pro 100 g: 213 kcal, Eiweiss 21,2 g, " +
            "Kohlenhydrate 0,0 g, Fett 14,2 g. Ballaststoffe 1,4 g, Zucker 2,1 g, " +
            "davon gesaettigte Fettsaeuren 5,6 g, Natrium 62 mg.",
    )
    private val genericPartialPage = page(
        "Rindersteak Kalorien",
        "https://example.test/rindersteak-kalorien",
        "Rindersteak hat 213 kcal pro 100 g. Die Naehrwerte variieren je nach Zuschnitt.",
    )
    private val ricePage = page(
        "Reis gekocht Naehrwerte",
        "https://example.test/reis",
        "Reis gekocht. Naehrwerte pro 100 g: 130 kcal, Eiweiss 2,7 g, " +
            "Kohlenhydrate 28,2 g, Fett 0,3 g. Eine Portion wiegt 180 g.",
    )
    private val appleSaucePage = page(
        "Apfelmus Naehrwerte",
        "https://example.test/apfelmus",
        "Apfelmus. Naehrwerte pro 100 g: 42 kcal, Eiweiss 0,2 g, " +
            "Kohlenhydrate 10,1 g, Fett 0,1 g.",
    )

    private fun genericIntent(text: String, name: String, grams: Double) = ParsedFoodIntent(
        originalText = text,
        items = listOf(
            ParsedFoodItem(name = name, quantity = grams, unit = "g", gramsEquivalent = grams),
        ),
    )

    @Suppress("LongParameterList")
    private fun genericPer100(
        kcal: Double,
        p: Double,
        c: Double,
        f: Double,
        sourceId: String = "exa-1",
    ) = GeminiNutritionItem(
        name = "Rindersteak",
        calories = kcal,
        proteinGrams = p,
        carbohydrateGrams = c,
        fatGrams = f,
        calorieExplanation = "Test explanation",
        sourceId = sourceId,
        sourceProductName = "Rindersteak",
        sourceServingQuantity = 100.0,
        sourceServingUnit = "g",
        nutritionBasis = ResearchNutritionBasis.PER_100_G,
        sourceBasisText = "pro 100 g",
        isEstimate = false,
        confidence = 0.95,
    )

    @Suppress("LongParameterList")
    private fun brandedItem(
        name: String,
        brand: String,
        kcal: Double,
        p: Double,
        c: Double,
        f: Double,
    ) = GeminiNutritionItem(
        name = name,
        brand = brand,
        calories = kcal,
        proteinGrams = p,
        carbohydrateGrams = c,
        fatGrams = f,
        calorieExplanation = "Test explanation",
        sourceId = "exa-1",
        sourceProductName = name,
        sourceServingQuantity = 100.0,
        sourceServingUnit = "g",
        nutritionBasis = ResearchNutritionBasis.PER_100_G,
        sourceBasisText = "pro 100 g",
        sourceCountry = "DE",
        isEstimate = false,
        confidence = 0.97,
    )

    private fun extraction(item: GeminiNutritionItem) =
        GeminiNutritionExtraction(items = listOf(item), overallConfidence = 0.95)
}

package com.nomi.app.data.remote.ai

import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.NutritionVerificationStatus
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import com.nomi.app.ai.validation.ServingNutritionNormalizer
import com.nomi.app.ai.validation.SourceIntegrityVerifier
import com.nomi.app.ai.validation.UserQuantityResolver
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class BrandedFoodResearchRegressionTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun fetchedOfficialRuegenwalderTableScalesToLogged80g() {
        val foodJson = """
            {
              "items": [{
                "name": "Veganer Schinken Spicker Mortadella",
                "brand": "Rügenwalder Mühle",
                "quantity": 200,
                "unit": "g",
                "gramsEquivalent": 200,
                "calories": 117,
                "proteinGrams": 2.2,
                "carbohydrateGrams": 2.9,
                "fatGrams": 9.0,
                "fiberGrams": 8.0,
                "sourceName": "Official manufacturer",
                "sourceProductName": "Veganer Schinken Spicker Mortadella",
                "sourceDomain": "ruegenwalder.de",
                "sourceServingQuantity": 100,
                "sourceServingUnit": "g",
                "sourceServingGramsEquivalent": 100,
                "sourceCountry": "DE",
                "sourcePackageQuantity": 200,
                "sourcePackageUnit": "g",
                "isEstimate": false,
                "confidence": 0.99,
                "assumptions": []
              }],
              "overallConfidence": 0.99
            }
        """.trimIndent()
        val officialUrl =
            "https://www.ruegenwalder.de/de/produkte/vegane-produkte/" +
                "veganer-wurstaufschnitt/veganer-schinken-spicker/mortadella"
        val fixture = """
            {
              "output": [
                {
                  "type": "openrouter:web_search",
                  "status": "completed",
                  "action": {
                    "type": "search",
                    "query": "\"Rügenwalder Mühle\" \"Veganer Schinken Spicker Mortadella\" Nährwerte",
                    "sources": [{"type": "url", "url": "__OFFICIAL_URL__"}]
                  }
                },
                {
                  "type": "openrouter:web_fetch",
                  "status": "completed",
                  "url": "__OFFICIAL_URL__",
                  "httpStatus": 200,
                  "title": "Veganer Schinken Spicker Mortadella",
                  "content": "Nährwerte je 100 g: 117 kcal, Fett 9,0 g, Kohlenhydrate 2,9 g, Eiweiß 2,2 g"
                },
                {
                  "type": "message",
                  "status": "completed",
                  "role": "assistant",
                  "content": [{
                    "type": "output_text",
                    "text": __FOOD_JSON__,
                    "annotations": [{"type": "url_citation", "url": "__OFFICIAL_URL__"}]
                  }]
                }
              ]
            }
        """.trimIndent()
            .replace("__FOOD_JSON__", json.encodeToString(JsonPrimitive(foodJson)))
            .replace("__OFFICIAL_URL__", officialUrl)

        val completion = decodeOpenRouterResponsesResearchPayload(json, fixture)
        val providerAnalysis: FoodAnalysis = json.decodeFromString(completion.content)
        val grounded = groundWithWebSearchEvidence(
            analysis = providerAnalysis,
            evidenceUrls = completion.evidenceUrls,
            fetchedUrls = completion.fetchedUrls,
            requiresFetchedBrandedSource = completion.requiresFetchedBrandedSource,
        )
        val userText = "80 g Rügenwalder Mühle Veganer Schinken Spicker Mortadella"
        val parserResult = ParsedFoodIntent(
            originalText = userText,
            language = "de",
            items = listOf(
                ParsedFoodItem(
                    name = "Veganer Schinken Spicker Mortadella",
                    brand = "Rügenwalder Mühle",
                    // Deliberately wrong provider amount: deterministic user-text parsing must win.
                    quantity = 200.0,
                    unit = "g",
                    gramsEquivalent = 200.0,
                ),
            ),
        )
        val reconciledIntent = UserQuantityResolver.reconcileParsedIntent(
            userText,
            parserResult,
            "DE",
        )
        val reconciledAnalysis = UserQuantityResolver.reconcileAnalysis(
            reconciledIntent,
            grounded,
        )
        val normalized = ServingNutritionNormalizer.normalize(
            reconciledIntent,
            reconciledAnalysis,
        )
        val verified = SourceIntegrityVerifier.resolve(
            rejectPlaceholderNutrition(normalized),
        ).items.single()

        assertEquals(80.0, verified.quantity, 0.0)
        assertEquals("g", verified.unit)
        assertEquals(93.6, verified.calories, 1e-12)
        assertEquals(1.76, verified.proteinGrams, 1e-12)
        assertEquals(2.32, verified.carbohydrateGrams, 1e-12)
        assertEquals(7.2, verified.fatGrams, 1e-12)
        assertEquals(117.0, verified.servingValidation!!.caloriesPer100, 0.0)
        assertEquals(200.0, verified.sourcePackageQuantity!!, 0.0)
        assertEquals(officialUrl, verified.sourceUrl)
        assertEquals("ruegenwalder.de", verified.sourceDomain)
        assertEquals(NutritionVerificationStatus.VERIFIED, verified.verificationStatus)
        ServingNutritionNormalizer.validateBeforeSave(FoodAnalysis(items = listOf(verified)))
    }
}

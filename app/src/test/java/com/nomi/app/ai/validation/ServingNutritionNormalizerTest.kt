package com.nomi.app.ai.validation

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import com.nomi.app.ai.parsing.LocalFoodIntentParser
import com.nomi.app.ai.prompt.AiPrompts
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ServingNutritionNormalizerTest {
    @Test
    fun `355 ml source serving is normalized before scaling to logged 250 ml`() {
        val normalized = normalize(
            raw = sourceItem(
                loggedQuantity = 250.0,
                loggedUnit = "ml",
                sourceQuantity = 355.0,
                sourceUnit = "ml",
                calories = 149.0,
                protein = 0.0,
                carbs = 39.0,
                fat = 0.0,
            ),
            requestedQuantity = 250.0,
            requestedUnit = "ml",
        )

        assertEquals(149.0 * 250.0 / 355.0, normalized.calories, 1e-12)
        assertEquals(39.0 * 250.0 / 355.0, normalized.carbohydrateGrams, 1e-12)
        assertFalse(normalized.calories == 149.0)
        assertEquals(250.0, normalized.quantity, 0.0)
        assertEquals("ml", normalized.unit)
        assertTrue(normalized.requiresServingValidation)
        assertEquals(149.0 * 100.0 / 355.0, normalized.servingValidation!!.caloriesPer100, 1e-12)
    }

    @Test
    fun `US fluid ounces are volume and scale exactly to milliliters`() {
        val normalized = normalize(
            raw = sourceItem(
                loggedQuantity = 250.0,
                loggedUnit = "ml",
                sourceQuantity = 12.0,
                sourceUnit = "US fl oz",
                calories = 150.0,
                protein = 0.0,
                carbs = 37.5,
                fat = 0.0,
            ),
            requestedQuantity = 250.0,
            requestedUnit = "ml",
        )

        val sourceMl = 12.0 * 29.5735295625
        assertEquals(150.0 * 250.0 / sourceMl, normalized.calories, 1e-12)
        assertEquals(sourceMl, normalized.servingValidation!!.sourceBaseAmount, 1e-12)
        ServingNutritionNormalizer.validateBeforeSave(FoodAnalysis(listOf(normalized)))
    }

    @Test
    fun `grams kilograms and mass ounces use one mass basis`() {
        val kilogram = normalize(
            raw = sourceItem(1.0, "kg", 100.0, "g"),
            requestedQuantity = 1.0,
            requestedUnit = "kg",
        )
        assertEquals(1_000.0, kilogram.calories, 1e-12)

        val ounces = normalize(
            raw = sourceItem(2.0, "oz", 28.349523125, "g"),
            requestedQuantity = 2.0,
            requestedUnit = "oz",
        )
        assertEquals(200.0, ounces.calories, 1e-10)
        assertEquals(56.69904625, ounces.servingValidation!!.loggedBaseAmount, 1e-10)
    }

    @Test
    fun `milligrams use the gram mass basis`() {
        val normalized = normalize(
            raw = sourceItem(500.0, "mg", 1.0, "g"),
            requestedQuantity = 500.0,
            requestedUnit = "mg",
        )

        assertEquals(50.0, normalized.calories, 1e-12)
        assertEquals(0.5, normalized.servingValidation!!.loggedBaseAmount, 1e-12)
    }

    @Test
    fun `English and German spoons use exact milliliter factors`() {
        val tablespoon = normalize(
            raw = sourceItem(1.0, "EL", 15.0, "ml"),
            requestedQuantity = 1.0,
            requestedUnit = "Essl\u00f6ffel",
        )
        val teaspoons = normalize(
            raw = sourceItem(2.0, "TL", 10.0, "ml"),
            requestedQuantity = 2.0,
            requestedUnit = "teaspoons",
        )

        assertEquals(15.0, tablespoon.servingValidation!!.loggedBaseAmount, 0.0)
        assertEquals(10.0, teaspoons.servingValidation!!.loggedBaseAmount, 0.0)
        ServingNutritionNormalizer.validateBeforeSave(FoodAnalysis(listOf(tablespoon, teaspoons)))
    }

    @Test
    fun `spoon to mass falls back visibly when no density is available`() {
        val estimated = normalize(
            raw = sourceItem(1.0, "EL", 100.0, "g"),
            requestedQuantity = 1.0,
            requestedUnit = "EL",
        )
        assertEquals(15.0, estimated.gramsEquivalent!!, 0.0)
        assertEquals(15.0, estimated.calories, 1e-12)
        assertTrue(estimated.isEstimate)
        assertTrue(estimated.assumptions.any { it.contains("1 g per ml") })

        val withMass = normalize(
            raw = sourceItem(1.0, "EL", 100.0, "g").copy(gramsEquivalent = 12.0),
            requestedQuantity = 1.0,
            requestedUnit = "EL",
        )
        assertEquals(12.0, withMass.calories, 1e-12)
        assertEquals(12.0, withMass.servingValidation!!.loggedBaseAmount, 0.0)
    }

    @Test
    fun `German jam spoon uses labeled food specific mass estimate`() {
        val normalized = normalize(
            raw = sourceItem(1.5, "Löffel", 100.0, "g")
                .copy(name = "Himbeer Marmelade"),
            requestedQuantity = 1.5,
            requestedUnit = "Löffel",
            requestedName = "Himbeer Marmelade",
        )

        assertEquals(30.0, normalized.gramsEquivalent!!, 1e-12)
        assertEquals(30.0, normalized.calories, 1e-12)
        assertTrue(normalized.isEstimate)
        assertTrue(normalized.assumptions.any { it.contains("20 g per German tablespoon") })
        ServingNutritionNormalizer.validateBeforeSave(FoodAnalysis(listOf(normalized)))
    }

    @Test
    fun `provider supplied jam spoon mass wins over fallback`() {
        val normalized = normalize(
            raw = sourceItem(1.5, "EL", 100.0, "g")
                .copy(name = "Himbeer Marmelade", gramsEquivalent = 27.0),
            requestedQuantity = 1.5,
            requestedUnit = "EL",
            requestedName = "Himbeer Marmelade",
        )

        assertEquals(27.0, normalized.gramsEquivalent!!, 0.0)
        assertEquals(27.0, normalized.calories, 1e-12)
        assertFalse(normalized.assumptions.any { it.contains("20 g per German tablespoon") })
    }

    @Test
    fun `mass log can use spoon source only with source total grams equivalent`() {
        val normalized = normalize(
            raw = sourceItem(15.0, "g", 1.0, "tbsp", calories = 30.0)
                .copy(sourceServingGramsEquivalent = 20.0),
            requestedQuantity = 15.0,
            requestedUnit = "g",
        )

        assertEquals(22.5, normalized.calories, 1e-12)
        assertEquals("mass_g", normalized.servingValidation!!.dimension)
    }

    @Test
    fun `liters normalize to milliliters without density assumptions`() {
        val normalized = normalize(
            raw = sourceItem(0.25, "l", 100.0, "ml"),
            requestedQuantity = 0.25,
            requestedUnit = "l",
        )

        assertEquals(250.0, normalized.calories, 1e-12)
        assertEquals("volume_ml", normalized.servingValidation!!.dimension)
        assertEquals(250.0, normalized.servingValidation!!.loggedBaseAmount, 1e-12)
    }

    @Test
    fun `piece servings scale only through compatible piece units`() {
        val normalized = normalize(
            raw = sourceItem(2.0, "pieces", 1.0, "piece"),
            requestedQuantity = 2.0,
            requestedUnit = "pieces",
        )

        assertEquals(200.0, normalized.calories, 1e-12)
        assertEquals(20.0, normalized.proteinGrams, 1e-12)
        assertEquals("piece", normalized.servingValidation!!.dimension)
    }

    @Test
    fun `German Stueck aliases preserve piece-compatible scaling`() {
        val normalized = normalize(
            raw = sourceItem(2.0, "Stücke", 1.0, "Stück"),
            requestedQuantity = 2.0,
            requestedUnit = "Stücke",
        )

        assertEquals(200.0, normalized.calories, 1e-12)
        assertEquals("piece", normalized.servingValidation!!.dimension)
        ServingNutritionNormalizer.validateBeforeSave(FoodAnalysis(listOf(normalized)))
    }

    @Test
    fun `apple logged as one Stueck scales a gram source through its exact gram equivalent`() {
        val normalized = normalize(
            raw = sourceItem(
                loggedQuantity = 1.0,
                loggedUnit = "St\u00fcck",
                sourceQuantity = 100.0,
                sourceUnit = "g",
                calories = 52.0,
                protein = 0.3,
                carbs = 13.8,
                fat = 0.2,
            ).copy(gramsEquivalent = 182.0),
            requestedQuantity = 1.0,
            requestedUnit = "St\u00fcck",
        )

        assertEquals(52.0 * 1.82, normalized.calories, 1e-12)
        assertEquals(182.0, normalized.gramsEquivalent!!, 0.0)
        assertEquals(1.0, normalized.quantity, 0.0)
        assertEquals("St\u00fcck", normalized.unit)
        assertEquals("mass_g", normalized.servingValidation!!.dimension)
        assertEquals(182.0, normalized.servingValidation!!.loggedBaseAmount, 0.0)
        ServingNutritionNormalizer.validateBeforeSave(FoodAnalysis(listOf(normalized)))
    }

    @Test
    fun `one generic apple without gram equivalent uses estimated medium apple mass`() {
        val normalized = normalize(
            raw = sourceItem(
                loggedQuantity = 1.0,
                loggedUnit = "piece",
                sourceQuantity = 100.0,
                sourceUnit = "g",
                calories = 52.0,
                protein = 0.3,
                carbs = 13.8,
                fat = 0.2,
            ).copy(name = "Apple"),
            requestedQuantity = 1.0,
            requestedUnit = "piece",
            requestedName = "apple",
        )

        assertEquals(182.0, normalized.gramsEquivalent!!, 0.0)
        assertEquals(52.0 * 1.82, normalized.calories, 1e-12)
        assertEquals(1.0, normalized.quantity, 0.0)
        assertEquals("piece", normalized.unit)
        assertTrue(normalized.isEstimate)
        assertTrue(normalized.assumptions.any { it.contains("182 g per medium apple") })
        ServingNutritionNormalizer.validateBeforeSave(FoodAnalysis(listOf(normalized)))
    }

    @Test
    fun `two generic Apfel pieces preserve count and use total estimated mass`() {
        val normalized = normalize(
            raw = sourceItem(
                loggedQuantity = 2.0,
                loggedUnit = "Stücke",
                sourceQuantity = 100.0,
                sourceUnit = "g",
                calories = 52.0,
                protein = 0.3,
                carbs = 13.8,
                fat = 0.2,
            ).copy(name = "Apfel"),
            requestedQuantity = 2.0,
            requestedUnit = "Stücke",
            requestedName = "Äpfel",
        )

        assertEquals(364.0, normalized.gramsEquivalent!!, 0.0)
        assertEquals(52.0 * 3.64, normalized.calories, 1e-12)
        assertEquals(2.0, normalized.quantity, 0.0)
        assertEquals("Stücke", normalized.unit)
        assertEquals(364.0, normalized.servingValidation!!.loggedBaseAmount, 0.0)
        assertTrue(normalized.isEstimate)
        assertTrue(normalized.assumptions.any { it.contains("182 g per medium apple") })
        ServingNutritionNormalizer.validateBeforeSave(FoodAnalysis(listOf(normalized)))
    }

    @Test
    fun `supplied generic apple gram equivalent wins without fallback estimate`() {
        val normalized = normalize(
            raw = sourceItem(
                loggedQuantity = 1.0,
                loggedUnit = "piece",
                sourceQuantity = 100.0,
                sourceUnit = "g",
                calories = 52.0,
                protein = 0.3,
                carbs = 13.8,
                fat = 0.2,
            ).copy(
                name = "Apple",
                gramsEquivalent = 150.0,
            ),
            requestedQuantity = 1.0,
            requestedUnit = "piece",
            requestedName = "apple",
        )

        assertEquals(150.0, normalized.gramsEquivalent!!, 0.0)
        assertEquals(52.0 * 1.5, normalized.calories, 1e-12)
        assertFalse(normalized.isEstimate)
        assertFalse(normalized.assumptions.any { it.contains("182 g per medium apple") })
        ServingNutritionNormalizer.validateBeforeSave(FoodAnalysis(listOf(normalized)))
    }

    @Test
    fun `piece source can scale to explicit grams only with its supplied total gram equivalent`() {
        val normalized = normalize(
            raw = sourceItem(
                loggedQuantity = 91.0,
                loggedUnit = "g",
                sourceQuantity = 1.0,
                sourceUnit = "piece",
                calories = 95.0,
                protein = 0.5,
                carbs = 25.0,
                fat = 0.3,
            ).copy(sourceServingGramsEquivalent = 182.0),
            requestedQuantity = 91.0,
            requestedUnit = "g",
        )

        assertEquals(47.5, normalized.calories, 1e-12)
        assertEquals(91.0, normalized.quantity, 0.0)
        assertEquals("g", normalized.unit)
        assertEquals("mass_g", normalized.servingValidation!!.dimension)
        ServingNutritionNormalizer.validateBeforeSave(FoodAnalysis(listOf(normalized)))
    }

    @Test
    fun `explicit gram and milliliter amounts retain their native basis`() {
        val grams = normalize(
            raw = sourceItem(176.0, "g", 100.0, "g"),
            requestedQuantity = 176.0,
            requestedUnit = "g",
        )
        val milliliters = normalize(
            raw = sourceItem(250.0, "ml", 100.0, "ml").copy(gramsEquivalent = 260.0),
            requestedQuantity = 250.0,
            requestedUnit = "ml",
        )

        assertEquals(176.0, grams.calories, 1e-12)
        assertEquals("mass_g", grams.servingValidation!!.dimension)
        assertEquals(250.0, milliliters.calories, 1e-12)
        assertEquals("volume_ml", milliliters.servingValidation!!.dimension)
    }

    @Test
    fun `mass source and piece log without an exact piece weight remain incompatible`() {
        val error = assertThrows(AiValidationException::class.java) {
            normalize(
                raw = sourceItem(1.0, "St\u00fcck", 100.0, "g"),
                requestedQuantity = 1.0,
                requestedUnit = "St\u00fcck",
            )
        }

        assertTrue(error.message!!.contains("not compatible"))
    }

    @Test
    fun `mass and volume mismatch uses a labeled fallback instead of blocking the log`() {
        val normalized = normalize(
            raw = sourceItem(250.0, "g", 355.0, "ml"),
            requestedQuantity = 250.0,
            requestedUnit = "g",
        )

        assertEquals(355.0, normalized.sourceServingGramsEquivalent!!, 0.0)
        assertEquals(100.0 * 250.0 / 355.0, normalized.calories, 1e-12)
        assertTrue(normalized.isEstimate)
        assertTrue(normalized.assumptions.any { it.contains("1 g per ml") })
        ServingNutritionNormalizer.validateBeforeSave(FoodAnalysis(listOf(normalized)))
    }

    @Test
    fun `AI source without an explicit serving basis is rejected`() {
        val raw = sourceItem(250.0, "ml", 355.0, "ml").copy(
            sourceServingQuantity = null,
            sourceServingUnit = null,
        )

        assertThrows(AiValidationException::class.java) {
            normalize(raw, 250.0, "ml")
        }
    }

    @Test
    fun `non finite source and logged amounts are rejected`() {
        assertThrows(AiValidationException::class.java) {
            normalize(
                sourceItem(250.0, "ml", 355.0, "ml").copy(
                    sourceServingQuantity = Double.NaN,
                ),
                250.0,
                "ml",
            )
        }
        assertThrows(AiValidationException::class.java) {
            ServingNutritionNormalizer.normalizeSourceServingTo(
                sourceItem(250.0, "ml", 355.0, "ml"),
                Double.POSITIVE_INFINITY,
                "ml",
            )
        }
    }

    @Test
    fun `AI logged amount must match the structured user amount`() {
        val fullCanMistakenlyReturnedAsLogged = sourceItem(
            loggedQuantity = 355.0,
            loggedUnit = "ml",
            sourceQuantity = 355.0,
            sourceUnit = "ml",
        )

        val error = assertThrows(AiValidationException::class.java) {
            normalize(fullCanMistakenlyReturnedAsLogged, 250.0, "ml")
        }
        assertTrue(error.message!!.contains("does not match"))
    }

    @Test
    fun `save validation rejects changed amount source basis and nutrition`() {
        val normalized = normalize(sourceItem(250.0, "ml", 355.0, "ml"), 250.0, "ml")

        assertThrows(AiValidationException::class.java) {
            ServingNutritionNormalizer.validateBeforeSave(
                FoodAnalysis(listOf(normalized.copy(quantity = 355.0))),
            )
        }
        assertThrows(AiValidationException::class.java) {
            ServingNutritionNormalizer.validateBeforeSave(
                FoodAnalysis(listOf(normalized.copy(sourceServingQuantity = 250.0))),
            )
        }
        assertThrows(AiValidationException::class.java) {
            ServingNutritionNormalizer.validateBeforeSave(
                FoodAnalysis(listOf(normalized.copy(calories = normalized.calories + 1.0))),
            )
        }
    }

    @Test
    fun `required AI validation marker cannot be saved without normalized basis`() {
        val unnormalized = sourceItem(250.0, "ml", 355.0, "ml").copy(
            requiresServingValidation = true,
        )

        assertThrows(AiValidationException::class.java) {
            ServingNutritionNormalizer.validateBeforeSave(FoodAnalysis(listOf(unnormalized)))
        }
    }

    @Test
    fun `trusted direct and manual-style values keep their explicit path`() {
        val direct = AnalyzedFoodItem(
            name = "Direct catalog item",
            quantity = 100.0,
            unit = "g",
            gramsEquivalent = 100.0,
            calories = 200.0,
            proteinGrams = 10.0,
            carbohydrateGrams = 20.0,
            fatGrams = 8.0,
            sourceName = "Open Food Facts",
            isEstimate = false,
        )

        val validated = ServingNutritionNormalizer.validateBeforeSave(FoodAnalysis(listOf(direct)))
        assertEquals(direct, validated.items.single())
        assertFalse(direct.requiresServingValidation)
    }

    @Test
    fun `arbitrary target API supports exact per 100 barcode source values`() {
        val per100Source = sourceItem(
            loggedQuantity = 100.0,
            loggedUnit = "g",
            sourceQuantity = 100.0,
            sourceUnit = "g",
            calories = 240.0,
            protein = 12.0,
            carbs = 30.0,
            fat = 8.0,
        )

        val portion = ServingNutritionNormalizer.normalizeSourceServingTo(
            sourceServingItem = per100Source,
            loggedQuantity = 37.5,
            loggedUnit = "g",
            loggedGramsEquivalent = 37.5,
        )

        assertEquals(90.0, portion.calories, 1e-12)
        assertEquals(4.5, portion.proteinGrams, 1e-12)
        assertEquals(37.5, portion.gramsEquivalent!!, 0.0)
        ServingNutritionNormalizer.validateBeforeSave(FoodAnalysis(listOf(portion)))
    }

    @Test
    fun `approved portion edit reuses validated per 100 basis`() {
        val original = normalize(sourceItem(250.0, "ml", 355.0, "ml"), 250.0, "ml")
        val changed = ServingNutritionNormalizer.rescaleValidatedItemTo(
            item = original,
            loggedQuantity = 500.0,
            loggedUnit = "ml",
        )

        assertEquals(original.calories * 2.0, changed.calories, 1e-12)
        assertEquals(500.0, changed.servingValidation!!.loggedBaseAmount, 0.0)
        ServingNutritionNormalizer.validateBeforeSave(FoodAnalysis(listOf(changed)))
        assertThrows(AiValidationException::class.java) {
            ServingNutritionNormalizer.rescaleValidatedItemTo(
                item = original,
                loggedQuantity = 500.0,
                loggedUnit = "g",
            )
        }
    }

    @Test
    fun `piece portion edit reuses only the already known grams per piece`() {
        val original = normalize(
            raw = sourceItem(1.0, "piece", 100.0, "g").copy(gramsEquivalent = 182.0),
            requestedQuantity = 1.0,
            requestedUnit = "piece",
        )
        val changed = ServingNutritionNormalizer.rescaleValidatedItemTo(
            item = original,
            loggedQuantity = 2.0,
            loggedUnit = "pieces",
        )

        assertEquals(364.0, changed.gramsEquivalent!!, 0.0)
        assertEquals(original.calories * 2.0, changed.calories, 1e-12)
        assertEquals(364.0, changed.servingValidation!!.loggedBaseAmount, 0.0)
        ServingNutritionNormalizer.validateBeforeSave(FoodAnalysis(listOf(changed)))
    }

    @Test
    fun `result count must match parsed item count`() {
        val intent = ParsedFoodIntent(
            originalText = "two drinks",
            items = listOf(
                ParsedFoodItem("Drink one", quantity = 250.0, unit = "ml"),
                ParsedFoodItem("Drink two", quantity = 250.0, unit = "ml"),
            ),
        )
        assertThrows(AiValidationException::class.java) {
            ServingNutritionNormalizer.normalize(
                intent,
                FoodAnalysis(listOf(sourceItem(250.0, "ml", 355.0, "ml"))),
            )
        }
    }

    @Test
    fun `German spoon aliases survive the complete production normalization path`() {
        val cases = listOf(
            Triple("Löffel", 22.5, 30.0),
            Triple("Esslöffel", 22.5, 30.0),
            Triple("EL", 22.5, 30.0),
            Triple("Teelöffel", 7.5, 10.0),
            Triple("TL", 7.5, 10.0),
        )

        cases.forEach { (unit, expectedMilliliters, expectedGrams) ->
            val text = "1,5 $unit Himbeer Marmelade"
            val parsed = requireNotNull(LocalFoodIntentParser.parseOrNull(text))
            val reconciledIntent = UserQuantityResolver.reconcileIntent(parsed, "DE")
            assertEquals(expectedMilliliters, reconciledIntent.items.single().quantity!!, 0.0)
            assertEquals("ml", reconciledIntent.items.single().unit)

            val providerResult = FoodAnalysis(
                items = listOf(
                    sourceItem(1.0, "serving", 100.0, "g")
                        .copy(name = "Himbeer Marmelade"),
                ),
            )
            val reconciledAnalysis = UserQuantityResolver.reconcileAnalysis(
                reconciledIntent,
                providerResult,
            )
            val normalized = ServingNutritionNormalizer.normalize(
                reconciledIntent,
                reconciledAnalysis,
            ).items.single()

            assertEquals(expectedGrams, normalized.gramsEquivalent!!, 1e-12)
            assertEquals(expectedGrams, normalized.calories, 1e-12)
            assertTrue(normalized.isEstimate)
            ServingNutritionNormalizer.validateBeforeSave(FoodAnalysis(listOf(normalized)))
        }
    }
    @Test
    fun `German locale prompt prioritizes official German product source and amount semantics`() {
        val prompt = AiPrompts.researchNutrition(
            intent = intent(250.0, "ml"),
            json = Json,
            localeCountry = "DE",
        )

        assertTrue(prompt.contains("official German manufacturer"))
        assertTrue(prompt.contains("sourceServingQuantity"))
        assertTrue(prompt.contains("Do NOT return quantity=355"))
        assertTrue(prompt.contains("per 100 g/ml"))
        assertTrue(prompt.contains("COUNT-VS-MASS CONVERSIONS MUST INCLUDE A TOTAL GRAM EQUIVALENT"))
        assertTrue(prompt.contains("gramsEquivalent=364"))
        assertTrue(prompt.contains("1 EL/Essloeffel/tbsp/tablespoon = 15 ml"))
        assertTrue(prompt.contains("unqualified German Löffel/Loeffel means EL"))
        assertTrue(prompt.contains("1.5 EL jam may use gramsEquivalent=30"))
    }

    private fun normalize(
        raw: AnalyzedFoodItem,
        requestedQuantity: Double,
        requestedUnit: String,
        requestedName: String = "Test food",
    ): AnalyzedFoodItem = ServingNutritionNormalizer.normalize(
        intent = intent(requestedQuantity, requestedUnit, requestedName),
        unnormalized = FoodAnalysis(items = listOf(raw)),
    ).items.single()

    private fun intent(quantity: Double, unit: String, name: String = "Test food") = ParsedFoodIntent(
        originalText = "$quantity $unit drink",
        language = "de",
        items = listOf(
            ParsedFoodItem(
                name = name,
                quantity = quantity,
                unit = unit,
                gramsEquivalent = quantity.takeIf { unit == "g" },
            ),
        ),
    )

    private fun sourceItem(
        loggedQuantity: Double,
        loggedUnit: String,
        sourceQuantity: Double,
        sourceUnit: String,
        calories: Double = 100.0,
        protein: Double = 10.0,
        carbs: Double = 10.0,
        fat: Double = 2.0,
    ) = AnalyzedFoodItem(
        name = "Test food",
        quantity = loggedQuantity,
        unit = loggedUnit,
        gramsEquivalent = loggedQuantity.takeIf { loggedUnit == "g" },
        calories = calories,
        proteinGrams = protein,
        carbohydrateGrams = carbs,
        fatGrams = fat,
        sourceName = "Official manufacturer",
        sourceUrl = "https://manufacturer.example/de/product",
        sourceServingQuantity = sourceQuantity,
        sourceServingUnit = sourceUnit,
        sourceCountry = "DE",
        isEstimate = false,
    )
}

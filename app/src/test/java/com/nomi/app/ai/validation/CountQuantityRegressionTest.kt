package com.nomi.app.ai.validation

import com.nomi.app.ai.model.*
import com.nomi.app.ai.parsing.LocalFoodIntentParser
import com.nomi.app.domain.usecase.PortionEditApplier
import com.nomi.app.ui.format.QuantityDisplayFormatter
import com.nomi.app.ui.format.QuantityDisplayRequest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CountQuantityRegressionTest {
    private fun intent(text: String): ParsedFoodIntent {
        val parsed = requireNotNull(LocalFoodIntentParser.parseOrNull(text)) { text }
        return AiResponseValidator.validate(UserQuantityResolver.reconcileParsedIntent(text, parsed))
    }

    private fun source(quantity: Double = 1.0, unit: String = "piece") = AnalyzedFoodItem(
        name = "Oreo", quantity = quantity, unit = unit,
        calories = 474.0, proteinGrams = 5.0, carbohydrateGrams = 68.0, fatGrams = 19.0,
        nutritionBasis = ResearchNutritionBasis.PER_100_G,
        sourceServingQuantity = 100.0, sourceServingUnit = "g",
        sourceName = "Manufacturer", sourceUrl = "https://manufacturer.example/oreo",
        sourceUnit = "piece", sourceUnitWeightGrams = 11.3, isEstimate = false,
    )

    private fun normalize(text: String, data: AnalyzedFoodItem): AnalyzedFoodItem {
        val requested = intent(text)
        return ServingNutritionNormalizer.validateBeforeSave(ServingNutritionNormalizer.normalize(
            requested, UserQuantityResolver.reconcileAnalysis(requested, FoodAnalysis(listOf(data))),
        )).items.single()
    }

    @Test fun `all requested count and household examples parse without grams`() {
        val cases = listOf(
            Triple("1 Oreo", 1.0, "piece"), Triple("2 Oreos", 2.0, "piece"),
            Triple("2 Kinder Riegel", 2.0, "piece"), Triple("3 Eier", 3.0, "piece"),
            Triple("1 egg", 1.0, "piece"), Triple("3 eggs", 3.0, "piece"),
            Triple("1 Big Mac", 1.0, "piece"), Triple("half a pizza", 0.5, "piece"),
            Triple("1.5 bananas", 1.5, "piece"), Triple("1,5 bananas", 1.5, "piece"),
            Triple("6 chicken nuggets", 6.0, "piece"), Triple("1 pack Oreos", 1.0, "pack"),
            Triple("2 packages Oreos", 2.0, "pack"), Triple("one serving lasagna", 1.0, "serving"),
            Triple("2 servings lasagna", 2.0, "serving"), Triple("2 items Oreo", 2.0, "piece"),
            Triple("2 bars chocolate", 2.0, "bar"), Triple("1 bar chocolate", 1.0, "bar"),
            Triple("2 slices bread", 2.0, "slice"), Triple("1 slice bread", 1.0, "slice"),
            Triple("2 bottles Coke", 2.0, "bottle"), Triple("1 bottle Coke", 1.0, "bottle"),
            Triple("2 cans Coke", 2.0, "can"), Triple("1 can Coke", 1.0, "can"),
            Triple("2 cups milk", 2.0, "cup"), Triple("1 cup milk", 1.0, "cup"),
            Triple("1 tablespoon olive oil", 1.0, "tbsp"), Triple("2 teaspoons olive oil", 2.0, "tsp"),
        )
        cases.forEach { (text, quantity, unit) ->
            val item = intent(text).items.single()
            assertEquals(text, quantity, item.userQuantity!!, 1e-10)
            assertEquals(text, unit, item.userUnit)
            assertEquals(text, unit, item.normalizedUnit)
            assertNull(text, item.resolvedWeightGrams)
        }
    }

    @Test fun `per-piece manufacturer data scales all macros while count survives save validation`() {
        val item = normalize("2 Oreos", source())
        assertEquals(2.0, item.quantity, 0.0)
        assertEquals("piece", item.unit)
        assertEquals(22.6, item.resolvedWeightGrams!!, 1e-10)
        assertEquals(474.0 * 0.226, item.calories, 1e-10)
        assertEquals(5.0 * 0.226, item.proteinGrams, 1e-10)
        assertEquals(68.0 * 0.226, item.carbohydrateGrams, 1e-10)
        assertEquals(19.0 * 0.226, item.fatGrams, 1e-10)
        assertFalse(item.isEstimated)
        assertEquals(item.sourceUrl, item.quantityResolution!!.resolutionSource)
    }

    @Test fun `one Oreo displays the user count and not its internal mass`() {
        val item = normalize("1 Oreo", source())
        assertEquals(53.562, item.calories, 1e-10)
        val display = QuantityDisplayFormatter.format(
            QuantityDisplayRequest(item.quantity, item.unit, item.gramsEquivalent), java.util.Locale.ENGLISH,
        )
        assertEquals("1 piece", display.primary)
        assertFalse(display.withContext.contains("g"))
    }

    @Test fun `package weight resolves only a matching package unit`() {
        val pack = normalize("1 pack Oreos", source(unit = "g", quantity = 100.0).copy(
            sourceUnit = "package", sourceUnitWeightGrams = 154.0,
        ))
        assertEquals(1.0, pack.quantity, 0.0)
        assertEquals("pack", pack.unit)
        assertEquals(154.0, pack.gramsEquivalent!!, 0.0)
        assertThrows(NutritionResearchException::class.java) {
            normalize("1 pack Oreos", source())
        }
    }

    @Test fun `source package and table amount never replace user quantity or supply a fake weight`() {
        val request = intent("1 Oreo")
        val reconciled = UserQuantityResolver.reconcileAnalysis(request, FoodAnalysis(listOf(source(154.0, "g").copy(
            gramsEquivalent = 154.0, sourceUnitWeightGrams = null,
            sourcePackageQuantity = 154.0, sourcePackageUnit = "g",
        )))).items.single()
        assertEquals(1.0, reconciled.quantity, 0.0)
        assertEquals("piece", reconciled.unit)
        assertNull(reconciled.resolvedWeightGrams)
    }

    @Test fun `sized cans use total ml independently from mass or source metadata`() {
        listOf("one 330 ml can Coke" to 330.0, "2 x 250 ml Red Bull" to 500.0).forEach { (text, ml) ->
            val item = normalize(text, source(999.0, "ml").copy(
                nutritionBasis = ResearchNutritionBasis.PER_100_ML,
                sourceServingUnit = "ml", calories = 42.0, proteinGrams = 0.0,
                carbohydrateGrams = 10.5, fatGrams = 0.0,
                sourceUnit = "can", sourceUnitWeightGrams = null, sourceUnitVolumeMl = 999.0,
            ))
            assertEquals("can", item.unit)
            assertEquals(ml, item.resolvedVolumeMl!!, 0.0)
            assertNull(item.resolvedWeightGrams)
            assertEquals(ml * 0.42, item.calories, 1e-10)
        }
    }

    @Test fun `manufacturer volume scales bottles cans and cups`() {
        listOf("bottle", "can", "cup").forEach { unit ->
            val item = normalize("2 $unit Coke", source().copy(
                nutritionBasis = ResearchNutritionBasis.PER_100_ML,
                sourceServingUnit = "ml", sourceUnit = unit,
                sourceUnitWeightGrams = null, sourceUnitVolumeMl = 250.0,
            ))
            assertEquals(500.0, item.resolvedVolumeMl!!, 0.0)
            assertNull(item.resolvedWeightGrams)
        }
    }

    @Test fun `spoon stays tablespoon and can use product density`() {
        val item = normalize("1 tablespoon olive oil", source().copy(
            sourceUnit = "tbsp", sourceUnitWeightGrams = 13.5,
        ))
        assertEquals(1.0, item.userQuantity, 0.0)
        assertEquals("tbsp", item.unit)
        assertEquals(15.0, item.resolvedVolumeMl!!, 0.0)
        assertEquals(13.5, item.resolvedWeightGrams!!, 0.0)
    }

    @Test fun `matching source count nutrition needs no gram conversion`() {
        val item = normalize("half a pizza", source().copy(
            nutritionBasis = ResearchNutritionBasis.SOURCE_SERVING,
            sourceServingQuantity = 1.0, sourceServingUnit = "piece",
            sourceUnitWeightGrams = null,
        ))
        assertEquals(237.0, item.calories, 0.0)
        assertNull(item.resolvedWeightGrams)
    }

    @Test fun `different count kinds cannot silently be treated as the same serving`() {
        assertThrows(NutritionResearchException::class.java) {
            normalize("1 pack Oreos", source().copy(
                nutritionBasis = ResearchNutritionBasis.SOURCE_SERVING,
                sourceServingQuantity = 1.0, sourceServingUnit = "piece", sourceUnitWeightGrams = null,
            ))
        }
    }

    @Test fun `estimated conversion stays marked estimated`() {
        val item = normalize("1 egg", source().copy(isEstimate = true, sourceUnitWeightGrams = 50.0))
        assertTrue(item.isEstimated)
        assertTrue(item.quantityResolution!!.isEstimated)
        assertEquals("estimated serving", item.quantityResolution!!.resolutionSource)
    }

    @Test fun `metric regression preserves normal quantities and computes the same totals`() {
        listOf("100g chicken" to 100.0, "80g pasta" to 80.0, "100 g Oreo" to 100.0,
            "0.25 kg chicken" to 250.0).forEach { (text, grams) ->
            val item = normalize(text, source())
            assertEquals(grams, item.quantity, 0.0)
            assertEquals("g", item.unit)
            assertEquals(grams * 4.74, item.calories, 1e-9)
        }
        listOf("250ml milk" to 250.0, "250 ml Cola" to 250.0, "0.5 l milk" to 500.0).forEach { (text, ml) ->
            val item = normalize(text, source().copy(nutritionBasis = ResearchNutritionBasis.PER_100_ML))
            assertEquals(ml, item.quantity, 0.0)
            assertEquals("ml", item.unit)
            assertEquals(ml * 4.74, item.calories, 1e-9)
        }
    }

    @Test fun `provider parsed weights cannot overwrite explicit counts`() {
        val request = UserQuantityResolver.reconcileParsedIntent("2 Oreos", ParsedFoodIntent(
            "2 Oreos", items = listOf(ParsedFoodItem("Oreo", quantity = 100.0, unit = "g", gramsEquivalent = 100.0)),
        ))
        assertEquals(2.0, request.items.single().quantity!!, 0.0)
        assertEquals("piece", request.items.single().unit)
        assertNull(request.items.single().gramsEquivalent)
    }

    @Test fun `meal quantities remain attached to the correct food`() {
        val request = UserQuantityResolver.reconcileParsedIntent("2 Oreos and 250ml milk", ParsedFoodIntent(
            "ignored", items = listOf(ParsedFoodItem("milk"), ParsedFoodItem("Oreo")),
        ))
        assertEquals(250.0, request.items[0].quantity!!, 0.0)
        assertEquals(2.0, request.items[1].quantity!!, 0.0)
    }

    @Test fun `serialized analysis retains optional resolution and source provenance`() {
        val item = normalize("1 Oreo", source())
        val roundTrip = Json.decodeFromString<AnalyzedFoodItem>(Json.encodeToString(item))
        assertEquals(item, roundTrip)
        assertEquals(1.0, roundTrip.userQuantity, 0.0)
        assertEquals(11.3, roundTrip.resolvedWeightGrams!!, 0.0)
    }

    @Test fun `count edits scale the bridge and retain valid metadata`() {
        val item = normalize("1 Oreo", source())
        val changed = ServingNutritionNormalizer.rescaleValidatedItemTo(item, 3.0, "piece")
        assertEquals(3.0, changed.userQuantity, 0.0)
        assertEquals(33.9, changed.resolvedWeightGrams!!, 1e-10)
        assertEquals(item.calories * 3, changed.calories, 1e-10)
    }

    @Test fun `invalid unit conversion is rejected`() {
        assertThrows(AiValidationException::class.java) { normalize("1 Oreo", source().copy(sourceUnitWeightGrams = -1.0)) }
        assertThrows(AiValidationException::class.java) { normalize("1 Oreo", source().copy(sourceUnitWeightGrams = Double.NaN)) }
    }
}

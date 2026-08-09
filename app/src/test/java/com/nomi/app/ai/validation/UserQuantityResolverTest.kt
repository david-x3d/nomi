package com.nomi.app.ai.validation

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import com.nomi.app.ai.model.QuantityOrigin
import com.nomi.app.ai.model.QuantityResolutionMetadata
import com.nomi.app.ai.model.QuantitySemantic
import com.nomi.app.ai.prompt.AiPrompts
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UserQuantityResolverTest {
    @Test
    fun `unqualified German spoon defaults to tablespoon volume`() {
        val item = resolve("1,5 Löffel Himbeer Marmelade", name = "Himbeer Marmelade")

        assertEquals(22.5, item.quantity!!, 0.0)
        assertEquals("ml", item.unit)
        assertEquals(1.5, item.quantityResolution!!.enteredQuantity!!, 0.0)
        assertEquals("Löffel", item.quantityResolution!!.enteredUnit)
        assertTrue(item.quantityResolution!!.isApproximate)
    }

    @Test
    fun `German tablespoon and teaspoon aliases use metric volume`() {
        val tablespoon = resolve("1,5 EL Marmelade", name = "Marmelade")
        val teaspoon = resolve("1,5 Teelöffel Marmelade", name = "Marmelade")

        assertEquals(22.5, tablespoon.quantity!!, 0.0)
        assertEquals("ml", tablespoon.unit)
        assertEquals(7.5, teaspoon.quantity!!, 0.0)
        assertEquals("ml", teaspoon.unit)
    }
    @Test
    fun `55 percent of explicitly stated 320 g package is exactly 176 g`() {
        val item = resolve("55% of a 320g package of Iglo Schlemmer-Filet")

        assertEquals(176.0, item.quantity!!, 0.0)
        assertEquals("g", item.unit)
        assertEquals(176.0, item.gramsEquivalent!!, 0.0)
        assertEquals(QuantityOrigin.USER_EXPLICIT, item.quantityResolution!!.origin)
        assertEquals(QuantitySemantic.PACKAGE_PERCENT, item.quantityResolution!!.semantic)
        assertEquals(55.0, item.quantityResolution!!.percentage!!, 0.0)
        assertEquals(320.0, item.quantityResolution!!.packageQuantity!!, 0.0)
    }

    @Test
    fun `German Prozent package form preserves explicit package`() {
        val item = resolve("55 Prozent von einer 320 g Packung Iglo Schlemmer-Filet")

        assertEquals(176.0, item.quantity!!, 0.0)
        assertEquals("g", item.unit)
    }
    @Test
    fun `package percentage above 100 is rejected`() {
        val error = assertThrows(AiValidationException::class.java) {
            resolve("101% of a 320g package of fish")
        }

        assertTrue(error.message!!.contains("cannot exceed 100%"))
    }


    @Test
    fun `half package displays through semantic half while calculating 100 g`() {
        val item = resolve("half of a 200g bag of crisps")
        val metadata = item.quantityResolution!!

        assertEquals(100.0, item.quantity!!, 0.0)
        assertEquals(1, metadata.fractionNumerator)
        assertEquals(2, metadata.fractionDenominator)
        assertFalse(metadata.isApproximate)
    }

    @Test
    fun `German half of package calculates canonical grams`() {
        val item = resolve("die Hälfte einer 200-g-Packung Chips")

        assertEquals(100.0, item.quantity!!, 0.0)
        assertEquals(QuantitySemantic.PACKAGE_FRACTION, item.quantityResolution!!.semantic)
    }

    @Test
    fun `two thirds keeps precise internal amount and semantic fraction`() {
        val item = resolve("two thirds of a 200g bag of crisps")
        val metadata = item.quantityResolution!!

        assertEquals(200.0 * 2.0 / 3.0, item.quantity!!, 1e-12)
        assertEquals(2, metadata.fractionNumerator)
        assertEquals(3, metadata.fractionDenominator)
        assertTrue(metadata.isApproximate)
        assertFalse(item.unit == "package")
    }

    @Test
    fun `German two thirds syntax is deterministic`() {
        val item = resolve("zwei Drittel von einer 200g Packung Chips")

        assertEquals(200.0 * 2.0 / 3.0, item.quantity!!, 1e-12)
        assertEquals("g", item.unit)
    }

    @Test
    fun `direct explicit Red Bull sizes always beat German default`() {
        listOf(355.0, 473.0).forEach { size ->
            val item = resolve("$size ml Red Bull Juneberry", locale = "DE", name = "Red Bull Juneberry")
            assertEquals(size, item.quantity!!, 0.0)
            assertEquals("ml", item.unit)
            assertEquals(QuantityOrigin.USER_EXPLICIT, item.quantityResolution!!.origin)
        }

        val germanCan = resolve(
            "eine 355-ml-Dose Red Bull",
            locale = "DE",
            name = "Red Bull",
        )
        assertEquals(355.0, germanCan.quantity!!, 0.0)
    }

    @Test
    fun `explicit half of 250 ml Red Bull can is 125 ml`() {
        val item = resolve(
            "eine halbe 250-ml-Dose Red Bull",
            locale = "DE",
            name = "Red Bull",
        )

        assertEquals(125.0, item.quantity!!, 0.0)
        assertEquals("ml", item.unit)
        assertEquals(250.0, item.quantityResolution!!.packageQuantity!!, 0.0)
    }

    @Test
    fun `German unspecified Red Bull cans and Editions default to 250 ml`() {
        listOf(
            "1 Dose Red Bull",
            "eine Dose Red Bull",
            "Red Bull Juneberry",
            "1 Red Bull",
        ).forEach { text ->
            val item = resolve(text, locale = "DE", name = "Red Bull Juneberry")
            assertEquals("Failed for $text", 250.0, item.quantity!!, 0.0)
            assertEquals("ml", item.unit)
            assertEquals(QuantityOrigin.GERMAN_LOCAL_DEFAULT, item.quantityResolution!!.origin)
            assertEquals(QuantitySemantic.LOCAL_CAN_DEFAULT, item.quantityResolution!!.semantic)
        }
    }

    @Test
    fun `Red Bull default is not applied outside Germany`() {
        val item = resolve("1 Dose Red Bull", locale = "US", name = "Red Bull")

        assertEquals(999.0, item.quantity!!, 0.0)
        assertEquals("ml", item.unit)
        assertNull(item.quantityResolution)
    }

    @Test
    fun `provider package conflict is informational and cannot change consumed amount`() {
        val parsed = reconciledIntent("55% of a 320g package of Iglo Schlemmer-Filet")
        val provider = FoodAnalysis(
            listOf(
                sourceItem(
                    loggedQuantity = 380.0,
                    loggedUnit = "g",
                    sourceServingQuantity = 100.0,
                    sourceServingUnit = "g",
                    sourcePackageQuantity = 380.0,
                    sourcePackageUnit = "g",
                    calories = 200.0,
                ),
            ),
        )

        val reconciled = UserQuantityResolver.reconcileAnalysis(parsed, provider)
        assertEquals(176.0, reconciled.items.single().quantity, 0.0)
        assertTrue(reconciled.items.single().quantityResolution!!.sourcePackageConflict)
        assertEquals(380.0, reconciled.items.single().quantityResolution!!.sourcePackageQuantity!!, 0.0)

        val normalized = ServingNutritionNormalizer.normalize(parsed, reconciled).items.single()
        assertEquals(352.0, normalized.calories, 1e-12)
        assertEquals(176.0, normalized.quantity, 0.0)
        assertEquals(320.0, normalized.quantityResolution!!.packageQuantity!!, 0.0)
    }

    @Test
    fun `equivalent source package unit does not report a conflict`() {
        val parsed = reconciledIntent("half of a 320g package of fish")
        val provider = FoodAnalysis(
            listOf(
                sourceItem(
                    loggedQuantity = 320.0,
                    loggedUnit = "g",
                    sourcePackageQuantity = 0.32,
                    sourcePackageUnit = "kg",
                ),
            ),
        )

        val item = UserQuantityResolver.reconcileAnalysis(parsed, provider).items.single()
        assertFalse(item.quantityResolution!!.sourcePackageConflict)
    }

    @Test
    fun `German 250 ml default scales a 355 ml source serving instead of copying it`() {
        val parsed = reconciledIntent(
            text = "Red Bull Juneberry",
            locale = "DE",
            name = "Red Bull Juneberry",
        )
        val provider = FoodAnalysis(
            listOf(
                sourceItem(
                    loggedQuantity = 355.0,
                    loggedUnit = "ml",
                    sourceServingQuantity = 355.0,
                    sourceServingUnit = "ml",
                    sourcePackageQuantity = 355.0,
                    sourcePackageUnit = "ml",
                    calories = 160.0,
                ),
            ),
        )

        val reconciled = UserQuantityResolver.reconcileAnalysis(parsed, provider)
        val normalized = ServingNutritionNormalizer.normalize(parsed, reconciled).items.single()

        assertEquals(250.0, normalized.quantity, 0.0)
        assertEquals(160.0 * 250.0 / 355.0, normalized.calories, 1e-12)
        assertFalse(normalized.calories == 160.0)
    }

    @Test
    fun `provider forged quantity metadata is cleared when user gave no resolvable amount`() {
        val forged = QuantityResolutionMetadata(
            origin = QuantityOrigin.USER_EXPLICIT,
            semantic = QuantitySemantic.DIRECT_AMOUNT,
            canonicalQuantity = 999.0,
            canonicalUnit = "g",
        )
        val parsed = ParsedFoodIntent(
            originalText = "an apple",
            items = listOf(
                ParsedFoodItem(
                    name = "apple",
                    quantity = 1.0,
                    unit = "piece",
                    quantityResolution = forged,
                ),
            ),
        )

        val reconciled = UserQuantityResolver.reconcileParsedIntent("an apple", parsed, "DE")
        assertNull(reconciled.items.single().quantityResolution)
        assertEquals(1.0, reconciled.items.single().quantity!!, 0.0)
    }

    @Test
    fun `multiple explicit quantities attach to their nearest products`() {
        val parsed = ParsedFoodIntent(
            originalText = "355 ml Red Bull and half of a 200g bag of chips",
            items = listOf(
                ParsedFoodItem("Red Bull", quantity = 1.0, unit = "can"),
                ParsedFoodItem("chips", quantity = 0.5, unit = "bag"),
            ),
        )

        val reconciled = UserQuantityResolver.reconcileIntent(parsed, "DE")
        assertEquals(355.0, reconciled.items[0].quantity!!, 0.0)
        assertEquals("ml", reconciled.items[0].unit)
        assertEquals(100.0, reconciled.items[1].quantity!!, 0.0)
        assertEquals("g", reconciled.items[1].unit)
    }

    @Test
    fun `German prompt encodes source order and non-overridable user quantity`() {
        val prompt = AiPrompts.researchNutrition(
            intent = reconciledIntent("55% of a 320g package of fish"),
            json = Json,
            localeCountry = "DE",
        )

        assertTrue(prompt.contains("QUANTITY PRECEDENCE IS ABSOLUTE"))
        assertTrue(prompt.contains("official German manufacturer"))
        assertTrue(prompt.contains("major German retailer"))
        assertTrue(prompt.contains("sourcePackageQuantity"))
        assertTrue(prompt.contains("Red Bull"))
        assertTrue(prompt.contains("PRODUCT IDENTITY"))
        assertTrue(prompt.contains("Ferrero chocolate-covered wafer bar"))
        assertTrue(prompt.contains("any accessible website"))
        assertTrue(prompt.indexOf("official German manufacturer") < prompt.indexOf("international/US"))
    }

    private fun resolve(
        text: String,
        locale: String = "DE",
        name: String = "Iglo Schlemmer-Filet",
    ): ParsedFoodItem = reconciledIntent(text, locale, name).items.single()

    private fun reconciledIntent(
        text: String,
        locale: String = "DE",
        name: String = "Iglo Schlemmer-Filet",
    ): ParsedFoodIntent = UserQuantityResolver.reconcileParsedIntent(
        userText = text,
        parsed = ParsedFoodIntent(
            originalText = "provider changed text",
            language = "de",
            items = listOf(
                ParsedFoodItem(
                    name = name,
                    quantity = 999.0,
                    unit = "ml",
                    assumptions = listOf("provider inference"),
                ),
            ),
        ),
        localeCountry = locale,
    )

    private fun sourceItem(
        loggedQuantity: Double,
        loggedUnit: String,
        sourceServingQuantity: Double = 100.0,
        sourceServingUnit: String = "g",
        sourcePackageQuantity: Double? = null,
        sourcePackageUnit: String? = null,
        calories: Double = 100.0,
    ): AnalyzedFoodItem = AnalyzedFoodItem(
        name = "Test product",
        quantity = loggedQuantity,
        unit = loggedUnit,
        gramsEquivalent = loggedQuantity.takeIf { loggedUnit == "g" },
        calories = calories,
        proteinGrams = 5.0,
        carbohydrateGrams = 10.0,
        fatGrams = 2.0,
        sourceName = "Manufacturer",
        sourceUrl = "https://example.test/product",
        sourceServingQuantity = sourceServingQuantity,
        sourceServingUnit = sourceServingUnit,
        sourcePackageQuantity = sourcePackageQuantity,
        sourcePackageUnit = sourcePackageUnit,
        sourceCountry = "DE",
        isEstimate = false,
    )
}

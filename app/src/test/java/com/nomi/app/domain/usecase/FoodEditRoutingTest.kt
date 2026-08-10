package com.nomi.app.domain.usecase

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.FoodEditClassification
import com.nomi.app.ai.model.FoodEditType
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import com.nomi.app.ai.model.PortionContext
import com.nomi.app.ai.validation.ServingNutritionNormalizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cost contract of the edit pipeline.
 *
 * "Does not invoke Sonar" is asserted by counting classifier calls rather than by inspecting a
 * parser's return value: a portion edit that reaches any model at all has already failed the
 * point of this routing, and research sits strictly behind the classifier.
 */
class FoodEditRoutingTest {

    private class CountingClassifier(
        private val response: (String) -> FoodEditClassification,
    ) {
        var calls = 0
            private set

        fun asRouter() = FoodEditRouter { _: PortionContext, correction: String ->
            calls += 1
            response(correction)
        }
    }

    /** Fails the test if the model is consulted at all; portion phrasings must never reach it. */
    private fun neverCalledRouter() = CountingClassifier {
        throw AssertionError("A model was consulted for an edit that is pure arithmetic")
    }

    private fun researchClassifier(reason: String) = CountingClassifier {
        FoodEditClassification(
            type = FoodEditType.CONTENT_CHANGE,
            confidence = 0.95,
            reason = reason,
        )
    }

    // 1. "half" does not invoke Sonar.
    @Test
    fun `half is scaled locally without consulting any model`() = runBlocking {
        val classifier = neverCalledRouter()

        val decision = classifier.asRouter().route(researchedItem(), "half")

        assertTrue(decision is FoodEditRouter.Decision.Scale)
        val scale = decision as FoodEditRouter.Decision.Scale
        assertEquals(NutritionRoute.Decision.LOCAL, scale.decidedBy)
        assertEquals(0, classifier.calls)
        assertEquals(0.5, scale.result.factor, 1e-12)
        assertEquals(200.0, scale.result.item.quantity, 1e-9)
        assertEquals(400.0, scale.result.item.calories, 1e-9)
        assertEquals(20.0, scale.result.item.proteinGrams, 1e-9)
    }

    // 2. "2x" does not invoke Sonar.
    @Test
    fun `2x is scaled locally without consulting any model`() = runBlocking {
        val classifier = neverCalledRouter()

        val decision = classifier.asRouter().route(researchedItem(), "2x")

        val scale = decision as FoodEditRouter.Decision.Scale
        assertEquals(NutritionRoute.Decision.LOCAL, scale.decidedBy)
        assertEquals(0, classifier.calls)
        assertEquals(2.0, scale.result.factor, 1e-12)
        assertEquals(1_600.0, scale.result.item.calories, 1e-9)
    }

    @Test
    fun `the everyday portion phrasings all resolve on device`() = runBlocking {
        val cases = mapOf(
            "half" to 0.5,
            "½" to 0.5,
            "1/2" to 0.5,
            "50%" to 0.5,
            "2x" to 2.0,
            "double" to 2.0,
            "only ate 3 of the 6 pieces" to 0.5,
            "one third" to 1.0 / 3.0,
            "75% of it" to 0.75,
            "a quarter" to 0.25,
        )
        cases.forEach { (correction, expected) ->
            val classifier = neverCalledRouter()
            val decision = classifier.asRouter().route(researchedItem(), correction)
            val scale = decision as? FoodEditRouter.Decision.Scale
                ?: throw AssertionError("\"$correction\" should scale locally")
            assertEquals("\"$correction\"", expected, scale.result.factor, 1e-9)
            assertEquals("\"$correction\" consulted a model", 0, classifier.calls)
        }
    }

    // 3. "55% of a 320 g package" becomes exactly 176 g.
    @Test
    fun `55 percent of a 320g package is exactly 176g`() = runBlocking {
        val classifier = neverCalledRouter()
        val packageItem = researchedItem(quantity = 320.0, caloriesPer100 = 100.0)

        val decision = classifier.asRouter().route(packageItem, "55% of the package")

        val scale = decision as FoodEditRouter.Decision.Scale
        assertEquals(0, classifier.calls)
        assertEquals(176.0, scale.result.item.quantity, 1e-9)
        assertEquals(176.0, scale.result.item.gramsEquivalent!!, 1e-9)
        // Every nutrient follows the same exact share of the package.
        assertEquals(176.0, scale.result.item.calories, 1e-9)
    }

    @Test
    fun `an explicit replacement amount sets the quantity outright`() = runBlocking {
        val classifier = neverCalledRouter()

        val decision = classifier.asRouter()
            .route(researchedItem(quantity = 400.0), "200g instead of 400g")

        val scale = decision as FoodEditRouter.Decision.Scale
        assertEquals(0, classifier.calls)
        assertEquals(200.0, scale.result.item.quantity, 1e-9)
        assertEquals(0.5, scale.result.factor, 1e-12)
    }

    // 4. Changing chicken to tuna invokes research.
    @Test
    fun `changing the food itself routes to research`() = runBlocking {
        val classifier = researchClassifier("The food changed from chicken to tuna")

        val decision = classifier.asRouter()
            .route(researchedItem(name = "Chicken sandwich"), "actually it was chicken, not tuna")

        assertTrue(decision is FoodEditRouter.Decision.Research)
        assertEquals(1, classifier.calls)
        assertEquals(
            "The food changed from chicken to tuna",
            (decision as FoodEditRouter.Decision.Research).reason,
        )
    }

    // 5. Changing the restaurant invokes research.
    @Test
    fun `naming a different restaurant routes to research`() = runBlocking {
        val classifier = researchClassifier("The restaurant changed")

        val decision = classifier.asRouter()
            .route(researchedItem(name = "Fries"), "this was from Burger King")

        assertTrue(decision is FoodEditRouter.Decision.Research)
        assertEquals(1, classifier.calls)
    }

    @Test
    fun `content edits are never mistaken for portions by the local parser`() {
        val contentEdits = listOf(
            "actually it was chicken, not tuna",
            "remove the cheese",
            "it was the large McDonald's fries",
            "this was from Burger King",
            "add 20g mayonnaise",
            "different product",
            "wrong brand",
        )
        contentEdits.forEach { edit ->
            assertNull(
                "\"$edit\" must not parse as a portion change",
                PortionEditParser.parseOrNull(edit),
            )
        }
    }

    @Test
    fun `wording with no defensible factor is left to the classifier`() {
        // "A couple of bites" has no honest multiplier, so guessing one locally would be worse
        // than asking. It must fall through rather than be invented.
        assertNull(PortionEditParser.parseOrNull("couple bites"))
        assertNull(PortionEditParser.parseOrNull("a few bites"))
    }

    @Test
    fun `an unconfident portion classification is researched instead of scaled`() = runBlocking {
        val classifier = CountingClassifier {
            FoodEditClassification(
                type = FoodEditType.PORTION_ONLY,
                confidence = 0.4,
                reason = "Not sure whether the food changed",
                portion = com.nomi.app.ai.model.PortionEditInstruction.scale(0.5),
            )
        }

        val decision = classifier.asRouter().route(researchedItem(), "maybe about a half-ish?")

        assertTrue(
            "A low-confidence portion guess must not silently rescale a possibly-different food",
            decision is FoodEditRouter.Decision.Research,
        )
    }

    @Test
    fun `a confident classifier portion instruction is applied deterministically`() = runBlocking {
        val classifier = CountingClassifier {
            FoodEditClassification(
                type = FoodEditType.PORTION_ONLY,
                confidence = 0.98,
                reason = "User changed only the consumed quantity",
                portion = com.nomi.app.ai.model.PortionEditInstruction.scale(0.25),
            )
        }

        val decision = classifier.asRouter().route(researchedItem(), "just a small taste of it")

        val scale = decision as FoodEditRouter.Decision.Scale
        assertEquals(NutritionRoute.Decision.CLASSIFIER, scale.decidedBy)
        assertEquals(1, classifier.calls)
        assertEquals(200.0, scale.result.item.calories, 1e-9)
    }

    // 9. Scaling moves calories, macros and enabled micronutrients together.
    @Test
    fun `scaling moves every nutrient including micronutrients consistently`() = runBlocking {
        val classifier = neverCalledRouter()

        val decision = classifier.asRouter().route(researchedItem(), "half")

        val scaled = (decision as FoodEditRouter.Decision.Scale).result.item
        assertEquals(400.0, scaled.calories, 1e-9)
        assertEquals(20.0, scaled.proteinGrams, 1e-9)
        assertEquals(30.0, scaled.carbohydrateGrams, 1e-9)
        assertEquals(10.0, scaled.fatGrams, 1e-9)
        assertEquals(4.0, scaled.fiberGrams!!, 1e-9)
        assertEquals(6.0, scaled.sugarGrams!!, 1e-9)
        assertEquals(5.0, scaled.saturatedFatGrams!!, 1e-9)
        assertEquals(600.0, scaled.sodiumMilligrams!!, 1e-9)
    }

    // 10. Repeated edits do not drift.
    @Test
    fun `repeated portion edits do not drift away from the researched values`() = runBlocking {
        val router = neverCalledRouter().asRouter()
        val original = researchedItem()

        var item = original
        repeat(6) {
            item = (router.route(item, "half") as FoodEditRouter.Decision.Scale).result.item
            item = (router.route(item, "2x") as FoodEditRouter.Decision.Scale).result.item
        }

        // Each edit is recomputed from the validated per-100 basis, so twelve of them land
        // exactly where one would.
        assertEquals(original.quantity, item.quantity, 1e-9)
        assertEquals(original.calories, item.calories, 1e-9)
        assertEquals(original.proteinGrams, item.proteinGrams, 1e-9)
        assertEquals(original.sugarGrams!!, item.sugarGrams!!, 1e-9)
        assertEquals(original.sodiumMilligrams!!, item.sodiumMilligrams!!, 1e-9)
    }

    @Test
    fun `a thirds cycle also returns to the researched values`() = runBlocking {
        val router = neverCalledRouter().asRouter()
        val original = researchedItem()

        val third = (router.route(original, "one third") as FoodEditRouter.Decision.Scale).result.item
        val restored = (router.route(third, "3x") as FoodEditRouter.Decision.Scale).result.item

        assertEquals(original.calories, restored.calories, 1e-9)
        assertEquals(original.quantity, restored.quantity, 1e-9)
    }

    // 8. A scaled item keeps the citation of the product that was researched.
    @Test
    fun `scaling keeps the researched product's citation attached`() = runBlocking {
        val classifier = neverCalledRouter()
        val original = researchedItem()

        val scaled = (classifier.asRouter().route(original, "half") as FoodEditRouter.Decision.Scale)
            .result.item

        assertEquals(original.sourceUrl, scaled.sourceUrl)
        assertEquals(original.sourceName, scaled.sourceName)
        assertEquals(original.sourceProductName, scaled.sourceProductName)
        assertEquals(original.sourceDomain, scaled.sourceDomain)
        assertNotNull(scaled.sourceUrl)
    }

    @Test
    fun `a portion edit never changes what the food is`() = runBlocking {
        val classifier = neverCalledRouter()
        val original = researchedItem(name = "Chicken sandwich")

        val scaled = (classifier.asRouter().route(original, "half") as FoodEditRouter.Decision.Scale)
            .result.item

        assertEquals("Chicken sandwich", scaled.name)
        assertEquals(original.brand, scaled.brand)
    }

    /**
     * A research-normalized item, which is what every edit actually operates on. Built through
     * the real normalizer so the per-100 basis these tests rely on is the genuine one.
     */
    private fun researchedItem(
        name: String = "Test meal",
        quantity: Double = 400.0,
        caloriesPer100: Double = 200.0,
    ): AnalyzedFoodItem {
        val raw = AnalyzedFoodItem(
            name = name,
            brand = "Test brand",
            quantity = quantity,
            unit = "g",
            gramsEquivalent = quantity,
            calories = caloriesPer100,
            proteinGrams = 10.0,
            carbohydrateGrams = 15.0,
            fatGrams = 5.0,
            fiberGrams = 2.0,
            sugarGrams = 3.0,
            saturatedFatGrams = 2.5,
            sodiumMilligrams = 300.0,
            sourceName = "Official manufacturer",
            sourceUrl = "https://manufacturer.example/products/test-meal",
            sourceProductName = name,
            sourceDomain = "manufacturer.example",
            sourceServingQuantity = 100.0,
            sourceServingUnit = "g",
            sourceServingGramsEquivalent = 100.0,
            sourceCountry = "DE",
            isEstimate = false,
        )
        return ServingNutritionNormalizer.normalize(
            intent = ParsedFoodIntent(
                originalText = "$quantity g $name",
                items = listOf(
                    ParsedFoodItem(
                        name = name,
                        quantity = quantity,
                        unit = "g",
                        gramsEquivalent = quantity,
                    ),
                ),
            ),
            unnormalized = FoodAnalysis(items = listOf(raw)),
        ).items.single()
    }
}

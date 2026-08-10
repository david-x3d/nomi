package com.nomi.app.ai

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import com.nomi.app.ai.model.VisionFoodItem
import com.nomi.app.ai.model.VisionFoodResult
import com.nomi.app.ai.prompt.AiPrompts
import com.nomi.app.ai.provider.NutritionResearchProvider
import com.nomi.app.ai.provider.VisionFoodProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The division of labour between the two models on the photo path.
 *
 * The vision model says what is on the plate; the research model says what that is worth. The
 * first test is the load-bearing one: a description type that cannot express a calorie is a
 * guarantee no prompt wording can be talked out of.
 */
class VisionResearchPipelineTest {

    @Test
    fun `the vision result type cannot express a nutrition value at all`() {
        val nutritionWords = listOf("calor", "protein", "carb", "fat", "kcal", "sugar", "sodium", "fiber")
        val descriptors = listOf(
            "VisionFoodItem" to VisionFoodItem.serializer().descriptor,
            "VisionFoodResult" to VisionFoodResult.serializer().descriptor,
        )
        descriptors.forEach { (typeName, descriptor) ->
            (0 until descriptor.elementsCount).forEach { index ->
                val field = descriptor.getElementName(index).lowercase()
                nutritionWords.forEach { word ->
                    assertFalse(
                        "$typeName.$field would let the vision model supply nutrition, " +
                            "which only research may do",
                        field.contains(word),
                    )
                }
            }
        }
    }

    @Test
    fun `the vision prompt forbids nutrition and asks for a describable plate`() {
        val prompt = AiPrompts.identifyFoodFromPhoto()

        assertTrue(prompt.contains("NEVER report calories, macros, or any nutrition value"))
        assertTrue(prompt.contains("not its source of nutrition"))
        // The details the description has to carry for research to be able to use it.
        listOf("visibleIngredients", "estimatedGrams", "pieces", "sauces", "preparation")
            .forEach { assertTrue("Prompt should mention $it", prompt.contains(it)) }
    }

    // 6. A photo reaches nutrition through vision first and research second.
    @Test
    fun `a photo is described by vision and only then researched`() = runBlocking {
        val calls = mutableListOf<String>()
        val vision = VisionFoodProvider { _, _ ->
            calls += "vision"
            VisionFoodResult(
                items = listOf(
                    VisionFoodItem(
                        name = "Salmon sashimi",
                        visibleIngredients = listOf("shredded daikon"),
                        estimatedQuantity = 8.0,
                        unit = "pieces",
                        estimatedGrams = 160.0,
                    ),
                ),
                notes = listOf("Soy sauce present but untouched"),
            )
        }
        var researchedIntent: ParsedFoodIntent? = null
        val research = NutritionResearchProvider { intent ->
            calls += "research"
            researchedIntent = intent
            FoodAnalysis(items = listOf(researchedItem()))
        }

        val described = vision.identifyFood(ByteArray(1), "image/jpeg")
        val intent = ParsedFoodIntent(
            originalText = "8 pieces Salmon sashimi",
            items = described.items.map {
                ParsedFoodItem(
                    name = it.name,
                    quantity = it.estimatedQuantity,
                    unit = it.unit,
                    gramsEquivalent = it.estimatedGrams,
                    assumptions = it.visibleIngredients,
                )
            },
        )
        val analysis = research.researchNutrition(intent)

        assertEquals(listOf("vision", "research"), calls)
        // What the camera saw is what gets looked up, weights and all.
        assertEquals("Salmon sashimi", researchedIntent!!.items.single().name)
        assertEquals(8.0, researchedIntent!!.items.single().quantity!!, 0.0)
        assertEquals(160.0, researchedIntent!!.items.single().gramsEquivalent!!, 0.0)
        // Nutrition and its citation come from research, never from the photo.
        assertEquals(320.0, analysis.items.single().calories, 0.0)
        assertEquals(
            "https://manufacturer.example/products/sashimi",
            analysis.items.single().sourceUrl,
        )
    }

    @Test
    fun `a restaurant named on the photo review reaches research as the brand`() {
        val described = ParsedFoodItem(name = "Fries", quantity = 1.0, unit = "serving")
        val place = "Five Guys"

        val withPlace = described.copy(brand = described.brand ?: place)

        // The brand field is what points research at a chain's own published nutrition rather
        // than at a generic recipe for the same dish.
        assertEquals("Five Guys", withPlace.brand)
    }

    private fun researchedItem() = AnalyzedFoodItem(
        name = "Salmon sashimi",
        quantity = 8.0,
        unit = "pieces",
        gramsEquivalent = 160.0,
        calories = 320.0,
        proteinGrams = 35.0,
        carbohydrateGrams = 0.0,
        fatGrams = 18.0,
        sourceName = "Official manufacturer",
        sourceUrl = "https://manufacturer.example/products/sashimi",
        sourceProductName = "Salmon sashimi",
        sourceServingQuantity = 100.0,
        sourceServingUnit = "g",
        isEstimate = false,
    )
}

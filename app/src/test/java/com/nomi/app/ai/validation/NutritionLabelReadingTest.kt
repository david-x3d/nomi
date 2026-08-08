package com.nomi.app.ai.validation

import com.nomi.app.ai.model.NutritionLabelReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * A label reading claims to be transcription, not research, so nothing downstream will ever
 * question it: no second source, no estimate flag, no sanity pass. That makes this validation
 * the only place a misread column can still be caught.
 */
class NutritionLabelReadingTest {
    @Test
    fun `a plausible per-100g table is accepted`() {
        val reading = reading(calories = 350.0, protein = 10.0, carbs = 60.0, fat = 7.0)

        assertEquals(reading, AiResponseValidator.validate(reading))
    }

    @Test
    fun `rounding drift between macros and energy is tolerated`() {
        // 20 g carbohydrates imply 80 kcal against 100 printed - within what real labels do.
        val reading = reading(calories = 100.0, protein = 0.0, carbs = 20.0, fat = 0.0)

        assertEquals(reading, AiResponseValidator.validate(reading))
    }

    @Test
    fun `macros that cannot produce the printed energy are refused`() {
        // A misread column: 350 kcal cannot come from 3.5 g of macros.
        val reading = reading(calories = 350.0, protein = 1.0, carbs = 2.0, fat = 0.5)

        val error = assertThrows(AiValidationException::class.java) {
            AiResponseValidator.validate(reading)
        }
        assertEquals(true, error.message!!.contains("don't match its energy value"))
    }

    @Test
    fun `more than 100 g of macros per 100 g is refused`() {
        val reading = reading(calories = 630.0, protein = 50.0, carbs = 40.0, fat = 30.0)

        val error = assertThrows(AiValidationException::class.java) {
            AiResponseValidator.validate(reading)
        }
        assertEquals(true, error.message!!.contains("more than 100 g per 100 g"))
    }

    @Test
    fun `a calorie-free drink passes without an energy cross-check`() {
        val reading = reading(calories = 0.0, protein = 0.0, carbs = 0.0, fat = 0.0)
            .copy(basisUnit = "ml")

        assertEquals(reading, AiResponseValidator.validate(reading))
    }

    @Test
    fun `a per-serving basis is accepted and not held to the per-100 rule`() {
        val reading = reading(calories = 150.0, protein = 5.0, carbs = 20.0, fat = 5.0)
            .copy(basisQuantity = 30.0, servingLabel = "1 Portion (30 g)")

        assertEquals(reading, AiResponseValidator.validate(reading))
    }

    @Test
    fun `a basis amount of zero is refused`() {
        val reading = reading(calories = 350.0, protein = 10.0, carbs = 60.0, fat = 7.0)
            .copy(basisQuantity = 0.0)

        assertThrows(AiValidationException::class.java) {
            AiResponseValidator.validate(reading)
        }
    }

    private fun reading(
        calories: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
    ) = NutritionLabelReading(
        productName = "Haferflocken",
        brand = "Kölln",
        basisQuantity = 100.0,
        basisUnit = "g",
        calories = calories,
        proteinGrams = protein,
        carbohydrateGrams = carbs,
        fatGrams = fat,
    )
}

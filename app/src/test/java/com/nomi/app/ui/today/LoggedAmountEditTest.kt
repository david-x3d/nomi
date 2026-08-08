package com.nomi.app.ui.today

import com.nomi.app.data.local.entity.NutritionValues
import com.nomi.app.data.local.entity.scaledBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LoggedAmountEditTest {
    @Test
    fun `German decimal commas are accepted`() {
        assertEquals(30.5, parseLoggedAmount("30,5")!!, 1e-9)
        assertEquals(30.5, parseLoggedAmount("30.5")!!, 1e-9)
        assertEquals(30.0, parseLoggedAmount("  30 ")!!, 1e-9)
    }

    @Test
    fun `unusable amounts are rejected`() {
        assertNull(parseLoggedAmount(""))
        assertNull(parseLoggedAmount("abc"))
        assertNull(parseLoggedAmount("0"))
        assertNull(parseLoggedAmount("-30"))
        assertNull(parseLoggedAmount("999999999"))
    }

    @Test
    fun `preview shows the calories the correction would store`() {
        val state = editState(originalAmount = 300.0, originalCalories = 600.0, amountText = "30")

        assertEquals(60.0, state.previewCalories!!, 1e-9)
        assertTrue(state.canSave)
    }

    @Test
    fun `an unparsable amount blocks saving and has no preview`() {
        val state = editState(originalAmount = 300.0, originalCalories = 600.0, amountText = "")

        assertNull(state.previewCalories)
        assertFalse(state.canSave)
    }

    @Test
    fun `saving in progress blocks a second save`() {
        val state = editState(
            originalAmount = 300.0,
            originalCalories = 600.0,
            amountText = "30",
        ).copy(isSaving = true)

        assertFalse(state.canSave)
    }

    @Test
    fun `scaling a portion scales every present nutrient and keeps absent ones absent`() {
        val logged = NutritionValues(
            caloriesKcal = 600.0,
            proteinGrams = 30.0,
            carbohydrateGrams = 12.0,
            fatGrams = 40.0,
            fiberGrams = 2.0,
            sugarGrams = null,
            saturatedFatGrams = 18.0,
            sodiumMilligrams = 500.0,
        )

        val corrected = logged.scaledBy(0.1)

        assertEquals(60.0, corrected.caloriesKcal, 1e-9)
        assertEquals(3.0, corrected.proteinGrams, 1e-9)
        assertEquals(1.2, corrected.carbohydrateGrams, 1e-9)
        assertEquals(4.0, corrected.fatGrams, 1e-9)
        assertEquals(0.2, corrected.fiberGrams!!, 1e-9)
        assertEquals(1.8, corrected.saturatedFatGrams!!, 1e-9)
        assertEquals(50.0, corrected.sodiumMilligrams!!, 1e-9)
        assertNull(corrected.sugarGrams)
    }

    @Test
    fun `a non-positive portion factor is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            NutritionValues(caloriesKcal = 100.0).scaledBy(0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NutritionValues(caloriesKcal = 100.0).scaledBy(-1.0)
        }
    }

    private fun editState(
        originalAmount: Double,
        originalCalories: Double,
        amountText: String,
    ) = LoggedAmountEditUiState(
        entryId = 7L,
        name = "Rinderhüfte",
        unit = "g",
        originalAmount = originalAmount,
        originalCalories = originalCalories,
        amountText = amountText,
    )
}

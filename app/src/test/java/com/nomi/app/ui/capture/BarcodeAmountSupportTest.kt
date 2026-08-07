package com.nomi.app.ui.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarcodeAmountSupportTest {
    @Test
    fun `metric amount is preferred when label also contains US fluid ounces`() {
        val suggestion = BarcodeAmountSupport.initialSuggestion(
            servingSize = "1 can (12 fl oz / 355 ml)",
            sourceUnit = "ml",
        )

        assertEquals("355", suggestion.amount)
        assertEquals("ml", suggestion.unit)
    }

    @Test
    fun `mass serving suggestion stays in the source dimension`() {
        val suggestion = BarcodeAmountSupport.initialSuggestion(
            servingSize = "2 oz (56 g)",
            sourceUnit = "g",
        )

        assertEquals("56", suggestion.amount)
        assertEquals("g", suggestion.unit)
    }

    @Test
    fun `volume and mass expose only compatible units`() {
        assertEquals(listOf("ml", "cl", "l", "fl oz", "tbsp", "tsp"), BarcodeAmountSupport.compatibleUnits("ml"))
        assertEquals(listOf("g", "mg", "kg", "oz"), BarcodeAmountSupport.compatibleUnits("g"))
    }

    @Test
    fun `German decimal comma is accepted`() {
        assertEquals(250.5, BarcodeAmountSupport.parseAmount("250,5"))
    }

    @Test
    fun `grams equivalent is never guessed for volume`() {
        assertEquals(1_000.0, BarcodeAmountSupport.gramsEquivalent(1.0, "kg"))
        assertEquals(0.5, BarcodeAmountSupport.gramsEquivalent(500.0, "mg")!!, 0.0)
        assertNull(BarcodeAmountSupport.gramsEquivalent(250.0, "ml"))
        assertNull(BarcodeAmountSupport.gramsEquivalent(1.0, "EL"))
    }

    @Test
    fun `German spoon serving suggestion stays in volume family`() {
        val tablespoon = BarcodeAmountSupport.initialSuggestion(
            servingSize = "1 Essl\u00f6ffel",
            sourceUnit = "ml",
        )
        val teaspoons = BarcodeAmountSupport.initialSuggestion(
            servingSize = "2 TL",
            sourceUnit = "ml",
        )

        assertEquals("1", tablespoon.amount)
        assertEquals("tbsp", tablespoon.unit)
        assertEquals("2", teaspoons.amount)
        assertEquals("tsp", teaspoons.unit)
    }
}

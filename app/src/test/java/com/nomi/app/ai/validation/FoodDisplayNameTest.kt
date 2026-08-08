package com.nomi.app.ai.validation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The prompts ask the model for a short, correctly spelled name. These cover the net under
 * that: what a drifting provider may leave in front of a name, and - just as important - what
 * must be left alone, because a wrong name is worse than a long one.
 */
class FoodDisplayNameTest {
    @Test
    fun `leading amounts and sentence words are dropped`() {
        assertEquals("Cheeseburger", FoodDisplayName.clean("2 x Cheeseburger"))
        assertEquals("Cheeseburger", FoodDisplayName.clean("2 Cheeseburger"))
        assertEquals("Pommes", FoodDisplayName.clean("mit Pommes"))
        assertEquals("Banane", FoodDisplayName.clean("eine Banane"))
        assertEquals("Fries", FoodDisplayName.clean("and the fries"))
    }

    @Test
    fun `a number that belongs to the name stays`() {
        assertEquals("7 Up", FoodDisplayName.clean("7 Up"))
    }

    @Test
    fun `words that carry the food are never dropped`() {
        assertEquals("Red Bull Juneberry", FoodDisplayName.clean("Red Bull Juneberry"))
        assertEquals("Coca-Cola Zero", FoodDisplayName.clean("Coca-Cola Zero"))
        assertEquals("McDonald's Cheeseburger", FoodDisplayName.clean("McDonald's Cheeseburger"))
    }

    @Test
    fun `a name gets a capital letter`() {
        assertEquals("Cheeseburger", FoodDisplayName.clean("cheeseburger"))
        assertEquals("Pommes frites", FoodDisplayName.clean("pommes frites"))
    }

    @Test
    fun `a shouted name is settled, a short brand-like one is not`() {
        assertEquals("Pizza", FoodDisplayName.clean("PIZZA"))
        assertEquals("Big Mac", FoodDisplayName.clean("BIG MAC"))
        assertEquals("KFC", FoodDisplayName.clean("KFC"))
        assertEquals("BBQ", FoodDisplayName.clean("BBQ"))
    }

    @Test
    fun `spacing is collapsed and blanks survive`() {
        // Only the first letter is decided here. Whether "huhn" inside the name takes a
        // capital is German grammar, which the prompts leave to the model.
        assertEquals("Reis mit huhn", FoodDisplayName.clean("  reis   mit\nhuhn "))
        assertEquals("", FoodDisplayName.clean("   "))
    }

    @Test
    fun `cleaning never empties a name`() {
        assertEquals("Mit", FoodDisplayName.clean("mit"))
        assertEquals("2", FoodDisplayName.clean("2"))
    }
}

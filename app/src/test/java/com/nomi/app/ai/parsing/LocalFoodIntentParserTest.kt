package com.nomi.app.ai.parsing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalFoodIntentParserTest {
    @Test
    fun `plain single food uses local intent`() {
        val intent = requireNotNull(LocalFoodIntentParser.parseOrNull("  apple  "))

        assertEquals("apple", intent.originalText)
        assertEquals("apple", intent.items.single().name)
    }

    @Test
    fun `article plus one food remains unambiguous`() {
        val intent = requireNotNull(LocalFoodIntentParser.parseOrNull("ein Apfel"))

        assertEquals("Apfel", intent.items.single().name)
        assertEquals(1.0, intent.items.single().quantity!!, 0.0)
        assertEquals("piece", intent.items.single().unit)
    }

    @Test
    fun `explicit German spoon amount uses local fast path`() {
        val intent = requireNotNull(
            LocalFoodIntentParser.parseOrNull("1,5 Löffel Himbeer Marmelade"),
        )

        assertEquals("Himbeer Marmelade", intent.items.single().name)
        assertEquals(1.5, intent.items.single().quantity!!, 0.0)
        assertEquals("Löffel", intent.items.single().unit)
        assertEquals(1, intent.items.single().assumptions.size)
    }

    @Test
    fun `explicit gram amount skips interpretation provider`() {
        val intent = requireNotNull(LocalFoodIntentParser.parseOrNull("250 g Skyr"))

        assertEquals("Skyr", intent.items.single().name)
        assertEquals(250.0, intent.items.single().quantity!!, 0.0)
        assertEquals("g", intent.items.single().unit)
    }

    @Test
    fun `explicit amount with multiple foods still uses provider parser`() {
        assertNull(LocalFoodIntentParser.parseOrNull("1 EL Marmelade und Toast"))
    }

    @Test
    fun `quantities packages brands lists and phrases use provider parser`() {
        listOf(
            "2 apples",

            "55% of a 320g package of fish",
            "half of a 200g bag",
            "pizza by Domino's",
            "apple and banana",
            "apple, banana",
            "my usual breakfast",
            "green apple",
        ).forEach { input ->
            assertNull("Expected provider fallback for: $input", LocalFoodIntentParser.parseOrNull(input))
        }
    }
}

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
    }

    @Test
    fun `quantities packages brands lists and phrases use provider parser`() {
        listOf(
            "2 apples",
            "250 g apple",
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

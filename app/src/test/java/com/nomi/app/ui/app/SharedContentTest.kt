package com.nomi.app.ui.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedContentTest {
    @Test
    fun `a browser share keeps the page title in front of its address`() {
        assertEquals(
            "Chicken tikka masala\nhttps://example.test/tikka",
            sharedLoggingText("Chicken tikka masala", "https://example.test/tikka"),
        )
    }

    @Test
    fun `a subject the body already starts with is not repeated`() {
        assertEquals(
            "Lunch: two eggs and toast",
            sharedLoggingText("Lunch", "Lunch: two eggs and toast"),
        )
    }

    @Test
    fun `either part alone is used as it is`() {
        assertEquals("200 g skyr", sharedLoggingText(null, "  200 g skyr \n"))
        assertEquals("Banana", sharedLoggingText("Banana", "   "))
    }

    @Test
    fun `an empty share yields nothing`() {
        assertNull(sharedLoggingText(" ", null))
        assertNull(sharedLoggingText(null, null))
    }

    @Test
    fun `a long share is capped`() {
        val text = sharedLoggingText(null, "a".repeat(5_000))
        assertEquals(2_000, text?.length)
    }
}

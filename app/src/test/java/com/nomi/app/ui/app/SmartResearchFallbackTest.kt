package com.nomi.app.ui.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartResearchFallbackTest {
    @Test
    fun `primary result is returned without spending on fallback`() = runBlocking {
        var fallbackCalled = false

        val result = runWithSmartFallback(
            primary = { "primary" },
            fallback = {
                fallbackCalled = true
                "fallback"
            },
        )

        assertEquals("primary", result)
        assertFalse(fallbackCalled)
    }

    @Test
    fun `failed primary uses smart fallback`() = runBlocking {
        val result = runWithSmartFallback(
            primary = { error("primary failed") },
            fallback = { "verified fallback" },
        )

        assertEquals("verified fallback", result)
    }

    @Test
    fun `cancellation never starts expensive fallback`() {
        var fallbackCalled = false

        assertThrows(CancellationException::class.java) {
            runBlocking {
                runWithSmartFallback(
                    primary = { throw CancellationException("cancelled") },
                    fallback = {
                        fallbackCalled = true
                        "fallback"
                    },
                )
            }
        }
        assertFalse(fallbackCalled)
    }

    @Test
    fun `fallback failure retains primary failure for diagnostics`() {
        val primaryError = IllegalStateException("primary failed")
        val fallbackError = IllegalArgumentException("fallback failed")

        val thrown = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                runWithSmartFallback(
                    primary = { throw primaryError },
                    fallback = { throw fallbackError },
                )
            }
        }
        assertSame(fallbackError, thrown)
        assertTrue(thrown.suppressed.contains(primaryError))
    }
}

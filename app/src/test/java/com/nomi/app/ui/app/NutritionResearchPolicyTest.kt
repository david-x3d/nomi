package com.nomi.app.ui.app

import com.nomi.app.ai.model.FoodAnalysis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NutritionResearchPolicyTest {
    private val analysis = FoodAnalysis(listOf(testAnalyzedItem()))

    @Test
    fun `primary success does not call another provider or estimate`() = runBlocking {
        val result = runNutritionResearchPolicy({ analysis }, { error("Fallback must not run") }, { error("Estimate must not run") })
        assertSame(analysis, result)
    }

    @Test
    fun `configured research fallback runs before any estimate`() = runBlocking {
        val calls = mutableListOf<String>()
        val result = runNutritionResearchPolicy(
            primary = { calls += "primary"; error("Missing source") },
            fallback = { calls += "fallback"; analysis },
            estimate = { calls += "estimate"; analysis },
        )
        assertSame(analysis, result)
        assertEquals(listOf("primary", "fallback"), calls)
    }

    @Test
    fun `estimate runs only after both research routes fail`() = runBlocking {
        val calls = mutableListOf<String>()
        runNutritionResearchPolicy(
            { calls += "primary"; error("Missing source") },
            { calls += "fallback"; error("No fallback source") },
            { calls += "estimate"; analysis },
        )
        assertEquals(listOf("primary", "fallback", "estimate"), calls)
    }

    @Test
    fun `all failures preserve the actionable primary error and other diagnostics`() {
        val primary = IllegalStateException("Primary")
        val fallback = IllegalArgumentException("Fallback")
        val estimate = IllegalArgumentException("Estimate")
        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking { runNutritionResearchPolicy({ throw primary }, { throw fallback }, { throw estimate }) }
        }
        assertSame(primary, thrown)
        assertEquals(listOf(fallback, estimate), thrown.suppressed.toList())
    }

    @Test
    fun `cancellation at any stage never starts a later stage`() {
        for (cancelAt in 0..2) {
            val calls = mutableListOf<Int>()
            fun attempt(stage: Int): FoodAnalysis {
                calls += stage
                if (stage == cancelAt) throw CancellationException()
                error("Research failed")
            }
            assertThrows(CancellationException::class.java) {
                runBlocking { runNutritionResearchPolicy({ attempt(0) }, { attempt(1) }, { attempt(2) }) }
            }
            assertEquals((0..cancelAt).toList(), calls)
        }
    }
}

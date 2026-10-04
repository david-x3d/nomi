package com.nomi.app.ui.app

import com.nomi.app.ai.model.FoodAnalysis
import java.util.Collections
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelComparisonTest {

    @Test
    fun `one model failing leaves the others running to the end`() = runBlocking {
        val reported = Collections.synchronizedList(mutableListOf<String>())
        val runs = compareModels(
            models = listOf("fails", "slow", "fast"),
            research = { model ->
                when (model) {
                    "fails" -> error("refused")
                    "slow" -> { delay(50); FoodAnalysis(emptyList()) }
                    else -> FoodAnalysis(emptyList())
                }
            },
            onRun = { reported += it.model },
        )

        assertEquals(listOf("fails", "slow", "fast"), runs.map { it.model })
        assertTrue(runs[0].result.isFailure)
        assertTrue(runs[1].result.isSuccess)
        assertTrue(runs[2].result.isSuccess)
        assertEquals(setOf("fails", "slow", "fast"), reported.toSet())
    }

    @Test
    fun `no more than four models run`() = runBlocking {
        val asked = Collections.synchronizedList(mutableListOf<String>())
        val runs = compareModels(
            models = listOf("a", "b", "a", "c", "d", "e"),
            research = { model -> asked += model; FoodAnalysis(emptyList()) },
            onRun = {},
        )

        assertEquals(listOf("a", "b", "c", "d"), runs.map { it.model })
        assertEquals(MAX_COMPARED_MODELS, asked.size)
    }

    @Test
    fun `a fifth model cannot be selected`() {
        var state = ModelComparisonUiState(models = listOf("a", "b", "c", "d", "e"), selected = emptySet())
        listOf("a", "b", "c", "d", "e").forEach { state = state.toggled(it) }

        assertEquals(setOf("a", "b", "c", "d"), state.selected)
        assertFalse(state.canSelectMore())
        assertEquals(setOf("b", "c", "d"), state.toggled("a").selected)
    }

    @Test
    fun `a typed model is listed and selected while there is room`() {
        val state = ModelComparisonUiState(
            models = listOf("a"),
            selected = setOf("a"),
            customModelInput = "  vendor/model ",
        ).withCustomModel()

        assertEquals(listOf("a", "vendor/model"), state.models)
        assertEquals(setOf("a", "vendor/model"), state.selected)
        assertEquals("", state.customModelInput)
    }

    @Test
    fun `selected models keep list order`() {
        val state = ModelComparisonUiState(models = listOf("a", "b", "c"), selected = setOf("c", "a"))
        assertEquals(listOf("a", "c"), state.selectedInOrder())
    }
}

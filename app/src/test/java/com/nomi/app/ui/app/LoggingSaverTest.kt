package com.nomi.app.ui.app

import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.data.local.entity.FoodLogEntity
import com.nomi.app.ui.logging.ManualFoodDraft
import com.nomi.app.ui.today.MealCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LoggingSaverTest {
    @Test
    fun `a confirmed multi-food meal preserves grouping original text citations and destination`() = runBlocking {
        var written = emptyList<FoodLogEntity>()
        var replaced: Long? = null
        val saver = LoggingSaver(
            cacheItem = { error("Grouped rows must keep their own nutrition snapshots") },
            cacheManual = { null },
            writeRows = { rows, id -> written = rows; replaced = id; listOf(11, 12) },
        )
        val context = LoggingSaveContext(testLogDestination, 7, listOf("https://evidence.test"), "Apple and pear", "meal")
        saver.saveAnalysis(
            FoodAnalysis(listOf(testAnalyzedItem(), testAnalyzedItem("Pear"))), MealCategory.LUNCH, context,
        )
        assertEquals(7L, replaced)
        assertEquals(listOf("Apple", "Pear"), written.map { it.displayNameSnapshot })
        assertEquals(listOf("meal", "meal"), written.map { it.entryGroupId })
        assertTrue(written.all { it.localDate == testLogDate.toString() && it.zoneId == "Europe/Berlin" })
        assertTrue(written.all { it.originalInput == "Apple and pear" })
        assertTrue(written.all { it.sourceSnapshot.citedUrls.orEmpty().contains("https://evidence.test") })
    }

    @Test
    fun `a manual rewrite passes the captured replacement to the same atomic writer`() = runBlocking {
        var row: FoodLogEntity? = null
        var replaced: Long? = null
        val saver = LoggingSaver({ null }, { 99 }, { rows, id -> row = rows.single(); replaced = id; listOf(1) })
        saver.saveManual(
            ManualFoodDraft("Apple", "100", "g", "100", "1", "20", "2", MealCategory.LUNCH),
            LoggingSaveContext(testLogDestination, 7),
        )
        assertEquals(7L, replaced)
        assertEquals(99L, row?.foodId)
        assertEquals("manual", row?.inputMethod)
    }

    @Test
    fun `an invalid analysis is rejected before caching or writing`() {
        var wrote = false
        val saver = LoggingSaver({ error("Must validate first") }, { null }, { _, _ -> wrote = true; emptyList() })
        assertThrows(Exception::class.java) {
            runBlocking {
                saver.saveAnalysis(
                    FoodAnalysis(listOf(testAnalyzedItem().copy(calories = Double.NaN))),
                    MealCategory.LUNCH, LoggingSaveContext(testLogDestination),
                )
            }
        }
        assertFalse(wrote)
    }
}

package com.nomi.wear

import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchDayRolloverTest {
    @Test
    fun `midnight clears totals without a phone update and new data restores them`() = runTest {
        var date = LocalDate.of(2026, 11, 3)
        val snapshot = WatchToday(
            date, true, "en", 1200.0, 2000.0, 80.0, 100.0,
            150.0, 250.0, 40.0, 70.0, emptyList(),
        )
        val phone = MutableStateFlow<WatchToday?>(snapshot)
        var latest: WatchToday? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            phone.withDayRollover { date }.collect { latest = it }
        }
        assertEquals(800, latest?.remainingKcal)

        date = date.plusDays(1)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(date, latest?.date)
        assertEquals(2000, latest?.remainingKcal)
        assertEquals(0.0, latest!!.proteinGrams, 0.0)
        assertEquals(0.0, latest!!.carbohydrateGrams, 0.0)
        assertEquals(0.0, latest!!.fatGrams, 0.0)

        phone.value = snapshot.copy(date = date, caloriesKcal = 300.0)
        runCurrent()
        assertEquals(1700, latest?.remainingKcal)
    }
}

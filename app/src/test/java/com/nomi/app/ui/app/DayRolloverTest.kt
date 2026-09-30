package com.nomi.app.ui.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DayRolloverTest {

    private val yesterday = LocalDate.of(2026, 9, 29)
    private val today = LocalDate.of(2026, 9, 30)

    @Test
    fun `a screen on the old today follows the clock to the new one`() {
        assertEquals(today, followDayRollover(yesterday, yesterday, today))
    }

    @Test
    fun `a day the user paged back to stays where it is`() {
        val older = LocalDate.of(2026, 9, 20)

        assertEquals(older, followDayRollover(older, yesterday, today))
    }

    @Test
    fun `the wait ends just after the next local midnight`() {
        val zone = ZoneId.of("Europe/Berlin")
        val now = ZonedDateTime.of(2026, 9, 29, 23, 59, 0, 0, zone)

        assertEquals(61_000L, delayUntilNextDay(now, zone))
    }
}

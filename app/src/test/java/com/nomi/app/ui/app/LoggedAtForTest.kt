package com.nomi.app.ui.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A food logged onto a past day carries a timestamp on that day.
 *
 * Health Connect files a record by its timestamp. Stamping a backdated entry with "now" put
 * yesterday's forgotten dinner on today there, while Nomi itself showed it on yesterday.
 */
class LoggedAtForTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val now = ZonedDateTime.of(2026, 10, 1, 21, 15, 30, 0, berlin).toInstant()

    @Test
    fun `today keeps the real instant`() {
        assertEquals(now.toEpochMilli(), loggedAtFor(LocalDate.of(2026, 10, 1), now, berlin))
    }

    @Test
    fun `a past day gets the same time of day on that date`() {
        val loggedAt = Instant.ofEpochMilli(loggedAtFor(LocalDate.of(2026, 9, 28), now, berlin))

        assertEquals(ZonedDateTime.of(2026, 9, 28, 21, 15, 30, 0, berlin), loggedAt.atZone(berlin))
    }

    @Test
    fun `today is judged in the user's zone, not in UTC`() {
        // 00:30 in Berlin is still the previous day in UTC.
        val justAfterMidnight = ZonedDateTime.of(2026, 10, 2, 0, 30, 0, 0, berlin).toInstant()

        assertEquals(
            justAfterMidnight.toEpochMilli(),
            loggedAtFor(LocalDate.of(2026, 10, 2), justAfterMidnight, berlin),
        )
    }

    @Test
    fun `a time that does not exist on the target day is moved forward, not rejected`() {
        // 02:30 does not exist in Berlin on 29 March 2026.
        val atHalfPastTwo = ZonedDateTime.of(2026, 10, 1, 2, 30, 0, 0, berlin).toInstant()

        val loggedAt = Instant.ofEpochMilli(loggedAtFor(LocalDate.of(2026, 3, 29), atHalfPastTwo, berlin))

        assertEquals(LocalDate.of(2026, 3, 29), loggedAt.atZone(berlin).toLocalDate())
    }
}

package com.nomi.app.integration.notifications

import com.nomi.app.data.preferences.ReminderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class NextReminderCalculatorTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun rollsPastFridayToNextEnabledMonday() {
        val next = NextReminderCalculator.nextOccurrence(
            setting = ReminderSetting(
                enabled = true,
                localTime = "08:00",
                daysOfWeek = setOf(1, 2, 3, 4, 5),
            ),
            after = Instant.parse("2026-08-07T07:00:00Z"), // Friday 09:00 in Berlin.
            zoneId = berlin,
        )

        assertEquals(Instant.parse("2026-08-10T06:00:00Z"), next)
    }

    @Test
    fun springGapUsesJavaTimeForwardResolution() {
        val next = NextReminderCalculator.nextOccurrence(
            setting = ReminderSetting(
                enabled = true,
                localTime = "02:30",
                daysOfWeek = setOf(7),
            ),
            after = Instant.parse("2026-03-28T12:00:00Z"),
            zoneId = berlin,
        )

        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), next)
    }

    @Test
    fun disabledReminderHasNoOccurrence() {
        assertNull(
            NextReminderCalculator.nextOccurrence(
                setting = ReminderSetting(enabled = false, localTime = "08:00"),
                after = Instant.parse("2026-08-07T07:00:00Z"),
                zoneId = berlin,
            ),
        )
    }
}

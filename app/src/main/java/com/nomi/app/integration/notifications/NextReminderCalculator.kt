package com.nomi.app.integration.notifications

import com.nomi.app.data.preferences.ReminderSetting
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Deterministically finds the first enabled wall-clock occurrence strictly after [after]. */
object NextReminderCalculator {
    fun nextOccurrence(
        setting: ReminderSetting,
        after: Instant,
        zoneId: ZoneId,
    ): Instant? {
        if (!setting.enabled || setting.daysOfWeek.isEmpty()) return null
        require(setting.daysOfWeek.all { it in 1..7 }) { "Reminder days must be ISO values 1..7" }
        require(setting.localTime.matches(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d"))) {
            "Reminder time must use HH:mm form"
        }
        val time = LocalTime.parse(setting.localTime)
        val localNow = ZonedDateTime.ofInstant(after, zoneId)
        for (offset in 0..7) {
            val date = localNow.toLocalDate().plusDays(offset.toLong())
            if (date.dayOfWeek.value !in setting.daysOfWeek) continue
            // ZonedDateTime resolves spring gaps forward and chooses the earlier offset in overlaps.
            val candidate = ZonedDateTime.of(date, time, zoneId).toInstant()
            if (candidate.isAfter(after)) return candidate
        }
        return null
    }
}

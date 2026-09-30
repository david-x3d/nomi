package com.nomi.app.ui.app

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Where a date the user is looking at should land when the calendar day changes.
 *
 * A screen parked on the old "today" follows the clock to the new one: someone who left Nomi open
 * overnight and logs breakfast in the morning means *this* morning, and writing it onto yesterday
 * would quietly put the food on the wrong day. A screen the user paged back to on purpose stays
 * where they put it.
 */
internal fun followDayRollover(shown: LocalDate, previousToday: LocalDate, newToday: LocalDate): LocalDate =
    if (shown == previousToday) newToday else shown

/**
 * How long until the next local midnight, plus a small margin so the check that follows the wait
 * already sees the new date rather than 23:59:59.999 of the old one.
 */
internal fun delayUntilNextDay(now: ZonedDateTime, zoneId: ZoneId, marginMillis: Long = 1_000): Long {
    val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zoneId)
    return Duration.between(now, nextMidnight).toMillis().coerceAtLeast(0) + marginMillis
}

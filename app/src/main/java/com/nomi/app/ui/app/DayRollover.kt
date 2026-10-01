package com.nomi.app.ui.app

import java.time.Duration
import java.time.Instant
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

/**
 * The instant a food logged onto [date] should carry.
 *
 * For today that is simply [now]. For a day the user paged back to, it is the same time of day on
 * that date: the row then sorts and reads as part of the day it was logged onto, and Health
 * Connect - which files a record by its timestamp, not by Nomi's local date - puts yesterday's
 * forgotten dinner on yesterday instead of on today.
 */
internal fun loggedAtFor(date: LocalDate, now: Instant, zoneId: ZoneId): Long {
    val local = now.atZone(zoneId)
    if (date == local.toLocalDate()) return now.toEpochMilli()
    return ZonedDateTime.of(date, local.toLocalTime(), zoneId).toInstant().toEpochMilli()
}

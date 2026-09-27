package com.nomi.app.ui.progress

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** How many columns the logging-activity strip draws, whatever range is selected. */
const val ACTIVITY_COLUMNS = 30

/**
 * Folds a range of days into a fixed number of columns, each holding how much of it was logged.
 *
 * Thirty columns for a long range is the point: the default 30-day window becomes one column per
 * day, so the strip doubles as a calendar of the month, while a year or "All" becomes thirty even
 * slices and still reads as a shape. A percentage alone cannot say *when* a month went quiet, and a
 * column per day cannot be drawn for a decade.
 *
 * Days are placed by proportion rather than in fixed-width blocks, because a range that does not
 * divide evenly would otherwise leave the last column permanently empty - the range ends in the
 * middle of it, and 365 days of perfect logging would draw as 364. A column is a fraction of the
 * days that actually map to it, so a short final column is never painted as if it were a full one.
 *
 * A range shorter than the column count falls back to one column per day, so a 7-day window draws
 * seven columns rather than seven columns and twenty-three blanks.
 */
fun loggingActivityColumns(
    loggedDates: Collection<LocalDate>,
    rangeStart: LocalDate,
    rangeDays: Int,
    columns: Int = ACTIVITY_COLUMNS,
): List<Float> {
    require(columns > 0) { "An activity strip needs at least one column" }
    require(rangeDays > 0) { "An activity strip needs a range of at least one day" }
    val count = minOf(columns, rangeDays)
    val daysPerColumn = LongArray(count) { index ->
        columnBoundary(index + 1, rangeDays, count) - columnBoundary(index, rangeDays, count)
    }
    val logged = IntArray(count)
    loggedDates.forEach { date ->
        val day = ChronoUnit.DAYS.between(rangeStart, date)
        if (day < 0L || day >= rangeDays.toLong()) return@forEach
        val column = ((day * count) / rangeDays).toInt()
        if (column in 0 until count) logged[column] += 1
    }
    return logged.mapIndexed { index, picked ->
        val days = daysPerColumn[index]
        if (days <= 0L) 0f else (picked.toFloat() / days.toFloat()).coerceIn(0f, 1f)
    }
}

/** The first day of column [index], rounded up so the columns tile the range without gaps. */
private fun columnBoundary(index: Int, rangeDays: Int, columns: Int): Long =
    (index.toLong() * rangeDays + columns - 1) / columns

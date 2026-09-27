package com.nomi.app.ui.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Coverage for the strip that shows *when* a range was logged.
 *
 * The interesting decisions are that every range collapses to the same number of columns, that a
 * column is a fraction of the days it covers rather than a raw count, and that dates outside the
 * window cannot spill into it.
 */
class LoggingActivityColumnsTest {

    private val start = LocalDate.of(2026, 9, 1)

    @Test
    fun `an empty range draws a full set of empty columns`() {
        val columns = loggingActivityColumns(
            loggedDates = emptyList(),
            rangeStart = start,
            rangeDays = 30,
        )

        assertEquals(ACTIVITY_COLUMNS, columns.size)
        assertTrue(columns.all { it == 0f })
    }

    @Test
    fun `a thirty day range gives one column per day`() {
        val columns = loggingActivityColumns(
            loggedDates = listOf(start, start.plusDays(1), start.plusDays(2)),
            rangeStart = start,
            rangeDays = 30,
        )

        // The default range, so the strip doubles as a calendar of the month.
        assertEquals(1f, columns[0], 0.0001f)
        assertEquals(1f, columns[1], 0.0001f)
        assertEquals(1f, columns[2], 0.0001f)
        assertEquals(0f, columns[3], 0.0001f)
    }

    @Test
    fun `a longer range still draws the same number of columns`() {
        listOf(90, 180, 365, 3_650).forEach { days ->
            val columns = loggingActivityColumns(
                loggedDates = (0 until days).map { start.plusDays(it.toLong()) },
                rangeStart = start,
                rangeDays = days,
            )

            assertEquals("$days days", ACTIVITY_COLUMNS, columns.size)
            // Every day logged means every column full, whatever the slice length.
            assertTrue("$days days should be full", columns.all { it == 1f })
        }
    }
    @Test
    fun `a range shorter than the column count draws one column per day`() {
        val columns = loggingActivityColumns(
            loggedDates = (0 until 7).map { start.plusDays(it.toLong()) },
            rangeStart = start,
            rangeDays = 7,
        )

        // Seven columns, not seven full columns followed by twenty-three empty ones.
        assertEquals(7, columns.size)
        assertTrue(columns.all { it == 1f })
    }

    @Test
    fun `a range that does not divide evenly does not leave a dead last column`() {
        // 365 days over 30 columns is 12 or 13 days per column. Fixed-width blocks would put the
        // last day of the range alone in a column nothing else reaches.
        val columns = loggingActivityColumns(
            loggedDates = (0 until 365).map { start.plusDays(it.toLong()) },
            rangeStart = start,
            rangeDays = 365,
        )

        assertEquals(ACTIVITY_COLUMNS, columns.size)
        assertTrue(columns.all { it == 1f })
    }

    @Test
    fun `a column is the fraction of the days it covers`() {
        val columns = loggingActivityColumns(
            // 30 days over 10 columns: three days per column, and the first is fully logged.
            loggedDates = (0 until 3).map { start.plusDays(it.toLong()) },
            rangeStart = start,
            rangeDays = 30,
            columns = 10,
        )

        assertEquals(10, columns.size)
        assertEquals(1f, columns[0], 0.0001f)
        assertEquals(0f, columns[1], 0.0001f)
    }

    @Test
    fun `a partly logged slice reads as a part column`() {
        val columns = loggingActivityColumns(
            // 30 days over 10 columns is three days each. The first slice is complete and the
            // second holds one of its three days.
            loggedDates = (0 until 4).map { start.plusDays(it.toLong()) },
            rangeStart = start,
            rangeDays = 30,
            columns = 10,
        )

        assertEquals(1f, columns[0], 0.0001f)
        assertEquals(1f / 3f, columns[1], 0.0001f)
        assertEquals(0f, columns[2], 0.0001f)
    }

    @Test
    fun `dates before the range are ignored`() {
        val columns = loggingActivityColumns(
            loggedDates = listOf(start.minusDays(1), start.minusDays(40)),
            rangeStart = start,
            rangeDays = 30,
        )

        // A negative column index would either throw or wrap around and paint the far end of the
        // strip full.
        assertTrue(columns.all { it == 0f })
    }

    @Test
    fun `dates after the range are ignored`() {
        val columns = loggingActivityColumns(
            loggedDates = listOf(start.plusDays(90), start.plusDays(365)),
            rangeStart = start,
            rangeDays = 30,
        )

        assertTrue(columns.all { it == 0f })
    }

    @Test
    fun `a repeated date is not counted twice`() {
        val columns = loggingActivityColumns(
            loggedDates = listOf(start, start, start),
            rangeStart = start,
            rangeDays = 30,
        )

        assertEquals(1f, columns[0], 0.0001f)
    }

    @Test
    fun `a nonsensical request is refused rather than drawn wrong`() {
        listOf(0, -1).forEach { columns ->
            val refused = runCatching {
                loggingActivityColumns(emptyList(), start, 30, columns = columns)
            }
            assertTrue("columns = $columns must be refused", refused.isFailure)
        }
        val refusedRange = runCatching { loggingActivityColumns(emptyList(), start, 0) }
        assertTrue("a zero-day range must be refused", refusedRange.isFailure)
    }
}

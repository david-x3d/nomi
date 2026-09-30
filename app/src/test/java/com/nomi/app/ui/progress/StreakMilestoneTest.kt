package com.nomi.app.ui.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Coverage for the milestone arithmetic behind the Progress page's streak card.
 *
 * The bug this file exists to prevent: showing a streak as `streakDays % 30`, which reads 0 / 30 on
 * day 30 and then sits at 30 / 30 forever. The user's streak is real and never resets; only the
 * *cycle* it is shown in repeats, and exact multiples of 30 belong to the milestone they reached.
 */
class StreakMilestoneTest {

    @Test
    fun `the first day of a streak is day 1 of the cycle`() {
        assertEquals(1, streakMilestone(1).cycleDay)
    }

    @Test
    fun `day 29 of a cycle is 29 of 30`() {
        assertEquals(29, streakMilestone(29).cycleDay)
    }

    @Test
    fun `day 30 fills the cycle instead of resetting it`() {
        val milestone = streakMilestone(30)

        // The regression: 30 % 30 == 0 would read as "nothing done today" on the one day the user
        // just completed a milestone.
        assertEquals(30, milestone.cycleDay)
        assertEquals(1f, milestone.cycleFraction, 0.0001f)
        assertTrue(milestone.isMilestoneDay)
    }

    @Test
    fun `day 31 starts the next cycle`() {
        assertEquals(1, streakMilestone(31).cycleDay)
    }

    @Test
    fun `day 59 is 29 of the second cycle`() {
        assertEquals(29, streakMilestone(59).cycleDay)
    }

    @Test
    fun `day 60 fills the second cycle`() {
        val milestone = streakMilestone(60)

        assertEquals(30, milestone.cycleDay)
        assertEquals(2, milestone.completedMilestones)
        assertTrue(milestone.isMilestoneDay)
    }

    @Test
    fun `day 61 starts the third cycle`() {
        val milestone = streakMilestone(61)

        assertEquals(1, milestone.cycleDay)
        assertEquals(2, milestone.completedMilestones)
        assertFalse(milestone.isMilestoneDay)
    }

    @Test
    fun `a long streak keeps counting while the cycle repeats`() {
        // 67 days: the real total is 67 and the cycle is 7 / 30. Both are on screen at once and
        // neither is a lie.
        val milestone = streakMilestone(67)

        assertEquals(67, milestone.streakDays)
        assertEquals(7, milestone.cycleDay)
        assertEquals(23, milestone.daysUntilNextMilestone)
        assertEquals(90, milestone.nextMilestoneDays)
    }

    @Test
    fun `every exact multiple of thirty fills its cycle`() {
        listOf(30, 60, 90, 120, 150, 300, 3_000).forEach { days ->
            assertEquals("$days should fill the cycle", 30, streakMilestone(days).cycleDay)
        }
    }

    @Test
    fun `the day after every exact multiple starts a fresh cycle at one`() {
        listOf(31, 61, 91, 121, 151, 301, 3_001).forEach { days ->
            assertEquals("$days should start a cycle", 1, streakMilestone(days).cycleDay)
        }
    }

    @Test
    fun `large streaks stay inside the cycle`() {
        val milestone = streakMilestone(4_097)

        assertEquals(4_097, milestone.streakDays)
        // 4 080 is 136 whole milestones, so this is day 17 of the next one.
        assertEquals(17, milestone.cycleDay)
        assertEquals(136, milestone.completedMilestones)
        assertEquals(4_110, milestone.nextMilestoneDays)
        assertEquals(13, milestone.daysUntilNextMilestone)
        assertEquals(17f / 30f, milestone.cycleFraction, 0.0001f)
    }

    @Test
    fun `the milestone ladder runs in thirties`() {
        val ladder = listOf(1, 29, 30, 31, 59, 60, 61, 67, 89, 90, 91)
            .map { streakMilestone(it).nextMilestoneDays }

        assertEquals(
            listOf(30, 30, 60, 60, 60, 90, 90, 90, 90, 120, 120),
            ladder,
        )
    }

    @Test
    fun `no streak has no cycle day and no progress`() {
        val milestone = streakMilestone(0)

        assertEquals(0, milestone.cycleDay)
        assertEquals(0f, milestone.cycleFraction, 0.0001f)
        assertEquals(0, milestone.completedMilestones)
        assertEquals(30, milestone.nextMilestoneDays)
        assertEquals(30, milestone.daysUntilNextMilestone)
        assertFalse(milestone.hasCompletedMilestone)
    }

    @Test
    fun `a negative streak is treated as none`() {
        // The count comes from a list of dates, so it cannot be negative, but nothing downstream
        // should be able to draw a filled ring from a bad value.
        assertEquals(streakMilestone(0), streakMilestone(-4))
    }

    @Test
    fun `a completed milestone is remembered`() {
        assertFalse(streakMilestone(29).hasCompletedMilestone)
        assertTrue(streakMilestone(30).hasCompletedMilestone)
        assertTrue(streakMilestone(67).hasCompletedMilestone)
    }

    @Test
    fun `the trail keeps the milestone just reached and looks forward from there`() {
        assertEquals(listOf(30, 60, 90, 120), milestoneTrail(67))
        assertEquals(listOf(30, 60, 90, 120), milestoneTrail(0))
        assertEquals(listOf(30, 60, 90, 120), milestoneTrail(30))
    }

    @Test
    fun `the trail scrolls once a long streak is past it`() {
        // 22 whole milestones reached: the last two of them stay on screen as the ones earned, and
        // the two after it as the horizon. A two-year streak must not draw two years of marks.
        assertEquals(listOf(630, 660, 690, 720), milestoneTrail(670))
    }

    @Test
    fun `the trail always has as many rungs as it is asked for`() {
        assertEquals(2, milestoneTrail(70, count = 2).size)
        assertEquals(6, milestoneTrail(70, count = 6).size)
    }
}

/**
 * The streak is counted from the days the user actually logged something, which is a different
 * question from how many days a range happens to contain.
 */
class LoggingStreakDaysTest {

    private val today = LocalDate.of(2026, 9, 27)

    @Test
    fun `consecutive days up to today are all counted`() {
        val dates = listOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(3))

        assertEquals(4, loggingStreakDays(dates, today))
    }

    @Test
    fun `a streak that includes today grows with it`() {
        assertEquals(1, loggingStreakDays(listOf(today), today))
        assertEquals(2, loggingStreakDays(listOf(today, today.minusDays(1)), today))
    }

    @Test
    fun `a streak that stopped yesterday is still alive today`() {
        val dates = listOf(today.minusDays(1), today.minusDays(2))

        // Today is not over. Reporting zero at breakfast for yesterday's run would be wrong.
        assertEquals(2, loggingStreakDays(dates, today))
    }

    @Test
    fun `a gap of two days has ended the streak`() {
        val dates = listOf(today.minusDays(2), today.minusDays(3))

        assertEquals(0, loggingStreakDays(dates, today))
    }

    @Test
    fun `nothing logged is no streak`() {
        assertEquals(0, loggingStreakDays(emptyList(), today))
    }

    @Test
    fun `a streak stops at the first gap`() {
        val dates = listOf(
            today,
            today.minusDays(1),
            today.minusDays(2),
            // A missed day.
            today.minusDays(4),
            today.minusDays(5),
        )

        assertEquals(3, loggingStreakDays(dates, today))
    }

    @Test
    fun `future dates are ignored`() {
        val dates = listOf(today.plusDays(1), today, today.minusDays(1))

        // Nothing can be logged for tomorrow yet, and a clock skew must not invent streak days.
        assertEquals(2, loggingStreakDays(dates, today))
    }

    @Test
    fun `a repeated date does not count twice`() {
        val dates = listOf(today, today, today.minusDays(1))

        assertEquals(2, loggingStreakDays(dates, today))
    }
}

/** The longest-streak stat: the best run ever, which a lapsed streak must not erase. */
class LongestLoggingStreakDaysTest {

    private val today = LocalDate.of(2026, 9, 27)

    @Test
    fun `an empty log has no streak`() {
        assertEquals(0, longestLoggingStreakDays(emptyList(), today))
    }

    @Test
    fun `an older run longer than the current one is the longest`() {
        val old = (20L..25L).map { today.minusDays(it) } // 6 days
        val current = listOf(today, today.minusDays(1)) // 2 days

        assertEquals(6, longestLoggingStreakDays(old + current, today))
    }

    @Test
    fun `the current run counts when it is the longest`() {
        val dates = (0L..4L).map { today.minusDays(it) } + today.minusDays(10)

        assertEquals(5, longestLoggingStreakDays(dates, today))
    }

    @Test
    fun `duplicates and unsorted input do not change the result`() {
        val dates = listOf(today.minusDays(2), today, today.minusDays(1), today, today.minusDays(9))

        assertEquals(3, longestLoggingStreakDays(dates, today))
    }

    @Test
    fun `dates after today are ignored`() {
        assertEquals(1, longestLoggingStreakDays(listOf(today, today.plusDays(1)), today))
    }
}

package com.nomi.app.ui.progress

import java.time.LocalDate

/**
 * Nomi counts a logging streak in fixed 30-day milestones.
 *
 * The number is the one thing on the Progress page that is never "reset by the design": the user's
 * streak is theirs, and it is stored as one integer that only ever counts up. The milestone is a
 * *presentation* of that number, and it is deliberately a repeating 30-day cycle rather than a
 * second, smaller streak, so the screen can show both at once without the two contradicting each
 * other.
 */
const val STREAK_MILESTONE_DAYS = 30

/**
 * Where a streak currently sits inside the milestone ladder.
 *
 * @param streakDays the user's real total, never reduced by the milestone cycle.
 * @param cycleDay the day inside the *current* 30-day cycle, 1..30. Never 0: an exact multiple of
 *   30 has just completed a cycle, so it shows as 30 / 30 and not as 0 / 30 of the next one.
 * @param completedMilestones how many whole 30-day milestones are behind the user.
 * @param nextMilestoneDays the next milestone on the ladder: 30, 60, 90, 120, …
 * @param daysUntilNextMilestone the days still to go to [nextMilestoneDays].
 */
data class StreakMilestone(
    val streakDays: Int,
    val cycleDay: Int,
    val completedMilestones: Int,
    val nextMilestoneDays: Int,
    val daysUntilNextMilestone: Int,
) {
    /** 0..1 through the current cycle, for a ring or a bar. */
    val cycleFraction: Float
        get() = if (cycleDay <= 0) 0f else cycleDay / STREAK_MILESTONE_DAYS.toFloat()

    /** True on the day a milestone is actually reached, which is what earns the celebration. */
    val isMilestoneDay: Boolean
        get() = cycleDay == STREAK_MILESTONE_DAYS

    /** True when at least one whole milestone is behind the user. */
    val hasCompletedMilestone: Boolean
        get() = completedMilestones > 0
}

/**
 * The milestone view of a streak.
 *
 * `cycleDay = ((streakDays - 1) % 30) + 1` is the whole trick, and the `-1` is what keeps an exact
 * multiple of 30 on the milestone it just reached:
 *
 * ```
 *  1 ->  1 / 30        30 -> 30 / 30        59 -> 29 / 30
 * 31 ->  1 / 30        60 -> 30 / 30        61 ->  1 / 30
 * ```
 *
 * A plain `streakDays % 30` would show 0 / 30 on day 30, which reads as "the streak is over" -
 * the exact opposite of what happened - and leaves the indicator visibly stuck at 30 / 30
 * afterwards.
 *
 * A streak of zero has no cycle day at all rather than day 0, so an empty ring is drawn instead of
 * a full one.
 */
fun streakMilestone(streakDays: Int): StreakMilestone {
    val days = streakDays.coerceAtLeast(0)
    if (days == 0) return StreakMilestone(0, 0, 0, STREAK_MILESTONE_DAYS, STREAK_MILESTONE_DAYS)
    val completed = days / STREAK_MILESTONE_DAYS
    val cycleDay = ((days - 1) % STREAK_MILESTONE_DAYS) + 1
    val next = (completed + 1) * STREAK_MILESTONE_DAYS
    return StreakMilestone(
        streakDays = days,
        cycleDay = cycleDay,
        completedMilestones = completed,
        nextMilestoneDays = next,
        daysUntilNextMilestone = next - days,
    )
}

/**
 * The handful of milestones worth drawing, oldest first.
 *
 * The trail always keeps the milestone just reached - so the user can see what they have already
 * earned - and looks forward from there. A streak that has run for years therefore scrolls rather
 * than showing a wall of completed pips.
 */
fun milestoneTrail(streakDays: Int, count: Int = 4): List<Int> {
    require(count > 0) { "A milestone trail needs at least one milestone" }
    val completed = streakDays.coerceAtLeast(0) / STREAK_MILESTONE_DAYS
    val first = (completed - 1).coerceAtLeast(1)
    return List(count) { (first + it) * STREAK_MILESTONE_DAYS }
}

/**
 * How many days in a row the user has logged something.
 *
 * [loggedDates] is every date with at least one entry, from the user's whole log rather than the
 * range the Progress page happens to be showing: a streak that restarted every time someone
 * switched to "7 days" would be a different number every tap.
 *
 * A streak is counted from today when today is logged, and from yesterday when it is not - the day
 * is not over yet, so yesterday's run is still alive and should not read as broken at breakfast.
 * A gap of two days is the point at which it has genuinely lapsed.
 */
fun loggingStreakDays(loggedDates: Collection<LocalDate>, today: LocalDate): Int {
    if (loggedDates.isEmpty()) return 0
    val logged = loggedDates.asSequence().filter { !it.isAfter(today) }.toHashSet()
    if (logged.isEmpty()) return 0
    var cursor = when {
        today in logged -> today
        today.minusDays(1) in logged -> today.minusDays(1)
        else -> return 0
    }
    var streak = 0
    while (cursor in logged) {
        streak += 1
        cursor = cursor.minusDays(1)
    }
    return streak
}

/**
 * The longest run of consecutive logged days the user has ever had, current one included.
 *
 * Like [loggingStreakDays] it reads the whole log, so it does not change with the range shown. It
 * is the number to beat after a streak lapses: a broken streak of 3 reads as a fresh start, but
 * "longest: 41 days" says what the user has already shown they can do. Dates after [today] are
 * ignored, as they are for the current streak, so the longest run can never be smaller than it.
 */
fun longestLoggingStreakDays(loggedDates: Collection<LocalDate>, today: LocalDate): Int {
    val days = loggedDates.asSequence().filter { !it.isAfter(today) }.distinct().sorted().toList()
    if (days.isEmpty()) return 0
    var longest = 1
    var run = 1
    for (i in 1 until days.size) {
        run = if (days[i - 1].plusDays(1) == days[i]) run + 1 else 1
        if (run > longest) longest = run
    }
    return longest
}

package com.nomi.app.ui.progress

import java.time.LocalDate

enum class ProgressRange(val label: String) {
    SEVEN_DAYS("7 days"),
    THIRTY_DAYS("30 days"),
    THREE_MONTHS("3 months"),
    SIX_MONTHS("6 months"),
    ONE_YEAR("1 year"),
    ALL("All"),
}

data class WeightPoint(val date: LocalDate, val kilograms: Double)
data class NutritionPoint(
    val date: LocalDate,
    val calories: Double,
    val protein: Double,
    val carbohydrates: Double,
    val fat: Double,
)

data class ProgressUiState(
    val range: ProgressRange = ProgressRange.THIRTY_DAYS,
    val weights: List<WeightPoint> = emptyList(),
    val nutrition: List<NutritionPoint> = emptyList(),
    val startingWeightKg: Double? = null,
    val targetWeightKg: Double? = null,
    val loggingDays: Int = 0,
    val totalDays: Int = 30,
    /**
     * The first day of [range].
     *
     * Carried because the activity strip has to know where the window *starts*: the first logged
     * day is not the same date, and a strip anchored on the wrong day misplaces every column.
     */
    val rangeStart: LocalDate = LocalDate.now(),
    /**
     * Consecutive logged days across the whole log, not across [range].
     *
     * Kept as the plain number the milestone maths reads, so the screen can show the real streak
     * and the 30-day cycle it currently sits in at the same time.
     */
    val streakDays: Int = 0,
) {
    /** The milestone view of [streakDays]; derived so no caller can hold a stale copy. */
    val milestone: StreakMilestone get() = streakMilestone(streakDays)
}

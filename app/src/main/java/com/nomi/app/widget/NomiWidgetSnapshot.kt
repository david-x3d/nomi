package com.nomi.app.widget

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Everything a Nomi home-screen widget renders, reduced to plain numbers so the math can be
 * unit-tested without Android. Targets are null when no nutrition plan exists yet.
 */
data class NomiWidgetSnapshot(
    val caloriesKcal: Double,
    val calorieTargetKcal: Double?,
    val proteinGrams: Double,
    val proteinTargetGrams: Double?,
    val carbohydrateGrams: Double,
    val carbohydrateTargetGrams: Double?,
    val fatGrams: Double,
    val fatTargetGrams: Double?,
) {
    /** True while the user has a plan; widgets show a setup hint before that. */
    val hasPlan: Boolean get() = calorieTargetKcal != null

    /**
     * Whether the day is over the calorie target. Over-target days stay visually calm: the
     * delta text switches wording, never to an alarming color.
     */
    val isOverCalorieTarget: Boolean
        get() = (calorieTargetKcal ?: 0.0) > 0.0 && caloriesKcal > calorieTargetKcal!!

    fun calorieFraction(): Float? = fraction(caloriesKcal, calorieTargetKcal)

    fun proteinFraction(): Float? = fraction(proteinGrams, proteinTargetGrams)

    fun carbohydrateFraction(): Float? = fraction(carbohydrateGrams, carbohydrateTargetGrams)

    fun fatFraction(): Float? = fraction(fatGrams, fatTargetGrams)

    companion object {
        val EMPTY = NomiWidgetSnapshot(
            caloriesKcal = 0.0,
            calorieTargetKcal = null,
            proteinGrams = 0.0,
            proteinTargetGrams = null,
            carbohydrateGrams = 0.0,
            carbohydrateTargetGrams = null,
            fatGrams = 0.0,
            fatTargetGrams = null,
        )

        internal const val PROGRESS_SCALE: Int = 10_000

        internal fun progressUnits(value: Double, target: Double?): Int? =
            fraction(value, target)?.let { (it * PROGRESS_SCALE).roundToInt() }

        private fun fraction(value: Double, target: Double?): Float? {
            if (target == null || target <= 0.0) return null
            return (value / target).toFloat().coerceIn(0f, 1f)
        }

        /** Locale-aware whole kilocalories, e.g. `1,240` or `1.240`. */
        fun formatKcal(kcal: Double, locale: Locale): String =
            NumberFormat.getIntegerInstance(locale).format(kcal.roundToInt())

        /** Locale-aware whole grams, e.g. `96`. */
        fun formatGrams(grams: Double, locale: Locale): String =
            NumberFormat.getIntegerInstance(locale).format(grams.roundToInt())

        /** Signed whole-kilocalorie distance from the target, always positive. */
        fun formatDelta(calories: Double, target: Double?, locale: Locale): String? {
            if (target == null || target <= 0.0) return null
            return formatKcal(kotlin.math.abs(calories - target), locale)
        }
    }
}

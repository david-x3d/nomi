package com.nomi.app.ui.today

import java.util.Locale
import kotlin.math.roundToInt

/** Keeps the estimate visibly approximate and avoids decimals that the input data cannot support. */
internal fun estimatedStepCaloriesText(kilocalories: Double, locale: Locale): String {
    val value = estimatedStepCaloriesValue(kilocalories, locale)
    val approximate = kilocalories >= STEP_CALORIE_DISPLAY_INCREMENT
    return if (approximate) "≈ $value kcal" else "$value kcal"
}

/**
 * The bare figure for the calorie pill in the action row: no unit and no approximation sign.
 *
 * The pill writes "kcal" once for both numbers, because spelling the unit out twice pushed the
 * burned figure past the edge of the pill and it was the number that got clipped away. The
 * five-kcal rounding still applies, and the pill's icon still says the value is read from steps;
 * the fuller "≈ 250 kcal from steps" wording lives in the goals sheet, which has room for it.
 */
internal fun estimatedStepCaloriesValue(kilocalories: Double, locale: Locale): String {
    require(kilocalories.isFinite() && kilocalories >= 0.0) {
        "Estimated step calories must be finite and non-negative."
    }
    if (kilocalories == 0.0) return "0"
    if (kilocalories < STEP_CALORIE_DISPLAY_INCREMENT) return "< 5"
    val rounded = (kilocalories / STEP_CALORIE_DISPLAY_INCREMENT).roundToInt() *
        STEP_CALORIE_DISPLAY_INCREMENT.toInt()
    return String.format(locale, "%,d", rounded)
}

private const val STEP_CALORIE_DISPLAY_INCREMENT = 5.0

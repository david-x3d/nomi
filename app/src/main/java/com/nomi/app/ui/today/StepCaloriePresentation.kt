package com.nomi.app.ui.today

import java.util.Locale
import kotlin.math.roundToInt

/** Keeps the estimate visibly approximate and avoids decimals that the input data cannot support. */
internal fun estimatedStepCaloriesText(kilocalories: Double, locale: Locale): String {
    require(kilocalories.isFinite() && kilocalories >= 0.0) {
        "Estimated step calories must be finite and non-negative."
    }
    if (kilocalories == 0.0) return "0 kcal"
    if (kilocalories < STEP_CALORIE_DISPLAY_INCREMENT) return "< 5 kcal"
    val rounded = (kilocalories / STEP_CALORIE_DISPLAY_INCREMENT).roundToInt() *
        STEP_CALORIE_DISPLAY_INCREMENT.toInt()
    return "≈ ${String.format(locale, "%,d", rounded)} kcal"
}

private const val STEP_CALORIE_DISPLAY_INCREMENT = 5.0

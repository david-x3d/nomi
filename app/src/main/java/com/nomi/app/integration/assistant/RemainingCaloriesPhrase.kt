package com.nomi.app.integration.assistant

import com.nomi.app.ui.localization.NomiLanguage
import com.nomi.app.ui.localization.NomiTranslations
import com.nomi.app.widget.NomiWidgetSnapshot
import kotlin.math.abs
import kotlin.math.roundToInt

object RemainingCaloriesPhrase {
    const val LEFT_TODAY: String = "{0} kcal left today"
    const val OVER_TARGET: String = "{0} kcal over target"

    fun templateKey(consumedKcal: Double, targetKcal: Double): String =
        if (consumedKcal > targetKcal) OVER_TARGET else LEFT_TODAY

    fun deltaKcal(consumedKcal: Double, targetKcal: Double): Int =
        abs(targetKcal - consumedKcal).roundToInt()

    fun spoken(consumedKcal: Double, targetKcal: Double, language: NomiLanguage): String {
        val formatted = NomiWidgetSnapshot.formatKcal(
            deltaKcal(consumedKcal, targetKcal).toDouble(),
            language.locale,
        )
        return NomiTranslations.format(templateKey(consumedKcal, targetKcal), language, formatted)
    }
}

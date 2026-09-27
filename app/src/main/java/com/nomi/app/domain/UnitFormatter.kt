package com.nomi.app.domain

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Converts between the unit a person reads and the unit Nomi stores.
 *
 * Storage is always metric - kilograms, centimetres, grams, millilitres - and this is the only
 * place that knows otherwise. The reason it exists is a verified defect: `weightUnit` was written
 * by the Settings screen, echoed back by it, and carried in every backup, but nothing ever read
 * it. The weight field still said "kg" while an Imperial user was entering pounds, so a real
 * 180 lb weigh-in was stored as **180 kg**, which then fed BMR, the calorie target and every step
 * estimate. A setting that is displayed but inert is worse than no setting, because it looks
 * like it is working.
 *
 * Rounding is deliberately at one decimal place: Nomi stores weight to the tenth of a kilogram
 * (and to a hundredth of a pound) already, and rounding a value twice is how a 82.7 kg person
 * slowly becomes 82.6 and then 82.5.
 */
object UnitFormatter {

    internal const val POUNDS_PER_KILOGRAM = 2.2046226218487757
    internal const val CENTIMETERS_PER_INCH = 2.54

    fun kilogramsToPounds(kilograms: Double): Double = kilograms * POUNDS_PER_KILOGRAM

    fun poundsToKilograms(pounds: Double): Double = pounds / POUNDS_PER_KILOGRAM

    fun centimetersToInches(centimeters: Double): Double = centimeters / CENTIMETERS_PER_INCH

    fun inchesToCentimeters(inches: Double): Double = inches * CENTIMETERS_PER_INCH

    /**
     * A weight as the user should read it, with its unit.
     *
     * The number is formatted for [locale] because this is the one place a weight is rendered
     * from a stored value; `1,5` for a German reader and `1.5` for an English one.
     */
    fun formatWeight(kilograms: Double, metric: Boolean, locale: Locale): String {
        val value = if (metric) kilograms else kilogramsToPounds(kilograms)
        return "${formatNumber(value, metric, locale)} ${weightUnit(metric)}"
    }

    /** The same number without a unit, for a field that supplies its own suffix. */
    fun weightValue(kilograms: Double, metric: Boolean, locale: Locale): Double =
        if (metric) kilograms else kilogramsToPounds(kilograms)

    fun formatHeight(centimeters: Double, metric: Boolean, locale: Locale): String {
        if (metric) return "${formatNumber(centimeters, true, locale)} cm"
        val totalInches = centimetersToInches(centimeters)
        val feet = (totalInches / 12).toInt()
        val inches = totalInches - feet * 12
        return "$feet′ ${formatNumber(inches, true, locale)}″"
    }

    fun weightUnit(metric: Boolean): String = if (metric) "kg" else "lb"

    fun heightUnit(metric: Boolean): String = if (metric) "cm" else "ft/in"

    /**
     * Rounds for display without losing the precision a person expects to see again.
     *
     * Metric weight is stored to 0.1 kg, so it is shown to 0.1 kg; pounds to 0.01 lb, which is
     * what a scale actually reports.
     */
    fun formatNumber(value: Double, metric: Boolean, locale: Locale): String {
        val decimals = if (metric) 1 else 2
        val symbols = DecimalFormatSymbols(locale)
        val pattern = if (decimals == 0) "#" else "#,##0." + "0".repeat(decimals)
        val format = DecimalFormat(pattern, symbols)
        format.roundingMode = java.math.RoundingMode.HALF_UP
        val formatted = format.format(value)
        // Trim a trailing separator a format with zero decimals can leave behind, but only when
        // the locale's separator is a dot: in a comma locale it is a real character.
        return if (symbols.decimalSeparator == '.') formatted.trimEnd('.') else formatted
    }

    /**
     * Parses a weight the user typed, in whichever unit the field was showing, into kilograms.
     *
     * Returns `null` rather than a wrong number: silently storing pounds as kilograms is the
     * exact failure this class exists to prevent, so an unparseable field must not produce a
     * plausible-looking value.
     */
    fun parseWeightToKilograms(input: String, metric: Boolean): Double? {
        val value = DecimalInput.parseOrNull(input) ?: return null
        val kilograms = if (metric) value else poundsToKilograms(value)
        return kilograms.takeIf { it.isFinite() && it > 0.0 }
    }

    /** Parses a height typed in the unit the field was showing, into centimetres. */
    fun parseHeightToCentimeters(input: String, metric: Boolean): Double? {
        val value = DecimalInput.parseOrNull(input) ?: return null
        val centimeters = if (metric) value else inchesToCentimeters(value)
        return centimeters.takeIf { it.isFinite() && it > 0.0 }
    }

    /** True when a parsed value is within the range a weighing scale can produce. */
    fun isPlausibleWeightKilograms(kilograms: Double): Boolean =
        kilograms.isFinite() && kilograms in 20.0..500.0

    /**
     * Parses a feet/inches height, as the imperial field presents it.
     *
     * Both parts are optional so "5" and "5'7" and "5'7\"" are all accepted.
     */
    fun parseFeetInchesToCentimeters(feet: String, inches: String): Double? {
        val feetValue = feet.trim().takeIf { it.isNotEmpty() }?.let { (it.toDoubleOrNull() ?: return null) }
        val inchesValue = inches.trim().takeIf { it.isNotEmpty() }
            ?.let { (it.toDoubleOrNull() ?: return null) }
        if (feetValue == null && inchesValue == null) return null
        if (feetValue != null && (feetValue < 0 || feetValue > 9)) return null
        if (inchesValue != null && (inchesValue < 0 || inchesValue >= 12)) return null
        val centimetres = (feetValue ?: 0.0) * 12.0 * CENTIMETERS_PER_INCH + (inchesValue ?: 0.0) * CENTIMETERS_PER_INCH
        return centimetres.takeIf { it > 0.0 }
    }

    /** Composes a feet/inches pair from centimetres, for prefilling the imperial field. */
    fun centimetersToFeetAndInches(centimeters: Double): Pair<Int, Double> {
        val totalInches = centimetersToInches(centimeters)
        val feet = (totalInches / 12).toInt().coerceAtLeast(0)
        val inches = totalInches - feet * 12
        return feet to (inches * 10).roundToInt() / 10.0
    }

    /** Absolute difference, used where a value is compared rather than displayed. */
    fun delta(a: Double, b: Double): Double = abs(a - b)
}

package com.nomi.app.domain

object UnitConverter {
    enum class MassUnit(val grams: Double) {
        GRAM(1.0),
        KILOGRAM(1_000.0),
        OUNCE(28.349523125),
        POUND(453.59237),
    }

    enum class VolumeUnit(val milliliters: Double) {
        MILLILITER(1.0),
        LITER(1_000.0),
        TEASPOON(4.92892159375),
        TABLESPOON(14.78676478125),
        CUP(236.5882365),
    }

    data class FeetAndInches(val feet: Int, val inches: Double) {
        init {
            require(feet >= 0) { "Feet cannot be negative." }
            require(inches.isFinite() && inches >= 0.0 && inches < INCHES_PER_FOOT) {
                "Inches must be in [0, 12)."
            }
        }
    }

    fun kilogramsToPounds(kilograms: Double): Double =
        requireNonNegative(kilograms, "Kilograms") * POUNDS_PER_KILOGRAM

    fun poundsToKilograms(pounds: Double): Double =
        requireNonNegative(pounds, "Pounds") / POUNDS_PER_KILOGRAM

    fun kgToLb(kilograms: Double): Double = kilogramsToPounds(kilograms)

    fun lbToKg(pounds: Double): Double = poundsToKilograms(pounds)

    fun centimetersToInches(centimeters: Double): Double =
        requireNonNegative(centimeters, "Centimeters") / CENTIMETERS_PER_INCH

    fun inchesToCentimeters(inches: Double): Double =
        requireNonNegative(inches, "Inches") * CENTIMETERS_PER_INCH

    fun cmToInches(centimeters: Double): Double = centimetersToInches(centimeters)

    fun inchesToCm(inches: Double): Double = inchesToCentimeters(inches)

    fun centimetersToFeetAndInches(centimeters: Double): FeetAndInches {
        val totalInches = centimetersToInches(centimeters)
        val feet = (totalInches / INCHES_PER_FOOT).toInt()
        return FeetAndInches(feet, totalInches - feet * INCHES_PER_FOOT)
    }

    fun cmToFeetAndInches(centimeters: Double): FeetAndInches =
        centimetersToFeetAndInches(centimeters)

    fun feetAndInchesToCentimeters(feet: Int, inches: Double): Double {
        require(feet >= 0) { "Feet cannot be negative." }
        require(inches.isFinite() && inches >= 0.0 && inches < INCHES_PER_FOOT) {
            "Inches must be in [0, 12)."
        }
        return inchesToCentimeters(feet * INCHES_PER_FOOT + inches)
    }

    fun feetAndInchesToCm(feet: Int, inches: Double): Double =
        feetAndInchesToCentimeters(feet, inches)

    fun convertMass(value: Double, from: MassUnit, to: MassUnit): Double {
        val grams = requireNonNegative(value, "Mass") * from.grams
        return grams / to.grams
    }

    fun convertVolume(value: Double, from: VolumeUnit, to: VolumeUnit): Double {
        val milliliters = requireNonNegative(value, "Volume") * from.milliliters
        return milliliters / to.milliliters
    }

    private fun requireNonNegative(value: Double, name: String): Double {
        require(value.isFinite() && value >= 0.0) { "$name must be finite and non-negative." }
        return value
    }

    const val POUNDS_PER_KILOGRAM = 2.2046226218487757
    const val CENTIMETERS_PER_INCH = 2.54
    private const val INCHES_PER_FOOT = 12.0
}

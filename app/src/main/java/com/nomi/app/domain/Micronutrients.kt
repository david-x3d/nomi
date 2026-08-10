package com.nomi.app.domain

/**
 * The nutrients Nomi can track beyond the three macros.
 *
 * Every one of these is already stored per logged item, so enabling one is a display and
 * target decision, never a data migration. The order here is the order they appear in
 * settings and on Today.
 */
enum class Micronutrient(
    val storageUnit: MicronutrientUnit,
    /**
     * A general adult reference intake, used as the starting target and as the number shown
     * when explaining what a day's worth looks like. These follow WHO/EFSA population guidance
     * for a roughly 2,000 kcal day; anyone with a reason to differ can edit the target.
     */
    val referenceDailyAmount: Double,
    /** Whether staying under the reference is the healthy direction, rather than reaching it. */
    val isLimit: Boolean,
) {
    FIBER(MicronutrientUnit.GRAMS, referenceDailyAmount = 30.0, isLimit = false),
    SUGAR(MicronutrientUnit.GRAMS, referenceDailyAmount = 25.0, isLimit = true),
    SATURATED_FAT(MicronutrientUnit.GRAMS, referenceDailyAmount = 20.0, isLimit = true),
    SODIUM(MicronutrientUnit.MILLIGRAMS, referenceDailyAmount = 2_000.0, isLimit = true),
    ;

    /** Reads this nutrient out of a nutrition total, in [storageUnit]. */
    fun amountIn(nutrition: Nutrition): Double = when (this) {
        FIBER -> nutrition.fiberGrams
        SUGAR -> nutrition.sugarGrams
        SATURATED_FAT -> nutrition.saturatedFatGrams
        SODIUM -> nutrition.sodiumMilligrams
    }

    /** The largest target the settings editor will accept, keeping typos out of storage. */
    val maximumTarget: Double
        get() = referenceDailyAmount * 20.0
}

enum class MicronutrientUnit(val suffix: String) {
    GRAMS("g"),
    MILLIGRAMS("mg"),
}

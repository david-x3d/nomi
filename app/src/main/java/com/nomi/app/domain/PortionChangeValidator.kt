package com.nomi.app.domain

import java.util.Locale
import kotlin.math.abs

enum class PortionValidationIssue {
    INVALID_ORIGINAL_QUANTITY,
    INVALID_NEW_QUANTITY,
    INCOMPATIBLE_UNITS,
    INVALID_REPORTED_MULTIPLIER,
    INCONSISTENT_MULTIPLIER,
    INCONSISTENT_NUTRITION,
    EXTREME_MULTIPLIER,
}

data class PortionChangeProposal(
    val originalQuantity: Double,
    val originalUnit: String,
    val originalNutrition: Nutrition,
    val newQuantity: Double,
    val newUnit: String = originalUnit,
    val reportedMultiplier: Double? = null,
    val proposedNutrition: Nutrition? = null,
)

data class PortionValidationResult(
    val isValid: Boolean,
    val multiplier: Double?,
    val expectedNutrition: Nutrition?,
    val issues: Set<PortionValidationIssue>,
)

object PortionChangeValidator {
    fun validate(proposal: PortionChangeProposal): PortionValidationResult = validate(
        currentNutrition = proposal.originalNutrition,
        originalQuantity = proposal.originalQuantity,
        newQuantity = proposal.newQuantity,
        originalUnit = proposal.originalUnit,
        newUnit = proposal.newUnit,
        reportedMultiplier = proposal.reportedMultiplier,
        proposedNutrition = proposal.proposedNutrition,
    )

    fun validate(
        currentNutrition: Nutrition,
        originalQuantity: Double,
        newQuantity: Double,
        originalUnit: String,
        newUnit: String = originalUnit,
        reportedMultiplier: Double? = null,
        proposedNutrition: Nutrition? = null,
    ): PortionValidationResult {
        val issues = linkedSetOf<PortionValidationIssue>()
        if (!originalQuantity.isFinite() || originalQuantity <= 0.0) {
            issues += PortionValidationIssue.INVALID_ORIGINAL_QUANTITY
        }
        if (!newQuantity.isFinite() || newQuantity <= 0.0) {
            issues += PortionValidationIssue.INVALID_NEW_QUANTITY
        }

        val multiplier = if (issues.none {
                it == PortionValidationIssue.INVALID_ORIGINAL_QUANTITY ||
                    it == PortionValidationIssue.INVALID_NEW_QUANTITY
            }
        ) {
            calculateMultiplier(originalQuantity, originalUnit, newQuantity, newUnit)
                ?: run {
                    issues += PortionValidationIssue.INCOMPATIBLE_UNITS
                    null
                }
        } else {
            null
        }

        if (reportedMultiplier != null) {
            if (!reportedMultiplier.isFinite() || reportedMultiplier <= 0.0) {
                issues += PortionValidationIssue.INVALID_REPORTED_MULTIPLIER
            } else if (multiplier != null && !approximatelyEqual(
                    reportedMultiplier,
                    multiplier,
                    relativeTolerance = MULTIPLIER_RELATIVE_TOLERANCE,
                    absoluteTolerance = MULTIPLIER_ABSOLUTE_TOLERANCE,
                )
            ) {
                issues += PortionValidationIssue.INCONSISTENT_MULTIPLIER
            }
        }

        if (multiplier != null && (multiplier < MIN_PLAUSIBLE_MULTIPLIER ||
                multiplier > MAX_PLAUSIBLE_MULTIPLIER)
        ) {
            issues += PortionValidationIssue.EXTREME_MULTIPLIER
        }

        val expectedNutrition = multiplier?.let { NutritionScaler.scale(currentNutrition, it) }
        if (proposedNutrition != null && expectedNutrition != null &&
            !nutritionApproximatelyEqual(expectedNutrition, proposedNutrition)
        ) {
            issues += PortionValidationIssue.INCONSISTENT_NUTRITION
        }

        return PortionValidationResult(
            isValid = issues.isEmpty(),
            multiplier = multiplier,
            expectedNutrition = expectedNutrition,
            issues = issues,
        )
    }

    fun calculateMultiplier(
        originalQuantity: Double,
        originalUnit: String,
        newQuantity: Double,
        newUnit: String = originalUnit,
    ): Double? {
        if (!originalQuantity.isFinite() || originalQuantity <= 0.0 ||
            !newQuantity.isFinite() || newQuantity <= 0.0
        ) return null

        val original = canonicalQuantity(originalQuantity, originalUnit)
        val updated = canonicalQuantity(newQuantity, newUnit)
        if (original.dimension != updated.dimension) return null
        if (original.dimension.startsWith("unknown:") && original.dimension != updated.dimension) return null
        return updated.value / original.value
    }

    private fun nutritionApproximatelyEqual(expected: Nutrition, actual: Nutrition): Boolean =
        approximatelyEqual(expected.caloriesKcal, actual.caloriesKcal, 0.05, 2.0) &&
            approximatelyEqual(expected.proteinGrams, actual.proteinGrams, 0.05, 0.5) &&
            approximatelyEqual(expected.carbsGrams, actual.carbsGrams, 0.05, 0.5) &&
            approximatelyEqual(expected.fatGrams, actual.fatGrams, 0.05, 0.5) &&
            approximatelyEqual(expected.fiberGrams, actual.fiberGrams, 0.05, 0.5)

    private fun approximatelyEqual(
        first: Double,
        second: Double,
        relativeTolerance: Double,
        absoluteTolerance: Double,
    ): Boolean {
        val difference = abs(first - second)
        return difference <= absoluteTolerance ||
            difference <= maxOf(abs(first), abs(second)) * relativeTolerance
    }

    private data class CanonicalQuantity(val value: Double, val dimension: String)

    private fun canonicalQuantity(quantity: Double, rawUnit: String): CanonicalQuantity {
        val unit = normalizeUnit(rawUnit)
        return when (unit) {
            "mg", "milligram", "milligrams", "milligramm" ->
                CanonicalQuantity(quantity * 0.001, "mass")
            "g", "gram", "grams", "gramm" -> CanonicalQuantity(quantity, "mass")
            "kg", "kilogram", "kilograms", "kilogramm" ->
                CanonicalQuantity(quantity * 1_000.0, "mass")
            "oz", "ounce", "ounces" -> CanonicalQuantity(quantity * 28.349523125, "mass")
            "lb", "lbs", "pound", "pounds" -> CanonicalQuantity(quantity * 453.59237, "mass")
            "ml", "milliliter", "milliliters", "millilitre", "millilitres" ->
                CanonicalQuantity(quantity, "volume")
            "l", "liter", "liters", "litre", "litres" ->
                CanonicalQuantity(quantity * 1_000.0, "volume")
            "tsp", "teaspoon", "teaspoons", "tl", "teeloffel", "teeloeffel" ->
                CanonicalQuantity(quantity * 5.0, "volume")
            "tbsp", "tbs", "tablespoon", "tablespoons", "el", "essloffel", "essloeffel" ->
                CanonicalQuantity(quantity * 15.0, "volume")
            "loffel", "loeffel", "spoon", "spoons" ->
                CanonicalQuantity(quantity * 15.0, "volume")
            "cup", "cups" -> CanonicalQuantity(quantity * 236.5882365, "volume")
            "piece", "pieces", "pc", "pcs" -> CanonicalQuantity(quantity, "count:piece")
            "slice", "slices" -> CanonicalQuantity(quantity, "count:slice")
            "serving", "servings" -> CanonicalQuantity(quantity, "count:serving")
            else -> CanonicalQuantity(quantity, "unknown:$unit")
        }
    }

    private fun normalizeUnit(rawUnit: String): String = rawUnit
        .trim()
        .lowercase(Locale.ROOT)
        .replace('\u00e4', 'a')
        .replace('\u00f6', 'o')
        .replace('\u00fc', 'u')
        .replace("\u00df", "ss")
        .replace(Regex("[._-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private const val MULTIPLIER_RELATIVE_TOLERANCE = 0.02
    private const val MULTIPLIER_ABSOLUTE_TOLERANCE = 0.01
    private const val MIN_PLAUSIBLE_MULTIPLIER = 0.05
    private const val MAX_PLAUSIBLE_MULTIPLIER = 20.0
}

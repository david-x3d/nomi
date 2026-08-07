package com.nomi.app.ai.validation

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import com.nomi.app.ai.model.ServingSizeValidation
import java.util.Locale
import kotlin.math.abs

/**
 * Converts source-serving nutrition into the exact amount the user logged.
 *
 * AI values are treated as nutrition for [AnalyzedFoodItem.sourceServingQuantity], never as
 * nutrition for the logged amount. The calculation is deliberately performed in app code:
 * source serving -> per 100 base units -> logged amount.
 */
object ServingNutritionNormalizer {
    private const val US_FLUID_OUNCE_ML = 29.5735295625
    private const val OUNCE_GRAMS = 28.349523125
    private const val MATCH_TOLERANCE = 1e-6
    private const val NUTRIENT_TOLERANCE = 1e-7

    private const val MEDIUM_APPLE_GRAMS_PER_PIECE = 182.0
    private const val MEDIUM_APPLE_MASS_ASSUMPTION =
        "Estimated 182 g per medium apple because no exact piece weight was provided."

    fun normalize(intent: ParsedFoodIntent, unnormalized: FoodAnalysis): FoodAnalysis {
        val cleanRaw = unnormalized.copy(
            items = unnormalized.items.map {
                // Provider output never gets to self-assert that serving validation already ran.
                it.copy(servingValidation = null, requiresServingValidation = false)
            },
        )
        AiResponseValidator.validate(cleanRaw)
        if (intent.items.isNotEmpty() && intent.items.size != cleanRaw.items.size) {
            throw AiValidationException(
                "Nutrition research must return exactly one result for each logged item",
            )
        }

        val normalizedItems = cleanRaw.items.mapIndexed { index, item ->
            normalizeItem(item, intent.items.getOrNull(index))
        }
        return AiResponseValidator.validate(cleanRaw.copy(items = normalizedItems))
    }

    /**
     * Normalizes one exact source serving to an arbitrary user-selected target amount.
     * Useful for deterministic barcode/catalog flows once their amount picker has a value.
     */
    fun normalizeSourceServingTo(
        sourceServingItem: AnalyzedFoodItem,
        loggedQuantity: Double,
        loggedUnit: String,
        loggedGramsEquivalent: Double? = null,
    ): AnalyzedFoodItem {
        val prepared = sourceServingItem.copy(
            quantity = loggedQuantity,
            unit = loggedUnit,
            gramsEquivalent = loggedGramsEquivalent,
            servingValidation = null,
            requiresServingValidation = false,
        )
        return normalize(
            intent = ParsedFoodIntent(
                originalText = "Amount selected by user",
                items = listOf(
                    ParsedFoodItem(
                        name = prepared.name,
                        brand = prepared.brand,
                        quantity = loggedQuantity,
                        unit = loggedUnit,
                        gramsEquivalent = loggedGramsEquivalent,
                    ),
                ),
            ),
            unnormalized = FoodAnalysis(items = listOf(prepared)),
        ).items.single()
    }

    /** Re-scales an already validated preview after a user-approved portion edit. */
    fun rescaleValidatedItemTo(
        item: AnalyzedFoodItem,
        loggedQuantity: Double,
        loggedUnit: String,
        loggedGramsEquivalent: Double? = null,
    ): AnalyzedFoodItem {
        if (!item.requiresServingValidation) {
            throw AiValidationException("Only a source-normalized item can be re-scaled this way")
        }
        validateBeforeSave(FoodAnalysis(items = listOf(item)))
        val previous = requireNotNull(item.servingValidation)
        val resolvedLoggedGramsEquivalent = loggedGramsEquivalent
            ?: inferPieceGramsEquivalent(item, loggedQuantity, loggedUnit)
        val (sourceMeasure, loggedMeasure) = reconcileServingMeasures(
            sourceQuantity = previous.sourceQuantity,
            sourceUnit = previous.sourceUnit,
            sourceGramsEquivalent = item.sourceServingGramsEquivalent,
            loggedQuantity = loggedQuantity,
            loggedUnit = loggedUnit,
            loggedGramsEquivalent = resolvedLoggedGramsEquivalent,
        )
        if (sourceMeasure.dimension.storageName != previous.dimension) {
            throw AiValidationException("A portion edit cannot change the validated serving basis")
        }
        val loggedFactor = loggedMeasure.baseAmount / 100.0
        val updatedValidation = previous.copy(
            loggedQuantity = loggedQuantity,
            loggedUnit = loggedUnit,
            loggedBaseAmount = loggedMeasure.baseAmount,
            scaleFactor = loggedMeasure.baseAmount / sourceMeasure.baseAmount,
        )
        return item.copy(
            quantity = loggedQuantity,
            unit = loggedUnit,
            gramsEquivalent = resolvedLoggedGramsEquivalent,
            calories = previous.caloriesPer100 * loggedFactor,
            proteinGrams = previous.proteinGramsPer100 * loggedFactor,
            carbohydrateGrams = previous.carbohydrateGramsPer100 * loggedFactor,
            fatGrams = previous.fatGramsPer100 * loggedFactor,
            fiberGrams = previous.fiberGramsPer100?.times(loggedFactor),
            assumptions = (item.assumptions +
                "Validated serving changed to ${clean(loggedQuantity)} $loggedUnit.").takeLast(12),
            servingValidation = updatedValidation,
        ).also {
            validateBeforeSave(FoodAnalysis(items = listOf(it)))
        }
    }

    /** Re-checks the exact source and logged bases immediately before database persistence. */
    fun validateBeforeSave(analysis: FoodAnalysis): FoodAnalysis {
        AiResponseValidator.validate(analysis)
        analysis.items.forEachIndexed { index, item ->
            if (!item.requiresServingValidation) return@forEachIndexed
            val validation = item.servingValidation
                ?: throw AiValidationException(
                    "Item ${index + 1} has no validated source serving; research it again",
                )
            val sourceQuantity = item.sourceServingQuantity
                ?: throw AiValidationException("Item ${index + 1} is missing its source serving amount")
            val sourceUnit = item.sourceServingUnit
                ?: throw AiValidationException("Item ${index + 1} is missing its source serving unit")
            val (sourceMeasure, loggedMeasure) = reconcileServingMeasures(
                sourceQuantity = sourceQuantity,
                sourceUnit = sourceUnit,
                sourceGramsEquivalent = item.sourceServingGramsEquivalent,
                loggedQuantity = item.quantity,
                loggedUnit = item.unit,
                loggedGramsEquivalent = item.gramsEquivalent,
            )

            requireClose(sourceQuantity, validation.sourceQuantity, "source serving amount changed")
            requireEquivalentUnits(
                sourceQuantity,
                sourceUnit,
                validation.sourceQuantity,
                validation.sourceUnit,
                "source serving changed",
            )
            requireEquivalentUnits(
                item.quantity,
                item.unit,
                validation.loggedQuantity,
                validation.loggedUnit,
                "logged amount changed",
            )
            requireClose(sourceMeasure.baseAmount, validation.sourceBaseAmount, "source basis changed")
            requireClose(loggedMeasure.baseAmount, validation.loggedBaseAmount, "logged basis changed")
            if (validation.dimension != sourceMeasure.dimension.storageName) {
                throw AiValidationException("Source and logged serving dimensions no longer match")
            }

            val expectedScale = validation.loggedBaseAmount / validation.sourceBaseAmount
            requireClose(expectedScale, validation.scaleFactor, "serving scale is inconsistent")
            requireNutrient(
                item.calories,
                validation.caloriesPer100 * validation.loggedBaseAmount / 100.0,
                "calories",
            )
            requireNutrient(
                item.proteinGrams,
                validation.proteinGramsPer100 * validation.loggedBaseAmount / 100.0,
                "protein",
            )
            requireNutrient(
                item.carbohydrateGrams,
                validation.carbohydrateGramsPer100 * validation.loggedBaseAmount / 100.0,
                "carbohydrates",
            )
            requireNutrient(
                item.fatGrams,
                validation.fatGramsPer100 * validation.loggedBaseAmount / 100.0,
                "fat",
            )
            when {
                item.fiberGrams == null && validation.fiberGramsPer100 == null -> Unit
                item.fiberGrams == null || validation.fiberGramsPer100 == null ->
                    throw AiValidationException("fiber basis changed before saving")
                else -> requireNutrient(
                    item.fiberGrams,
                    validation.fiberGramsPer100 * validation.loggedBaseAmount / 100.0,
                    "fiber",
                )
            }
        }
        return analysis
    }

    private fun normalizeItem(item: AnalyzedFoodItem, requested: ParsedFoodItem?): AnalyzedFoodItem {
        val loggedQuantity = requested?.quantity ?: item.quantity
        val loggedUnit = requested?.unit?.takeIf(String::isNotBlank) ?: item.unit
        requireFinitePositive(loggedQuantity, "logged amount")
        if (requested?.quantity != null && !requested.unit.isNullOrBlank()) {
            requireEquivalentUnits(
                item.quantity,
                item.unit,
                requested.quantity,
                requested.unit,
                "AI result does not match the amount the user logged",
            )
        }

        val sourceQuantity = item.sourceServingQuantity
            ?: throw AiValidationException("Nutrition source serving amount is missing")
        val sourceUnit = item.sourceServingUnit?.takeIf(String::isNotBlank)
            ?: throw AiValidationException("Nutrition source serving unit is missing")
        requireFinitePositive(sourceQuantity, "source serving amount")
        item.sourceServingGramsEquivalent?.let {
            requireFinitePositive(it, "source serving grams")
        }

        val suppliedLoggedGramsEquivalent = requested?.gramsEquivalent ?: item.gramsEquivalent
        val estimatedAppleGramsEquivalent = if (suppliedLoggedGramsEquivalent == null) {
            estimateGenericAppleGramsEquivalent(
                item = item,
                requested = requested,
                loggedQuantity = loggedQuantity,
                loggedUnit = loggedUnit,
                sourceUnit = sourceUnit,
            )
        } else {
            null
        }
        val loggedGramsEquivalent = suppliedLoggedGramsEquivalent ?: estimatedAppleGramsEquivalent
        val (sourceMeasure, loggedMeasure) = reconcileServingMeasures(
            sourceQuantity = sourceQuantity,
            sourceUnit = sourceUnit,
            sourceGramsEquivalent = item.sourceServingGramsEquivalent,
            loggedQuantity = loggedQuantity,
            loggedUnit = loggedUnit,
            loggedGramsEquivalent = loggedGramsEquivalent,
        )
        val per100Factor = 100.0 / sourceMeasure.baseAmount
        val loggedFactor = loggedMeasure.baseAmount / 100.0
        val validation = ServingSizeValidation(
            dimension = sourceMeasure.dimension.storageName,
            sourceQuantity = sourceQuantity,
            sourceUnit = sourceUnit,
            sourceBaseAmount = sourceMeasure.baseAmount,
            loggedQuantity = loggedQuantity,
            loggedUnit = loggedUnit,
            loggedBaseAmount = loggedMeasure.baseAmount,
            scaleFactor = loggedMeasure.baseAmount / sourceMeasure.baseAmount,
            caloriesPer100 = item.calories * per100Factor,
            proteinGramsPer100 = item.proteinGrams * per100Factor,
            carbohydrateGramsPer100 = item.carbohydrateGrams * per100Factor,
            fatGramsPer100 = item.fatGrams * per100Factor,
            fiberGramsPer100 = item.fiberGrams?.times(per100Factor),
        )
        return item.copy(
            quantity = loggedQuantity,
            unit = loggedUnit,
            gramsEquivalent = loggedGramsEquivalent,
            calories = validation.caloriesPer100 * loggedFactor,
            proteinGrams = validation.proteinGramsPer100 * loggedFactor,
            carbohydrateGrams = validation.carbohydrateGramsPer100 * loggedFactor,
            fatGrams = validation.fatGramsPer100 * loggedFactor,
            fiberGrams = validation.fiberGramsPer100?.times(loggedFactor),
            isEstimate = item.isEstimate || estimatedAppleGramsEquivalent != null,
            assumptions = (
                item.assumptions + listOfNotNull(
                    MEDIUM_APPLE_MASS_ASSUMPTION.takeIf {
                        estimatedAppleGramsEquivalent != null
                    },
                    "Source serving ${clean(sourceQuantity)} $sourceUnit normalized to per 100 " +
                        "and scaled to ${clean(loggedQuantity)} $loggedUnit.",
                )
            ).takeLast(12),
            servingValidation = validation,
            requiresServingValidation = true,
        )
    }

    /**
     * Narrow runtime fallback for a generic, unbranded apple researched per gram.
     *
     * The logged count remains authoritative: only its missing total mass is filled in. Every
     * other count-to-mass mismatch still reaches [reconcileServingMeasures] and is rejected.
     */
    private fun estimateGenericAppleGramsEquivalent(
        item: AnalyzedFoodItem,
        requested: ParsedFoodItem?,
        loggedQuantity: Double,
        loggedUnit: String,
        sourceUnit: String,
    ): Double? {
        val logged = measure(loggedQuantity, loggedUnit)
        val source = measure(1.0, sourceUnit)
        if (logged.dimension != Dimension.Piece || source.dimension != Dimension.Mass) return null
        if (!requested?.brand.isNullOrBlank() || !item.brand.isNullOrBlank()) return null

        val authoritativeName = requested?.name?.takeIf(String::isNotBlank) ?: item.name
        if (!isGenericAppleName(authoritativeName)) return null
        return logged.baseAmount * MEDIUM_APPLE_GRAMS_PER_PIECE
    }

    private fun isGenericAppleName(value: String): Boolean {
        val normalized = value
            .trim()
            .lowercase(Locale.ROOT)
            .replace('\u00e4', 'a')
            .replace(Regex("\\s+"), " ")
        return normalized in setOf("apple", "apples", "apfel")
    }

    private fun requireEquivalentUnits(
        firstQuantity: Double,
        firstUnit: String,
        secondQuantity: Double,
        secondUnit: String,
        message: String,
    ) {
        val first = measure(firstQuantity, firstUnit)
        val second = measure(secondQuantity, secondUnit)
        requireCompatible(first, second)
        requireClose(first.baseAmount, second.baseAmount, message)
    }

    private fun requireCompatible(first: Measure, second: Measure) {
        if (first.dimension != second.dimension) {
            throw AiValidationException(
                "Source serving '${first.originalUnit}' is not compatible with logged unit " +
                    "'${second.originalUnit}'",
            )
        }
    }

    /**
     * Places the source and logged serving on one arithmetic basis.
     *
     * Count- or spoon-to-mass conversion is permitted only when that exact serving already carries
     * a total gram equivalent. Spoons remain volume-compatible, and no average piece weight or
     * mass/volume density is invented here.
     */
    private fun reconcileServingMeasures(
        sourceQuantity: Double,
        sourceUnit: String,
        sourceGramsEquivalent: Double?,
        loggedQuantity: Double,
        loggedUnit: String,
        loggedGramsEquivalent: Double?,
    ): ServingMeasures {
        val source = measure(sourceQuantity, sourceUnit)
        val logged = measure(loggedQuantity, loggedUnit)
        if (source.dimension == logged.dimension) return ServingMeasures(source, logged)

        if (source.canBridgeToMass && logged.dimension == Dimension.Mass) {
            val sourceMass = sourceGramsEquivalent?.let {
                massEquivalent(it, source.originalUnit, "source serving grams")
            }
            if (sourceMass != null) return ServingMeasures(sourceMass, logged)
        }
        if (source.dimension == Dimension.Mass && logged.canBridgeToMass) {
            val loggedMass = loggedGramsEquivalent?.let {
                massEquivalent(it, logged.originalUnit, "logged serving grams")
            }
            if (loggedMass != null) return ServingMeasures(source, loggedMass)
        }

        requireCompatible(source, logged)
        return ServingMeasures(source, logged)
    }

    private fun massEquivalent(
        grams: Double,
        originalUnit: String,
        label: String,
    ): Measure {
        requireFinitePositive(grams, label)
        return Measure(Dimension.Mass, grams, originalUnit)
    }

    /**
     * Reuses a known total gram equivalent when a validated count is edited to another count.
     * The existing grams-per-piece ratio is the only inferred value; a missing weight stays null.
     */
    private fun inferPieceGramsEquivalent(
        item: AnalyzedFoodItem,
        loggedQuantity: Double,
        loggedUnit: String,
    ): Double? {
        val currentGrams = item.gramsEquivalent ?: return null
        val current = measure(item.quantity, item.unit)
        val updated = measure(loggedQuantity, loggedUnit)
        if (current.dimension != Dimension.Piece || updated.dimension != Dimension.Piece) return null
        requireFinitePositive(currentGrams, "logged serving grams")
        return currentGrams * updated.baseAmount / current.baseAmount
    }

    private fun isSpoonUnit(unit: String): Boolean = when (unit) {
        "tbsp", "tbs", "tablespoon", "tablespoons",
        "el", "essloffel", "essloeffel",
        "tsp", "teaspoon", "teaspoons",
        "tl", "teeloffel", "teeloeffel",
        -> true
        else -> false
    }

    private fun measure(quantity: Double, rawUnit: String): Measure {
        requireFinitePositive(quantity, "serving amount")
        val unit = normalizeUnit(rawUnit)
        if (unit.isEmpty()) throw AiValidationException("Serving unit is missing")
        val (dimension, factor) = when (unit) {
            "mg", "milligram", "milligrams", "milligramm" -> Dimension.Mass to 0.001
            "g", "gram", "grams", "gramm" -> Dimension.Mass to 1.0
            "kg", "kilogram", "kilograms", "kilogramm" -> Dimension.Mass to 1_000.0
            "oz", "ounce", "ounces" -> Dimension.Mass to OUNCE_GRAMS
            "tbsp", "tbs", "tablespoon", "tablespoons", "el", "essloffel", "essloeffel" -> Dimension.Volume to 15.0
            "tsp", "teaspoon", "teaspoons", "tl", "teeloffel", "teeloeffel" -> Dimension.Volume to 5.0
            "ml", "milliliter", "milliliters", "millilitre", "millilitres" ->
                Dimension.Volume to 1.0
            "cl", "centiliter", "centiliters", "centilitre", "centilitres" ->
                Dimension.Volume to 10.0
            "l", "liter", "liters", "litre", "litres" -> Dimension.Volume to 1_000.0
            "fl oz", "us fl oz", "floz", "fluid ounce", "fluid ounces" ->
                Dimension.Volume to US_FLUID_OUNCE_ML
            "piece", "pieces", "pc", "pcs", "each", "stuck", "stucke" ->
                Dimension.Piece to 1.0
            else -> Dimension.Custom(unit) to 1.0
        }
        return Measure(
            dimension = dimension,
            baseAmount = quantity * factor,
            originalUnit = rawUnit,
            canBridgeToMass = dimension == Dimension.Piece || isSpoonUnit(unit),
        )
    }

    private fun normalizeUnit(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        .replace("fl. oz.", "fl oz")
        .replace("fl. oz", "fl oz")
        .replace("fluid oz", "fl oz")
        .replace('\u00e4', 'a')
        .replace('\u00f6', 'o')
        .replace('\u00fc', 'u')
        .replace("\u00df", "ss")
        .replace(Regex("[._-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun requireFinitePositive(value: Double, label: String) {
        if (!value.isFinite() || value <= 0.0) {
            throw AiValidationException("$label must be finite and greater than zero")
        }
    }

    private fun requireClose(actual: Double, expected: Double, message: String) {
        val difference = abs(actual - expected) / maxOf(abs(actual), abs(expected), 1.0)
        if (!difference.isFinite() || difference > MATCH_TOLERANCE) {
            throw AiValidationException(message)
        }
    }

    private fun requireNutrient(actual: Double, expected: Double, name: String) {
        val difference = abs(actual - expected) / maxOf(abs(actual), abs(expected), 1.0)
        if (!difference.isFinite() || difference > NUTRIENT_TOLERANCE) {
            throw AiValidationException("$name no longer matches the validated logged amount")
        }
    }

    private fun clean(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private sealed interface Dimension {
        val storageName: String

        data object Mass : Dimension { override val storageName = "mass_g" }
        data object Volume : Dimension { override val storageName = "volume_ml" }
        data object Piece : Dimension { override val storageName = "piece" }
        data class Custom(val unit: String) : Dimension { override val storageName = "custom:$unit" }
    }

    private data class Measure(
        val dimension: Dimension,
        val baseAmount: Double,
        val originalUnit: String,
        val canBridgeToMass: Boolean = dimension == Dimension.Piece,
    )

    private data class ServingMeasures(
        val source: Measure,
        val logged: Measure,
    )
}

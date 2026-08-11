package com.nomi.app.ai.validation

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import com.nomi.app.ai.model.ServingSizeValidation
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

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

    /** Rounding headroom over the 100 g physical ceiling for label values. */
    private const val MAX_MACRO_GRAMS_PER_100 = 102.0
    /** Pure fat is 884 kcal per 100 g; the margin covers polyol and rounding edge cases. */
    private const val MAX_CALORIES_PER_100 = 950.0
    /** 100 ml of honey weighs about 142 g, the densest common food. */
    private const val MAX_PLAUSIBLE_DENSITY = 1.5

    /** Headroom for a component row rounded up while its parent row rounded down. */
    private const val COMPONENT_ROUNDING_ALLOWANCE = 1.05
    private const val COMPONENT_ROUNDING_GRAMS = 1.0

    private const val MEDIUM_APPLE_GRAMS_PER_PIECE = 182.0
    private const val MEDIUM_APPLE_MASS_ASSUMPTION =
        "Estimated 182 g per medium apple because no exact piece weight was provided."
    private const val JAM_GRAMS_PER_TABLESPOON = 20.0
    private const val MILLILITERS_PER_TABLESPOON = 15.0
    private const val JAM_VOLUME_MASS_ASSUMPTION =
        "Estimated jam mass from 20 g per German tablespoon (15 ml); product density can vary."
    private const val GENERIC_VOLUME_MASS_ASSUMPTION =
        "Estimated mass from 1 g per ml because the source did not provide a food-specific density."

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
            sugarGrams = previous.sugarGramsPer100?.times(loggedFactor),
            saturatedFatGrams = previous.saturatedFatGramsPer100?.times(loggedFactor),
            sodiumMilligrams = previous.sodiumMilligramsPer100?.times(loggedFactor),
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
            requireOptionalNutrient(
                item.fiberGrams,
                validation.fiberGramsPer100,
                validation.loggedBaseAmount,
                "fiber",
            )
            requireOptionalNutrient(
                item.sugarGrams,
                validation.sugarGramsPer100,
                validation.loggedBaseAmount,
                "sugar",
            )
            requireOptionalNutrient(
                item.saturatedFatGrams,
                validation.saturatedFatGramsPer100,
                validation.loggedBaseAmount,
                "saturated fat",
            )
            requireOptionalNutrient(
                item.sodiumMilligrams,
                validation.sodiumMilligramsPer100,
                validation.loggedBaseAmount,
                "sodium",
            )
        }
        return analysis
    }

    /**
     * A nutrient the source never reported stays absent on both sides. One side appearing or
     * disappearing means the item and its validated basis no longer describe the same reading,
     * which is exactly the kind of silent drift this check exists to catch.
     */
    private fun requireOptionalNutrient(
        loggedAmount: Double?,
        amountPer100: Double?,
        loggedBaseAmount: Double,
        name: String,
    ) {
        when {
            loggedAmount == null && amountPer100 == null -> Unit
            loggedAmount == null || amountPer100 == null ->
                throw AiValidationException("$name basis changed before saving")
            else -> requireNutrient(
                loggedAmount,
                amountPer100 * loggedBaseAmount / 100.0,
                name,
            )
        }
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
        val estimatedJamGramsEquivalent = if (
            suppliedLoggedGramsEquivalent == null && estimatedAppleGramsEquivalent == null
        ) {
            estimateJamGramsEquivalent(
                item = item,
                requested = requested,
                loggedQuantity = loggedQuantity,
                loggedUnit = loggedUnit,
                sourceUnit = sourceUnit,
            )
        } else {
            null
        }
        val estimatedVolumeGramsEquivalent = if (
            suppliedLoggedGramsEquivalent == null &&
            estimatedAppleGramsEquivalent == null &&
            estimatedJamGramsEquivalent == null
        ) {
            estimateVolumeGramsEquivalent(
                loggedQuantity = loggedQuantity,
                loggedUnit = loggedUnit,
                sourceUnit = sourceUnit,
            )
        } else {
            null
        }
        val loggedGramsEquivalent = suppliedLoggedGramsEquivalent
            ?: estimatedAppleGramsEquivalent
            ?: estimatedJamGramsEquivalent
            ?: estimatedVolumeGramsEquivalent
        val estimatedSourceGramsEquivalent = item.sourceServingGramsEquivalent ?: run {
            val source = measure(sourceQuantity, sourceUnit)
            val logged = measure(loggedQuantity, loggedUnit)
            source.baseAmount.takeIf {
                source.dimension == Dimension.Volume && logged.dimension == Dimension.Mass
            }
        }
        val (sourceMeasure, loggedMeasure) = reconcileServingMeasures(
            sourceQuantity = sourceQuantity,
            sourceUnit = sourceUnit,
            sourceGramsEquivalent = estimatedSourceGramsEquivalent,
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
            sugarGramsPer100 = item.sugarGrams?.times(per100Factor),
            saturatedFatGramsPer100 = item.saturatedFatGrams?.times(per100Factor),
            sodiumMilligramsPer100 = item.sodiumMilligrams?.times(per100Factor),
        )
        requirePhysicallyPossiblePer100(validation, sourceMeasure.dimension)
        return item.copy(
            quantity = loggedQuantity,
            unit = loggedUnit,
            gramsEquivalent = loggedGramsEquivalent,
            sourceServingGramsEquivalent = estimatedSourceGramsEquivalent,
            calories = validation.caloriesPer100 * loggedFactor,
            proteinGrams = validation.proteinGramsPer100 * loggedFactor,
            carbohydrateGrams = validation.carbohydrateGramsPer100 * loggedFactor,
            fatGrams = validation.fatGramsPer100 * loggedFactor,
            fiberGrams = validation.fiberGramsPer100?.times(loggedFactor),
            sugarGrams = validation.sugarGramsPer100?.times(loggedFactor),
            saturatedFatGrams = validation.saturatedFatGramsPer100?.times(loggedFactor),
            sodiumMilligrams = validation.sodiumMilligramsPer100?.times(loggedFactor),
            isEstimate = item.isEstimate ||
                estimatedAppleGramsEquivalent != null ||
                estimatedJamGramsEquivalent != null ||
                estimatedVolumeGramsEquivalent != null ||
                (item.sourceServingGramsEquivalent == null && estimatedSourceGramsEquivalent != null),
            assumptions = (
                item.assumptions + listOfNotNull(
                    MEDIUM_APPLE_MASS_ASSUMPTION.takeIf {
                        estimatedAppleGramsEquivalent != null
                    },
                    JAM_VOLUME_MASS_ASSUMPTION.takeIf {
                        estimatedJamGramsEquivalent != null
                    },
                    GENERIC_VOLUME_MASS_ASSUMPTION.takeIf {
                        estimatedVolumeGramsEquivalent != null ||
                            (item.sourceServingGramsEquivalent == null && estimatedSourceGramsEquivalent != null)
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

    /**
     * A narrow, labeled fallback for jam and fruit spreads. These products are commonly published
     * per 100 g while German users naturally log EL/TL. Other foods still require a provider- or
     * product-supplied gram equivalent because their densities vary too widely.
     */
    private fun estimateJamGramsEquivalent(
        item: AnalyzedFoodItem,
        requested: ParsedFoodItem?,
        loggedQuantity: Double,
        loggedUnit: String,
        sourceUnit: String,
    ): Double? {
        val logged = measure(loggedQuantity, loggedUnit)
        val source = measure(1.0, sourceUnit)
        if (logged.dimension != Dimension.Volume || source.dimension != Dimension.Mass) return null

        val authoritativeName = listOfNotNull(requested?.name, item.name)
            .joinToString(" ")
            .normalizeFoodName()
        val isJam = listOf(
            "marmelade",
            "konfiture",
            "konfituere",
            "fruchtaufstrich",
            "jam",
            "fruit spread",
            "preserve",
            "preserves",
        ).any(authoritativeName::contains)
        if (!isJam) return null

        return logged.baseAmount * JAM_GRAMS_PER_TABLESPOON / MILLILITERS_PER_TABLESPOON
    }

    private fun estimateVolumeGramsEquivalent(
        loggedQuantity: Double,
        loggedUnit: String,
        sourceUnit: String,
    ): Double? {
        val logged = measure(loggedQuantity, loggedUnit)
        val source = measure(1.0, sourceUnit)
        if (logged.dimension != Dimension.Volume || source.dimension != Dimension.Mass) return null
        return logged.baseAmount
    }

    private fun String.normalizeFoodName(): String = trim()
        .lowercase(Locale.ROOT)
        .replace('\u00e4', 'a')
        .replace('\u00f6', 'o')
        .replace('\u00fc', 'u')
        .replace("\u00df", "ss")
        .replace(Regex("[^\\p{L}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /**
     * Rejects per-100 values that no real food can have, which is the strongest deterministic
     * signal that reported nutrition did not describe the declared source serving — typically
     * values already scaled to the logged portion, or a mg/g unit mix-up.
     *
     * 100 g of food cannot contain more than 100 g of macronutrients, and pure fat (884 kcal
     * per 100 g) bounds the energy. Volume bases allow for dense liquids such as honey, whose
     * 100 ml weighs about 142 g.
     *
     * Sugar and saturated fat are components of carbohydrate and fat, so they are checked
     * against their parent rather than added to the total: a sugar figure larger than the
     * carbohydrate it came from means the two were read from different columns.
     */
    private fun requirePhysicallyPossiblePer100(
        validation: ServingSizeValidation,
        dimension: Dimension,
    ) {
        requireComponentWithinParent(
            component = validation.sugarGramsPer100,
            parent = validation.carbohydrateGramsPer100,
            componentName = "sugar",
            parentName = "carbohydrates",
        )
        requireComponentWithinParent(
            component = validation.saturatedFatGramsPer100,
            parent = validation.fatGramsPer100,
            componentName = "saturated fat",
            parentName = "fat",
        )
        val densityAllowance = when (dimension) {
            Dimension.Mass -> 1.0
            Dimension.Volume -> MAX_PLAUSIBLE_DENSITY
            else -> return
        }
        val macroGrams = validation.proteinGramsPer100 + validation.carbohydrateGramsPer100 +
            validation.fatGramsPer100 + (validation.fiberGramsPer100 ?: 0.0)
        if (macroGrams > MAX_MACRO_GRAMS_PER_100 * densityAllowance) {
            throw AiValidationException(
                "The researched nutrition is not possible per 100 ${dimension.per100Label}: " +
                    "${clean(round(macroGrams))} g of macronutrients. The source values may " +
                    "already be scaled to the logged amount.",
            )
        }
        if (validation.caloriesPer100 > MAX_CALORIES_PER_100 * densityAllowance) {
            throw AiValidationException(
                "The researched nutrition is not possible per 100 ${dimension.per100Label}: " +
                    "${clean(round(validation.caloriesPer100))} kcal. The source values may " +
                    "already be scaled to the logged amount.",
            )
        }
    }

    /**
     * Labels round each row independently, so a component may legitimately print a hair above
     * its parent — 0.5 g of sugar in 0.4 g of carbohydrate. The allowance absorbs that without
     * admitting a genuinely swapped column.
     */
    private fun requireComponentWithinParent(
        component: Double?,
        parent: Double,
        componentName: String,
        parentName: String,
    ) {
        if (component == null) return
        if (component > parent * COMPONENT_ROUNDING_ALLOWANCE + COMPONENT_ROUNDING_GRAMS) {
            throw AiValidationException(
                "The researched nutrition reports more $componentName than $parentName, so those " +
                    "two values were probably read from different rows.",
            )
        }
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
     * Cross-dimension conversion is permitted only when that exact source or logged serving
     * already carries a total gram equivalent. Creation of density/count estimates happens in the
     * narrow policy helpers above; this method only consumes an explicit total.
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

        if (logged.dimension == Dimension.Mass) {
            val sourceMass = sourceGramsEquivalent?.let {
                massEquivalent(it, source.originalUnit, "source serving grams")
            }
            if (sourceMass != null) return ServingMeasures(sourceMass, logged)
        }
        if (source.dimension == Dimension.Mass) {
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
            "loffel", "loeffel", "spoon", "spoons" -> Dimension.Volume to 15.0
            "ml", "milliliter", "milliliters", "millilitre", "millilitres" ->
                Dimension.Volume to 1.0
            "cl", "centiliter", "centiliters", "centilitre", "centilitres" ->
                Dimension.Volume to 10.0
            "l", "liter", "liters", "litre", "litres" -> Dimension.Volume to 1_000.0
            "fl oz", "us fl oz", "floz", "fluid ounce", "fluid ounces" ->
                Dimension.Volume to US_FLUID_OUNCE_ML
            "piece", "pieces", "pc", "pcs", "each", "stuck", "stucke",
            "item", "items", "serving", "servings", "portion", "portions", "portionen",
            "bar", "bars", "riegel" ->
                Dimension.Piece to 1.0
            else -> Dimension.Custom(unit) to 1.0
        }
        return Measure(
            dimension = dimension,
            baseAmount = quantity * factor,
            originalUnit = rawUnit,
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
        val per100Label: String get() = "units"

        data object Mass : Dimension {
            override val storageName = "mass_g"
            override val per100Label = "g"
        }
        data object Volume : Dimension {
            override val storageName = "volume_ml"
            override val per100Label = "ml"
        }
        data object Piece : Dimension { override val storageName = "piece" }
        data class Custom(val unit: String) : Dimension { override val storageName = "custom:$unit" }
    }

    private data class Measure(
        val dimension: Dimension,
        val baseAmount: Double,
        val originalUnit: String,
    )

    private data class ServingMeasures(
        val source: Measure,
        val logged: Measure,
    )
}

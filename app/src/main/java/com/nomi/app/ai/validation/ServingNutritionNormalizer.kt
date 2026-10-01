package com.nomi.app.ai.validation

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import com.nomi.app.ai.model.ResearchNutritionBasis
import com.nomi.app.ai.model.ServingSizeValidation
import com.nomi.app.ai.model.QuantityUnits
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
        loggedResolvedVolumeMl: Double? = null,
    ): AnalyzedFoodItem {
        val prepared = sourceServingItem.copy(
            quantity = loggedQuantity,
            unit = loggedUnit,
            gramsEquivalent = loggedGramsEquivalent,
            resolvedVolumeMl = loggedResolvedVolumeMl,
            quantityResolution = null,
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
                        resolvedVolumeMl = loggedResolvedVolumeMl,
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
        val volume = item.resolvedVolumeMl?.let {
            if (normalizeUnit(item.unit) == normalizeUnit(loggedUnit)) it * loggedQuantity / item.quantity else null
        }
        val (sourceMeasure, loggedMeasure) = reconcileServingMeasures(
            sourceQuantity = previous.sourceQuantity,
            sourceUnit = previous.sourceUnit,
            sourceGramsEquivalent = item.sourceServingGramsEquivalent,
            loggedQuantity = loggedQuantity,
            loggedUnit = loggedUnit,
            loggedGramsEquivalent = resolvedLoggedGramsEquivalent,
            loggedVolumeMl = volume,
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
            resolvedVolumeMl = volume,
            quantityResolution = item.quantityResolution?.copy(
                canonicalQuantity = loggedQuantity, canonicalUnit = loggedUnit,
                enteredQuantity = loggedQuantity, enteredUnit = loggedUnit,
                resolvedWeightGrams = resolvedLoggedGramsEquivalent, resolvedVolumeMl = volume,
            ),
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

    /**
     * Applies a user's correction to one item's nutrient values, keeping it saveable.
     *
     * The user is correcting what the food *contains*, not how much was eaten, so the logged
     * amount and the source serving stay put and the per-100 basis is re-derived from the
     * corrected logged value. That is what keeps [validateBeforeSave] satisfied: the recorded
     * basis and the item's numbers describe the same reading again, instead of the two being
     * left to disagree.
     *
     * This deliberately re-derives rather than waives. The corrected per-100 values still have
     * to be physically possible, so a correction to an impossible number is refused exactly as
     * an impossible research result is.
     */
    fun applyUserNutrientCorrection(
        item: AnalyzedFoodItem,
        calories: Double,
        proteinGrams: Double,
        carbohydrateGrams: Double,
        fatGrams: Double,
    ): AnalyzedFoodItem {
        val validation = item.servingValidation?.takeIf { item.requiresServingValidation }
        if (validation == null) {
            // No recorded basis to preserve, so the correction is the whole truth for this
            // entry. It still has to be a food.
            requireFiniteNonNegative(calories, "calories")
            requireFiniteNonNegative(proteinGrams, "protein")
            requireFiniteNonNegative(carbohydrateGrams, "carbohydrates")
            requireFiniteNonNegative(fatGrams, "fat")
            requireComponentWithinParent(
                component = item.sugarGrams,
                parent = carbohydrateGrams,
                componentName = "sugar",
                parentName = "carbohydrates",
            )
            requireComponentWithinParent(
                component = item.saturatedFatGrams,
                parent = fatGrams,
                componentName = "saturated fat",
                parentName = "fat",
            )
            // The per-100 ceiling needs the portion's weight. The totals of a 300 g plate are
            // not per-100 values, and reading them as such refused any real meal above 100 g of
            // macros; a portion with no known weight makes no per-100 claim to check at all.
            val grams = item.gramsEquivalent
                ?: item.quantity.takeIf { normalizeUnit(item.unit) == "g" }
            if (grams != null && grams.isFinite() && grams > 0.0) {
                val per100 = 100.0 / grams
                requirePhysicallyPossiblePer100(
                    validation = per100ForLogged(
                        loggedBaseAmount = 100.0,
                        calories = calories * per100,
                        proteinGrams = proteinGrams * per100,
                        carbohydrateGrams = carbohydrateGrams * per100,
                        fatGrams = fatGrams * per100,
                        fiberGrams = item.fiberGrams?.times(per100),
                        sugarGrams = item.sugarGrams?.times(per100),
                        saturatedFatGrams = item.saturatedFatGrams?.times(per100),
                        sodiumMilligrams = item.sodiumMilligrams?.times(per100),
                    ),
                    dimension = Dimension.Mass,
                )
            }
            return correctedItem(item, calories, proteinGrams, carbohydrateGrams, fatGrams, null)
        }

        val loggedFactor = validation.loggedBaseAmount / 100.0
        requireFiniteNonNegative(calories, "calories")
        requireFiniteNonNegative(proteinGrams, "protein")
        requireFiniteNonNegative(carbohydrateGrams, "carbohydrates")
        requireFiniteNonNegative(fatGrams, "fat")

        // Sugar and saturated fat are components of their parents and are not edited here, so a
        // correction that drops a parent below its own component is still a contradiction.
        requireComponentWithinParent(
            component = validation.sugarGramsPer100,
            parent = carbohydrateGrams / loggedFactor,
            componentName = "sugar",
            parentName = "carbohydrates",
        )
        requireComponentWithinParent(
            component = validation.saturatedFatGramsPer100,
            parent = fatGrams / loggedFactor,
            componentName = "saturated fat",
            parentName = "fat",
        )

        val correctedValidation = validation.copy(
            caloriesPer100 = calories / loggedFactor,
            proteinGramsPer100 = proteinGrams / loggedFactor,
            carbohydrateGramsPer100 = carbohydrateGrams / loggedFactor,
            fatGramsPer100 = fatGrams / loggedFactor,
        )
        requirePhysicallyPossiblePer100(
            correctedValidation,
            dimensionFromStorageName(validation.dimension),
        )
        return correctedItem(
            item, calories, proteinGrams, carbohydrateGrams, fatGrams, correctedValidation,
        ).also { validateBeforeSave(FoodAnalysis(items = listOf(it))) }
    }

    /**
     * Applies a hand-typed amount or unit to one preview item, keeping it saveable.
     *
     * The dialog treats its fields as one snapshot the user vouches for, so the nutrition is not
     * rescaled here. What has to go is the recorded serving basis: it describes the amount the
     * research was scaled to, and leaving it attached made [validateBeforeSave] refuse the whole
     * meal with "logged amount changed". The known weight follows the amount only while the unit
     * is unchanged; a new unit has no weight unless it is grams itself.
     */
    fun applyUserAmountOverride(
        item: AnalyzedFoodItem,
        quantity: Double,
        unit: String,
    ): AnalyzedFoodItem {
        val newUnit = unit.trim()
        if (!quantity.isFinite() || quantity <= 0.0) {
            throw AiValidationException("amount must be finite and greater than zero")
        }
        if (newUnit.isEmpty()) throw AiValidationException("Serving unit is missing")
        if (quantity == item.quantity && newUnit == item.unit) return item
        val sameUnit = normalizeUnit(newUnit) == normalizeUnit(item.unit)
        val factor = quantity / item.quantity
        return item.copy(
            quantity = quantity,
            unit = newUnit,
            gramsEquivalent = when {
                sameUnit -> item.gramsEquivalent?.times(factor)
                normalizeUnit(newUnit) == "g" -> quantity
                else -> null
            },
            resolvedVolumeMl = item.resolvedVolumeMl?.times(factor)?.takeIf { sameUnit },
            quantityResolution = null,
            servingValidation = null,
            requiresServingValidation = false,
            // The cited source reported these numbers for a different amount.
            isEstimate = true,
            verificationStatus = com.nomi.app.ai.model.NutritionVerificationStatus.ESTIMATED,
            assumptions = (item.assumptions + "Amount changed by hand before saving.").takeLast(12),
        ).also { validateBeforeSave(FoodAnalysis(items = listOf(it))) }
    }

    private fun correctedItem(
        item: AnalyzedFoodItem,
        calories: Double,
        proteinGrams: Double,
        carbohydrateGrams: Double,
        fatGrams: Double,
        validation: ServingSizeValidation?,
    ): AnalyzedFoodItem = item.copy(
        calories = calories,
        proteinGrams = proteinGrams,
        carbohydrateGrams = carbohydrateGrams,
        fatGrams = fatGrams,
        servingValidation = validation,
        requiresServingValidation = validation != null,
        // A value the cited source did not report is no longer a source reading.
        isEstimate = true,
        verificationStatus = com.nomi.app.ai.model.NutritionVerificationStatus.ESTIMATED,
        assumptions = (
            item.assumptions + "Nutrition corrected by hand before saving."
            ).takeLast(12),
    )

    private fun per100ForLogged(
        loggedBaseAmount: Double,
        calories: Double,
        proteinGrams: Double,
        carbohydrateGrams: Double,
        fatGrams: Double,
        fiberGrams: Double?,
        sugarGrams: Double?,
        saturatedFatGrams: Double?,
        sodiumMilligrams: Double?,
    ) = ServingSizeValidation(
        dimension = Dimension.Mass.storageName,
        sourceQuantity = loggedBaseAmount,
        sourceUnit = "g",
        sourceBaseAmount = loggedBaseAmount,
        loggedQuantity = loggedBaseAmount,
        loggedUnit = "g",
        loggedBaseAmount = loggedBaseAmount,
        scaleFactor = 1.0,
        caloriesPer100 = calories,
        proteinGramsPer100 = proteinGrams,
        carbohydrateGramsPer100 = carbohydrateGrams,
        fatGramsPer100 = fatGrams,
        fiberGramsPer100 = fiberGrams,
        sugarGramsPer100 = sugarGrams,
        saturatedFatGramsPer100 = saturatedFatGrams,
        sodiumMilligramsPer100 = sodiumMilligrams,
    )

    private fun requireFiniteNonNegative(value: Double, label: String) {
        if (!value.isFinite() || value < 0.0) {
            throw AiValidationException("$label must be a finite value of zero or more")
        }
    }

    /** Reads back the dimension a recorded validation was written against. */
    private fun dimensionFromStorageName(storageName: String): Dimension = when {
        storageName == Dimension.Mass.storageName -> Dimension.Mass
        storageName == Dimension.Volume.storageName -> Dimension.Volume
        storageName == Dimension.Piece.storageName -> Dimension.Piece
        storageName.startsWith("custom:") -> Dimension.Custom(storageName.removePrefix("custom:"))
        else -> Dimension.Mass
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
                loggedGramsEquivalent = item.resolvedWeightGrams,
                loggedVolumeMl = item.resolvedVolumeMl,
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

        val declaredBasis = item.nutritionBasis
        val sourceQuantity = when (declaredBasis) {
            ResearchNutritionBasis.PER_100_G,
            ResearchNutritionBasis.PER_100_ML,
            -> 100.0
            ResearchNutritionBasis.SOURCE_SERVING, null -> item.sourceServingQuantity
        }
            ?: throw AiValidationException("Nutrition source serving amount is missing")
        val sourceUnit = when (declaredBasis) {
            ResearchNutritionBasis.PER_100_G -> "g"
            ResearchNutritionBasis.PER_100_ML -> "ml"
            ResearchNutritionBasis.SOURCE_SERVING, null -> item.sourceServingUnit
        }?.takeIf(String::isNotBlank)
            ?: throw AiValidationException("Nutrition source serving unit is missing")
        requireFinitePositive(sourceQuantity, "source serving amount")
        val declaredSourceGramsEquivalent = when (declaredBasis) {
            ResearchNutritionBasis.PER_100_G -> 100.0
            ResearchNutritionBasis.PER_100_ML -> null
            ResearchNutritionBasis.SOURCE_SERVING, null -> item.sourceServingGramsEquivalent
        }
        declaredSourceGramsEquivalent?.let {
            requireFinitePositive(it, "source serving grams")
        }

        // Cross-dimension arithmetic is valid only with an explicit, food-specific bridge from
        // the parser, package, manufacturer, or researched serving. A generic density or a
        // food-name table silently substitutes unrelated nutrition and is never safe here.
        val loggedGramsEquivalent = requested?.resolvedWeightGrams ?: item.resolvedWeightGrams
        val volume = requested?.resolvedVolumeMl ?: item.resolvedVolumeMl
        val sourceGramsEquivalent = declaredSourceGramsEquivalent
        val (sourceMeasure, loggedMeasure) = reconcileServingMeasures(
            sourceQuantity = sourceQuantity,
            sourceUnit = sourceUnit,
            sourceGramsEquivalent = sourceGramsEquivalent,
            loggedQuantity = loggedQuantity,
            loggedUnit = loggedUnit,
            loggedGramsEquivalent = loggedGramsEquivalent,
            loggedVolumeMl = volume,
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
            resolvedVolumeMl = volume,
            quantityResolution = item.quantityResolution?.copy(
                resolvedWeightGrams = loggedGramsEquivalent, resolvedVolumeMl = volume,
                resolutionSource = item.resolutionSource, isEstimated = item.isEstimate,
            ),
            sourceServingGramsEquivalent = sourceGramsEquivalent,
            sourceServingQuantity = sourceQuantity,
            sourceServingUnit = sourceUnit,
            nutritionBasis = declaredBasis ?: ResearchNutritionBasis.SOURCE_SERVING,
            calories = validation.caloriesPer100 * loggedFactor,
            proteinGrams = validation.proteinGramsPer100 * loggedFactor,
            carbohydrateGrams = validation.carbohydrateGramsPer100 * loggedFactor,
            fatGrams = validation.fatGramsPer100 * loggedFactor,
            fiberGrams = validation.fiberGramsPer100?.times(loggedFactor),
            sugarGrams = validation.sugarGramsPer100?.times(loggedFactor),
            saturatedFatGrams = validation.saturatedFatGramsPer100?.times(loggedFactor),
            sodiumMilligrams = validation.sodiumMilligramsPer100?.times(loggedFactor),
            isEstimate = item.isEstimate,
            assumptions = (
                item.assumptions + listOf(
                    "Source serving ${clean(sourceQuantity)} $sourceUnit normalized to per 100 " +
                        "and scaled to ${clean(loggedQuantity)} $loggedUnit.",
                )
            ).takeLast(12),
            servingValidation = validation,
            requiresServingValidation = true,
        )
    }

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
        // Fibre is checked on its own rather than added to the total. US-style tables already
        // count it inside carbohydrate, so adding it again put chia seeds at 124 g per 100 g and
        // refused almonds, flaxseed, bran and cocoa powder as impossible. Protein, carbohydrate
        // and fat fitting into the serving holds under both labelling conventions.
        val fiberGrams = validation.fiberGramsPer100 ?: 0.0
        val macroGrams = maxOf(
            validation.proteinGramsPer100 + validation.carbohydrateGramsPer100 +
                validation.fatGramsPer100,
            fiberGrams,
        )
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

    /**
     * Two servings that do not share a dimension cannot be scaled into one another, but the two
     * ways that happens need different answers. A counted amount on either side is missing a
     * conversion the research must resolve; anything else is a
     * basis the source and the request simply do not share.
     */
    private fun requireCompatible(first: Measure, second: Measure) {
        if (first.dimension == second.dimension) return
        throw NutritionResearchException(
            reason = if (second.dimension.isCounted) {
                // The quantity is valid; research has not resolved its nutritional basis.
                NutritionFailureReason.MISSING_PORTION_WEIGHT
            } else {
                // The logged amount is already a real mass or volume; it is the source's own
                // basis that cannot be converted into it.
                NutritionFailureReason.INVALID_NUTRITION_BASIS
            },
            detail = "Source serving '${first.originalUnit}' is not compatible with logged unit " +
                "'${second.originalUnit}'",
        )
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
        loggedVolumeMl: Double? = null,
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

        if (source.dimension == Dimension.Volume && loggedVolumeMl != null) {
            requireFinitePositive(loggedVolumeMl, "resolved volume")
            return ServingMeasures(source, Measure(Dimension.Volume, loggedVolumeMl, logged.originalUnit))
        }
        // Neither side is a mass, but each carries its own total weight: a restaurant
        // "Portion (105 g)" against the burger the user logged as a piece. A portion is still
        // never read as a piece; the two only meet in grams, and only when both weights exist.
        if (sourceGramsEquivalent != null && loggedGramsEquivalent != null) {
            return ServingMeasures(
                massEquivalent(sourceGramsEquivalent, source.originalUnit, "source serving grams"),
                massEquivalent(loggedGramsEquivalent, logged.originalUnit, "logged serving grams"),
            )
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
        if (current.dimension != updated.dimension || !current.dimension.isCounted) return null
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
            "item", "items" ->
                Dimension.Piece to 1.0
            else -> Dimension.Custom(unit) to 1.0
        }
        return Measure(
            dimension = dimension,
            baseAmount = quantity * factor,
            originalUnit = rawUnit,
        )
    }

    private fun normalizeUnit(value: String): String = QuantityUnits.normalize(value)
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

        /** A count or an unrecognized household unit: real, but with no weight of its own. */
        val isCounted: Boolean get() = this is Piece || this is Custom

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

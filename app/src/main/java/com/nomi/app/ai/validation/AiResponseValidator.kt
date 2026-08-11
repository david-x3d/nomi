package com.nomi.app.ai.validation

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.FoodEditClassification
import com.nomi.app.ai.model.FoodEditType
import com.nomi.app.ai.model.MenuScanResult
import com.nomi.app.ai.model.NutritionLabelReading
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.PortionAdjustment
import com.nomi.app.ai.model.PortionContext
import com.nomi.app.ai.model.QuantityOrigin
import com.nomi.app.ai.model.QuantityResolutionMetadata
import com.nomi.app.ai.model.QuantitySemantic
import com.nomi.app.ai.model.VisionFoodResult
import kotlin.math.abs

class AiValidationException(message: String) : IllegalArgumentException(message)

object AiResponseValidator {
    private const val MAX_ITEMS = 50
    private const val MAX_TEXT_LIST_ITEMS = 50
    private const val MAX_ITEM_CALORIES = 10_000.0
    private const val MAX_MACRO_GRAMS = 5_000.0

    /** Sodium is stored in milligrams, so it needs its own ceiling rather than the gram one. */
    private const val MAX_SODIUM_MILLIGRAMS = 5_000_000.0
    private const val MAX_PORTION_GRAMS = 100_000.0
    private const val MAX_QUANTITY = 1_000_000.0
    private const val MAX_PORTION_MULTIPLIER = 20.0
    private const val MAX_INPUT_TEXT_CHARS = 8_192
    private const val MAX_NAME_CHARS = 300
    private const val MAX_BRAND_CHARS = 300
    private const val MAX_UNIT_CHARS = 64
    private const val MAX_LANGUAGE_CHARS = 64
    private const val MAX_REFERENCE_CHARS = 500
    private const val MAX_DETAIL_CHARS = 1_000
    private const val MAX_SOURCE_NAME_CHARS = 500
    private const val MAX_SOURCE_URL_CHARS = 2_048
    private const val MAX_SUPPORTING_SOURCE_URLS = 5
    private const val MAX_COUNTRY_CHARS = 8
    private const val MAX_DOMAIN_CHARS = 253

    /**
     * How far a label's macros may sit from its printed energy before the reading is refused.
     * Real tables drift a little: rounding, sugar alcohols, and fibre that some markets count
     * toward energy and others do not.
     */
    private const val LABEL_ENERGY_TOLERANCE = 0.25
    private const val LABEL_ENERGY_TOLERANCE_KCAL = 30.0

    fun validate(intent: ParsedFoodIntent): ParsedFoodIntent {
        validateText(intent.originalText, "original text", required = true, maxChars = MAX_INPUT_TEXT_CHARS)
        validateText(intent.language, "language", maxChars = MAX_LANGUAGE_CHARS)
        validateText(intent.mealReference, "meal reference", maxChars = MAX_REFERENCE_CHARS)
        if (intent.items.isEmpty() && intent.mealReference.isNullOrBlank()) {
            throw AiValidationException("No food was recognized")
        }
        if (intent.items.size > MAX_ITEMS) throw AiValidationException("Too many foods were returned")
        intent.items.forEach { item ->
            validateText(item.name, "food name", required = true, maxChars = MAX_NAME_CHARS)
            validateText(item.brand, "brand", maxChars = MAX_BRAND_CHARS)
            validateText(item.unit, "unit", maxChars = MAX_UNIT_CHARS)
            validateText(item.preparation, "preparation", maxChars = MAX_DETAIL_CHARS)
            validateTextList(item.assumptions, "assumptions")
            item.quantity?.let { requireFinitePositive(it, "quantity", MAX_QUANTITY) }
            item.gramsEquivalent?.let { requireFinitePositive(it, "grams", MAX_PORTION_GRAMS) }
            item.quantityResolution?.let { resolution ->
                validateQuantityResolution(item.quantity, item.unit, resolution)
            }
        }
        return intent
    }

    fun validate(analysis: FoodAnalysis): FoodAnalysis {
        if (analysis.items.isEmpty()) throw AiValidationException("No nutrition result was returned")
        if (analysis.items.size > MAX_ITEMS) throw AiValidationException("Too many foods were returned")
        analysis.items.forEach(::validateItem)
        analysis.overallConfidence?.let(::requireConfidence)
        return analysis
    }

    /**
     * A label reading claims to be transcription, not research, so it is held to what a real
     * printed table can say. The macro cross-check is the important one: grams that cannot
     * add up to the printed energy mean a column was misread, and a misread column is worse
     * than no reading at all, because nothing downstream would ever question it.
     */
    fun validate(reading: NutritionLabelReading): NutritionLabelReading {
        validateText(reading.productName, "product name", maxChars = MAX_NAME_CHARS)
        validateText(reading.brand, "brand", maxChars = MAX_BRAND_CHARS)
        validateText(reading.basisUnit, "label basis unit", required = true, maxChars = MAX_UNIT_CHARS)
        validateText(reading.packageUnit, "package unit", maxChars = MAX_UNIT_CHARS)
        validateText(reading.servingLabel, "serving label", maxChars = MAX_DETAIL_CHARS)
        validateTextList(reading.notes, "label notes")
        requireFinitePositive(reading.basisQuantity, "label basis amount", MAX_QUANTITY)
        reading.packageQuantity?.let { requireFinitePositive(it, "package amount", MAX_QUANTITY) }
        reading.confidence?.let(::requireConfidence)
        requireFiniteNonNegative(reading.calories, "label calories", MAX_ITEM_CALORIES)
        requireFiniteNonNegative(reading.proteinGrams, "label protein", MAX_MACRO_GRAMS)
        requireFiniteNonNegative(reading.carbohydrateGrams, "label carbohydrates", MAX_MACRO_GRAMS)
        requireFiniteNonNegative(reading.fatGrams, "label fat", MAX_MACRO_GRAMS)
        reading.fiberGrams?.let { requireFiniteNonNegative(it, "label fibre", MAX_MACRO_GRAMS) }
        reading.sugarGrams?.let { requireFiniteNonNegative(it, "label sugar", MAX_MACRO_GRAMS) }
        reading.saturatedFatGrams?.let {
            requireFiniteNonNegative(it, "label saturated fat", MAX_MACRO_GRAMS)
        }
        reading.sodiumMilligrams?.let {
            requireFiniteNonNegative(it, "label sodium", MAX_SODIUM_MILLIGRAMS)
        }

        // Per 100 g/ml no food can contain more than 100 g of anything.
        if (reading.basisQuantity == 100.0 && reading.basisUnit.trim().lowercase() in setOf("g", "ml")) {
            val macros = reading.proteinGrams + reading.carbohydrateGrams + reading.fatGrams
            if (macros > 100.0) {
                throw AiValidationException(
                    "The values read from the label add up to more than 100 g per 100 g. " +
                        "Photograph the table again, straight on and in focus.",
                )
            }
        }
        val impliedCalories = reading.proteinGrams * 4 + reading.carbohydrateGrams * 4 +
            reading.fatGrams * 9
        val tolerance = maxOf(LABEL_ENERGY_TOLERANCE_KCAL, reading.calories * LABEL_ENERGY_TOLERANCE)
        if (reading.calories > 0.0 && abs(impliedCalories - reading.calories) > tolerance) {
            throw AiValidationException(
                "The macros read from the label don't match its energy value, so a column was " +
                    "probably misread. Photograph the table again, straight on and in focus.",
            )
        }
        return reading
    }

    fun validate(vision: VisionFoodResult): VisionFoodResult {
        if (vision.items.isEmpty()) throw AiValidationException("No food was visible in the photo")
        if (vision.items.size > MAX_ITEMS) throw AiValidationException("Too many foods were returned")
        validateTextList(vision.notes, "vision notes")
        vision.items.forEach { item ->
            validateText(item.name, "detected food name", required = true, maxChars = MAX_NAME_CHARS)
            validateText(item.unit, "detected unit", maxChars = MAX_UNIT_CHARS)
            validateTextList(item.visibleIngredients, "visible ingredients")
            item.estimatedQuantity?.let { requireFinitePositive(it, "quantity", MAX_QUANTITY) }
            item.estimatedGrams?.let { requireFinitePositive(it, "grams", MAX_PORTION_GRAMS) }
            item.confidence?.let(::requireConfidence)
        }
        return vision
    }

    fun validate(menu: MenuScanResult): MenuScanResult {
        if (menu.items.isEmpty()) throw AiValidationException("No menu items were readable in the photo")
        if (menu.items.size > 250) throw AiValidationException("Too many menu items were returned")
        validateText(menu.restaurantName, "restaurant name", maxChars = MAX_BRAND_CHARS)
        validateTextList(menu.notes, "menu notes")
        menu.items.forEach { item ->
            validateText(item.number, "menu item number", maxChars = 64)
            validateText(item.name, "menu item name", required = true, maxChars = MAX_NAME_CHARS)
            validateText(item.description, "menu item description", maxChars = MAX_DETAIL_CHARS)
            validateText(item.category, "menu category", maxChars = MAX_NAME_CHARS)
            validateText(item.price, "menu price", maxChars = 64)
            validateText(item.quantityText, "menu quantity", maxChars = 120)
        }
        return menu
    }

    fun validate(
        current: PortionContext,
        adjustment: PortionAdjustment,
    ): PortionAdjustment {
        validateText(current.name, "current food name", required = true, maxChars = MAX_NAME_CHARS)
        validateText(current.currentUnit, "current unit", required = true, maxChars = MAX_UNIT_CHARS)
        validateText(adjustment.newUnit, "proposed unit", required = true, maxChars = MAX_UNIT_CHARS)
        validateText(
            adjustment.interpretation,
            "portion interpretation",
            required = true,
            maxChars = MAX_DETAIL_CHARS,
        )
        requireFinitePositive(current.currentQuantity, "current quantity", MAX_QUANTITY)
        requireFinitePositive(adjustment.newQuantity, "new quantity", MAX_QUANTITY)
        requireFinitePositive(adjustment.multiplier, "multiplier", MAX_PORTION_MULTIPLIER)
        adjustment.newGrams?.let { requireFinitePositive(it, "new grams", MAX_PORTION_GRAMS) }
        current.currentGrams?.let { requireFinitePositive(it, "current grams", MAX_PORTION_GRAMS) }
        requireFiniteNonNegative(current.calories, "current calories", MAX_ITEM_CALORIES)
        requireFiniteNonNegative(current.proteinGrams, "current protein", MAX_MACRO_GRAMS)
        requireFiniteNonNegative(current.carbohydrateGrams, "current carbohydrates", MAX_MACRO_GRAMS)
        requireFiniteNonNegative(current.fatGrams, "current fat", MAX_MACRO_GRAMS)

        if (current.currentUnit.equals(adjustment.newUnit, ignoreCase = true)) {
            val expected = adjustment.newQuantity / current.currentQuantity
            if (relativeDifference(expected, adjustment.multiplier) > 0.05) {
                throw AiValidationException("The proposed portion multiplier is inconsistent")
            }
        }
        if (current.currentGrams != null && adjustment.newGrams != null) {
            val expected = adjustment.newGrams / current.currentGrams
            if (relativeDifference(expected, adjustment.multiplier) > 0.08) {
                throw AiValidationException("The proposed gram multiplier is inconsistent")
            }
        }
        return adjustment
    }

    /**
     * A classification is a routing decision, so the only thing worth enforcing is that its
     * fields are usable. An instruction attached to a non-portion type is dropped rather than
     * rejected: the route is still correct, and the arithmetic simply will not be used.
     */
    fun validate(classification: FoodEditClassification): FoodEditClassification {
        validateText(classification.reason, "edit reason", maxChars = MAX_DETAIL_CHARS)
        classification.confidence?.let(::requireConfidence)
        val portion = classification.portion?.takeIf {
            classification.type == FoodEditType.PORTION_ONLY
        }
        portion?.let { instruction ->
            runCatching { instruction.requireUsable() }.getOrElse {
                throw AiValidationException(
                    it.message ?: "The proposed portion change is not usable",
                )
            }
            instruction.factor?.let { requireFinitePositive(it, "portion factor", MAX_PORTION_MULTIPLIER) }
            instruction.quantity?.let { requireFinitePositive(it, "portion quantity", MAX_QUANTITY) }
            validateText(instruction.unit, "portion unit", maxChars = MAX_UNIT_CHARS)
        }
        return classification.copy(portion = portion)
    }

    private fun validateItem(item: AnalyzedFoodItem) {
        validateText(item.name, "food name", required = true, maxChars = MAX_NAME_CHARS)
        validateText(item.brand, "brand", maxChars = MAX_BRAND_CHARS)
        validateText(item.unit, "food unit", required = true, maxChars = MAX_UNIT_CHARS)
        validateText(item.sourceName, "source name", maxChars = MAX_SOURCE_NAME_CHARS)
        rejectFailureMessage(item.name, "food name")
        rejectFailureMessage(item.sourceName, "source name")
        rejectFailureMessage(item.sourceProductName, "source product name")
        validateText(item.sourceUrl, "source URL", maxChars = MAX_SOURCE_URL_CHARS)
        if (item.supportingSourceUrls.size > MAX_SUPPORTING_SOURCE_URLS) {
            throw AiValidationException("Too many supporting source URLs were returned")
        }
        item.supportingSourceUrls.forEach { url ->
            validateText(url, "supporting source URL", required = true, maxChars = MAX_SOURCE_URL_CHARS)
        }
        validateText(item.sourceProductName, "source product name", maxChars = MAX_SOURCE_NAME_CHARS)
        validateText(item.sourceDomain, "source domain", maxChars = MAX_DOMAIN_CHARS)
        validateText(item.sourceServingUnit, "source serving unit", maxChars = MAX_UNIT_CHARS)
        validateText(item.sourcePackageUnit, "source package unit", maxChars = MAX_UNIT_CHARS)
        validateText(item.sourceCountry, "source country", maxChars = MAX_COUNTRY_CHARS)
        validateTextList(item.assumptions, "assumptions")
        requireFinitePositive(item.quantity, "quantity", MAX_QUANTITY)
        item.gramsEquivalent?.let { requireFinitePositive(it, "grams", MAX_PORTION_GRAMS) }
        item.sourceServingQuantity?.let { requireFinitePositive(it, "source serving", MAX_QUANTITY) }
        item.sourceServingGramsEquivalent?.let { requireFinitePositive(it, "source serving grams", MAX_PORTION_GRAMS) }
        item.sourcePackageQuantity?.let { requireFinitePositive(it, "source package", MAX_QUANTITY) }
        if ((item.sourcePackageQuantity == null) != (item.sourcePackageUnit == null)) {
            throw AiValidationException("Source package amount and unit must be supplied together")
        }
        item.quantityResolution?.let { validateQuantityResolution(item.quantity, item.unit, it) }
        requireFiniteNonNegative(item.calories, "calories", MAX_ITEM_CALORIES)
        requireFiniteNonNegative(item.proteinGrams, "protein", MAX_MACRO_GRAMS)
        requireFiniteNonNegative(item.carbohydrateGrams, "carbohydrates", MAX_MACRO_GRAMS)
        requireFiniteNonNegative(item.fatGrams, "fat", MAX_MACRO_GRAMS)
        item.fiberGrams?.let { requireFiniteNonNegative(it, "fiber", MAX_MACRO_GRAMS) }
        item.sugarGrams?.let { requireFiniteNonNegative(it, "sugar", MAX_MACRO_GRAMS) }
        item.saturatedFatGrams?.let { requireFiniteNonNegative(it, "saturated fat", MAX_MACRO_GRAMS) }
        item.sodiumMilligrams?.let { requireFiniteNonNegative(it, "sodium", MAX_SODIUM_MILLIGRAMS) }
        item.confidence?.let(::requireConfidence)
        item.uncertaintyPercent?.let {
            requireFiniteNonNegative(it, "estimate uncertainty", 100.0)
        }
        val macroCalories = item.proteinGrams * 4 + item.carbohydrateGrams * 4 + item.fatGrams * 9
        if (item.calories >= 20 && macroCalories > item.calories * 2.0 + 50) {
            throw AiValidationException("Calories and macronutrients are inconsistent")
        }
    }

    private fun validateQuantityResolution(
        itemQuantity: Double?,
        itemUnit: String?,
        resolution: QuantityResolutionMetadata,
    ) {
        requireFinitePositive(resolution.canonicalQuantity, "resolved quantity", MAX_QUANTITY)
        validateText(
            resolution.canonicalUnit,
            "resolved unit",
            required = true,
            maxChars = MAX_UNIT_CHARS,
        )
        if (resolution.origin != QuantityOrigin.MENU_EXPLICIT &&
            resolution.canonicalUnit != "g" && resolution.canonicalUnit != "ml"
        ) {
            throw AiValidationException("Resolved quantity must use canonical g or ml")
        }
        if (itemQuantity == null || itemUnit == null ||
            relativeDifference(itemQuantity, resolution.canonicalQuantity) > 1e-7 ||
            !itemUnit.equals(resolution.canonicalUnit, ignoreCase = true)
        ) {
            throw AiValidationException("Resolved quantity does not match the logged amount")
        }

        val hasPackageQuantity = resolution.packageQuantity != null
        val hasPackageUnit = resolution.packageUnit != null
        if (hasPackageQuantity != hasPackageUnit) {
            throw AiValidationException("Resolved package amount and unit must be supplied together")
        }
        resolution.packageQuantity?.let { requireFinitePositive(it, "resolved package", MAX_QUANTITY) }
        validateText(resolution.packageUnit, "resolved package unit", maxChars = MAX_UNIT_CHARS)
        resolution.sourcePackageQuantity?.let {
            requireFinitePositive(it, "resolved source package", MAX_QUANTITY)
        }
        validateText(resolution.sourcePackageUnit, "resolved source package unit", maxChars = MAX_UNIT_CHARS)
        if ((resolution.sourcePackageQuantity == null) != (resolution.sourcePackageUnit == null)) {
            throw AiValidationException("Resolved source package amount and unit must be supplied together")
        }
        if (resolution.sourcePackageConflict &&
            (resolution.packageQuantity == null || resolution.sourcePackageQuantity == null)
        ) {
            throw AiValidationException("Package conflict requires both user and source package sizes")
        }

        when (resolution.semantic) {
            QuantitySemantic.PACKAGE_PERCENT -> {
                val percentage = resolution.percentage
                    ?: throw AiValidationException("Package percentage is missing")
                if (!percentage.isFinite() || percentage <= 0.0 || percentage > 100.0 ||
                    resolution.packageQuantity == null
                ) throw AiValidationException("Package percentage is invalid")
            }
            QuantitySemantic.PACKAGE_FRACTION -> {
                val numerator = resolution.fractionNumerator
                    ?: throw AiValidationException("Package fraction numerator is missing")
                val denominator = resolution.fractionDenominator
                    ?: throw AiValidationException("Package fraction denominator is missing")
                if (numerator <= 0 || denominator <= 0 || numerator > denominator ||
                    resolution.packageQuantity == null
                ) throw AiValidationException("Package fraction is invalid")
            }
            QuantitySemantic.DIRECT_AMOUNT,
            QuantitySemantic.LOCAL_CAN_DEFAULT,
            -> Unit
        }
    }

    private fun validateText(
        value: String?,
        field: String,
        required: Boolean = value != null,
        maxChars: Int,
    ) {
        if (required && value.isNullOrBlank()) throw AiValidationException("$field is missing")
        if (value != null && value.length > maxChars) {
            throw AiValidationException("$field is too long")
        }
        if (value?.contains('\u0000') == true) {
            throw AiValidationException("$field contains an invalid character")
        }
    }

    private val FAILURE_MESSAGE_PHRASES = listOf(
        "no sufficient", "insufficient", "no evidence", "evidence found", "not found",
        "no data", "no source", "could not", "cannot find", "unable to",
        "keine daten", "keine quelle", "nicht gefunden",
    )

    /** A provider must fail with an error, never smuggle failure text into data fields. */
    private fun rejectFailureMessage(value: String?, field: String) {
        val normalized = value?.lowercase(java.util.Locale.ROOT) ?: return
        if (FAILURE_MESSAGE_PHRASES.any(normalized::contains)) {
            throw AiValidationException(
                "The $field contains a research failure message instead of product data",
            )
        }
    }

    private fun validateTextList(values: List<String>, field: String) {
        if (values.size > MAX_TEXT_LIST_ITEMS) throw AiValidationException("Too many $field were returned")
        values.forEach { validateText(it, field, required = true, maxChars = MAX_DETAIL_CHARS) }
    }

    private fun requireConfidence(value: Double) {
        if (!value.isFinite() || value !in 0.0..1.0) {
            throw AiValidationException("Confidence is outside the valid range")
        }
    }

    private fun requireFinitePositive(value: Double, field: String, maximum: Double) {
        if (!value.isFinite() || value <= 0 || value > maximum) {
            throw AiValidationException("$field must be a finite value in the supported range")
        }
    }

    private fun requireFiniteNonNegative(value: Double, field: String, maximum: Double) {
        if (!value.isFinite() || value < 0 || value > maximum) {
            throw AiValidationException("$field must be a finite value in the supported range")
        }
    }

    private fun relativeDifference(a: Double, b: Double): Double = abs(a - b) / maxOf(abs(a), abs(b), 1e-9)
}

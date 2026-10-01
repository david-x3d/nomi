package com.nomi.app.ui.app

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.NutritionLabelReading
import com.nomi.app.ai.model.PortionAdjustment
import com.nomi.app.ai.validation.FoodDisplayName
import com.nomi.app.ai.validation.SourceIntegrityVerifier
import com.nomi.app.data.local.entity.FoodEntity
import com.nomi.app.data.local.entity.FoodLogEntity
import com.nomi.app.data.local.entity.NutritionSourceSnapshot
import com.nomi.app.data.local.entity.NutritionValues
import com.nomi.app.data.local.entity.toCitedUrlColumn
import com.nomi.app.data.local.model.FavoriteFoodWithCatalog
import com.nomi.app.data.remote.openfoodfacts.BarcodeProduct
import com.nomi.app.domain.DecimalInput
import com.nomi.app.domain.usecase.PortionEditApplier
import com.nomi.app.ui.library.LibraryItem
import com.nomi.app.ui.library.LibraryItemKind
import com.nomi.app.ui.localization.NomiLanguage
import com.nomi.app.ui.localization.NomiTranslations
import com.nomi.app.ui.logging.ManualFoodDraft
import com.nomi.app.ui.today.MealCategory
import com.nomi.app.ui.today.TodayFoodEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/*
 * Conversions between what research, a barcode, a label or the library hands over and the row
 * that is written to the food log. None of them reads view-model state: the day and zone a new
 * row lands on arrive as a [LogDestination].
 */

/** Where a new log row lands: the day being viewed, in the device's current zone. */
internal data class LogDestination(val date: LocalDate, val zone: ZoneId)

/** The meal a new entry most likely belongs to, going by the time of day in [zone]. */
internal fun defaultMealCategory(zone: ZoneId): MealCategory = when (LocalTime.now(zone).hour) {
    in 4..10 -> MealCategory.BREAKFAST
    in 11..15 -> MealCategory.LUNCH
    in 16..21 -> MealCategory.DINNER
    else -> MealCategory.SNACKS
}

internal fun AnalyzedFoodItem.toLog(
    category: MealCategory,
    inputMethod: String,
    destination: LogDestination,
    consultedUrls: List<String> = emptyList(),
    originalInput: String? = null,
): FoodLogEntity {
    val now = System.currentTimeMillis()
    val (date, zoneId) = destination
    val enteredServingUnit = quantityResolution?.enteredUnit
        ?.takeIf { it.isSpoonLoggingUnit() || it.isHouseholdCountLoggingUnit() }
    val enteredServingQuantity = quantityResolution?.enteredQuantity
        ?.takeIf { enteredServingUnit != null && it.isFinite() && it > 0.0 }
    return FoodLogEntity(
        mealCategory = category.name,
        displayNameSnapshot = name.trim(),
        brandSnapshot = brand,
        amount = enteredServingQuantity ?: quantity,
        unit = enteredServingUnit?.takeIf { enteredServingQuantity != null } ?: unit,
        grams = resolvedWeightGrams,
        resolvedVolumeMl = resolvedVolumeMl,
        resolutionSource = resolutionSource,
        nutritionSnapshot = NutritionValues(
            caloriesKcal = calories,
            proteinGrams = proteinGrams,
            carbohydrateGrams = carbohydrateGrams,
            fatGrams = fatGrams,
            fiberGrams = fiberGrams,
            sugarGrams = sugarGrams,
            saturatedFatGrams = saturatedFatGrams,
            sodiumMilligrams = sodiumMilligrams,
        ),
        sourceSnapshot = NutritionSourceSnapshot(
            kind = if (isEstimate) "ai_estimate" else "database",
            providerName = sourceName,
            displayName = sourceName,
            url = sourceUrl,
            // The primary source leads so the detail view can show it first without
            // re-deriving which of the citations the numbers actually came from. An estimate
            // cites nothing, but the research still opened pages to reach it, and those are
            // recorded instead of leaving the entry looking unresearched.
            citedUrls = (listOfNotNull(sourceUrl) + supportingSourceUrls)
                .ifEmpty { consultedUrls }
                .toCitedUrlColumn(),
            confidence = confidence,
            productName = sourceProductName,
            servingQuantity = sourceServingQuantity,
            servingUnit = sourceServingUnit,
            calorieExplanation = calorieExplanation,
            retrievedAtEpochMillis = now,
            verifiedAtEpochMillis = now.takeUnless { isEstimate },
        ),
        isEstimated = isEstimate,
        inputMethod = inputMethod,
        originalInput = originalInput?.trim()?.takeIf(String::isNotBlank),
        localDate = date.toString(),
        loggedAtEpochMillis = loggedAtFor(date, Instant.ofEpochMilli(now), zoneId),
        zoneId = zoneId.id,
        createdAtEpochMillis = now,
        updatedAtEpochMillis = now,
    )
}

/**
 * Stored verbatim so it is stable across releases and languages, and resolved for display
 * at the read boundary rather than baked in at write time. See [nomiString].
 */
internal const val MANUAL_SOURCE_NAME = "Manual entry"
private const val LIBRARY_SOURCE_NAME = "Nomi food library"

internal fun ManualFoodDraft.toLog(destination: LogDestination): FoodLogEntity {
    // DecimalInput so a comma decimal - which is what nine of the ten supported keyboards
    // produce - is the same number as a point decimal. isValid already gated this shape.
    fun field(raw: String, label: String): Double = DecimalInput.parseOrNull(raw)
        ?: throw IllegalArgumentException("$label must be a number")
    return AnalyzedFoodItem(
        name = name.trim(),
        quantity = field(amount, "Amount"),
        unit = unit.trim(),
        calories = field(calories, "Calories"),
        proteinGrams = field(protein, "Protein"),
        carbohydrateGrams = field(carbohydrates, "Carbohydrates"),
        fatGrams = field(fat, "Fat"),
        isEstimate = false,
        sourceName = MANUAL_SOURCE_NAME,
    ).toLog(mealCategory, "manual", destination)
}

internal fun FoodEntity.toLog(destination: LogDestination): FoodLogEntity = AnalyzedFoodItem(
    name = canonicalName,
    brand = brand,
    quantity = 100.0,
    unit = "g",
    gramsEquivalent = 100.0,
    calories = nutritionPer100g.caloriesKcal,
    proteinGrams = nutritionPer100g.proteinGrams,
    carbohydrateGrams = nutritionPer100g.carbohydrateGrams,
    fatGrams = nutritionPer100g.fatGrams,
    fiberGrams = nutritionPer100g.fiberGrams,
    sugarGrams = nutritionPer100g.sugarGrams,
    saturatedFatGrams = nutritionPer100g.saturatedFatGrams,
    sodiumMilligrams = nutritionPer100g.sodiumMilligrams,
    sourceName = LIBRARY_SOURCE_NAME,
    isEstimate = isEstimated,
).toLog(defaultMealCategory(destination.zone), "recent", destination).copy(foodId = id)

internal fun FavoriteFoodWithCatalog.toLog(destination: LogDestination): FoodLogEntity {
    val grams = favorite.typicalGrams ?: favorite.typicalAmount.takeIf { favorite.typicalUnit.equals("g", true) } ?: 100.0
    val factor = grams / 100.0
    val values = food.nutritionPer100g
    return AnalyzedFoodItem(
        name = food.canonicalName,
        brand = food.brand,
        quantity = favorite.typicalAmount,
        unit = favorite.typicalUnit,
        gramsEquivalent = grams,
        calories = values.caloriesKcal * factor,
        proteinGrams = values.proteinGrams * factor,
        carbohydrateGrams = values.carbohydrateGrams * factor,
        fatGrams = values.fatGrams * factor,
        fiberGrams = values.fiberGrams?.times(factor),
        sugarGrams = values.sugarGrams?.times(factor),
        saturatedFatGrams = values.saturatedFatGrams?.times(factor),
        sodiumMilligrams = values.sodiumMilligrams?.times(factor),
        sourceName = "Nomi favorite",
        isEstimate = food.isEstimated,
    ).toLog(defaultMealCategory(destination.zone), "favorite", destination).copy(foodId = food.id)
}

internal fun FoodEntity.toLibraryItem(kind: LibraryItemKind, amountText: String = "100 g") = LibraryItem(
    id = id,
    kind = kind,
    title = canonicalName,
    subtitle = listOfNotNull(brand, amountText).joinToString(" · "),
    calories = nutritionPer100g.caloriesKcal,
)

internal fun BarcodeProduct.toAnalyzedItemOrNull(): AnalyzedFoodItem? {
    val calories = caloriesPer100g?.takeIf { it.isFinite() && it in 0.0..1_500.0 } ?: return null
    val protein = proteinPer100g?.takeIf { it.isFinite() && it in 0.0..100.0 } ?: return null
    val carbohydrates = carbohydratesPer100g?.takeIf { it.isFinite() && it in 0.0..100.0 } ?: return null
    val fat = fatPer100g?.takeIf { it.isFinite() && it in 0.0..100.0 } ?: return null
    val basisUnit = nutritionBasisUnit.takeIf { it == "ml" } ?: "g"
    return SourceIntegrityVerifier.resolveItem(
        AnalyzedFoodItem(
            name = name.take(300),
            brand = brand?.take(200),
            quantity = 100.0,
            unit = basisUnit,
            gramsEquivalent = 100.0.takeIf { basisUnit == "g" },
            calories = calories,
            proteinGrams = protein,
            carbohydrateGrams = carbohydrates,
            fatGrams = fat,
            fiberGrams = fiberPer100g?.takeIf { it.isFinite() && it in 0.0..100.0 },
            sugarGrams = sugarPer100g?.takeIf { it.isFinite() && it in 0.0..100.0 },
            saturatedFatGrams = saturatedFatPer100g?.takeIf { it.isFinite() && it in 0.0..100.0 },
            // 100 g of pure salt carries 40,000 mg of sodium, so that bounds a per-100 value.
            sodiumMilligrams = sodiumMilligramsPer100g
                ?.takeIf { it.isFinite() && it in 0.0..40_000.0 },
            sourceName = sourceName,
            sourceUrl = sourceUrl,
            sourceProductName = name.take(300),
            sourceServingQuantity = 100.0,
            sourceServingUnit = basisUnit,
            sourceServingGramsEquivalent = 100.0.takeIf { basisUnit == "g" },
            isEstimate = false,
        ),
    )
}

internal fun FoodEntity.toAnalyzedItem(source: String) = AnalyzedFoodItem(
    name = canonicalName,
    brand = brand,
    quantity = 100.0,
    unit = "g",
    gramsEquivalent = 100.0,
    calories = nutritionPer100g.caloriesKcal,
    proteinGrams = nutritionPer100g.proteinGrams,
    carbohydrateGrams = nutritionPer100g.carbohydrateGrams,
    fatGrams = nutritionPer100g.fatGrams,
    fiberGrams = nutritionPer100g.fiberGrams,
    sugarGrams = nutritionPer100g.sugarGrams,
    saturatedFatGrams = nutritionPer100g.saturatedFatGrams,
    sodiumMilligrams = nutritionPer100g.sodiumMilligrams,
    sourceName = source,
    sourceServingQuantity = 100.0,
    sourceServingUnit = "g",
    sourceServingGramsEquivalent = 100.0,
    isEstimate = isEstimated,
)

internal fun AnalyzedFoodItem.asBarcodeSourceServing(): AnalyzedFoodItem = copy(
    sourceServingQuantity = quantity,
    sourceServingUnit = unit,
    sourceServingGramsEquivalent = gramsEquivalent,
    servingValidation = null,
    requiresServingValidation = false,
)

/**
 * A printed table is a source serving like any other, so it enters the pipeline the same
 * way an Open Food Facts product does. It is never an estimate: these numbers were read,
 * not guessed.
 */
internal fun NutritionLabelReading.toAnalyzedItem(language: NomiLanguage): AnalyzedFoodItem {
    val unit = basisUnit.trim().ifBlank { "g" }
    val grams = basisQuantity.takeIf { unit.equals("g", ignoreCase = true) }
    return AnalyzedFoodItem(
        name = FoodDisplayName.clean(
            productName?.takeIf(String::isNotBlank) ?: NomiTranslations.translate("Photographed label", language),
        ),
        brand = brand?.takeIf(String::isNotBlank)?.take(200),
        quantity = basisQuantity,
        unit = unit,
        gramsEquivalent = grams,
        calories = calories,
        proteinGrams = proteinGrams,
        carbohydrateGrams = carbohydrateGrams,
        fatGrams = fatGrams,
        fiberGrams = fiberGrams,
        sugarGrams = sugarGrams,
        saturatedFatGrams = saturatedFatGrams,
        sodiumMilligrams = sodiumMilligrams,
        sourceName = NomiTranslations.translate("Nutrition label photo", language),
        sourceProductName = productName?.takeIf(String::isNotBlank),
        sourceServingQuantity = basisQuantity,
        sourceServingUnit = unit,
        sourceServingGramsEquivalent = grams,
        sourcePackageQuantity = packageQuantity,
        sourcePackageUnit = packageUnit?.takeIf(String::isNotBlank),
        confidence = confidence,
        assumptions = notes,
        isEstimate = false,
    )
}

/** Presents a deterministic result in the shape the preview sheet already renders. */
internal fun PortionEditApplier.Result.toPortionAdjustment(): PortionAdjustment = PortionAdjustment(
    newQuantity = item.quantity,
    newUnit = item.unit,
    multiplier = factor,
    newGrams = item.gramsEquivalent,
    interpretation = description,
    requiresConfirmation = false,
)

internal operator fun NutritionValues.plus(other: NutritionValues) = NutritionValues(
    caloriesKcal = caloriesKcal + other.caloriesKcal,
    proteinGrams = proteinGrams + other.proteinGrams,
    carbohydrateGrams = carbohydrateGrams + other.carbohydrateGrams,
    fatGrams = fatGrams + other.fatGrams,
    fiberGrams = fiberGrams.plusOptional(other.fiberGrams),
    sugarGrams = sugarGrams.plusOptional(other.sugarGrams),
    saturatedFatGrams = saturatedFatGrams.plusOptional(other.saturatedFatGrams),
    sodiumMilligrams = sodiumMilligrams.plusOptional(other.sodiumMilligrams),
)

/**
 * Sums what is known and stays null while nothing is. A day whose foods never reported sugar
 * has no sugar total, which is a different statement from a day that genuinely contained none -
 * and the difference is what stops Today from showing a confident 0 g it cannot support.
 */
private fun Double?.plusOptional(other: Double?): Double? =
    if (this == null && other == null) null else (this ?: 0.0) + (other ?: 0.0)

internal fun String.toMealCategory(): MealCategory = runCatching {
    MealCategory.valueOf(trim().uppercase(Locale.ROOT))
}.getOrDefault(MealCategory.SNACKS)

internal fun Double.cleanNumber(): String = if (this == toLong().toDouble()) toLong().toString()
else String.format(Locale.US, "%.1f", this)

/** Minimal stored snapshot needed by the existing portion-only arithmetic/router. */
internal fun TodayFoodEntry.toAmountEditItem(): AnalyzedFoodItem = AnalyzedFoodItem(
    name = name,
    brand = brand,
    quantity = amount,
    unit = unit,
    gramsEquivalent = grams,
    calories = calories,
    proteinGrams = proteinGrams,
    carbohydrateGrams = carbohydrateGrams,
    fatGrams = fatGrams,
    sourceName = sourceName,
    sourceUrl = sourceUrl,
    calorieExplanation = calorieExplanation,
    isEstimate = isEstimated,
)

/** Prefills the amount field without a trailing ".0" on whole amounts. */
internal fun formatLoggedAmountInput(amount: Double): String =
    if (amount == amount.toLong().toDouble()) amount.toLong().toString() else amount.toString()

internal fun String.isSpoonLoggingUnit(): Boolean = trim()
    .lowercase(Locale.ROOT)
    .replace('ö', 'o')
    .replace("oe", "o") in setOf(
    "el", "essloffel", "tbsp", "tbs", "tablespoon", "tablespoons",
    "tl", "teeloffel", "tsp", "teaspoon", "teaspoons",
    "loffel", "spoon", "spoons",
)

internal fun String.isHouseholdCountLoggingUnit(): Boolean = trim()
    .lowercase(Locale.ROOT)
    .replace('\u00fc', 'u')
    .replace("ue", "u") in setOf(
    "piece", "pieces", "pc", "pcs", "stuck", "stucke",
    "kugel", "kugeln", "scoop", "scoops",
)

package com.nomi.app.ui.app

import com.nomi.app.data.local.entity.FoodLogEntity
import com.nomi.app.data.local.entity.NutritionPlanEntity
import com.nomi.app.data.local.entity.NutritionValues
import com.nomi.app.data.local.entity.citedUrlList
import com.nomi.app.data.preferences.GoalsCardStyle
import com.nomi.app.data.preferences.MicronutrientPreferences
import com.nomi.app.data.preferences.enabledMicronutrients
import com.nomi.app.data.preferences.resolvedTarget
import com.nomi.app.data.preferences.settingFor
import com.nomi.app.domain.Micronutrient
import com.nomi.app.ui.history.HistoryDay
import com.nomi.app.ui.history.HistoryUiState
import com.nomi.app.ui.localization.NomiLanguage
import com.nomi.app.ui.logging.groupedMealTitle
import com.nomi.app.ui.progress.ProgressRange
import com.nomi.app.ui.today.MacroProgress
import com.nomi.app.ui.today.MicronutrientProgress
import com.nomi.app.ui.today.TodayFoodEntry
import com.nomi.app.ui.today.TodayUiState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/*
 * Stored log rows to what the Today and History pages draw. Pure functions of their arguments,
 * so a day's totals and grouping can be checked without a view model or a database.
 */

internal fun mapToday(
    date: LocalDate,
    logs: List<FoodLogEntity>,
    plan: NutritionPlanEntity?,
    micronutrients: MicronutrientPreferences,
    goalsCardStyle: GoalsCardStyle,
    freshInputs: Map<String, String>,
    language: NomiLanguage,
    fallbackZone: ZoneId,
): TodayUiState {
    val totals = logs.fold(NutritionValues()) { total, log -> total + log.nutritionSnapshot }
    return TodayUiState(
        date = date,
        caloriesConsumed = totals.caloriesKcal,
        calorieTarget = plan?.calorieTargetKcal ?: 2_000.0,
        protein = MacroProgress(totals.proteinGrams, plan?.proteinTargetGrams ?: 130.0),
        carbohydrates = MacroProgress(totals.carbohydrateGrams, plan?.carbohydrateTargetGrams ?: 240.0),
        fat = MacroProgress(totals.fatGrams, plan?.fatTargetGrams ?: 65.0),
        micronutrients = micronutrients.toProgress(logs, totals),
        entries = logs.toGroupedTodayEntries(language, fallbackZone, freshInputs),
        goalsCardStyle = goalsCardStyle,
    )
}

/**
 * Builds the day's micronutrient rows for the nutrients the user chose to track.
 *
 * A row is marked partial when only some of the day's foods reported the nutrient, because
 * a total assembled from half the plate is a floor rather than an answer, and the card says
 * so instead of presenting it as complete.
 */
private fun MicronutrientPreferences.toProgress(
    logs: List<FoodLogEntity>,
    totals: NutritionValues,
): List<MicronutrientProgress> = enabledMicronutrients().map { nutrient ->
    val reportingLogs = logs.count { nutrient.amountIn(it.nutritionSnapshot) != null }
    MicronutrientProgress(
        nutrient = nutrient,
        consumed = nutrient.amountIn(totals),
        target = settingFor(nutrient).resolvedTarget(nutrient),
        isPartial = reportingLogs in 1 until logs.size,
    )
}

/** Reads one optional nutrient out of a stored snapshot, keeping "not reported" as null. */
private fun Micronutrient.amountIn(values: NutritionValues): Double? = when (this) {
    Micronutrient.FIBER -> values.fiberGrams
    Micronutrient.SUGAR -> values.sugarGrams
    Micronutrient.SATURATED_FAT -> values.saturatedFatGrams
    Micronutrient.SODIUM -> values.sodiumMilligrams
}

internal fun mapHistory(
    logs: List<FoodLogEntity>,
    query: String,
    selected: LocalDate,
    plan: NutritionPlanEntity?,
    language: NomiLanguage,
    fallbackZone: ZoneId,
): HistoryUiState {
    val filtered = query.trim().lowercase(Locale.ROOT).let { normalized ->
        if (normalized.isBlank()) logs else logs.filter {
            it.displayNameSnapshot.lowercase(Locale.ROOT).contains(normalized) ||
                it.brandSnapshot?.lowercase(Locale.ROOT)?.contains(normalized) == true
        }
    }
    val days = filtered.groupBy { LocalDate.parse(it.localDate) }
        .toSortedMap(compareByDescending { it })
        .map { (date, entries) ->
            val nutrition = entries.fold(NutritionValues()) { total, log -> total + log.nutritionSnapshot }
            HistoryDay(
                date = date,
                calories = nutrition.caloriesKcal,
                calorieTarget = plan?.calorieTargetKcal ?: 2_000.0,
                proteinGrams = nutrition.proteinGrams,
                carbohydrateGrams = nutrition.carbohydrateGrams,
                fatGrams = nutrition.fatGrams,
                entries = entries.toGroupedTodayEntries(language, fallbackZone),
            )
        }
    return HistoryUiState(query, selected, days, isSearching = false)
}

/** Keeps one compact Today row per meal group while retaining every product for drill-down. */
internal fun List<FoodLogEntity>.toGroupedTodayEntries(
    language: NomiLanguage,
    fallbackZone: ZoneId,
    freshInputs: Map<String, String> = emptyMap(),
): List<TodayFoodEntry> = groupBy { log -> log.entryGroupId ?: "single:${log.id}" }
    .values
    .sortedBy { group -> group.minOf(FoodLogEntity::loggedAtEpochMillis) }
    .map { group ->
        val items = group
            .sortedWith(compareBy<FoodLogEntity> { it.loggedAtEpochMillis }.thenBy { it.id })
            .map { log ->
                log.toTodayEntry(fallbackZone, revealText = log.entryGroupId?.let(freshInputs::get))
            }
        if (items.size == 1) return@map items.single()

        val first = items.first()
        val title = groupedMealTitle(items.map(TodayFoodEntry::name), language)
        TodayFoodEntry(
            id = first.id,
            name = title,
            amountText = "",
            calories = items.sumOf(TodayFoodEntry::calories),
            proteinGrams = items.sumOf(TodayFoodEntry::proteinGrams),
            carbohydrateGrams = items.sumOf(TodayFoodEntry::carbohydrateGrams),
            fatGrams = items.sumOf(TodayFoodEntry::fatGrams),
            fiberGrams = items.mapNotNull(TodayFoodEntry::fiberGrams)
                .takeIf(List<Double>::isNotEmpty)?.sum(),
            sugarGrams = items.mapNotNull(TodayFoodEntry::sugarGrams)
                .takeIf(List<Double>::isNotEmpty)?.sum(),
            saturatedFatGrams = items.mapNotNull(TodayFoodEntry::saturatedFatGrams)
                .takeIf(List<Double>::isNotEmpty)?.sum(),
            sodiumMilligrams = items.mapNotNull(TodayFoodEntry::sodiumMilligrams)
                .takeIf(List<Double>::isNotEmpty)?.sum(),
            mealCategory = first.mealCategory,
            time = first.time,
            isEstimated = items.any(TodayFoodEntry::isEstimated),
            citedSourceUrls = items.flatMap(TodayFoodEntry::citedSourceUrls).distinct(),
            confidence = items.mapNotNull(TodayFoodEntry::confidence).minOrNull(),
            groupItems = items,
            originalInput = first.originalInput,
            revealText = first.revealText,
        )
    }

/** [fallbackZone] only applies to a row whose stored zone id can no longer be parsed. */
internal fun FoodLogEntity.toTodayEntry(
    fallbackZone: ZoneId,
    revealText: String? = null,
): TodayFoodEntry = TodayFoodEntry(
    id = id,
    name = displayNameSnapshot,
    brand = brandSnapshot,
    amountText = "${amount.cleanNumber()} $unit",
    calories = nutritionSnapshot.caloriesKcal,
    proteinGrams = nutritionSnapshot.proteinGrams,
    carbohydrateGrams = nutritionSnapshot.carbohydrateGrams,
    fatGrams = nutritionSnapshot.fatGrams,
    fiberGrams = nutritionSnapshot.fiberGrams,
    sugarGrams = nutritionSnapshot.sugarGrams,
    saturatedFatGrams = nutritionSnapshot.saturatedFatGrams,
    sodiumMilligrams = nutritionSnapshot.sodiumMilligrams,
    mealCategory = mealCategory.toMealCategory(),
    time = Instant.ofEpochMilli(loggedAtEpochMillis)
        .atZone(runCatching { ZoneId.of(zoneId) }.getOrDefault(fallbackZone))
        .toLocalTime(),
    isEstimated = isEstimated,
    foodId = foodId,
    amount = amount,
    unit = unit,
    grams = grams,
    sourceName = sourceSnapshot.displayName,
    sourceUrl = sourceSnapshot.url,
    citedSourceUrls = sourceSnapshot.citedUrlList(),
    confidence = sourceSnapshot.confidence,
    sourceProductName = sourceSnapshot.productName,
    sourceServingQuantity = sourceSnapshot.servingQuantity,
    sourceServingUnit = sourceSnapshot.servingUnit,
    calorieExplanation = sourceSnapshot.calorieExplanation,
    inputMethod = inputMethod,
    originalInput = originalInput,
    revealText = revealText,
)

internal fun ProgressRange.dayCount(): Int = when (this) {
    ProgressRange.SEVEN_DAYS -> 7
    ProgressRange.THIRTY_DAYS -> 30
    ProgressRange.THREE_MONTHS -> 90
    ProgressRange.SIX_MONTHS -> 180
    ProgressRange.ONE_YEAR -> 365
    ProgressRange.ALL -> 3_650
}
